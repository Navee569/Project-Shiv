package app.shiv.domain.actions

import kotlinx.coroutines.flow.MutableStateFlow

/** Claims a confirmation and removes its dialog before any suspending audit write. */
class PendingActionCoordinator<T : Any> {
    val pendingAction = MutableStateFlow<T?>(null)
    private val gate = ActionConfirmationGate()

    fun tryClaim(): Claim<T>? {
        val lease = gate.tryAcquire() ?: return null
        val action = pendingAction.value
        if (action == null || !pendingAction.compareAndSet(action, null)) {
            lease.close()
            return null
        }
        return Claim(action, lease)
    }

    fun cancel(): T? {
        while (true) {
            val action = pendingAction.value ?: return null
            if (pendingAction.compareAndSet(action, null)) return action
        }
    }

    class Claim<T : Any> internal constructor(val action: T, private val lease: ActionConfirmationGate.Lease) : AutoCloseable {
        override fun close() = lease.close()
    }
}
