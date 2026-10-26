package com.shiv.assistant.data

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated portable encryption independent of Android Keystore.
 * Owners must keep the passphrase: it cannot be recovered by Shiv or the cloud.
 * No plaintext intermediate files, secrets in logs, or network requests.
 */
object PortableBackupCodec {
    private val magic = byteArrayOf(0x53,0x48,0x49,0x56,0x42,0x4B,0x30,0x31) // SHIVBK01
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 210_000
    const val MAX_ARCHIVE_BYTES = 12 * 1024 * 1024
    private val random = SecureRandom()

    private fun passphraseAllowed(secret: CharArray) {
        require(secret.size in 12..256) { "Use a backup passphrase between 12 and 256 characters" }
    }
    private fun key(secret: CharArray, salt: ByteArray): SecretKeySpec {
        passphraseAllowed(secret)
        val spec = PBEKeySpec(secret, salt, ITERATIONS, 256)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            try { return SecretKeySpec(raw, "AES") }
            finally { raw.fill(0) }
        } finally { spec.clearPassword() }
    }
    fun encrypt(plaintext: ByteArray, secret: CharArray): ByteArray {
        require(plaintext.isNotEmpty() && plaintext.size <= MAX_ARCHIVE_BYTES) {
            "Backup data exceeds the supported safe size"
        }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = magic + salt + nonce
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(secret, salt), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        val result = header + cipher.doFinal(plaintext)
        require(result.size <= MAX_ARCHIVE_BYTES + 64)
        return result
    }
    fun decrypt(payload: ByteArray, secret: CharArray): ByteArray {
        require(payload.size in (magic.size + SALT_BYTES + NONCE_BYTES + 16)..
            (MAX_ARCHIVE_BYTES + 64)) { "Invalid or oversized Shiv backup file" }
        require(payload.copyOfRange(0,magic.size).contentEquals(magic)) {
            "Unsupported Shiv backup format"
        }
        val headerBytes = magic.size + SALT_BYTES + NONCE_BYTES
        val salt=payload.copyOfRange(magic.size,magic.size+SALT_BYTES)
        val nonce=payload.copyOfRange(magic.size+SALT_BYTES,headerBytes)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(secret,salt),GCMParameterSpec(TAG_BITS,nonce))
        cipher.updateAAD(payload.copyOfRange(0,headerBytes))
        val opened=cipher.doFinal(payload.copyOfRange(headerBytes,payload.size))
        require(opened.isNotEmpty() && opened.size<=MAX_ARCHIVE_BYTES)
        return opened
    }
}
