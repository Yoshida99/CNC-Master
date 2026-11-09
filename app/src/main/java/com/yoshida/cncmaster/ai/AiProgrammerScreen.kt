package com.yoshida.cncmaster.ai

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoshida.cncmaster.BuildConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProgrammerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val installationId = remember(context) { AiInstallationId.get(context) }

    var task by remember { mutableStateOf("") }
    var machine by remember { mutableStateOf("Токарный станок, оси X/Z") }
    var control by remember { mutableStateOf("Fanuc 0i-TF") }
    var material by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var tools by remember { mutableStateOf("") }
    var workholding by remember { mutableStateOf("") }
    var zeroPoint by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var aiWarnings by remember { mutableStateOf(emptyList<String>()) }
    var assumptions by remember { mutableStateOf(emptyList<String>()) }
    var errorText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var backendHealth by remember { mutableStateOf<AiBackendHealth?>(null) }
    var healthLoading by remember { mutableStateOf(false) }
    var healthError by remember { mutableStateOf("") }
    var exportAcknowledged by remember { mutableStateOf(false) }

    val backendAvailable = BuildConfig.AI_BACKEND_URL.isNotBlank()
    val findings = remember(code) { CncProgramSafety.inspect(code) }
    val hasCritical = findings.any { it.level == CncRiskLevel.CRITICAL }

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(CncProgramSafety.machineAscii(code).toByteArray(Charsets.US_ASCII))
                stream.flush()
            } ?: error("Не удалось открыть выбранный файл")
        }.onSuccess {
            Toast.makeText(context, "NC-файл сохранён. Перед запуском проверь его на станке.", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(context, "Ошибка сохранения: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }


    LaunchedEffect(backendAvailable) {
        if (!backendAvailable) {
            backendHealth = null
            healthError = ""
            return@LaunchedEffect
        }
        healthLoading = true
        runCatching {
            AiCncApi.checkHealth(BuildConfig.AI_BACKEND_URL)
        }.onSuccess {
            backendHealth = it
            healthError = ""
        }.onFailure {
            backendHealth = null
            healthError = it.message ?: "Нет связи с AI-сервером"
        }
        healthLoading = false
    }

    val refreshHealth: () -> Unit = {
        if (backendAvailable && !healthLoading) {
            healthLoading = true
            scope.launch {
                runCatching {
                    AiCncApi.checkHealth(BuildConfig.AI_BACKEND_URL)
                }.onSuccess {
                    backendHealth = it
                    healthError = ""
                }.onFailure {
                    backendHealth = null
                    healthError = it.message ?: "Нет связи с AI-сервером"
                }
                healthLoading = false
            }
        }
    }

    val requestAi: () -> Unit = requestAi@{
        if (!backendAvailable) {
            errorText = "AI backend ещё не подключён. Редактор, проверка и экспорт работают офлайн."
            return@requestAi
        }
        if (task.isBlank()) {
            errorText = "Сначала опиши задачу."
            return@requestAi
        }
        if (loading) return@requestAi

        loading = true
        errorText = ""
        scope.launch {
            runCatching {
                AiCncApi.generate(
                    baseUrl = BuildConfig.AI_BACKEND_URL,
                    appToken = BuildConfig.AI_APP_TOKEN,
                    installationId = installationId,
                    request = AiCncRequest(
                        message = task,
                        machine = machine,
                        control = control,
                        material = material,
                        stock = stock,
                        tools = tools,
                        workholding = workholding,
                        zeroPoint = zeroPoint,
                        currentCode = code,
                    ),
                )
            }.onSuccess { result ->
                answer = result.answer
                aiWarnings = result.warnings
                assumptions = result.assumptions
                if (result.code.isNotBlank()) code = result.code
                runCatching {
                    AiCncApi.checkHealth(BuildConfig.AI_BACKEND_URL)
                }.onSuccess {
                    backendHealth = it
                    healthError = ""
                }
            }.onFailure { failure ->
                val health = runCatching {
                    AiCncApi.checkHealth(BuildConfig.AI_BACKEND_URL)
                }.getOrNull()
                backendHealth = health
                errorText = when {
                    health?.serverOnline == true && health.workerConnected == false ->
                        "AI-сервер доступен, но GPU worker сейчас офлайн. Редактор, локальная проверка и USB-экспорт продолжают работать."
                    health == null ->
                        "Нет связи с AI-сервером. Проверь интернет и повтори запрос. Редактор и локальная проверка работают офлайн."
                    else ->
                        failure.message ?: "Не удалось получить ответ AI"
                }
            }
            loading = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("AI-программист", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Text(
                        "‹",
                        fontSize = 34.sp,
                        modifier = Modifier.padding(horizontal = 16.dp).clickable { onBack() },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SafetyHeaderCard(
                    backendAvailable = backendAvailable,
                    health = backendHealth,
                    checking = healthLoading,
                    healthError = healthError,
                    onRefresh = refreshHealth,
                )
            }

            item {
                SectionTitle("Задача для нейросети")
                OutlinedTextField(
                    value = task,
                    onValueChange = { task = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("Что нужно обработать / исправить") },
                    placeholder = { Text("Например: наружная резьба M12×1, длина 18 мм, заготовка Ø14…") },
                )
            }

            item {
                SectionTitle("Контекст станка")
                CompactField("Станок", machine) { machine = it }
                CompactField("Стойка", control) { control = it }
                CompactField("Материал", material) { material = it }
                CompactField("Заготовка и размеры", stock) { stock = it }
                CompactField("Инструменты / корректоры", tools) { tools = it }
                CompactField("Зажим / патрон / вылет", workholding) { workholding = it }
                CompactField("Ноль детали / система координат", zeroPoint) { zeroPoint = it }
            }

            item {
                Button(
                    onClick = requestAi,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  Генерирую черновик…")
                    } else {
                        Text(if (backendAvailable) "Спросить AI" else "AI пока не подключён")
                    }
                }
                if (errorText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorText, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = requestAi,
                            enabled = backendAvailable && task.isNotBlank() && !loading,
                        ) {
                            Text("Повторить запрос")
                        }
                        OutlinedButton(
                            onClick = refreshHealth,
                            enabled = backendAvailable && !healthLoading,
                        ) {
                            Text("Проверить AI")
                        }
                    }
                }
            }

            if (answer.isNotBlank() || aiWarnings.isNotEmpty() || assumptions.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (answer.isNotBlank()) Text(answer)
                            if (assumptions.isNotEmpty()) {
                                Text("Допущения AI", fontWeight = FontWeight.Bold)
                                assumptions.forEach { Text("• $it", fontSize = 13.sp) }
                            }
                            if (aiWarnings.isNotEmpty()) {
                                Text("Предупреждения AI", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                aiWarnings.forEach { Text("• $it", fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle("Редактор G-кода")
                Text(
                    "Можно писать код вручную, вставить существующую программу или отредактировать результат AI.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .64f),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 12,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    label = { Text("NC program") },
                    placeholder = { Text("%\nO0001\n...\nM30\n%") },
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        enabled = code.isNotBlank(),
                        onClick = {
                            clipboard.setText(AnnotatedString(code))
                            Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
                        },
                    ) { Text("Копировать") }
                    OutlinedButton(
                        enabled = code.isNotBlank(),
                        onClick = {
                            val count = CncProgramSafety.inspect(code).size
                            Toast.makeText(context, "Проверка обновлена: $count пункт(ов)", Toast.LENGTH_SHORT).show()
                        },
                    ) { Text("Проверить") }
                }
            }

            if (code.isNotBlank()) {
                item {
                    SectionTitle("Проверка перед экспортом")
                    findings.forEach { FindingCard(it) }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasCritical) MaterialTheme.colorScheme.errorContainer else Color(0xFF142019),
                    ),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Экспорт на флешку", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Android откроет системный выбор папки. Если USB-флешка подключена к телефону через OTG и видна системе, выбери её и сохрани файл .NC.",
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Checkbox(
                                checked = exportAcknowledged,
                                onCheckedChange = { exportAcknowledged = it },
                            )
                            Text(
                                "Перед фактическим резанием я проверю программу, корректора, ноль, зажим и траекторию на станке в графике/Single Block/Dry Run.",
                                modifier = Modifier.padding(top = 11.dp),
                                fontSize = 13.sp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = code.isNotBlank() && exportAcknowledged,
                            onClick = { createDocument.launch(CncProgramSafety.suggestedFileName(code)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Сохранить .NC на устройство / USB")
                        }
                        if (hasCritical) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "В коде есть критические флаги. Экспорт нужен только для дальнейшей проверки — не как разрешение на запуск.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "AI-код всегда считается черновиком. CNC Master не выполняет удалённый старт станка и не подтверждает отсутствие столкновений.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun SafetyHeaderCard(
    backendAvailable: Boolean,
    health: AiBackendHealth?,
    checking: Boolean,
    healthError: String,
    onRefresh: () -> Unit,
) {
    val statusText = when {
        !backendAvailable ->
            "AI endpoint не настроен. Редактор, локальная проверка и USB-экспорт работают офлайн."
        checking ->
            "Проверяю защищённое соединение с AI…"
        health?.aiOnline == true -> buildString {
            append("AI онлайн")
            if (health.workerId.isNotBlank()) append(" · GPU worker подключён")
            if (health.queueMax > 0) append(" · очередь ${health.queueDepth}/${health.queueMax}")
        }
        health?.serverOnline == true && health.workerConnected == false ->
            "Gateway онлайн, но GPU worker сейчас офлайн. Локальные функции CNC Master продолжают работать."
        healthError.isNotBlank() ->
            "AI временно недоступен. Редактор, проверка G-кода и экспорт доступны офлайн."
        else ->
            "AI endpoint настроен. Нажми «Проверить AI», чтобы обновить состояние."
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121B23)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("AI + редактор NC", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                statusText,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .78f),
            )
            if (backendAvailable) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !checking,
                ) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("  Проверяю…")
                    } else {
                        Text("Проверить AI")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Сгенерированный NC-код всегда остаётся черновиком до проверки на конкретном станке.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
            )
        }
    }
}

@Composable
private fun CompactField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        singleLine = true,
        label = { Text(label) },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
}

@Composable
private fun FindingCard(finding: CncSafetyFinding) {
    val background = when (finding.level) {
        CncRiskLevel.CRITICAL -> MaterialTheme.colorScheme.errorContainer
        CncRiskLevel.WARNING -> MaterialTheme.colorScheme.surfaceVariant
        CncRiskLevel.INFO -> MaterialTheme.colorScheme.surface
    }
    val foreground = when (finding.level) {
        CncRiskLevel.CRITICAL -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(13.dp)) {
            Text("${finding.level.title} · ${finding.title}", fontWeight = FontWeight.Bold, color = foreground)
            Spacer(Modifier.height(3.dp))
            Text(finding.detail, color = foreground.copy(alpha = .82f), fontSize = 12.sp)
        }
    }
}
