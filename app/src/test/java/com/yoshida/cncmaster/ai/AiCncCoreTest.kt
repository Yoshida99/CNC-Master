package com.yoshida.cncmaster.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCncCoreTest {

    @Test
    fun `G96 without spindle clamp is critical`() {
        val findings = CncProgramSafety.inspect(
            "%\nO1001\nT0101\nG96 S180 M03\nG00 X40 Z2\nM05\nM30\n%"
        )

        assertTrue(findings.any {
            it.level == CncRiskLevel.CRITICAL && it.title.contains("G96")
        })
    }

    @Test
    fun `G96 with spindle clamp does not raise G96 critical`() {
        val findings = CncProgramSafety.inspect(
            "%\nO1001\nT0101\nG50 S2500\nG96 S180 M03\nG00 X40 Z2\nM05\nM30\n%"
        )

        assertFalse(findings.any {
            it.level == CncRiskLevel.CRITICAL && it.title.contains("G96")
        })
    }

    @Test
    fun `negative X is critical`() {
        val findings = CncProgramSafety.inspect(
            "O1002\nT0202\nG97 S500 M03\nG00 X-1.0 Z5.0\nM05\nM30"
        )

        assertTrue(findings.any {
            it.level == CncRiskLevel.CRITICAL && it.title.contains("отрицательный X")
        })
    }

    @Test
    fun `macro variables are critical`() {
        val findings = CncProgramSafety.inspect(
            "O1003\nT0101\nG97 S400 M03\n#100=10\nG00 X40 Z5\nM05\nM30"
        )

        assertTrue(findings.any {
            it.level == CncRiskLevel.CRITICAL && it.title.contains("Макропрограмма")
        })
    }

    @Test
    fun `suggested filename uses O number`() {
        assertEquals("O1234.NC", CncProgramSafety.suggestedFileName("%\nO1234\nG97 S500\nM30\n%"))
        assertEquals("CNC_AI_DRAFT.NC", CncProgramSafety.suggestedFileName("G97 S500\nM30"))
    }

    @Test
    fun `machine export is ASCII and CRLF`() {
        val exported = CncProgramSafety.machineAscii("O1\n(ТЕСТ)\nG97 S500\nM30")

        assertTrue(exported.endsWith("\r\n"))
        assertTrue(exported.all { it == '\r' || it == '\n' || it == '\t' || it.code in 32..126 })
        assertFalse(exported.contains("ТЕСТ"))
    }
}
