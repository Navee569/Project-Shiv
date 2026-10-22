package app.shiv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
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
    @Test
    fun productionSuccessHandlerSkipsSideEffectsAfterFailureWins() {
        var closes = 0
        var completionCallbacks = 0
        var descriptions = 0
        var closeRequests = 0
        val failure = IllegalStateException("synchronous failure")
        var observedError: Throwable? = null
        val completion = PhotoCaptureCompletion(
            closeImage = { closes++ },
            onComplete = { error -> completionCallbacks++; observedError = error }
        )

        completion.finish(failure)
        val lateCallbackAccepted = completion.finishSuccess {
            descriptions++
            closeRequests++
        }

        assertEquals(false, lateCallbackAccepted)
        assertEquals(0, descriptions)
        assertEquals(0, closeRequests)
        assertEquals(1, closes)
        assertEquals(1, completionCallbacks)
        assertSame(failure, observedError)
    }

    @Test
    fun onCompleteExceptionPropagatesWithoutRepeatingTerminalCleanup() {
        var closes = 0
        var callbacks = 0
        val callbackFailure = IllegalStateException("completion callback failed")
        val completion = PhotoCaptureCompletion(
            closeImage = { closes++ },
            onComplete = { callbacks++; throw callbackFailure }
        )

        try {
            completion.finishSuccess { fail("success effects must not run") }
            fail("expected completion callback exception to propagate")
        } catch (actual: IllegalStateException) {
            assertSame(callbackFailure, actual)
        }

        assertEquals(false, completion.finishSuccess { fail("duplicate success effects must not run") })
        assertEquals(1, closes)
        assertEquals(1, callbacks)
    }

    @Test
    fun processingFailureRemainsPrimaryWhenClosingAlsoFails() {
        val processingFailure = IllegalArgumentException("processing failed")
        var observedError: Throwable? = null
        val completion = PhotoCaptureCompletion(
            closeImage = { throw IllegalStateException("close failed") },
            onComplete = { error -> observedError = error }
        )

        completion.finish(processingFailure)

        assertSame(processingFailure, observedError)
    }


    @Test
    fun normalSuccessFirstRunsSuccessEffectsExactlyOnce() {
        var closes = 0
        var completionCallbacks = 0
        var descriptions = 0
        var closeRequests = 0
        var observedError: Throwable? = IllegalStateException("sentinel")
        val completion = PhotoCaptureCompletion(
            closeImage = { closes++ },
            onComplete = { error -> completionCallbacks++; observedError = error }
        )

        val firstSuccessAccepted = completion.finishSuccess {
            descriptions++
            closeRequests++
        }
        val secondSuccessAccepted = completion.finishSuccess {
            descriptions++
            closeRequests++
        }
        val laterTerminalAccepted = completion.finish(IllegalStateException("late failure"))

        assertEquals(true, firstSuccessAccepted)
        assertEquals(false, secondSuccessAccepted)
        assertEquals(false, laterTerminalAccepted)
        assertEquals(1, descriptions)
        assertEquals(1, closeRequests)
        assertEquals(1, closes)
        assertEquals(1, completionCallbacks)
        assertEquals(null, observedError)
    }

}
