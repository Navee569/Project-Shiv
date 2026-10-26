package com.shiv.assistant.data

import org.junit.Assert.*
import org.junit.Test

class BackupCryptoTest {
    @Test fun encryptedBackupRoundTrip() {
        val raw = "secret Kannada Telugu English history".toByteArray()
        val enc = BackupCrypto.seal(raw, "correct horse battery".toCharArray())
        assertFalse(enc.contains("secret Kannada"))
        assertArrayEquals(raw, BackupCrypto.open(enc, "correct horse battery".toCharArray()))
    }
    @Test fun wrongPasswordIsNeverPlaintextFallback() {
        val enc = BackupCrypto.seal("private".toByteArray(), "correct horse battery".toCharArray())
        try {
            BackupCrypto.open(enc, "wrong password test".toCharArray())
            fail("Must reject wrong passphrase")
        } catch (_: Exception) {}
    }
    @Test fun tamperedCiphertextRejected() {
        val enc = BackupCrypto.seal("private".toByteArray(), "correct horse battery".toCharArray())
        val tampered = enc.dropLast(2) + "AA"
        try {
            BackupCrypto.open(tampered, "correct horse battery".toCharArray())
            fail("Must reject modified ciphertext")
        } catch (_: Exception) {}
    }
}
