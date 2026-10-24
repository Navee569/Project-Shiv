package app.shiv.domain.actions

import java.util.concurrent.atomic.AtomicBoolean

/** Synchronous single-flight claim for an action awaiting owner confirmation. */
class ActionConfirmationGate {
    private val inFlight = AtomicBoolean(false)

    fun tryAcquire(): Lease? = if (inFlight.compareAndSet(false, true)) Lease(inFlight) else null

    class Lease internal constructor(private val inFlight: AtomicBoolean) : AutoCloseable {
        private val released = AtomicBoolean(false)

        override fun close() {
            if (released.compareAndSet(false, true)) inFlight.set(false)
        }
    }
}
