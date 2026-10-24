package app.shiv

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal const val MAX_BACKUP_BYTES = 150 * 1024 * 1024

/** Required fields emitted by every supported backup. Missing collections must never mean "empty". */
internal val REQUIRED_BACKUP_FIELDS = setOf(
    "format", "createdAt", "profile", "messages", "memories", "events",
    "pendingEvents", "actions", "rules", "drafts", "actionTiers", "settings"
)

internal fun requireBackupFields(present: Set<String>) {
    val missing = REQUIRED_BACKUP_FIELDS - present
    require(missing.isEmpty()) {
        "This backup is incomplete; missing required fields: ${missing.sorted().joinToString(", ")}."
    }
}
internal fun validateBackupEnvelope(json: JSONObject) {
    requireBackupFields(json.keys().asSequence().toSet())
    require(requireJsonLong(json, "format", 1L..1L) == 1L) {
        "This backup format is not supported."
    }
    require(requireJsonLong(json, "createdAt", 0L..Long.MAX_VALUE) >= 0L) {
        "This backup has an invalid creation timestamp."
    }
    val profile = json.get("profile")
    require(profile == JSONObject.NULL || profile is JSONObject) {
        "This backup has an invalid profile object."
    }
    val arrayFields = REQUIRED_BACKUP_FIELDS - setOf("format", "createdAt", "profile", "settings")
    arrayFields.forEach { key ->
        require(json.opt(key) is JSONArray) {
            "This backup is incomplete; required array '$key' is missing or invalid."
        }
    }
    val settings = json.opt("settings")
    require(settings is JSONObject) { "This backup has an invalid settings object." }
    requireSettingsSchema(settings as JSONObject)
    validateBackupRecords(json)
}

/** Validate record types before any Room write; JSONObject getters otherwise coerce strings. */
private fun validateBackupRecords(root: JSONObject) {
    fun record(o: JSONObject, strings: List<String>, longs: List<String>,
               nullable: List<String> = emptyList(), booleans: List<String> = emptyList()) {
        strings.forEach { require(o.opt(it) is String) { "Invalid backup string '$it'." } }
        longs.forEach { requireJsonLong(o, it, 0L..Long.MAX_VALUE) }
        nullable.forEach {
            require(o.has(it) && (o.get(it) == JSONObject.NULL || o.get(it) is String)) {
                "Invalid nullable backup string '$it'."
            }
        }
        booleans.forEach { require(o.opt(it) is Boolean) { "Invalid backup Boolean '$it'." } }
    }
    if (!root.isNull("profile")) {
        val p = root.getJSONObject("profile")
        record(p, listOf("name", "notes"), listOf("updatedAt"))
        requireJsonLong(p, "id", 1L..1L)
    }
    val arrays = listOf("messages", "memories", "events", "pendingEvents", "actions", "rules", "drafts", "actionTiers")
    arrays.forEach { key ->
        val array = root.getJSONArray(key)
        for (index in 0 until array.length()) {
            val o = array.opt(index)
            require(o is JSONObject) { "Invalid backup record '$key' at $index." }
            when (key) {
                "messages" -> record(o, listOf("id", "text"), listOf("createdAt"), listOf("tier"), listOf("fromUser"))
                "memories" -> record(o, listOf("id", "text"), listOf("createdAt"))
                "events" -> record(o, listOf("id", "title", "source"), listOf("startsAt", "createdAt"), listOf("place", "person"))
                "pendingEvents" -> record(o, listOf("id", "title"), listOf("startsAt", "createdAt"), listOf("place", "person"))
                "actions" -> record(o, listOf("id", "action", "tier", "outcome"), listOf("createdAt"))
                "rules" -> record(o, listOf("ruleKey", "value"), listOf("updatedAt"))
                "drafts" -> record(o, listOf("id", "person", "body"), listOf("createdAt"))
                "actionTiers" -> record(o, listOf("actionKey", "tier"), emptyList())
            }
        }
    }
}

internal fun requireSettingsSchema(settings: JSONObject) {
    requireJsonLong(settings, "id", 1L..1L)
    require(settings.opt("proactiveEnabled") is Boolean) {
        "This backup has an invalid proactiveEnabled setting."
    }
    requireJsonLong(settings, "quietStartMinute", 0L..1439L)
    requireJsonLong(settings, "quietEndMinute", 0L..1439L)
    requireJsonLong(settings, "maxDailyInterruptions", 0L..20L)
}

/** Reject coercion, fractions and overflow before converting untrusted JSON numbers. */
internal fun requireJsonLong(json: JSONObject, key: String, range: LongRange): Long {
    val raw = json.opt(key)
    require(raw is Number) { "This backup has a missing or nonnumeric '$key'." }
    val value = try {
        java.math.BigDecimal(raw.toString()).longValueExact()
    } catch (failure: ArithmeticException) {
        throw IllegalArgumentException("This backup has a nonintegral or overflowing '$key'.", failure)
    } catch (failure: NumberFormatException) {
        throw IllegalArgumentException("This backup has an invalid '$key'.", failure)
    }
    require(value in range) { "This backup has an out-of-range '$key'." }
    return value
}

internal const val BACKUP_HEADER_BYTES = 8 + 16 + 12
internal const val BACKUP_TAG_BYTES = 16
internal const val MAX_BACKUP_PLAINTEXT_BYTES = MAX_BACKUP_BYTES - BACKUP_HEADER_BYTES - BACKUP_TAG_BYTES

/** Read at most maxBytes + 1 bytes, rejecting as soon as the input exceeds its cap. */
internal fun readBoundedBytes(input: InputStream, maxBytes: Int): ByteArray {
    require(maxBytes >= 0) { "Maximum size must not be negative" }
    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val allowance = maxBytes + 1 - total
        if (allowance <= 0) throw IllegalArgumentException("Input exceeds the maximum supported size.")
        val count = input.read(buffer, 0, minOf(buffer.size, allowance))
        if (count < 0) break
        if (count == 0) {
            val value = input.read()
            if (value < 0) break
            total++
            if (total > maxBytes) throw IllegalArgumentException("Input exceeds the maximum supported size.")
            output.write(value)
        } else {
            total += count
            if (total > maxBytes) throw IllegalArgumentException("Input exceeds the maximum supported size.")
            output.write(buffer, 0, count)
        }
    }
    return output.toByteArray()
}

/** Enforces a byte limit before forwarding each write to the wrapped stream. */
internal class BoundedOutputStream(
    output: OutputStream,
    private val maxBytes: Int
) : FilterOutputStream(output) {
    private var written = 0L

    init { require(maxBytes >= 0) { "Maximum size must not be negative" } }

    override fun write(value: Int) {
        ensureCapacity(1)
        out.write(value)
        written++
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || length > buffer.size - offset) {
            throw IndexOutOfBoundsException()
        }
        ensureCapacity(length)
        out.write(buffer, offset, length)
        written += length
    }

    private fun ensureCapacity(additional: Int) {
        if (written + additional > maxBytes.toLong()) {
            throw IOException("Output exceeds the maximum supported size.")
        }
    }
}

/** Validates the bounded portable-backup envelope and authenticates it before JSON parsing. */
internal fun decryptBackupPayload(packed: ByteArray, passphrase: CharArray): ByteArray {
    val magic = "SHIVBK01".toByteArray(Charsets.US_ASCII)
    require(packed.size in (BACKUP_HEADER_BYTES + BACKUP_TAG_BYTES)..MAX_BACKUP_BYTES) {
        "This backup file has an invalid size."
    }
    require(packed.copyOfRange(0, magic.size).contentEquals(magic)) {
        "This is not a Shiv encrypted backup."
    }
    val salt = packed.copyOfRange(magic.size, magic.size + 16)
    val iv = packed.copyOfRange(magic.size + 16, magic.size + 28)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, deriveBackupKey(passphrase, salt), GCMParameterSpec(128, iv))
    cipher.updateAAD(magic)
    return cipher.doFinal(packed.copyOfRange(magic.size + 28, packed.size))
}

internal fun deriveBackupKey(password: CharArray, salt: ByteArray): SecretKeySpec {
    val spec = PBEKeySpec(password, salt, 310_000, 256)
    return try {
        SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    } finally {
        spec.clearPassword()
    }
}
