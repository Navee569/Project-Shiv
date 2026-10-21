package app.shiv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PhotoCaptureCompletionTest {
    @Test
    fun finishClosesImageAndCompletesExactlyOnce() {
        var closes = 0
        var callbacks = 0
        var observedError: Throwable? = IllegalStateException("sentinel")
        val completion = PhotoCaptureCompletion(
            closeImage = { closes++ },
            onComplete = { error -> callbacks++; observedError = error }
        )

        completion.finish()
        completion.finish(IllegalStateException("late callback"))

        assertEquals(1, closes)
        assertEquals(1, callbacks)
        assertEquals(null, observedError)
    }

    @Test
    fun finishReportsProcessingFailureAndClosesImage() {
        var closes = 0
        var observedError: Throwable? = null
        val failure = IllegalArgumentException("processing failed")
        val completion = PhotoCaptureCompletion(
            closeImage = { closes++ },
            onComplete = { error -> observedError = error }
        )

        completion.finish(failure)

        assertEquals(1, closes)
        assertSame(failure, observedError)
    }

    @Test
    fun closeFailureIsReportedAndCompletionStillRuns() {
        var callbacks = 0
        val failure = IllegalStateException("close failed")
        var observedError: Throwable? = null
        val completion = PhotoCaptureCompletion(
            closeImage = { throw failure },
            onComplete = { error -> callbacks++; observedError = error }
        )

        completion.finish()

        assertEquals(1, callbacks)
        assertSame(failure, observedError)
    }
}
