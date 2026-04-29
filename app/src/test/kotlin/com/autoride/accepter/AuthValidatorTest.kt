package com.autoride.accepter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun shouldAcceptValidEmail() {
        assertTrue(AuthValidator.isValidEmail("driver@example.com"))
    }

    @Test
    fun shouldRejectInvalidEmail() {
        assertFalse(AuthValidator.isValidEmail("driver.example.com"))
    }

    @Test
    fun shouldAcceptStrongEnoughPassword() {
        assertTrue(AuthValidator.isValidPassword("password123"))
    }

    @Test
    fun shouldRejectShortPassword() {
        assertFalse(AuthValidator.isValidPassword("pass12"))
    }
}
