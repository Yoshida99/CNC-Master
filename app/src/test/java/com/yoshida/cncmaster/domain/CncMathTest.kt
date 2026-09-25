package com.yoshida.cncmaster.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CncMathTest {
    @Test
    fun rpm_isCalculatedCorrectly() {
        assertEquals(1591.55, CncMath.rpm(150.0, 30.0), 0.1)
    }

    @Test
    fun feedRate_isCalculatedCorrectly() {
        assertEquals(300.0, CncMath.feedRate(0.2, 1500.0), 0.001)
    }
}
