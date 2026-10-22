package app.shiv

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal const val MAX_BACKUP_BYTES = 150 * 1024 * 1024
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
