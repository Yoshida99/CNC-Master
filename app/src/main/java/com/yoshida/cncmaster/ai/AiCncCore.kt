package com.yoshida.cncmaster.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiCncRequest(
    val message: String,
    val machine: String,
    val control: String,
    val material: String,
    val stock: String,
    val tools: String,
    val workholding: String,
    val zeroPoint: String,
    val currentCode: String = "",
)

data class AiCncResult(
    val answer: String,
    val code: String,
    val warnings: List<String>,
    val assumptions: List<String>,
)

enum class CncRiskLevel(val title: String) {
    INFO("Инфо"),
    WARNING("Проверить"),
    CRITICAL("Критично"),
}

data class CncSafetyFinding(
    val level: CncRiskLevel,
    val title: String,
    val detail: String,
)

object AiCncApi {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generate(baseUrl: String, request: AiCncRequest): AiCncResult = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) { "AI backend ещё не настроен" }
        val endpoint = baseUrl.trimEnd('/') + "/v1/cnc/generate"

        val payload = JSONObject()
            .put("message", request.message)
            .put("machine", request.machine)
            .put("control", request.control)
            .put("material", request.material)
            .put("stock", request.stock)
            .put("tools", request.tools)
            .put("workholding", request.workholding)
            .put("zero_point", request.zeroPoint)
            .put("current_code", request.currentCode)

        val httpRequest = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(jsonType))
            .header("Accept", "application/json")
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(body).optString("detail") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "AI backend вернул HTTP ${response.code}"
                error(message)
            }

            val json = JSONObject(body)
            AiCncResult(
                answer = json.optString("answer"),
                code = json.optString("code"),
                warnings = json.stringList("warnings"),
                assumptions = json.stringList("assumptions"),
            )
        }
    }

    private fun JSONObject.stringList(name: String): List<String> {
        val array = optJSONArray(name) ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}

object CncProgramSafety {
    private val toolRegex = Regex("(?m)^\\s*T\\d{2,8}\\b", RegexOption.IGNORE_CASE)
    private val negativeXRegex = Regex("\\bX\\s*-\\s*\\d", RegexOption.IGNORE_CASE)
    private val machineMCodeRegex = Regex("\\bM(?!0?0\\b|0?1\\b|0?2\\b|0?3\\b|0?4\\b|0?5\\b|0?8\\b|0?9\\b|30\\b)\\d+\\b", RegexOption.IGNORE_CASE)

    fun inspect(program: String): List<CncSafetyFinding> {
        val code = program.uppercase()
        if (code.isBlank()) return emptyList()

        val findings = mutableListOf<CncSafetyFinding>()

        if ("G96" in code && "G50" !in code) {
            findings += CncSafetyFinding(
                CncRiskLevel.CRITICAL,
                "G96 без ограничения оборотов",
                "При постоянной скорости резания обязательно проверь ограничение максимальных оборотов шпинделя для конкретного станка/патрона.",
            )
        }
        if (negativeXRegex.containsMatchIn(code)) {
            findings += CncSafetyFinding(
                CncRiskLevel.CRITICAL,
                "Обнаружен отрицательный X",
                "Для большинства токарных программ это требует отдельной проверки системы координат, типа программирования X и фактической кинематики станка.",
            )
        }
        if (listOf("G28", "G53", "G10").any { it in code }) {
            findings += CncSafetyFinding(
                CncRiskLevel.CRITICAL,
                "Команда перемещения/координат повышенного риска",
                "Найдены G28/G53/G10. Проверь промежуточные перемещения, машинные координаты и запись смещений по руководству именно твоего станка.",
            )
        }
        if ("#" in code || "WHILE" in code || "GOTO" in code) {
            findings += CncSafetyFinding(
                CncRiskLevel.CRITICAL,
                "Макропрограмма",
                "Макропеременные и переходы нельзя считать проверенными локальным анализатором. Нужна отдельная построчная проверка.",
            )
        }
        if (!toolRegex.containsMatchIn(code)) {
            findings += CncSafetyFinding(
                CncRiskLevel.WARNING,
                "Не найден вызов инструмента",
                "Проверь номер позиции и корректора T, геометрию инструмента и фактический вылет.",
            )
        }
        if ("G96" !in code && "G97" !in code) {
            findings += CncSafetyFinding(
                CncRiskLevel.WARNING,
                "Режим шпинделя не указан явно",
                "Уточни, должен ли участок выполняться в G96 или G97, и проверь допустимые обороты.",
            )
        }
        if (("G41" in code || "G42" in code)) {
            findings += CncSafetyFinding(
                CncRiskLevel.WARNING,
                "Коррекция радиуса вершины",
                "Проверь направление G41/G42, номер корректора, безопасный подвод и обязательную отмену коррекции.",
            )
        }
        if ("G76" in code || Regex("\\bG92\\b").containsMatchIn(code)) {
            findings += CncSafetyFinding(
                CncRiskLevel.WARNING,
                "Цикл резьбы",
                "Проверь стартовую X/Z, глубину, шаг, направление резьбы, направление шпинделя и формат цикла для конкретной версии Fanuc.",
            )
        }
        if (machineMCodeRegex.containsMatchIn(code)) {
            findings += CncSafetyFinding(
                CncRiskLevel.WARNING,
                "Станкоспецифичный M-код",
                "Есть M-коды вне базового набора. Их назначение зависит от производителя станка — сверяйся с его документацией.",
            )
        }
        if ("M30" !in code && "M02" !in code) {
            findings += CncSafetyFinding(
                CncRiskLevel.INFO,
                "Нет явного конца программы",
                "Если это полная программа, проверь наличие корректного M30/M02. Для фрагмента это нормально.",
            )
        }
        if ("M03" in code && "M04" in code) {
            findings += CncSafetyFinding(
                CncRiskLevel.WARNING,
                "Есть оба направления шпинделя",
                "Убедись, что переключение M03/M04 действительно задумано и выполняется только после полной остановки шпинделя.",
            )
        }

        if (findings.none { it.level == CncRiskLevel.CRITICAL }) {
            findings += CncSafetyFinding(
                CncRiskLevel.INFO,
                "Локальная проверка завершена",
                "Это не проверка траектории и не гарантия отсутствия столкновений. Перед резанием обязательны графика/симуляция, безопасная позиция, Single Block/Dry Run и контроль первого прохода.",
            )
        }
        return findings
    }

    fun machineAscii(program: String): String = buildString(program.length) {
        program.replace("\r\n", "\n").replace('\r', '\n').forEach { ch ->
            when {
                ch == '\n' || ch == '\t' -> append(ch)
                ch.code in 32..126 -> append(ch)
                else -> append(' ')
            }
        }
    }.lineSequence().joinToString("\r\n") { it.trimEnd() }.trim() + "\r\n"

    fun suggestedFileName(program: String): String {
        val number = Regex("(?m)^\\s*O(\\d{1,8})\\b", RegexOption.IGNORE_CASE)
            .find(program)?.groupValues?.getOrNull(1)
        return if (number != null) "O$number.NC" else "CNC_AI_DRAFT.NC"
    }
}
