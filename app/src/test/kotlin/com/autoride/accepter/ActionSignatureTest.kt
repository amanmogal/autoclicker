package com.autoride.accepter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ActionSignatureTest {

    @Test
    fun `build keeps same signature for small coordinate jitter`() {
        val first = ActionSignature.build(
            windowType = "APP",
            label = "Accept",
            fare = 173.0,
            distance = 1.8,
            centerX = 601,
            centerY = 1201,
            viewId = "com.rapido.rider:id/accept_button"
        )

        val second = ActionSignature.build(
            windowType = "APP",
            label = "Accept",
            fare = 173.0,
            distance = 1.8,
            centerX = 619,
            centerY = 1218,
            viewId = "com.rapido.rider:id/accept_button"
        )

        assertEquals(first, second)
    }

    @Test
    fun `build changes signature for meaningfully different position`() {
        val first = ActionSignature.build(
            windowType = "APP",
            label = "Accept",
            fare = 173.0,
            distance = 1.8,
            centerX = 601,
            centerY = 1201,
            viewId = "com.rapido.rider:id/accept_button"
        )

        val second = ActionSignature.build(
            windowType = "APP",
            label = "Accept",
            fare = 173.0,
            distance = 1.8,
            centerX = 901,
            centerY = 1201,
            viewId = "com.rapido.rider:id/accept_button"
        )

        assertNotEquals(first, second)
    }
}
