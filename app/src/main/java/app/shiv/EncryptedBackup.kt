package app.shiv

import android.content.Context
import android.net.Uri
import android.util.JsonWriter
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.io.IOException
import java.io.File
import javax.crypto.CipherOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** Password-encrypted portable backup. The passphrase is never persisted. */
class EncryptedBackup(context: Context) {
    private val app = context.applicationContext
    private val database = ShivDatabase.get(app)
    private val dao = database.dao()

    suspend fun export(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        try {
            require(passphrase.size >= 12) { "Use a passphrase with at least 12 characters." }
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveBackupKey(passphrase, salt), GCMParameterSpec(128, iv))
            cipher.updateAAD(MAGIC)
            val temporary = File.createTempFile("shiv-backup-", ".tmp", app.cacheDir)
            try {
                try {
                    temporary.outputStream().use { raw ->
                        raw.write(MAGIC)
                        raw.write(salt)
                        raw.write(iv)
                        val encrypted = CipherOutputStream(raw, cipher)
                        val bounded = BoundedOutputStream(encrypted, MAX_BACKUP_PLAINTEXT_BYTES)
                        val writer = JsonWriter(java.io.OutputStreamWriter(bounded, Charsets.UTF_8))
                        writer.isLenient = false
                        try {
                            writeSnapshot(writer)
                            writer.flush()
                        } finally {
                            writer.close()
                        }
                    }
                } catch (failure: Throwable) {
                    if (failure is IOException && failure.message?.contains("maximum supported size") == true) {
                        throw IllegalArgumentException("This backup is too large to export safely (150 MiB maximum).", failure)
                    }
                    throw failure
                }
                // Do not touch the user-selected destination until the complete capped backup exists.
                val output = app.contentResolver.openOutputStream(uri, "wt")
                requireNotNull(output) { "Could not create the selected backup file." }.use { destination ->
                    temporary.inputStream().use { it.copyTo(destination, 8192) }
                    destination.flush()
                }
            } finally {
                temporary.delete()
            }
        } finally {
            passphrase.fill('\u0000')
        }
    }

    suspend fun restore(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        try {
        val packed = app.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open the selected backup file." }.use { readBoundedBytes(it, MAX_BACKUP_BYTES) }
        }
        val json = JSONObject(String(decryptBackupPayload(packed, passphrase), Charsets.UTF_8))
        requireBackupFields(json.keys().asSequence().toSet())
        require(json.optInt("format") == 1) { "This backup format is not supported." }
        val profileValue = json.get("profile")
        val profile = if (profileValue == JSONObject.NULL) UserProfile() else readProfile(json.getJSONObject("profile"))
        val messages = readArray(json, "messages", ::readMessage)
        val memories = readArray(json, "memories", ::readMemory)
        val events = readArray(json, "events", ::readEvent)
        val pendingEvents = readArray(json, "pendingEvents", ::readPendingEvent)
        val actions = readArray(json, "actions", ::readAction)
        val rules = readArray(json, "rules", ::readRule)
        val drafts = readArray(json, "drafts", ::readDraft)
        val tiers = readArray(json, "actionTiers", ::readTier)
        val settings = readSettings(json.getJSONObject("settings"))

        // Replace existing data only after the file has decrypted and every record has parsed.
        database.withTransaction {
            dao.clearMessages(); dao.clearProfile(); dao.clearMemories(); dao.clearEvents(); dao.clearPendingEvents()
            dao.clearLog(); dao.clearRules(); dao.clearDrafts(); dao.clearActionTiers(); dao.clearSettings()
            dao.saveProfile(profile)
            dao.saveMessages(messages); dao.saveMemories(memories); dao.saveEvents(events); dao.savePendingEvents(pendingEvents)
            dao.saveActions(actions); dao.saveRules(rules); dao.saveDrafts(drafts); dao.saveActionTiers(tiers); dao.saveSettings(settings)
        }
        } finally {
            passphrase.fill('\u0000')
        }
    }

    private suspend fun writeSnapshot(writer: JsonWriter) {
        writer.beginObject()
        writer.name("format").value(1)
        writer.name("createdAt").value(System.currentTimeMillis())
        writer.name("profile")
        val profile = dao.profileOnce()
        if (profile == null) writer.nullValue() else writeProfile(writer, profile)
        writePagedArray(writer, "messages", dao::messagesPage, ::writeMessage)
        writePagedArray(writer, "memories", dao::memoriesPage, ::writeMemory)
        writePagedArray(writer, "events", dao::eventsPage, ::writeEvent)
        writePagedArray(writer, "pendingEvents", dao::pendingEventsPage, ::writePendingEvent)
        writePagedArray(writer, "actions", dao::actionsPage, ::writeAction)
        writePagedArray(writer, "rules", dao::rulesPage, ::writeRule)
        writePagedArray(writer, "drafts", dao::draftsPage, ::writeDraft)
        writePagedArray(writer, "actionTiers", dao::actionTiersPage, ::writeTier)
        writer.name("settings")
        writeSettings(writer, dao.settingsOnce() ?: ShivSettings())
        writer.endObject()
    }

    private suspend fun <T> writePagedArray(
        writer: JsonWriter,
        name: String,
        fetchPage: suspend (Int, Int) -> List<T>,
        writeItem: (JsonWriter, T) -> Unit
    ) {
        writer.name(name).beginArray()
        var offset = 0
        while (true) {
            val page = fetchPage(PAGE_SIZE, offset)
            if (page.isEmpty()) break
            page.forEach { writeItem(writer, it) }
            offset += page.size
            if (page.size < PAGE_SIZE) break
        }
        writer.endArray()
    }

    private fun writeProfile(w: JsonWriter, v: UserProfile) {
        w.beginObject().name("id").value(v.id).name("name").value(v.name)
            .name("notes").value(v.notes).name("updatedAt").value(v.updatedAt).endObject()
    }
    private fun writeMessage(w: JsonWriter, v: ChatMessage) {
        w.beginObject().name("id").value(v.id).name("text").value(v.text)
            .name("fromUser").value(v.fromUser).name("createdAt").value(v.createdAt).name("tier")
        if (v.tier == null) w.nullValue() else w.value(v.tier)
        w.endObject()
    }
    private fun writeMemory(w: JsonWriter, v: MemoryFact) {
        w.beginObject().name("id").value(v.id).name("text").value(v.text)
            .name("createdAt").value(v.createdAt).endObject()
    }
    private fun writeEvent(w: JsonWriter, v: EventRecord) {
        w.beginObject().name("id").value(v.id).name("title").value(v.title).name("startsAt").value(v.startsAt)
        writeNullable(w, "place", v.place); writeNullable(w, "person", v.person)
        w.name("source").value(v.source).name("createdAt").value(v.createdAt).endObject()
    }
    private fun writePendingEvent(w: JsonWriter, v: PendingEventRecord) {
        w.beginObject().name("id").value(v.id).name("title").value(v.title).name("startsAt").value(v.startsAt)
        writeNullable(w, "place", v.place); writeNullable(w, "person", v.person)
        w.name("createdAt").value(v.createdAt).endObject()
    }
    private fun writeAction(w: JsonWriter, v: ActionLogEntry) {
        w.beginObject().name("id").value(v.id).name("action").value(v.action).name("tier").value(v.tier)
            .name("outcome").value(v.outcome).name("createdAt").value(v.createdAt).endObject()
    }
    private fun writeRule(w: JsonWriter, v: ReminderRule) {
        w.beginObject().name("ruleKey").value(v.ruleKey).name("value").value(v.value)
            .name("updatedAt").value(v.updatedAt).endObject()
    }
    private fun writeDraft(w: JsonWriter, v: ReplyDraft) {
        w.beginObject().name("id").value(v.id).name("person").value(v.person).name("body").value(v.body)
            .name("createdAt").value(v.createdAt).endObject()
    }
    private fun writeTier(w: JsonWriter, v: ActionTierSetting) {
        w.beginObject().name("actionKey").value(v.actionKey).name("tier").value(v.tier).endObject()
    }
    private fun writeSettings(w: JsonWriter, v: ShivSettings) {
        w.beginObject().name("id").value(v.id).name("proactiveEnabled").value(v.proactiveEnabled)
            .name("quietStartMinute").value(v.quietStartMinute).name("quietEndMinute").value(v.quietEndMinute)
            .name("maxDailyInterruptions").value(v.maxDailyInterruptions).endObject()
    }
    private fun writeNullable(w: JsonWriter, name: String, value: String?) {
        w.name(name)
        if (value == null) w.nullValue() else w.value(value)
    }


    private fun readProfile(o: JSONObject) = UserProfile(o.optInt("id", 1), o.getString("name"), o.getString("notes"), o.getLong("updatedAt"))
    private fun readMessage(o: JSONObject) = ChatMessage(o.getString("id"), o.getString("text"), o.getBoolean("fromUser"), o.getLong("createdAt"), o.optString("tier").takeUnless { it == "null" || it.isEmpty() })
    private fun readMemory(o: JSONObject) = MemoryFact(o.getString("id"), o.getString("text"), o.getLong("createdAt"))
    private fun readEvent(o: JSONObject) = EventRecord(o.getString("id"), o.getString("title"), o.getLong("startsAt"), o.optString("place").takeUnless { it == "null" || it.isEmpty() }, o.optString("person").takeUnless { it == "null" || it.isEmpty() }, o.optString("source", "CHAT"), o.getLong("createdAt"))
    private fun readPendingEvent(o: JSONObject) = PendingEventRecord(o.getString("id"), o.getString("title"), o.getLong("startsAt"), o.optString("place").takeUnless { it == "null" || it.isEmpty() }, o.optString("person").takeUnless { it == "null" || it.isEmpty() }, o.getLong("createdAt"))
    private fun readAction(o: JSONObject) = ActionLogEntry(o.getString("id"), o.getString("action"), o.getString("tier"), o.getString("outcome"), o.getLong("createdAt"))
    private fun readRule(o: JSONObject) = ReminderRule(o.getString("ruleKey"), o.getString("value"), o.getLong("updatedAt"))
    private fun readDraft(o: JSONObject) = ReplyDraft(o.getString("id"), o.getString("person"), o.getString("body"), o.getLong("createdAt"))
    private fun readTier(o: JSONObject) = ActionTierSetting(o.getString("actionKey"), o.getString("tier"))
    private fun readSettings(o: JSONObject) = ShivSettings(o.optInt("id", 1), o.optBoolean("proactiveEnabled", true), o.optInt("quietStartMinute", 1320), o.optInt("quietEndMinute", 480), o.optInt("maxDailyInterruptions", 3).coerceIn(0, 20))
    private fun <T> readArray(root: JSONObject, key: String, read: (JSONObject) -> T): List<T> {
        val array = root.optJSONArray(key)
            ?: throw IllegalArgumentException("This backup is incomplete; required array '$key' is missing or invalid.")
        return (0 until array.length()).map { index -> read(array.getJSONObject(index)) }
    }

    private companion object {
        val MAGIC = "SHIVBK01".toByteArray(Charsets.US_ASCII)
        const val PAGE_SIZE = 128
    }
}
