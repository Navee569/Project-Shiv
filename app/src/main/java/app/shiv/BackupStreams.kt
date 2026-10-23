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
    require(json.opt("format") is Number && json.getInt("format") == 1) {
        "This backup format is not supported."
    }
    require(json.opt("createdAt") is Number && json.getLong("createdAt") >= 0L) {
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
}

internal fun requireSettingsSchema(settings: JSONObject) {
    val fields = mapOf(
        "id" to Number::class.java,
        "proactiveEnabled" to Boolean::class.java,
        "quietStartMinute" to Number::class.java,
        "quietEndMinute" to Number::class.java,
        "maxDailyInterruptions" to Number::class.java
    )
    fields.forEach { (key, type) ->
        val value = settings.opt(key)
        require(value != null && type.isInstance(value)) {
            "This backup has a missing or invalid settings field '$key'."
        }
    }
    require(settings.getInt("id") > 0) { "This backup has an invalid settings id." }
    require(settings.getInt("quietStartMinute") in 0..1439) { "This backup has an invalid quiet start minute." }
    require(settings.getInt("quietEndMinute") in 0..1439) { "This backup has an invalid quiet end minute." }
    require(settings.getInt("maxDailyInterruptions") in 0..20) { "This backup has an invalid daily interruption limit." }
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
