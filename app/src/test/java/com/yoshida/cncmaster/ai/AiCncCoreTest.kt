package com.yoshida.cncmaster.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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

    @Test
    fun `AI request sends configured app token`() {
        val request = AiCncApi.buildRequest(
            endpoint = "https://example.com/v1/cnc/generate",
            payload = "{}",
            appToken = "  test-token  ",
        )

        assertEquals("test-token", request.header(CNC_APP_TOKEN_HEADER))
    }

    @Test
    fun `AI request omits empty app token`() {
        val request = AiCncApi.buildRequest(
            endpoint = "https://example.com/v1/cnc/generate",
            payload = "{}",
            appToken = "   ",
        )

        assertNull(request.header(CNC_APP_TOKEN_HEADER))
    }


    @Test
    fun `gateway session request sends bootstrap app token`() {
        val request = AiCncApi.buildSessionRequest(
            endpoint = "https://example.com/v1/auth/session",
            appToken = " bootstrap-token ",
        )

        assertEquals("bootstrap-token", request.header(CNC_APP_TOKEN_HEADER))
        assertNull(request.header("Authorization"))
    }

    @Test
    fun `AI request prefers bearer session over app token`() {
        val request = AiCncApi.buildRequest(
            endpoint = "https://example.com/v1/cnc/generate",
            payload = "{}",
            appToken = "legacy-token",
            bearerToken = "session-token",
        )

        assertEquals("Bearer session-token", request.header("Authorization"))
        assertNull(request.header(CNC_APP_TOKEN_HEADER))
    }


    @Test
    fun `gateway health reports connected GPU worker and queue`() {
        val health = AiCncApi.parseHealth(
            """{"ok":true,"worker_connected":true,"worker_id":"GPU-PC","queue_depth":1,"queue_max":8}"""
        )

        assertTrue(health.serverOnline)
        assertTrue(health.aiOnline)
        assertEquals(true, health.workerConnected)
        assertEquals("GPU-PC", health.workerId)
        assertEquals(1, health.queueDepth)
        assertEquals(8, health.queueMax)
    }

    @Test
    fun `gateway health reports AI offline when worker disconnected`() {
        val health = AiCncApi.parseHealth(
            """{"ok":true,"worker_connected":false,"queue_depth":0,"queue_max":8}"""
        )

        assertTrue(health.serverOnline)
        assertFalse(health.aiOnline)
        assertEquals(false, health.workerConnected)
    }

    @Test
    fun `direct local backend health stays compatible`() {
        val health = AiCncApi.parseHealth(
            """{"ok":true,"provider":"ollama","model":"qwen2.5-coder:7b-instruct","ai_configured":true}"""
        )

        assertTrue(health.serverOnline)
        assertTrue(health.aiOnline)
        assertNull(health.workerConnected)
        assertEquals("ollama", health.provider)
        assertEquals("qwen2.5-coder:7b-instruct", health.model)
    }

    @Test
    fun `gateway session request sends installation id without shared secret`() {
        val request = AiCncApi.buildSessionRequest(
            endpoint = "https://example.com/v1/auth/session",
            installationId = "7f4c7834-5727-4ca8-9c53-5b769fa18e8e",
        )

        assertEquals(
            "7f4c7834-5727-4ca8-9c53-5b769fa18e8e",
            request.header("X-CNC-Install-Id"),
        )
        assertNull(request.header(CNC_APP_TOKEN_HEADER))
    }

}
