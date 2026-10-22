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
        val closeFailure = IllegalStateException("close failed")
        var observedError: Throwable? = null
        val completion = PhotoCaptureCompletion(
            closeImage = { throw closeFailure },
            onComplete = { error -> observedError = error }
        )

        completion.finish(processingFailure)

        assertSame(processingFailure, observedError)
        assertEquals(1, processingFailure.suppressed.size)
        assertSame(closeFailure, processingFailure.suppressed.single())
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

    @Test
    fun imageCanBeReleasedAtTaskTerminalityBeforeUiCompletion() {
        var closes = 0
        var callbacks = 0
        val completion = PhotoCaptureCompletion(
            closeImage = { closes++ },
            onComplete = { callbacks++ }
        )

        assertEquals(null, completion.releaseImage())
        assertEquals(1, closes)
        assertEquals(0, callbacks)
        assertEquals(null, completion.releaseImage())
        assertEquals(1, closes)

        assertEquals(true, completion.finish())
        assertEquals(1, closes)
        assertEquals(1, callbacks)
    }

    @Test
    fun closeFailureDuringEarlyReleaseIsRetainedUntilCompletionNotification() {
        val closeFailure = IllegalStateException("proxy close failed")
        var callbacks = 0
        var observed: Throwable? = null
        val completion = PhotoCaptureCompletion(
            closeImage = { throw closeFailure },
            onComplete = { callbacks++; observed = it }
        )

        assertSame(closeFailure, completion.releaseImage())
        assertEquals(0, callbacks)
        assertEquals(true, completion.finish())
        assertSame(closeFailure, observed)
        assertEquals(1, callbacks)
    }

    @Test
    fun productionPhotoAdapterReleasesImageBeforeUiResultAndOnlyOnce() {
        var closes = 0
        var completionCallbacks = 0
        var descriptions = 0
        var closeRequests = 0
        var observedDescription: String? = null
        val completion = PhotoCaptureCompletion(
            closeImage = { closes++ },
            onComplete = { completionCallbacks++ }
        )
        val adapter = PhotoCaptureAnalysisAdapter(
            completion = completion,
            onDescription = { description ->
                assertEquals(1, closes)
                descriptions++
                observedDescription = description
            },
            onClose = { closeRequests++ }
        )

        adapter.imageConsumed()
        assertEquals(1, closes)
        assertEquals(0, completionCallbacks)
        adapter.descriptionReady("cat")
        adapter.descriptionReady("late result")

        assertEquals("cat", observedDescription)
        assertEquals(1, descriptions)
        assertEquals(1, closeRequests)
        assertEquals(1, closes)
        assertEquals(1, completionCallbacks)
    }


    @Test
    fun concurrentReleaseAndFinishAttemptImageCloseExactlyOnce() {
        val start = java.util.concurrent.CountDownLatch(1)
        val ready = java.util.concurrent.CountDownLatch(2)
        val closes = java.util.concurrent.atomic.AtomicInteger()
        val callbacks = java.util.concurrent.atomic.AtomicInteger()
        val completion = PhotoCaptureCompletion(
            closeImage = { closes.incrementAndGet() },
            onComplete = { callbacks.incrementAndGet() }
        )
        val releaseThread = Thread {
            ready.countDown()
            start.await()
            completion.releaseImage()
        }
        val finishThread = Thread {
            ready.countDown()
            start.await()
            completion.finish()
        }
        releaseThread.start()
        finishThread.start()
        assertEquals(true, ready.await(3, java.util.concurrent.TimeUnit.SECONDS))
        start.countDown()
        releaseThread.join(3000)
        finishThread.join(3000)
        assertEquals(false, releaseThread.isAlive)
        assertEquals(false, finishThread.isAlive)
        assertEquals(1, closes.get())
        assertEquals(1, callbacks.get())
    }

    @Test
    fun duplicateReleaseAndFinishDoNotRepeatCloseOrCompletion() {
        var closes = 0
        var callbacks = 0
        val completion = PhotoCaptureCompletion(
            closeImage = { closes++ },
            onComplete = { callbacks++ }
        )
        assertEquals(null, completion.releaseImage())
        assertEquals(null, completion.releaseImage())
        assertEquals(true, completion.finish())
        assertEquals(false, completion.finish())
        assertEquals(1, closes)
        assertEquals(1, callbacks)
    }

    @Test
    fun unresolvedAnalysisAbandonsUiButRetainsImageUntilTaskActuallyCompletes() {
        var closes = 0
        var completions = 0
        var descriptions = 0
        var closeRequests = 0
        var unresolvedWarnings = 0
        val completion = PhotoCaptureCompletion(
            closeImage = { closes++ },
            onComplete = { completions++ }
        )
        val adapter = PhotoCaptureAnalysisAdapter(
            completion = completion,
            onDescription = { descriptions++ },
            onClose = { closeRequests++ },
            onAnalysisUnresolved = { unresolvedWarnings++ }
        )

        adapter.analysisUnresolved(TaskTerminalityUnresolvedException(java.util.concurrent.TimeoutException()))
        assertEquals(1, unresolvedWarnings)
        assertEquals(0, closes)
        assertEquals(0, completions)

        // This is the later, actual task-terminal signal from the pipeline.
        adapter.imageConsumed()
        adapter.descriptionReady("late result")
        assertEquals(1, closes)
        assertEquals(1, completions)
        assertEquals(0, descriptions)
        assertEquals(0, closeRequests)
    }

}
