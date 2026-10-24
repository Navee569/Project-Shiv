package app.shiv.ui

import app.shiv.domain.model.UserProfile
import app.shiv.domain.repository.ShivViewRepository
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsyncShivViewRepositoryTest {
    @Test
    fun slowDatabaseCreationDoesNotBlockViewModelConstructionOrCallerThread() = runBlocking {
        val creating = CountDownLatch(1)
        val release = CountDownLatch(1)
        val createdBy = AtomicReference<Thread>()
        val creationCount = AtomicInteger(0)
        val caller = Thread.currentThread()
        val repository = AsyncShivViewRepository {
            creationCount.incrementAndGet()
            createdBy.set(Thread.currentThread())
            creating.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            Proxy.newProxyInstance(
                ShivViewRepository::class.java.classLoader,
                arrayOf(ShivViewRepository::class.java)
            ) { _, method, _ ->
                when (method.name) {
                    "profile" -> flowOf(UserProfile(name = "Ada"))
                    else -> error("Unexpected repository call: ${method.name}")
                }
            } as ShivViewRepository
        }

        assertEquals(0, creationCount.get())
        val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
            assertEquals("Ada", repository.profile().first()?.name)
        }
        try {
            assertTrue(creating.await(5, TimeUnit.SECONDS))
            assertNotEquals(caller, createdBy.get())
            assertEquals(1, creationCount.get())
        } finally {
            release.countDown()
        }
        collecting.join()
    }
}
