package com.xieguiawu.picturetrans.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenGuardTest {

    @Test fun `valid token accepted`() {
        val guard = TokenGuard("abc123-_x")
        assertTrue(guard.isValid("abc123-_x"))
    }

    @Test fun `wrong token rejected`() {
        val guard = TokenGuard("abc123")
        assertFalse(guard.isValid("abc124"))
        assertFalse(guard.isValid("abc12"))
        assertFalse(guard.isValid("abc1234"))
    }

    @Test fun `null and empty rejected`() {
        val guard = TokenGuard("t")
        assertFalse(guard.isValid(null))
        assertFalse(guard.isValid(""))
    }

    @Test fun `prefix and suffix not accepted`() {
        val guard = TokenGuard("secret")
        assertFalse(guard.isValid("sec"))
        assertFalse(guard.isValid("secretx"))
    }
}
