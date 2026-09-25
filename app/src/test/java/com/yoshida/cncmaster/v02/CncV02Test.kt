package com.yoshida.cncmaster.v02

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CncV02Test {
    @Test
    fun metricM12x1_basicGeometryMatchesReference() {
        val g = ThreadCalculator.metricBasic(12.0, 1.0)
        assertEquals(11.350, g.pitchDiameterBasicMm, 0.001)
        assertEquals(10.773, g.externalMinorBasicMm, 0.001)
        assertEquals(10.917, g.internalMinorBasicMm, 0.001)
        assertEquals(11.0, g.tapDrillApproxMm, 0.01)
    }

    @Test
    fun coarsePitchM12_is175() {
        assertEquals(1.75, ThreadCalculator.standardCoarsePitch(12.0)!!, 0.0001)
    }

    @Test
    fun fanucG76_containsExpectedWords() {
        val result = FanucThreadGenerator.generate(
            FanucThreadInput(
                nominalDiameterMm = 12.0,
                pitchMm = 1.0,
                zEndMm = -18.0,
                side = ThreadSide.EXTERNAL,
                hand = ThreadHand.RIGHT,
            )
        )
        assertEquals(10.773, result.calculatedFinalX, 0.001)
        assertTrue(result.g76.contains("G76 P020060 Q50 R0.050"))
        assertTrue(result.g76.contains("X10.773"))
        assertTrue(result.g76.contains("F1.000"))
    }

    @Test
    fun leftHandThread_requiresOrientationWarning() {
        val result = FanucThreadGenerator.generate(
            FanucThreadInput(
                nominalDiameterMm = 12.0,
                pitchMm = 1.0,
                zEndMm = -18.0,
                side = ThreadSide.INTERNAL,
                hand = ThreadHand.LEFT,
            )
        )
        assertTrue(result.warnings.any { it.contains("M03/M04") })
    }

    @Test
    fun cuttingAdvisor_returnsFinitePositiveValues() {
        val material = CncCatalog.materials.first { it.id == "30HGSA_HARD" }
        val r = CuttingAdvisor.recommend(
            material = material,
            operation = OperationType.ROUGH,
            strategy = Strategy.STABLE,
            diameterMm = 35.5,
            insertRadiusMm = 0.4,
        )
        assertTrue(r.cuttingSpeedMMin > 0.0)
        assertTrue(r.rpm > 0)
        assertTrue(r.feedPerRevMm > 0.0)
        assertTrue(r.feedRateMmMin > 0)
        assertTrue(r.depthOfCutMm > 0.0)
    }
}
