package app.shiv

import java.io.ByteArrayOutputStream
import java.io.InputStream

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
