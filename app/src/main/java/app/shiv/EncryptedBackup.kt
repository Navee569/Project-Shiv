package app.shiv

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-encrypted portable backup. The passphrase is never persisted. */
class EncryptedBackup(context: Context) {
    private val app = context.applicationContext
    private val database = ShivDatabase.get(app)
    private val dao = database.dao()

    suspend fun export(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        require(passphrase.size >= 12) { "Use a passphrase with at least 12 characters." }
        val payload = makeSnapshot().toString().toByteArray(Charsets.UTF_8)
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val encrypted = cipher.doFinal(payload)
        app.contentResolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "Could not create the selected backup file." }.use {
                it.write(MAGIC); it.write(salt); it.write(iv); it.write(encrypted); it.flush()
            }
        }
    }

    suspend fun restore(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val packed = app.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open the selected backup file." }.use { it.readBytes() }
        }
        require(packed.size in (MAGIC.size + 16 + 12 + 16)..MAX_BACKUP_BYTES) { "This backup file has an invalid size." }
        require(packed.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "This is not a Shiv encrypted backup." }
        val salt = packed.copyOfRange(MAGIC.size, MAGIC.size + 16)
        val iv = packed.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val json = JSONObject(String(cipher.doFinal(packed.copyOfRange(MAGIC.size + 28, packed.size)), Charsets.UTF_8))
        require(json.optInt("format") == 1) { "This backup format is not supported." }
        val profile = json.optJSONObject("profile")?.let(::readProfile) ?: UserProfile()
        val messages = readArray(json, "messages", ::readMessage)
        val memories = readArray(json, "memories", ::readMemory)
        val events = readArray(json, "events", ::readEvent)
        val pendingEvents = readArray(json, "pendingEvents", ::readPendingEvent)
        val actions = readArray(json, "actions", ::readAction)
        val rules = readArray(json, "rules", ::readRule)
        val drafts = readArray(json, "drafts", ::readDraft)
        val tiers = readArray(json, "actionTiers", ::readTier)
        val settings = json.optJSONObject("settings")?.let(::readSettings) ?: ShivSettings()

        // Replace existing data only after the file has decrypted and every record has parsed.
        database.withTransaction {
            dao.clearMessages(); dao.clearProfile(); dao.clearMemories(); dao.clearEvents(); dao.clearPendingEvents()
            dao.clearLog(); dao.clearRules(); dao.clearDrafts(); dao.clearActionTiers(); dao.clearSettings()
            dao.saveProfile(profile)
            dao.saveMessages(messages); dao.saveMemories(memories); dao.saveEvents(events); dao.savePendingEvents(pendingEvents)
            dao.saveActions(actions); dao.saveRules(rules); dao.saveDrafts(drafts); dao.saveActionTiers(tiers); dao.saveSettings(settings)
        }
    }

    private suspend fun makeSnapshot() = JSONObject()
        .put("format", 1)
        .put("createdAt", System.currentTimeMillis())
        .put("profile", dao.profileOnce()?.let(::profileJson) ?: JSONObject.NULL)
        .put("messages", jsonArrayTyped(dao.allMessages(), ::messageJson))
        .put("memories", jsonArrayTyped(dao.allMemories(), ::memoryJson))
        .put("events", jsonArrayTyped(dao.allEvents(), ::eventJson))
        .put("pendingEvents", jsonArrayTyped(dao.allPendingEvents(), ::pendingEventJson))
        .put("actions", jsonArrayTyped(dao.allActions(), ::actionJson))
        .put("rules", jsonArrayTyped(dao.allRules(), ::ruleJson))
        .put("drafts", jsonArrayTyped(dao.allDrafts(), ::draftJson))
        .put("actionTiers", jsonArrayTyped(dao.allActionTiers(), ::tierJson))
        .put("settings", settingsJson(dao.settingsOnce() ?: ShivSettings()))

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 310_000, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }

    private fun <T> jsonArrayTyped(values: List<T>, convert: (T) -> JSONObject) = JSONArray().apply { values.forEach { put(convert(it)) } }
    private fun profileJson(v: UserProfile) = JSONObject().put("id", v.id).put("name", v.name).put("notes", v.notes).put("updatedAt", v.updatedAt)
    private fun messageJson(v: ChatMessage) = JSONObject().put("id", v.id).put("text", v.text).put("fromUser", v.fromUser).put("createdAt", v.createdAt).put("tier", v.tier)
    private fun memoryJson(v: MemoryFact) = JSONObject().put("id", v.id).put("text", v.text).put("createdAt", v.createdAt)
    private fun eventJson(v: EventRecord) = JSONObject().put("id", v.id).put("title", v.title).put("startsAt", v.startsAt).put("place", v.place).put("person", v.person).put("source", v.source).put("createdAt", v.createdAt)
    private fun pendingEventJson(v: PendingEventRecord) = JSONObject().put("id", v.id).put("title", v.title).put("startsAt", v.startsAt).put("place", v.place).put("person", v.person).put("createdAt", v.createdAt)
    private fun actionJson(v: ActionLogEntry) = JSONObject().put("id", v.id).put("action", v.action).put("tier", v.tier).put("outcome", v.outcome).put("createdAt", v.createdAt)
    private fun ruleJson(v: ReminderRule) = JSONObject().put("ruleKey", v.ruleKey).put("value", v.value).put("updatedAt", v.updatedAt)
    private fun draftJson(v: ReplyDraft) = JSONObject().put("id", v.id).put("person", v.person).put("body", v.body).put("createdAt", v.createdAt)
    private fun tierJson(v: ActionTierSetting) = JSONObject().put("actionKey", v.actionKey).put("tier", v.tier)
    private fun settingsJson(v: ShivSettings) = JSONObject().put("id", v.id).put("proactiveEnabled", v.proactiveEnabled).put("quietStartMinute", v.quietStartMinute).put("quietEndMinute", v.quietEndMinute).put("maxDailyInterruptions", v.maxDailyInterruptions)
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
        val array = root.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { index -> read(array.getJSONObject(index)) }
    }

    private companion object {
        val MAGIC = "SHIVBK01".toByteArray(Charsets.US_ASCII)
        const val MAX_BACKUP_BYTES = 150 * 1024 * 1024
    }
}
