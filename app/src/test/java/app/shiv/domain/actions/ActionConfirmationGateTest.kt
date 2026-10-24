package app.shiv.domain.actions

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ActionConfirmationGateTest {
    @Test
    fun overlappingConfirmationsCannotAcquireTwoDispatchClaims() {
        val gate = ActionConfirmationGate()

        val firstConfirmation = gate.tryAcquire()
        val overlappingConfirmation = gate.tryAcquire()

        assertNotNull(firstConfirmation)
        assertNull(overlappingConfirmation)

        firstConfirmation?.close()
        val retry = gate.tryAcquire()
        assertNotNull(retry)
        retry?.close()
    }
}
