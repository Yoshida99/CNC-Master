package com.yoshida.cncmaster.v02

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedThreadCatalogTest {
    @Test
    fun uncQuarter20_convertsToMetricCorrectly() {
        val row = ExtendedThreadCatalog.unc.first { it.designation == "1/4-20 UNC" }
        assertEquals(6.35, row.majorMm, 0.001)
        assertEquals(1.27, row.pitchMm, 0.001)
        assertEquals(5.5245, row.pitchDiameterMm, 0.001)
    }

    @Test
    fun unfQuarter28_hasExpectedPitch() {
        val row = ExtendedThreadCatalog.unf.first { it.designation == "1/4-28 UNF" }
        assertEquals(0.9071, row.pitchMm, 0.001)
        assertEquals(28, row.tpi)
    }

    @Test
    fun bsptAndNptAreNotSameStandard() {
        val bspt = ExtendedThreadCatalog.bspt.first { it.designation == "R 1/4" }
        val npt = ExtendedThreadCatalog.npt.first { it.designation == "1/4-18 NPT" }
        assertEquals(55, bspt.flankAngleDeg)
        assertEquals(60, npt.flankAngleDeg)
        assertTrue(bspt.tpi != npt.tpi)
    }
}
