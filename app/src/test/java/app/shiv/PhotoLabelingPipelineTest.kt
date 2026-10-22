package app.shiv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

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
        PhotoLabelingPipeline.run("image", labeler, onResult = results::add)
        assertEquals(1, labeler.closes)
        assertEquals(1, results.size)
        assertSame(failure, results.single().exceptionOrNull())
    }

    @Test fun asynchronousSuccessClosesBeforeDeliveringExactlyOnce() {
        val labeler = FakeLabeler { success, _, complete ->
            success("cat"); complete(); success("late result"); complete()
        }
        val results = mutableListOf<Result<String>>()
        PhotoLabelingPipeline.run("image", labeler) { results.add(it) }
        assertEquals(1, labeler.closes)
        assertEquals(1, results.size)
        assertEquals("cat", results.single().getOrNull())
    }

    @Test fun asynchronousFailureClosesAndReportsFailureOnce() {
        val failure = IllegalArgumentException("labeling failed")
        val labeler = FakeLabeler { _, fail, complete -> fail(failure); complete(); complete() }
        val results = mutableListOf<Result<String>>()
        PhotoLabelingPipeline.run("image", labeler) { results.add(it) }
        assertEquals(1, labeler.closes)
        assertEquals(1, results.size)
        assertSame(failure, results.single().exceptionOrNull())
    }

    @Test fun setupFailureAfterCallbackRegistrationIsTerminalAndIgnoresLateCompletion() {
        val failure = IllegalStateException("listener setup failed")
        lateinit var lateComplete: () -> Unit
        val labeler = FakeLabeler { success, _, complete ->
            success("early value"); lateComplete = complete; throw failure
        }
        val results = mutableListOf<Result<String>>()
        PhotoLabelingPipeline.run("image", labeler) { results.add(it) }
        lateComplete()
        assertEquals(1, labeler.closes)
        assertEquals(1, results.size)
        assertSame(failure, results.single().exceptionOrNull())
    }

    @Test fun callbackExceptionPropagatesWithoutSecondCloseOrCallback() {
        val callbackFailure = IllegalStateException("consumer callback failed")
        val labeler = FakeLabeler { success, _, complete -> success("cat"); complete() }
        var callbacks = 0
        try {
            PhotoLabelingPipeline.run("image", labeler) { callbacks++; throw callbackFailure }
            throw AssertionError("Expected callback exception")
        } catch (actual: IllegalStateException) {
            assertSame(callbackFailure, actual)
        }
        assertEquals(1, labeler.closes)
        assertEquals(1, callbacks)
    }

    @Test fun cleanupRunsBeforeCallbackDispatcherAndResultCallback() {
        val queued = mutableListOf<Runnable>()
        var consumed = false
        var callbackSawConsumed = false
        val labeler = FakeLabeler { success, _, complete -> success("dog"); complete() }
        PhotoLabelingPipeline.run(
            image = "image",
            labeler = labeler,
            callbackExecutor = Executor { queued.add(it) },
            onImageConsumed = { consumed = true }
        ) {
            callbackSawConsumed = consumed
            assertEquals("dog", it.getOrNull())
        }
        assertTrue(consumed)
        assertFalse(callbackSawConsumed)
        assertEquals(1, queued.size)
        queued.single().run()
        assertTrue(callbackSawConsumed)
        assertEquals(1, labeler.closes)
    }

    @Test fun rejectedResultDispatcherCannotStrandTerminalCleanupOrRunUiCallbackOnWaiter() {
        var consumed = 0
        var results = 0
        var deliveryFailures = 0
        val labeler = FakeLabeler { success, _, complete -> success("cat"); complete() }
        PhotoLabelingPipeline.run(
            image = "image",
            labeler = labeler,
            callbackExecutor = Executor { throw RejectedExecutionException("main queue rejected") },
            onImageConsumed = { consumed++ },
            onDeliveryFailure = { deliveryFailures++ }
        ) { results++ }
        assertEquals(1, labeler.closes)
        assertEquals(1, consumed)
        assertEquals(0, results)
        assertEquals(1, deliveryFailures)
        assertEquals(1, labeler.closes)
    }

    @Test fun cleanupFailureDoesNotSkipImageReleaseAndProcessingFailureStaysPrimary() {
        val processing = IllegalArgumentException("processing")
        val labeler = object : PhotoLabeler<String, String> {
            override fun process(image: String, onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit, onComplete: () -> Unit) {
                onFailure(processing); onComplete()
            }
            override fun close() { throw IllegalStateException("labeler close failed") }
        }
        var imageConsumed = 0
        val results = mutableListOf<Result<String>>()
        PhotoLabelingPipeline.run(
            image = "image",
            labeler = labeler,
            onImageConsumed = { imageConsumed++; throw IllegalStateException("proxy close failed") }
        ) { results.add(it) }
        assertEquals(1, imageConsumed)
        assertSame(processing, results.single().exceptionOrNull())
        assertEquals(2, processing.suppressed.size)
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

    @Test fun fallbackListenerRegistrationFailurePreservesSuccessfulTaskResult() {
        val waiting = mutableListOf<Runnable>()
        val registration = IllegalStateException("listener registration failed")
        var successes = 0
        var failures = 0
        var completions = 0
        var result: String? = null
        val accepted = ListenerRegistrationFallback.schedule(
            awaitTask = { "known successful result" },
            registrationFailure = registration,
            waiter = Executor { waiting.add(it) },
            onSuccess = { successes++; result = it },
            onFailure = { failures++ },
            onComplete = { completions++ }
        )
        assertTrue(accepted)
        assertEquals(0, successes)
        waiting.single().run()
        assertEquals(1, successes)
        assertEquals("known successful result", result)
        assertEquals(0, failures)
        assertEquals(1, completions)
    }

    @Test fun fallbackTaskFailureIsPrimaryAndRegistrationFailureIsSuppressed() {
        val waiting = mutableListOf<Runnable>()
        val taskFailure = IllegalStateException("underlying task failed")
        val registration = IllegalArgumentException("listener registration failed")
        var observed: Throwable? = null
        var successes = 0
        var completions = 0
        ListenerRegistrationFallback.schedule(
            awaitTask = { throw taskFailure },
            registrationFailure = registration,
            waiter = Executor { waiting.add(it) },
            onSuccess = { successes++ },
            onFailure = { observed = it },
            onComplete = { completions++ }
        )
        waiting.single().run()
        assertEquals(0, successes)
        assertSame(taskFailure, observed)
        assertEquals(1, taskFailure.suppressed.size)
        assertSame(registration, taskFailure.suppressed.single())
        assertEquals(1, completions)
    }

    @Test fun fallbackCallbackExceptionDoesNotRepeatFailureAndStillCompletes() {
        val waiting = mutableListOf<Runnable>()
        val callbackFailure = IllegalArgumentException("consumer callback failed")
        var failureCalls = 0
        var completions = 0
        var diagnostics = 0
        ListenerRegistrationFallback.schedule(
            awaitTask = { throw IllegalStateException("task failed") },
            registrationFailure = IllegalStateException("registration failed"),
            waiter = Executor { waiting.add(it) },
            onSuccess = {},
            onFailure = { failureCalls++; throw callbackFailure },
            onComplete = { completions++ },
            onSchedulingFailure = { diagnostics++ }
        )
        waiting.single().run()
        assertEquals(1, failureCalls)
        assertEquals(1, completions)
        assertEquals(1, diagnostics)
    }

    @Test fun fallbackWaiterRejectionIsObservableAndDoesNotClaimCompletion() {
        var failures = 0
        var completions = 0
        var diagnostics = 0
        val accepted = ListenerRegistrationFallback.schedule(
            awaitTask = { "not reached" },
            registrationFailure = IllegalStateException("registration failed"),
            waiter = Executor { throw RejectedExecutionException("waiter capacity exhausted") },
            onSuccess = { failures++ },
            onFailure = { failures++ },
            onComplete = { completions++ },
            onSchedulingFailure = { diagnostics++ }
        )
        assertFalse(accepted)
        assertEquals(0, failures)
        assertEquals(0, completions)
        assertEquals(1, diagnostics)
    }

    @Test fun productionWaiterFactoryHasBoundedQueueAndRejectsOverflow() {
        val executor = PhotoLabelingExecutors.createWaiterExecutor(workerCount = 1, queueCapacity = 1)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val queuedRan = CountDownLatch(1)
        try {
            executor.execute {
                started.countDown()
                check(release.await(3, TimeUnit.SECONDS))
            }
            assertTrue("worker should start", started.await(3, TimeUnit.SECONDS))
            executor.execute { queuedRan.countDown() }
            try {
                executor.execute { throw AssertionError("overflow work must not execute") }
                throw AssertionError("expected bounded queue rejection")
            } catch (_: RejectedExecutionException) {
                // Expected: the active worker and one queued task exhaust capacity.
            }
            release.countDown()
            assertTrue("queued work should proceed after terminal worker returns", queuedRan.await(3, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS))
        }
    }

    @Test fun fallbackCompletionAfterTaskWasAlreadyTerminalStillProducesOneOutcome() {
        val waiting = mutableListOf<Runnable>()
        var successes = 0
        var failures = 0
        var completions = 0
        // A task that completed before listener registration threw is observed as terminal by awaitTask.
        ListenerRegistrationFallback.schedule(
            awaitTask = { "completed-before-registration-error" },
            registrationFailure = IllegalStateException("late registration exception"),
            waiter = Executor { waiting.add(it) },
            onSuccess = { successes++; assertEquals("completed-before-registration-error", it) },
            onFailure = { failures++ },
            onComplete = { completions++ }
        )
        waiting.single().run()
        assertEquals(1, successes)
        assertEquals(0, failures)
        assertEquals(1, completions)
    }
    @Test fun acceptedCallbackDispatcherKeepsResultOffWaiterThread() {
        val waiter = java.util.concurrent.Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "test-photo-waiter") }
        val callback = java.util.concurrent.Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "test-ui-dispatcher") }
        val delivered = CountDownLatch(1)
        val labeler = FakeLabeler { success, _, complete ->
            waiter.execute { success("cat"); complete() }
        }
        var cleanupThread: String? = null
        var callbackThread: String? = null
        var callbackResult: String? = null
        try {
            PhotoLabelingPipeline.run(
                image = "image",
                labeler = labeler,
                callbackExecutor = callback,
                onImageConsumed = { cleanupThread = Thread.currentThread().name }
            ) {
                callbackThread = Thread.currentThread().name
                callbackResult = it.getOrNull()
                delivered.countDown()
            }
            assertTrue("result should be delivered", delivered.await(3, TimeUnit.SECONDS))
            assertEquals("cat", callbackResult)
            assertEquals("test-photo-waiter", cleanupThread)
            assertEquals("test-ui-dispatcher", callbackThread)
            assertEquals(1, labeler.closes)
        } finally {
            waiter.shutdownNow()
            callback.shutdownNow()
            assertTrue(waiter.awaitTermination(3, TimeUnit.SECONDS))
            assertTrue(callback.awaitTermination(3, TimeUnit.SECONDS))
        }
    }

    @Test fun unresolvedTaskTerminalityReportsOnceAndDoesNotSignalCompletion() {
        val waiting = mutableListOf<Runnable>()
        var successCalls = 0
        var failureCalls = 0
        var completions = 0
        var diagnostics = 0
        ListenerRegistrationFallback.schedule(
            awaitTask = { throw TaskTerminalityUnresolvedException(java.util.concurrent.TimeoutException("bounded wait elapsed")) },
            registrationFailure = IllegalStateException("listener registration failed"),
            waiter = Executor { waiting.add(it) },
            onSuccess = { successCalls++ },
            onFailure = { failureCalls++ },
            onComplete = { completions++ },
            onSchedulingFailure = { diagnostics++ }
        )
        waiting.single().run()
        assertEquals(0, successCalls)
        assertEquals(0, failureCalls)
        assertEquals(0, completions)
        assertEquals(1, diagnostics)
    }

    @Test fun concurrentListenerAndFallbackTerminalCallbacksCloseAndDeliverExactlyOnce() {
        val labeler = FakeLabeler { _, _, _ -> }
        lateinit var success: (String) -> Unit
        lateinit var complete: () -> Unit
        // Replace the no-op fake with a controlled callback holder for both completion paths.
        val controlled = object : PhotoLabeler<String, String> {
            override fun process(image: String, onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit, onComplete: () -> Unit) {
                success = onSuccess
                complete = onComplete
            }
            override fun close() { labeler.close() }
        }
        val results = java.util.Collections.synchronizedList(mutableListOf<Result<String>>())
        PhotoLabelingPipeline.run("image", controlled) { results.add(it) }
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val workerFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val listener = Thread {
            try { ready.countDown(); check(start.await(3, TimeUnit.SECONDS)); success("listener result"); complete() }
            catch (failure: Throwable) { workerFailure.compareAndSet(null, failure) }
        }
        val fallback = Thread {
            try { ready.countDown(); check(start.await(3, TimeUnit.SECONDS)); success("fallback result"); complete() }
            catch (failure: Throwable) { workerFailure.compareAndSet(null, failure) }
        }
        listener.start()
        fallback.start()
        assertTrue(ready.await(3, TimeUnit.SECONDS))
        start.countDown()
        listener.join(3000)
        fallback.join(3000)
        assertFalse(listener.isAlive)
        assertFalse(fallback.isAlive)
        assertEquals(null, workerFailure.get())
        assertEquals(1, labeler.closes)
        assertEquals(1, results.size)
        assertTrue(results.single().isSuccess)
    }

    @Test fun interruptSafeAwaitRestoresRealThreadInterruptStatus() {
        val restored = java.util.concurrent.atomic.AtomicBoolean(false)
        val workerFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val thread = Thread {
            try {
                var attempts = 0
                val value = InterruptSafeAwait.await(
                    awaitOnce = {
                        attempts++
                        if (attempts == 1) throw InterruptedException("controlled interruption")
                        "terminal"
                    }
                )
                assertEquals("terminal", value)
            } catch (failure: Throwable) {
                // Assertions on a raw Thread do not automatically fail the JUnit test.
                workerFailure.set(failure)
            } finally {
                restored.set(Thread.currentThread().isInterrupted)
            }
        }
        thread.start()
        thread.join(3000)
        assertFalse("interrupt-safe await worker did not finish", thread.isAlive)
        assertEquals("worker failed", null, workerFailure.get())
        assertTrue("worker interrupt flag was not restored", restored.get())
    }


    @Test
    fun fallbackAwaitUsesNamedArgumentAndReturnsTerminalResult() {
        val terminal = InterruptSafeAwait.await(
            awaitOnce = { "terminal-result" }
        )
        assertEquals("terminal-result", terminal)
    }

    @Test
    fun labelerAndImageRemainOwnedUntilActualTaskTerminality() {
        lateinit var success: (String) -> Unit
        lateinit var complete: () -> Unit
        var closes = 0
        var consumed = 0
        val results = mutableListOf<Result<String>>()
        val labeler = object : PhotoLabeler<String, String> {
            override fun process(image: String, onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit, onComplete: () -> Unit) {
                success = onSuccess; complete = onComplete
            }
            override fun close() { closes++ }
        }
        PhotoLabelingPipeline.run("image", labeler, onImageConsumed = { consumed++ }) { results.add(it) }
        success("result before terminality")
        assertEquals(0, closes); assertEquals(0, consumed); assertTrue(results.isEmpty())
        complete()
        assertEquals(1, closes); assertEquals(1, consumed)
        assertEquals("result before terminality", results.single().getOrNull())
    }

    @Test
    fun productionPipelineAdapterRetainsImageAfterUnresolvedNoticeUntilTaskTerminality() {
        var imageCloses = 0
        var completions = 0
        var descriptions = 0
        var unresolved = 0
        lateinit var success: (String) -> Unit
        lateinit var complete: () -> Unit
        val adapter = PhotoCaptureAnalysisAdapter(
            completion = PhotoCaptureCompletion({ imageCloses++ }, { completions++ }),
            onDescription = { descriptions++ }, onClose = {}, onAnalysisUnresolved = { unresolved++ }
        )
        val labeler = object : PhotoLabeler<String, String> {
            override fun process(image: String, onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit, onComplete: () -> Unit) {
                success = onSuccess; complete = onComplete
            }
            override fun close() {}
        }
        PhotoLabelingPipeline.run("image", labeler, onImageConsumed = adapter::imageConsumed) { result ->
            result.fold(adapter::descriptionReady, adapter::failed)
        }
        adapter.analysisUnresolved(TaskTerminalityUnresolvedException(java.util.concurrent.TimeoutException("bounded wait elapsed")))
        assertEquals(1, unresolved); assertEquals(0, imageCloses); assertEquals(0, completions)
        success("late result"); complete()
        assertEquals(1, imageCloses); assertEquals(1, completions); assertEquals(0, descriptions)
    }

    @Test
    fun productionAdapterPipelineDeduplicatesPartiallyAttachedListenerFallbackRace() {
        val fallbackQueue = mutableListOf<Runnable>()
        lateinit var listenerSuccess: (String) -> Unit
        lateinit var listenerComplete: () -> Unit
        var labelerCloses = 0
        var imageCloses = 0
        var completions = 0
        var descriptions = 0
        var closeRequests = 0
        val adapter = PhotoCaptureAnalysisAdapter(
            completion = PhotoCaptureCompletion({ imageCloses++ }, { completions++ }),
            onDescription = { descriptions++ }, onClose = { closeRequests++ }
        )
        val labeler = object : PhotoLabeler<String, String> {
            override fun process(image: String, onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit, onComplete: () -> Unit) {
                listenerSuccess = onSuccess; listenerComplete = onComplete
                assertTrue(ListenerRegistrationFallback.schedule(
                    awaitTask = { "fallback result" },
                    registrationFailure = IllegalStateException("listener attached then registration threw"),
                    waiter = Executor { fallbackQueue.add(it) },
                    onSuccess = onSuccess, onFailure = onFailure, onComplete = onComplete
                ))
            }
            override fun close() { labelerCloses++ }
        }
        val results = java.util.Collections.synchronizedList(mutableListOf<Result<String>>())
        PhotoLabelingPipeline.run("image", labeler, callbackExecutor = Executor { it.run() }, onImageConsumed = adapter::imageConsumed) { result ->
            results.add(result); result.fold(adapter::descriptionReady, adapter::failed)
        }
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val workerFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val fallbackThread = Thread {
            try { ready.countDown(); check(start.await(3, TimeUnit.SECONDS)); fallbackQueue.single().run() }
            catch (failure: Throwable) { workerFailure.compareAndSet(null, failure) }
        }
        val listenerThread = Thread {
            try { ready.countDown(); check(start.await(3, TimeUnit.SECONDS)); listenerSuccess("listener result"); listenerComplete() }
            catch (failure: Throwable) { workerFailure.compareAndSet(null, failure) }
        }
        fallbackThread.start(); listenerThread.start()
        assertTrue(ready.await(3, TimeUnit.SECONDS))
        start.countDown()
        fallbackThread.join(3000); listenerThread.join(3000)
        assertFalse(fallbackThread.isAlive); assertFalse(listenerThread.isAlive)
        assertEquals(null, workerFailure.get())
        assertEquals(1, labelerCloses); assertEquals(1, imageCloses)
        assertEquals(1, completions); assertEquals(1, results.size)
        assertEquals(1, descriptions); assertEquals(1, closeRequests)
    }

    @Test
    fun unresolvedFallbackDoesNotCompleteWhenTerminalityCannotBeEstablished() {
        val waiting = mutableListOf<Runnable>()
        var completions = 0
        var diagnostics = 0
        val accepted = ListenerRegistrationFallback.schedule(
            awaitTask = { throw TaskTerminalityUnresolvedException(java.util.concurrent.TimeoutException("not terminal")) },
            registrationFailure = IllegalStateException("registration failed"),
            waiter = Executor { waiting.add(it) },
            onSuccess = { completions++ },
            onFailure = { completions++ },
            onComplete = { completions++ },
            onSchedulingFailure = { diagnostics++ }
        )
        assertTrue(accepted)
        waiting.single().run()
        assertEquals(0, completions)
        assertEquals(1, diagnostics)
    }
}
