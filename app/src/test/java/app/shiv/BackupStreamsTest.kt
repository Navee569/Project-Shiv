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
    @Test fun jsonRuntimeUsesRealImplementation() {
        val origin = org.json.JSONObject::class.java.protectionDomain.codeSource.location.toString()
        println("JSONObject origin=$origin")
        println("JSONArray origin=" + org.json.JSONArray::class.java.protectionDomain.codeSource.location)
        org.junit.Assert.assertTrue(origin, origin.contains("json-20240303"))
        assertEquals(1, org.json.JSONObject("""{"value":1}""").getInt("value"))
    }

    @Test fun rejectsFractionalOverflowingAndCoercedNumbers() {
        val invalid = listOf<Any>(1.5, -0.5, Long.MAX_VALUE, "1", true, org.json.JSONObject.NULL,
            java.math.BigInteger("18446744073709551617"))
        for (value in invalid) {
            val json = org.json.JSONObject().put("value", value)
            assertThrows("value=$value", IllegalArgumentException::class.java) {
                requireJsonLong(json, "value", 0L..1439L)
            }
        }
        assertEquals(Long.MAX_VALUE, requireJsonLong(
            org.json.JSONObject().put("value", Long.MAX_VALUE), "value", 0L..Long.MAX_VALUE))
    }

    @Test fun rejectsSettingsFractionsOverflowAndNonSingletonIds() {
        for (key in listOf("id", "quietStartMinute", "quietEndMinute", "maxDailyInterruptions")) {
            for (value in listOf<Any>(0.5, java.math.BigInteger("4294967297"), "1", false)) {
                val settings = org.json.JSONObject("""{"id":1,"proactiveEnabled":true,"quietStartMinute":1320,"quietEndMinute":480,"maxDailyInterruptions":3}""")
                    .put(key, value)
                assertThrows("$key=$value", IllegalArgumentException::class.java) { requireSettingsSchema(settings) }
            }
        }
        val settings = org.json.JSONObject("""{"id":2,"proactiveEnabled":true,"quietStartMinute":1320,"quietEndMinute":480,"maxDailyInterruptions":3}""")
        assertThrows(IllegalArgumentException::class.java) { requireSettingsSchema(settings) }
    }

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

    @Test fun rejectsMissingAndWrongTypeSettingsFields() {
        val missing = org.json.JSONObject("""{"id":1,"proactiveEnabled":true,"quietStartMinute":1320,"quietEndMinute":480}""")
        assertThrows(IllegalArgumentException::class.java) { requireSettingsSchema(missing) }
        val wrongType = org.json.JSONObject("""{"id":1,"proactiveEnabled":"true","quietStartMinute":1320,"quietEndMinute":480,"maxDailyInterruptions":3}""")
        assertThrows(IllegalArgumentException::class.java) { requireSettingsSchema(wrongType) }
    }

    @Test fun rejectsOutOfRangeSettingsValues() {
        val settings = org.json.JSONObject("""{"id":1,"proactiveEnabled":true,"quietStartMinute":1440,"quietEndMinute":480,"maxDailyInterruptions":3}""")
        assertThrows(IllegalArgumentException::class.java) { requireSettingsSchema(settings) }
    }

    @Test fun validatesRealBackupEnvelopeAndRejectsMissingArrays() {
        val complete = org.json.JSONObject().put("format", 1).put("createdAt", 1L)
            .put("profile", org.json.JSONObject.NULL)
            .put("messages", org.json.JSONArray()).put("memories", org.json.JSONArray())
            .put("events", org.json.JSONArray()).put("pendingEvents", org.json.JSONArray())
            .put("actions", org.json.JSONArray()).put("rules", org.json.JSONArray())
            .put("drafts", org.json.JSONArray()).put("actionTiers", org.json.JSONArray())
            .put("settings", org.json.JSONObject("""{"id":1,"proactiveEnabled":true,"quietStartMinute":1320,"quietEndMinute":480,"maxDailyInterruptions":3}"""))
        validateBackupEnvelope(complete)
        for (format in listOf<Any>(1.5, "1", true, java.math.BigInteger("4294967297"))) {
            val invalid = org.json.JSONObject(complete.toString()).put("format", format)
            assertThrows(IllegalArgumentException::class.java) { validateBackupEnvelope(invalid) }
        }
        for (createdAt in listOf<Any>(-0.5, -1, "1", java.math.BigInteger("9223372036854775808"))) {
            val invalid = org.json.JSONObject(complete.toString()).put("createdAt", createdAt)
            assertThrows(IllegalArgumentException::class.java) { validateBackupEnvelope(invalid) }
        }
        complete.remove("memories")
        assertThrows(IllegalArgumentException::class.java) { validateBackupEnvelope(complete) }
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


    @Test fun rejectsMalformedRecordInEveryBackupCollectionBeforeDatabaseMutation() {
        val collections = listOf("messages", "memories", "events", "pendingEvents",
            "actions", "rules", "drafts", "actionTiers")
        fun emptyEnvelope() = org.json.JSONObject()
            .put("format", 1).put("createdAt", 1L)
            .put("profile", org.json.JSONObject.NULL)
            .put("messages", org.json.JSONArray())
            .put("memories", org.json.JSONArray())
            .put("events", org.json.JSONArray())
            .put("pendingEvents", org.json.JSONArray())
            .put("actions", org.json.JSONArray())
            .put("rules", org.json.JSONArray())
            .put("drafts", org.json.JSONArray())
            .put("actionTiers", org.json.JSONArray())
            .put("settings", org.json.JSONObject()
                .put("id", 1).put("proactiveEnabled", false)
                .put("quietStartMinute", 0).put("quietEndMinute", 0)
                .put("maxDailyInterruptions", 0))

        // Empty collections and a historical null profile remain accepted.
        validateBackupEnvelope(emptyEnvelope())
        for (collection in collections) {
            val malformedObject = emptyEnvelope().put(collection,
                org.json.JSONArray().put(org.json.JSONObject()))
            assertThrows("missing required record fields in $collection",
                IllegalArgumentException::class.java) {
                validateBackupEnvelope(malformedObject)
            }
            val malformedScalar = emptyEnvelope().put(collection,
                org.json.JSONArray().put("not-an-object"))
            assertThrows("scalar record in $collection",
                IllegalArgumentException::class.java) {
                validateBackupEnvelope(malformedScalar)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateBackupEnvelope(emptyEnvelope().put("profile", org.json.JSONObject()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateBackupEnvelope(emptyEnvelope().put("profile", "invalid-profile"))
        }
    }
}
