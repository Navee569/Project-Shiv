package app.shiv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class PhotoLabelingPipelineTest {
    private class FakeLabeler(
        private val onProcess: (onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit, onComplete: () -> Unit) -> Unit
    ) : PhotoLabeler<String, String> {
        var closes = 0
        override fun process(image: String, onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit, onComplete: () -> Unit) {
            onProcess(onSuccess, onFailure, onComplete)
        }
        override fun close() { closes++ }
    }

    @Test fun synchronousProcessSetupFailureClosesAndReportsOnce() {
        val failure = IllegalStateException("process setup failed")
        val labeler = FakeLabeler { _, _, _ -> throw failure }
        val results = mutableListOf<Result<String>>()
        PhotoLabelingPipeline.run("image", labeler, results::add)
        assertEquals(1, labeler.closes)
        assertEquals(1, results.size)
        assertSame(failure, results.single().exceptionOrNull())
    }

    @Test fun asynchronousSuccessClosesBeforeDeliveringExactlyOnce() {
        var closeCountAtCallback = -1
        val labeler = FakeLabeler { success, _, complete ->
            success("cat")
            complete()
            success("late result")
            complete()
        }
        val results = mutableListOf<Result<String>>()
        // The fake close counter is observable to prove cleanup precedes delivery.
        val observing = object : PhotoLabeler<String, String> {
            override fun process(image: String, onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit, onComplete: () -> Unit) =
                labeler.process(image, onSuccess, onFailure, onComplete)
            override fun close() { labeler.close() }
        }
        PhotoLabelingPipeline.run("image", observing) {
            closeCountAtCallback = labeler.closes
            results.add(it)
        }
        assertEquals(1, labeler.closes)
        assertEquals(1, closeCountAtCallback)
        assertEquals(1, results.size)
        assertEquals("cat", results.single().getOrNull())
    }

    @Test fun asynchronousFailureClosesAndReportsFailureOnce() {
        val failure = IllegalArgumentException("labeling failed")
        val labeler = FakeLabeler { _, fail, complete ->
            fail(failure)
            complete()
            complete()
        }
        val results = mutableListOf<Result<String>>()
        PhotoLabelingPipeline.run("image", labeler, results::add)
        assertEquals(1, labeler.closes)
        assertEquals(1, results.size)
        assertSame(failure, results.single().exceptionOrNull())
    }

    @Test fun setupFailureAfterCallbackRegistrationIsTerminalAndIgnoresLateCompletion() {
        val failure = IllegalStateException("listener setup failed")
        lateinit var lateComplete: () -> Unit
        val labeler = FakeLabeler { success, _, complete ->
            success("early value")
            lateComplete = complete
            throw failure
        }
        val results = mutableListOf<Result<String>>()
        PhotoLabelingPipeline.run("image", labeler, results::add)
        lateComplete()
        assertEquals(1, labeler.closes)
        assertEquals(1, results.size)
        assertSame(failure, results.single().exceptionOrNull())
    }

    @Test fun callbackExceptionPropagatesWithoutSecondCloseOrCallback() {
        val callbackFailure = IllegalStateException("consumer callback failed")
        val labeler = FakeLabeler { success, _, complete ->
            success("cat")
            complete()
        }
        var callbacks = 0
        try {
            PhotoLabelingPipeline.run("image", labeler) {
                callbacks++
                throw callbackFailure
            }
            throw AssertionError("Expected callback exception")
        } catch (actual: IllegalStateException) {
            assertSame(callbackFailure, actual)
        }
        assertEquals(1, labeler.closes)
        assertEquals(1, callbacks)
    }

    @Test fun imageOwnershipRemainsWithCallerUntilCompletion() {
        var imageClosed = false
        val labeler = FakeLabeler { success, _, complete ->
            success("dog")
            assertFalse(imageClosed)
            complete()
        }
        PhotoLabelingPipeline.run("image", labeler) {
            assertEquals("dog", it.getOrNull())
            assertEquals(1, labeler.closes)
            imageClosed = true // models the caller closing ImageProxy after callback
        }
        assertTrue(imageClosed)
    }
    @Test fun successWithCleanupFailureReportsCleanupFailure() {
        val cleanup = IllegalStateException("cleanup")
        val labeler = object : PhotoLabeler<String, String> {
            override fun process(image: String, onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit, onComplete: () -> Unit) {
                onSuccess("cat"); onComplete()
            }
            override fun close() { throw cleanup }
        }
        val results = mutableListOf<Result<String>>()
        PhotoLabelingPipeline.run("image", labeler, results::add)
        assertSame(cleanup, results.single().exceptionOrNull())
    }

    @Test fun processingFailureRemainsPrimaryAndCleanupIsSuppressed() {
        val processing = IllegalArgumentException("processing")
        val cleanup = IllegalStateException("cleanup")
        val labeler = object : PhotoLabeler<String, String> {
            override fun process(image: String, onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit, onComplete: () -> Unit) {
                onFailure(processing); onComplete()
            }
            override fun close() { throw cleanup }
        }
        val results = mutableListOf<Result<String>>()
        PhotoLabelingPipeline.run("image", labeler, results::add)
        assertSame(processing, results.single().exceptionOrNull())
        assertEquals(1, processing.suppressed.size)
        assertSame(cleanup, processing.suppressed.single())
    }

    @Test fun setupFailureRemainsPrimaryAndCleanupIsSuppressed() {
        val setup = IllegalStateException("setup")
        val cleanup = IllegalArgumentException("cleanup")
        val labeler = object : PhotoLabeler<String, String> {
            override fun process(image: String, onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit, onComplete: () -> Unit) { throw setup }
            override fun close() { throw cleanup }
        }
        val results = mutableListOf<Result<String>>()
        PhotoLabelingPipeline.run("image", labeler, results::add)
        assertSame(setup, results.single().exceptionOrNull())
        assertEquals(1, setup.suppressed.size)
        assertSame(cleanup, setup.suppressed.single())
    }

    @Test fun lateCallbacksCannotChangeCompletedResult() {
        lateinit var success: (String) -> Unit
        lateinit var failure: (Throwable) -> Unit
        lateinit var complete: () -> Unit
        var closes = 0
        val labeler = object : PhotoLabeler<String, String> {
            override fun process(image: String, onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit, onComplete: () -> Unit) {
                success = onSuccess; failure = onFailure; complete = onComplete
                success("winner"); complete()
            }
            override fun close() { closes++ }
        }
        val results = mutableListOf<Result<String>>()
        PhotoLabelingPipeline.run("image", labeler, results::add)
        failure(IllegalStateException("late")); success("late"); complete()
        assertEquals(1, closes)
        assertEquals(1, results.size)
        assertEquals("winner", results.single().getOrNull())
    }
    @Test fun interruptedWaitRetriesUntilTaskIsTerminalAndRestoresInterruptFlag() {
        var attempts = 0
        var restoredInterrupt = false
        val terminal = InterruptSafeAwait.await(
            awaitOnce = {
                attempts++
                if (attempts == 1) throw InterruptedException("simulated interrupt")
                "terminal"
            },
            restoreInterrupt = { restoredInterrupt = true }
        )
        assertEquals("terminal", terminal)
        assertEquals(2, attempts)
        assertTrue(restoredInterrupt)
    }

    @Test fun registrationFallbackWaitsOffCallerThreadAndDispatchesFailureOnce() {
        val waiting = mutableListOf<Runnable>()
        val callbacks = mutableListOf<Runnable>()
        val registration = IllegalStateException("listener registration failed")
        var awaits = 0
        var failureCalls = 0
        var completions = 0

        val scheduled = ListenerRegistrationFallback.schedule(
            awaitTask = { awaits++ },
            registrationFailure = registration,
            waiter = Executor { waiting.add(it) },
            callbackExecutor = Executor { callbacks.add(it) },
            onFailure = { assertSame(registration, it); failureCalls++ },
            onComplete = { completions++ }
        )

        assertTrue(scheduled)
        assertEquals(1, waiting.size)
        assertEquals(0, awaits)
        assertEquals(0, failureCalls)
        assertEquals(0, completions)
        waiting.single().run()
        assertEquals(1, awaits)
        assertEquals(1, callbacks.size)
        assertEquals(0, failureCalls)
        callbacks.single().run()
        assertEquals(1, failureCalls)
        assertEquals(1, completions)
    }

    @Test fun registrationFallbackDoesNotRepeatFailureWhenConsumerCallbackThrows() {
        val waiting = mutableListOf<Runnable>()
        val callbacks = mutableListOf<Runnable>()
        val callbackFailure = IllegalArgumentException("consumer callback failed")
        var failureCalls = 0
        var completions = 0
        ListenerRegistrationFallback.schedule(
            awaitTask = {},
            registrationFailure = IllegalStateException("registration failed"),
            waiter = Executor { waiting.add(it) },
            callbackExecutor = Executor { callbacks.add(it) },
            onFailure = { failureCalls++; throw callbackFailure },
            onComplete = { completions++ }
        )

        waiting.single().run()
        try {
            callbacks.single().run()
            throw AssertionError("Expected callback exception")
        } catch (actual: IllegalArgumentException) {
            assertSame(callbackFailure, actual)
        }
        assertEquals(1, failureCalls)
        assertEquals(1, completions)
    }

    @Test fun registrationFallbackPreservesTaskFailureAndRegistrationCause() {
        val waiting = mutableListOf<Runnable>()
        val callbacks = mutableListOf<Runnable>()
        val taskFailure = IllegalStateException("task failed")
        val registration = IllegalArgumentException("registration failed")
        var observed: Throwable? = null
        ListenerRegistrationFallback.schedule(
            awaitTask = { throw taskFailure },
            registrationFailure = registration,
            waiter = Executor { waiting.add(it) },
            callbackExecutor = Executor { callbacks.add(it) },
            onFailure = { observed = it },
            onComplete = {}
        )
        waiting.single().run()
        callbacks.single().run()
        assertSame(taskFailure, observed)
        assertEquals(1, taskFailure.suppressed.size)
        assertSame(registration, taskFailure.suppressed.single())
    }

    @Test fun registrationFallbackCallbackExecutorRejectionStillCompletesAfterTaskTerminality() {
        val waiting = mutableListOf<Runnable>()
        val completions = mutableListOf<Runnable>()
        val taskFailure = IllegalStateException("ML task failed")
        var taskTerminal = false
        var failureCalls = 0
        var completionCalls = 0

        val accepted = ListenerRegistrationFallback.schedule(
            awaitTask = { taskTerminal = true; throw taskFailure },
            registrationFailure = IllegalArgumentException("listener registration failed"),
            waiter = Executor { waiting.add(it) },
            callbackExecutor = Executor { throw RejectedExecutionException("callback queue full") },
            completionExecutor = Executor { completions.add(it) },
            onFailure = { assertTrue(taskTerminal); assertSame(taskFailure, it); failureCalls++ },
            onComplete = { assertTrue(taskTerminal); completionCalls++ }
        )

        assertTrue(accepted)
        assertEquals(1, waiting.size)
        assertTrue(completions.isEmpty())
        waiting.single().run()
        assertTrue(taskTerminal)
        assertEquals(1, completions.size)
        assertEquals(0, failureCalls)
        assertEquals(0, completionCalls)
        completions.single().run()
        assertEquals(1, failureCalls)
        assertEquals(1, completionCalls)
    }

    @Test fun registrationFallbackWaiterCapacityRejectionDoesNotReleaseActiveImage() {
        var closes = 0
        var taskStillActive = true
        val owner = PhotoCaptureCompletion(closeImage = { closes++ }, onComplete = {})
        val accepted = ListenerRegistrationFallback.schedule(
            awaitTask = { taskStillActive = false },
            registrationFailure = IllegalStateException("listener registration failed"),
            waiter = Executor { throw RejectedExecutionException("waiter capacity exhausted") },
            callbackExecutor = Executor { it.run() },
            completionExecutor = Executor { it.run() },
            onFailure = {},
            onComplete = { owner.finish() },
            onSchedulingFailure = {}
        )

        assertFalse(accepted)
        assertTrue(taskStillActive)
        assertEquals(0, closes)
    }

    @Test fun registrationFallbackSaturationKeepsImageUntilAcceptedTaskTerminates() {
        val waiting = mutableListOf<Runnable>()
        val callbacks = mutableListOf<Runnable>()
        var taskTerminal = false
        var closes = 0
        val owner = PhotoCaptureCompletion(closeImage = { closes++ }, onComplete = {})
        val accepted = ListenerRegistrationFallback.schedule(
            awaitTask = { taskTerminal = true },
            registrationFailure = IllegalStateException("listener registration failed"),
            waiter = Executor { waiting.add(it) },
            callbackExecutor = Executor { callbacks.add(it) },
            completionExecutor = Executor { callbacks.add(it) },
            onFailure = { assertTrue(taskTerminal) },
            onComplete = { assertTrue(taskTerminal); owner.finish() }
        )

        assertTrue(accepted)
        assertTrue(waiting.isNotEmpty())
        assertFalse(taskTerminal)
        assertEquals(0, closes)
        waiting.single().run()
        assertTrue(taskTerminal)
        assertEquals(1, callbacks.size)
        callbacks.single().run()
        assertEquals(1, closes)
        assertFalse(owner.finish())
        assertEquals(1, closes)
    }

    @Test fun registrationFallbackDoesNotSignalCompletionWhenWaiterRejects() {
        var failures = 0
        var completions = 0
        val accepted = ListenerRegistrationFallback.schedule(
            awaitTask = {},
            registrationFailure = IllegalStateException("registration failed"),
            waiter = Executor { throw RejectedExecutionException("test rejection") },
            callbackExecutor = Executor { it.run() },
            onFailure = { failures++ },
            onComplete = { completions++ },
            onSchedulingFailure = {}
        )
        assertFalse(accepted)
        assertEquals(0, failures)
        assertEquals(0, completions)
    }

}
