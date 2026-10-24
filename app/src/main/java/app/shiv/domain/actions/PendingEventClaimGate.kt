package app.shiv.domain.actions

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** A pending event can be saved or ignored by only one in-flight owner action. */
class PendingEventClaimGate {
    private val claims = ConcurrentHashMap<String, Any>()

    fun tryAcquire(eventId: String): Lease? {
        val token = Any()
        return if (claims.putIfAbsent(eventId, token) == null) Lease(eventId, token) else null
    }

    inner class Lease internal constructor(private val eventId: String, private val token: Any) : AutoCloseable {
        private val released = AtomicBoolean(false)
        override fun close() {
            if (released.compareAndSet(false, true)) claims.remove(eventId, token)
        }
    }
}
