package app.shiv

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.AEADBadTagException
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupStreamsTest {
    @Test fun acceptsBytesAtLimit() {
        val source = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(source, readBoundedBytes(ByteArrayInputStream(source), 4))
    }
    @Test fun rejectsOverLimitAfterOnlyOneExtraByte() {
        var consumed = 0
        val source = object : InputStream() {
            override fun read(): Int { consumed++; return if (consumed <= 100) 7 else -1 }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (consumed >= 100) return -1
                val count = minOf(length, 100 - consumed)
                java.util.Arrays.fill(buffer, offset, offset + count, 7.toByte())
                consumed += count
                return count
            }
        }
        assertThrows(IllegalArgumentException::class.java) { readBoundedBytes(source, 8) }
        assertEquals(9, consumed)
    }
    @Test fun rejectsNegativeLimit() {
        assertThrows(IllegalArgumentException::class.java) { readBoundedBytes(ByteArrayInputStream(byteArrayOf()), -1) }
    }

    @Test fun boundedOutputAcceptsExactLimit() {
        val sink = ByteArrayOutputStream()
        BoundedOutputStream(sink, 4).use { it.write(byteArrayOf(1, 2, 3, 4)) }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), sink.toByteArray())
    }

    @Test fun boundedOutputRejectsBeforeWritingOverLimit() {
        val sink = ByteArrayOutputStream()
        val bounded = BoundedOutputStream(sink, 4)
        bounded.write(byteArrayOf(1, 2, 3))
        assertThrows(IOException::class.java) { bounded.write(byteArrayOf(4, 5)) }
        assertArrayEquals(byteArrayOf(1, 2, 3), sink.toByteArray())
    }


    @Test fun rejectsIncompleteBackupFieldSetInsteadOfTreatingMissingDataAsEmpty() {
        val complete = REQUIRED_BACKUP_FIELDS.toMutableSet()
        requireBackupFields(complete)
        complete.remove("memories")
        val failure = assertThrows(IllegalArgumentException::class.java) {
            requireBackupFields(complete)
        }
        org.junit.Assert.assertTrue(failure.message!!.contains("memories"))
    }

    @Test fun acceptsKnownRequiredBackupFieldsAlongsideFutureOptionalFields() {
        requireBackupFields(REQUIRED_BACKUP_FIELDS + "futureOptionalMetadata")
    }

    @Test fun plaintextCapAndEnvelopeOverheadFitExactPackedLimit() {
        assertEquals(150 * 1024 * 1024, MAX_BACKUP_BYTES)
        assertEquals(MAX_BACKUP_BYTES, BACKUP_HEADER_BYTES + MAX_BACKUP_PLAINTEXT_BYTES + BACKUP_TAG_BYTES)
    }


    private fun packedBackup(payload: ByteArray, passphrase: CharArray): ByteArray {
        val magic = "SHIVBK01".toByteArray(Charsets.US_ASCII)
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { (it + 1).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveBackupKey(passphrase, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(magic)
        return magic + salt + iv + cipher.doFinal(payload)
    }

    @Test fun decryptsAuthenticatedBackupPayload() {
        val password = "correct horse battery".toCharArray()
        val payload = """{"format":1,"messages":[]}""".toByteArray(Charsets.UTF_8)
        assertArrayEquals(payload, decryptBackupPayload(packedBackup(payload, password), password))
    }

    @Test fun rejectsWrongPassphraseAndTamperedAuthenticationTag() {
        val password = "correct horse battery".toCharArray()
        val packed = packedBackup("{}".toByteArray(), password)
        assertThrows(AEADBadTagException::class.java) {
            decryptBackupPayload(packed, "wrong password 123".toCharArray())
        }
        val tampered = packed.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(AEADBadTagException::class.java) { decryptBackupPayload(tampered, password) }
    }

    @Test fun rejectsTruncatedAndInvalidBackupHeaders() {
        assertThrows(IllegalArgumentException::class.java) {
            decryptBackupPayload(byteArrayOf(1, 2, 3), "correct horse battery".toCharArray())
        }
        val badMagic = packedBackup("{}".toByteArray(), "correct horse battery".toCharArray())
            .also { it[0] = 0 }
        assertThrows(IllegalArgumentException::class.java) {
            decryptBackupPayload(badMagic, "correct horse battery".toCharArray())
        }
    }

}
