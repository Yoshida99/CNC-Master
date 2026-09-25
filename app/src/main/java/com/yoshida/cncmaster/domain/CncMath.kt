package com.yoshida.cncmaster.domain

import kotlin.math.PI

object CncMath {
    /** RPM from cutting speed Vc (m/min) and diameter D (mm). */
    fun rpm(cuttingSpeedMMin: Double, diameterMm: Double): Double {
        if (cuttingSpeedMMin <= 0.0 || diameterMm <= 0.0) return 0.0
        return (1000.0 * cuttingSpeedMMin) / (PI * diameterMm)
    }

    /** Cutting speed Vc (m/min) from RPM and diameter D (mm). */
    fun cuttingSpeed(rpm: Double, diameterMm: Double): Double {
        if (rpm <= 0.0 || diameterMm <= 0.0) return 0.0
        return (PI * diameterMm * rpm) / 1000.0
    }

    /** Feed rate Vf (mm/min) from feed per revolution f (mm/rev) and RPM. */
    fun feedRate(feedPerRevMm: Double, rpm: Double): Double {
        if (feedPerRevMm <= 0.0 || rpm <= 0.0) return 0.0
        return feedPerRevMm * rpm
    }

    /** Feed per revolution f (mm/rev) from linear feed Vf (mm/min) and RPM. */
    fun feedPerRev(feedRateMmMin: Double, rpm: Double): Double {
        if (feedRateMmMin <= 0.0 || rpm <= 0.0) return 0.0
        return feedRateMmMin / rpm
    }
}
