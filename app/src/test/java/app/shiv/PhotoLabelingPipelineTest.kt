package app.shiv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val labeler = FakeLabeler { _, success, complete ->
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
}
