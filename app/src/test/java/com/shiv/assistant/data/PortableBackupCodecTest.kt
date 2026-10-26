package com.shiv.assistant.data

import org.junit.Assert.*
import org.junit.Test

class PortableBackupCodecTest {
    private fun password()="long-secret-passphrase-2026".toCharArray()

    @Test fun roundtripUsesAuthenticatedRandomizedCiphertext() {
        val plaintext="sensitive Shiv owner note: do not print in logs".toByteArray()
        val first=PortableBackupCodec.encrypt(plaintext,password())
        val second=PortableBackupCodec.encrypt(plaintext,password())
        assertFalse(first.contentEquals(second))
        assertFalse(String(first, Charsets.ISO_8859_1).contains("sensitive Shiv owner note"))
        assertArrayEquals(plaintext,PortableBackupCodec.decrypt(first,password()))
        assertArrayEquals(plaintext,PortableBackupCodec.decrypt(second,password()))
    }

    @Test fun alteredCiphertextWrongPassphraseAndFormatFailClosed() {
        val archive=PortableBackupCodec.encrypt("test data".toByteArray(),password())
        val tampered=archive.copyOf()
        tampered[tampered.lastIndex]=(tampered.last().toInt() xor 1).toByte()
        assertTrue(runCatching { PortableBackupCodec.decrypt(tampered,password()) }.isFailure)
        assertTrue(runCatching {
            PortableBackupCodec.decrypt(archive,"wrong-long-passphrase!".toCharArray())
        }.isFailure)
        assertTrue(runCatching {
            PortableBackupCodec.decrypt("bad backup format".toByteArray(),password())
        }.isFailure)
        assertTrue(runCatching {
            PortableBackupCodec.encrypt("hello".toByteArray(),"short".toCharArray())
        }.isFailure)
    }
}
