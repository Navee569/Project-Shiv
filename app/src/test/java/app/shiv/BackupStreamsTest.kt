package app.shiv

import java.io.ByteArrayInputStream
import java.io.InputStream
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
}
