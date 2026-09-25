package com.yoshida.cncmaster.v02

data class UnifiedThreadRow(
    val designation: String,
    val majorIn: Double,
    val tpi: Int,
    val pitchDiameterIn: Double,
    val externalMinorIn: Double,
    val internalMinorIn: Double,
) {
    val majorMm: Double get() = majorIn * 25.4
    val pitchMm: Double get() = 25.4 / tpi.toDouble()
    val pitchDiameterMm: Double get() = pitchDiameterIn * 25.4
    val externalMinorMm: Double get() = externalMinorIn * 25.4
    val internalMinorMm: Double get() = internalMinorIn * 25.4
}

data class TaperedPipeReference(
    val designation: String,
    val tpi: Double,
    val nominalMaleDiameterMm: Double,
    val flankAngleDeg: Int,
    val standard: String,
    val profileLetter: String,
) {
    val pitchMm: Double get() = 25.4 / tpi
}

object ExtendedThreadCatalog {
    val unc: List<UnifiedThreadRow> = listOf(
        UnifiedThreadRow("1/4-20 UNC", 0.2500, 20, 0.2175, 0.1905, 0.1959),
        UnifiedThreadRow("5/16-18 UNC", 0.3125, 18, 0.2764, 0.2464, 0.2524),
        UnifiedThreadRow("3/8-16 UNC", 0.3750, 16, 0.3344, 0.3005, 0.3073),
        UnifiedThreadRow("7/16-14 UNC", 0.4375, 14, 0.3911, 0.3525, 0.3602),
        UnifiedThreadRow("1/2-13 UNC", 0.5000, 13, 0.4500, 0.4084, 0.4167),
        UnifiedThreadRow("9/16-12 UNC", 0.5625, 12, 0.5084, 0.4633, 0.4723),
        UnifiedThreadRow("5/8-11 UNC", 0.6250, 11, 0.5660, 0.5168, 0.5266),
        UnifiedThreadRow("3/4-10 UNC", 0.7500, 10, 0.6850, 0.6309, 0.6417),
        UnifiedThreadRow("7/8-9 UNC", 0.8750, 9, 0.8028, 0.7427, 0.7547),
        UnifiedThreadRow("1-8 UNC", 1.0000, 8, 0.9188, 0.8512, 0.8647),
    )

    val unf: List<UnifiedThreadRow> = listOf(
        UnifiedThreadRow("1/4-28 UNF", 0.2500, 28, 0.2268, 0.2074, 0.2113),
        UnifiedThreadRow("5/16-24 UNF", 0.3125, 24, 0.2854, 0.2629, 0.2674),
        UnifiedThreadRow("3/8-24 UNF", 0.3750, 24, 0.3479, 0.3254, 0.3299),
        UnifiedThreadRow("7/16-20 UNF", 0.4375, 20, 0.4050, 0.3780, 0.3834),
        UnifiedThreadRow("1/2-20 UNF", 0.5000, 20, 0.4675, 0.4405, 0.4459),
        UnifiedThreadRow("9/16-18 UNF", 0.5625, 18, 0.5264, 0.4964, 0.5024),
        UnifiedThreadRow("5/8-18 UNF", 0.6250, 18, 0.5889, 0.5589, 0.5649),
        UnifiedThreadRow("3/4-16 UNF", 0.7500, 16, 0.7094, 0.6763, 0.6823),
        UnifiedThreadRow("7/8-14 UNF", 0.8750, 14, 0.8286, 0.7900, 0.7977),
        UnifiedThreadRow("1-12 UNF", 1.0000, 12, 0.9459, 0.9001, 0.9098),
    )

    val bspt: List<TaperedPipeReference> = listOf(
        TaperedPipeReference("R 1/16", 28.0, 7.72, 55, "ISO 7-1 / BSPT", "Whitworth"),
        TaperedPipeReference("R 1/8", 28.0, 9.73, 55, "ISO 7-1 / BSPT", "Whitworth"),
        TaperedPipeReference("R 1/4", 19.0, 13.16, 55, "ISO 7-1 / BSPT", "Whitworth"),
        TaperedPipeReference("R 3/8", 19.0, 16.86, 55, "ISO 7-1 / BSPT", "Whitworth"),
        TaperedPipeReference("R 1/2", 14.0, 20.96, 55, "ISO 7-1 / BSPT", "Whitworth"),
        TaperedPipeReference("R 3/4", 14.0, 26.44, 55, "ISO 7-1 / BSPT", "Whitworth"),
        TaperedPipeReference("R 1", 11.0, 33.25, 55, "ISO 7-1 / BSPT", "Whitworth"),
        TaperedPipeReference("R 1-1/4", 11.0, 41.91, 55, "ISO 7-1 / BSPT", "Whitworth"),
        TaperedPipeReference("R 1-1/2", 11.0, 47.80, 55, "ISO 7-1 / BSPT", "Whitworth"),
        TaperedPipeReference("R 2", 11.0, 59.61, 55, "ISO 7-1 / BSPT", "Whitworth"),
    )

    val npt: List<TaperedPipeReference> = listOf(
        TaperedPipeReference("1/16-27 NPT", 27.0, 7.84, 60, "ASME B1.20.1 / NPT", "Unified"),
        TaperedPipeReference("1/8-27 NPT", 27.0, 10.18, 60, "ASME B1.20.1 / NPT", "Unified"),
        TaperedPipeReference("1/4-18 NPT", 18.0, 13.54, 60, "ASME B1.20.1 / NPT", "Unified"),
        TaperedPipeReference("3/8-18 NPT", 18.0, 16.98, 60, "ASME B1.20.1 / NPT", "Unified"),
        TaperedPipeReference("1/2-14 NPT", 14.0, 21.14, 60, "ASME B1.20.1 / NPT", "Unified"),
        TaperedPipeReference("3/4-14 NPT", 14.0, 26.49, 60, "ASME B1.20.1 / NPT", "Unified"),
        TaperedPipeReference("1-11.5 NPT", 11.5, 33.14, 60, "ASME B1.20.1 / NPT", "Unified"),
        TaperedPipeReference("1-1/4-11.5 NPT", 11.5, 41.90, 60, "ASME B1.20.1 / NPT", "Unified"),
        TaperedPipeReference("1-1/2-11.5 NPT", 11.5, 47.97, 60, "ASME B1.20.1 / NPT", "Unified"),
        TaperedPipeReference("2-11.5 NPT", 11.5, 60.00, 60, "ASME B1.20.1 / NPT", "Unified"),
    )
}
