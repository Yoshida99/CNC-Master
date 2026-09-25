package com.yoshida.cncmaster

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoshida.cncmaster.domain.CncMath
import com.yoshida.cncmaster.ui.theme.CNCMasterTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var openUpdatesRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openUpdatesRequested = intent?.getBooleanExtra("open_updates", false) == true
        AppUpdateManager.schedule(this)
        setContent {
            CNCMasterTheme {
                CncMasterApp(openUpdatesRequested = openUpdatesRequested)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_updates", false)) {
            openUpdatesRequested = true
        }
    }
}

private enum class Screen { HOME, RPM, FEED, VC, THREADS, MATERIALS, TOOLING, DIAGNOSTICS }

private data class HomeTool(
    val title: String,
    val subtitle: String,
    val badge: String,
    val screen: Screen
)

@Composable
private fun CncMasterApp(openUpdatesRequested: Boolean) {
    var screen by remember { mutableStateOf(Screen.HOME) }

    when (screen) {
        Screen.HOME -> HomeScreen(openUpdatesRequested = openUpdatesRequested, onOpen = { screen = it })
        Screen.RPM -> RpmScreen(onBack = { screen = Screen.HOME })
        Screen.FEED -> FeedScreen(onBack = { screen = Screen.HOME })
        Screen.VC -> CuttingSpeedScreen(onBack = { screen = Screen.HOME })
        Screen.THREADS -> PlaceholderScreen("Резьбы", "Метрические, трубные и дюймовые резьбы — следующий модуль.", onBack = { screen = Screen.HOME })
        Screen.MATERIALS -> PlaceholderScreen("Материалы", "База материалов и стартовые режимы резания — следующий модуль.", onBack = { screen = Screen.HOME })
        Screen.TOOLING -> PlaceholderScreen("Инструмент", "Журнал пластин, державок и стойкости инструмента — следующий модуль.", onBack = { screen = Screen.HOME })
        Screen.DIAGNOSTICS -> PlaceholderScreen("Диагностика", "Симптом → вероятные причины → что проверить первым.", onBack = { screen = Screen.HOME })
    }
}

@Composable
private fun HomeScreen(openUpdatesRequested: Boolean, onOpen: (Screen) -> Unit) {
    val tools = listOf(
        HomeTool("Обороты шпинделя", "Vc + диаметр → RPM", "N", Screen.RPM),
        HomeTool("Подача", "f × RPM → мм/мин", "F", Screen.FEED),
        HomeTool("Скорость резания", "RPM + диаметр → Vc", "VC", Screen.VC),
        HomeTool("Резьбы", "Шаги, диаметры, подсказки", "M", Screen.THREADS),
        HomeTool("Материалы", "Сталь, нержавейка, латунь…", "MAT", Screen.MATERIALS),
        HomeTool("Инструмент", "Пластины и стойкость", "TOOL", Screen.TOOLING),
        HomeTool("Диагностика", "Брак, вибрация, скол, размер", "!", Screen.DIAGNOSTICS)
    )

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("CNC MASTER", fontSize = 30.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text("Карманный помощник наладчика", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f))
                Spacer(Modifier.height(18.dp))
                MachineStatusCard()
                Spacer(Modifier.height(10.dp))
                Text("Быстрые инструменты", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            items(tools) { tool -> ToolCard(tool, onOpen) }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Обновление приложения", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                UpdateCenterCard(openRequested = openUpdatesRequested)
                Spacer(Modifier.height(8.dp))
                UpdateNotificationPermissionCard()
                Spacer(Modifier.height(12.dp))
                Text(
                    "v${BuildConfig.VERSION_NAME} • расчёты работают офлайн",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MachineStatusCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121B23)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("CNC", color = Color(0xFF17120A), fontWeight = FontWeight.Black)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("Рабочий режим", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Расчёты без сети • данные остаются на устройстве", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            }
        }
    }
}

@Composable
private fun ToolCard(tool: HomeTool, onOpen: (Screen) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(tool.screen) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(tool.badge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = if (tool.badge.length > 2) 10.sp else 16.sp)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(tool.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(tool.subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 12.sp)
            }
            Text("›", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculatorScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = { Text("‹", fontSize = 34.sp, modifier = Modifier.padding(horizontal = 16.dp).clickable { onBack() }) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        content = content
    )
}

@Composable
private fun NumberField(value: String, label: String, suffix: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ResultCard(label: String, value: String, unit: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171E20)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 38.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text(" $unit", modifier = Modifier.padding(bottom = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
            }
        }
    }
}

private fun String.toDoubleSafe(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun Double.pretty(decimals: Int = 0): String = String.format(Locale.US, "% .${decimals}f", this).trim()

@Composable
private fun RpmScreen(onBack: () -> Unit) {
    var vc by remember { mutableStateOf("150") }
    var diameter by remember { mutableStateOf("30") }
    val rpm = CncMath.rpm(vc.toDoubleSafe(), diameter.toDoubleSafe())

    CalculatorScaffold("Обороты шпинделя", onBack) { padding ->
        Column(Modifier.padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("n = 1000 × Vc / (π × D)", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            NumberField(vc, "Скорость резания Vc", "м/мин") { vc = it }
            NumberField(diameter, "Диаметр D", "мм") { diameter = it }
            ResultCard("Рекомендуемые расчётные обороты", rpm.pretty(), "об/мин")
            Hint("Учитывай максимальные обороты станка, зажим детали и ограничения производителя инструмента.")
        }
    }
}

@Composable
private fun FeedScreen(onBack: () -> Unit) {
    var f by remember { mutableStateOf("0.20") }
    var rpm by remember { mutableStateOf("1500") }
    val vf = CncMath.feedRate(f.toDoubleSafe(), rpm.toDoubleSafe())

    CalculatorScaffold("Подача", onBack) { padding ->
        Column(Modifier.padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Vf = f × n", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            NumberField(f, "Подача на оборот f", "мм/об") { f = it }
            NumberField(rpm, "Обороты n", "об/мин") { rpm = it }
            ResultCard("Линейная подача", vf.pretty(1), "мм/мин")
            Hint("Для резьбонарезания действуют отдельные правила синхронизации — вынесем их в модуль «Резьбы».")
        }
    }
}

@Composable
private fun CuttingSpeedScreen(onBack: () -> Unit) {
    var rpm by remember { mutableStateOf("1500") }
    var diameter by remember { mutableStateOf("30") }
    val vc = CncMath.cuttingSpeed(rpm.toDoubleSafe(), diameter.toDoubleSafe())

    CalculatorScaffold("Скорость резания", onBack) { padding ->
        Column(Modifier.padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Vc = π × D × n / 1000", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            NumberField(rpm, "Обороты n", "об/мин") { rpm = it }
            NumberField(diameter, "Диаметр D", "мм") { diameter = it }
            ResultCard("Фактическая скорость резания", vc.pretty(1), "м/мин")
            Hint("Сверяй Vc с каталогом конкретной пластины/сплава и фактическими условиями обработки.")
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f), fontSize = 12.sp, lineHeight = 17.sp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(title: String, text: String, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = { Text("‹", fontSize = 34.sp, modifier = Modifier.padding(horizontal = 16.dp).clickable { onBack() }) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text(title, fontWeight = FontWeight.Black, fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(text, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .7f), lineHeight = 22.sp)
            Spacer(Modifier.height(20.dp))
            Text("Модуль уже заложен в навигацию v0.1.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .42f))
        }
    }
}
