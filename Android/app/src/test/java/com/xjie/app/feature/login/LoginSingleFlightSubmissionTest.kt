package com.xjie.app.feature.login

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginSingleFlightSubmissionTest {
    @Test
    fun duplicateLoginOrSignupTapCannotAcquireWhileSubmissionIsInFlight() {
        val gate = LoginSubmissionGate()

        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())

        gate.release()
        assertTrue(gate.tryAcquire())
    }
}
