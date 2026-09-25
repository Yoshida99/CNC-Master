package com.yoshida.cncmaster.v02

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoshida.cncmaster.BuildConfig
import com.yoshida.cncmaster.UpdateCenterCard
import com.yoshida.cncmaster.UpdateNotificationPermissionCard
import com.yoshida.cncmaster.domain.CncMath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID


enum class V02Screen {
    HOME,
    ADVISOR,
    RPM,
    FEED,
    VC,
    THREADS,
    MATERIALS,
    FANUC,
    DIAGNOSTICS,
    TOOLING,
    PARTS,
}

private data class MenuItem(
    val title: String,
    val subtitle: String,
    val badge: String,
    val screen: V02Screen,
)

@Composable
fun V02HomeScreen(
    openUpdatesRequested: Boolean,
    onNavigate: (V02Screen) -> Unit,
) {
    val items = listOf(
        MenuItem("Умные режимы", "Материал + операция → Vc / RPM / f / F / ap", "AI", V02Screen.ADVISOR),
        MenuItem("Резьбы", "ISO metric, fine, BSPP и расчёт профиля", "M", V02Screen.THREADS),
        MenuItem("Fanuc", "Шаблоны G76 и G92 для 0i-TF", "G", V02Screen.FANUC),
        MenuItem("Диагностика", "Износ, скол, нарост, вибрация, размер", "!", V02Screen.DIAGNOSTICS),
        MenuItem("Инструмент", "Справочник + журнал стойкости пластин", "T", V02Screen.TOOLING),
        MenuItem("Мои детали", "Наладки, программа, материал и время цикла", "P", V02Screen.PARTS),
        MenuItem("Материалы", "ISO-группы и рабочие стартовые диапазоны", "MAT", V02Screen.MATERIALS),
        MenuItem("Обороты", "Vc + диаметр → RPM", "N", V02Screen.RPM),
        MenuItem("Подача", "f × RPM → мм/мин", "F", V02Screen.FEED),
        MenuItem("Скорость резания", "RPM + диаметр → Vc", "VC", V02Screen.VC),
    )

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("CNC MASTER", fontSize = 30.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text("v0.2 • помощник наладчика", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f))
                Spacer(Modifier.height(16.dp))
                StatusCard()
                Spacer(Modifier.height(10.dp))
                Text("Рабочие модули", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            items(items) { item -> MenuCard(item, onNavigate) }
            item {
                Spacer(Modifier.height(6.dp))
                Text("Обновление приложения", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                UpdateCenterCard(openRequested = openUpdatesRequested)
                Spacer(Modifier.height(8.dp))
                UpdateNotificationPermissionCard()
                Spacer(Modifier.height(12.dp))
                Text(
                    "v${BuildConfig.VERSION_NAME} • справочники и журнал работают офлайн",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .45f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun StatusCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF121B23)), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("0.2", color = Color(0xFF17120A), fontWeight = FontWeight.Black)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("Офлайн-ядро готово", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Расчёты, резьбы, Fanuc, диагностика и локальные журналы", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
            }
        }
    }
}

@Composable
private fun MenuCard(item: MenuItem, onNavigate: (V02Screen) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onNavigate(item.screen) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.badge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = if (item.badge.length > 2) 10.sp else 16.sp)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(item.subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 12.sp)
            }
            Text("›", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V02Scaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
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
        content = content,
    )
}

@Composable
fun AdvisorScreen(onBack: () -> Unit) {
    var material by remember { mutableStateOf(CncCatalog.materials.first()) }
    var operation by remember { mutableStateOf(OperationType.ROUGH) }
    var strategy by remember { mutableStateOf(Strategy.STABLE) }
    var diameter by remember { mutableStateOf("35.5") }
    var radius by remember { mutableStateOf("0.4") }

    val recommendation = runCatching {
        CuttingAdvisor.recommend(
            material = material,
            operation = operation,
            strategy = strategy,
            diameterMm = diameter.asDouble(),
            insertRadiusMm = radius.asDouble().takeIf { it > 0.0 } ?: 0.4,
        )
    }.getOrNull()

    V02Scaffold("Умные режимы", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                WarningCard("Это стартовая настройка, не замена каталогу конкретной пластины. Сначала добейся стабильного резания, потом ускоряй цикл.")
            }
            item { SectionTitle("Материал") }
            item {
                ChipRow(CncCatalog.materials, material, { it.name }) { material = it }
            }
            item {
                InfoCard(material.name) {
                    Text(material.isoGroup, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(material.aliases, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                    Text(material.hardnessNote, fontSize = 12.sp)
                }
            }
            item { SectionTitle("Операция") }
            item { ChipRow(OperationType.entries, operation, { it.title }) { operation = it } }
            item { SectionTitle("Стратегия") }
            item { ChipRow(Strategy.entries, strategy, { it.title }) { strategy = it } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(diameter, "Диаметр", "мм", Modifier.weight(1f)) { diameter = it }
                    NumberField(radius, "Радиус", "мм", Modifier.weight(1f)) { radius = it }
                }
            }
            recommendation?.let { r ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("Vc", "${r.cuttingSpeedMMin.pretty(1)} м/мин", Modifier.weight(1f))
                        MetricCard("RPM", r.rpm.toString(), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("f", "${r.feedPerRevMm.pretty(3)} мм/об", Modifier.weight(1f))
                        MetricCard("F", "${r.feedRateMmMin} мм/мин", Modifier.weight(1f))
                    }
                }
                item { MetricCard("ap / DOC", "${r.depthOfCutMm.pretty(2)} мм", Modifier.fillMaxWidth()) }
                item {
                    InfoCard("Перед запуском") {
                        r.warnings.forEach { Bullet(it) }
                    }
                }
                item {
                    SourceCard("Классификация материалов: Seco ISO/SMG. Числа в CNC Master — консервативные стартовые диапазоны, которые нужно уточнять по каталогу конкретной пластины.")
                }
            }
        }
    }
}

@Composable
fun MaterialsScreen(onBack: () -> Unit) {
    V02Scaffold("Материалы", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoCard("ISO-группы") {
                    Bullet("P — стали; свойства сильно меняются с составом и твёрдостью.")
                    Bullet("M — нержавейки; характерны тепло, нарост и notch wear.")
                    Bullet("N — цветные металлы; часто нужны острые кромки и высокий Vc.")
                    Bullet("H — закалённые стали; высокая абразивность и тепловая нагрузка.")
                }
            }
            items(CncCatalog.materials) { material -> MaterialCard(material) }
            item { SourceCard("Seco Tools: ISO Material Groups / SMG. A12 привязана к free-cutting группе P1; C45E — P4; 42CrMo4 — P5. 30ХГСА 35–40 HRC отмечена как переходная тяжёлая группа и требует проверки фактической твёрдости.") }
        }
    }
}

@Composable
private fun MaterialCard(material: MaterialPreset) {
    InfoCard(material.name) {
        Text(material.isoGroup, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(material.aliases, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric("Vc", "${material.vcMin.pretty(0)}–${material.vcMax.pretty(0)}", Modifier.weight(1f))
            MiniMetric("f", "${material.feedMin.pretty(2)}–${material.feedMax.pretty(2)}", Modifier.weight(1f))
            MiniMetric("ap", "${material.apMin.pretty(2)}–${material.apMax.pretty(2)}", Modifier.weight(1f))
        }
        material.machiningNotes.forEach { Bullet(it) }
        Text("Источник: ${material.sourceLabel}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
    }
}

@Composable
fun ThreadsScreen(onBack: () -> Unit) {
    var nominal by remember { mutableStateOf("12") }
    var pitch by remember { mutableStateOf("1.0") }
    val geometry = runCatching { ThreadCalculator.metricBasic(nominal.asDouble(), pitch.asDouble()) }.getOrNull()
    val coarse = ThreadCalculator.standardCoarsePitch(nominal.asDouble())

    V02Scaffold("Резьбы", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { WarningCard("Расчёт ниже показывает базовую геометрию профиля 60°. Поля допуска 6g/6H и калибр учитываются отдельно.") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(nominal, "Номинал M", "мм", Modifier.weight(1f)) { nominal = it }
                    NumberField(pitch, "Шаг P", "мм", Modifier.weight(1f)) { pitch = it }
                }
            }
            if (coarse != null) {
                item {
                    OutlinedButton(onClick = { pitch = coarse.pretty(2) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Подставить стандартный крупный шаг: ${coarse.pretty(2)} мм")
                    }
                }
            }
            geometry?.let { g ->
                item {
                    InfoCard("Базовый профиль M${g.nominalMm.pretty(2)}×${g.pitchMm.pretty(2)}") {
                        DataPair("Средний диаметр d2", "${g.pitchDiameterBasicMm.pretty(3)} мм")
                        DataPair("Наружная: малый d1", "${g.externalMinorBasicMm.pretty(3)} мм")
                        DataPair("Внутренняя: малый D1", "${g.internalMinorBasicMm.pretty(3)} мм")
                        DataPair("Сверло приближённо D−P", "${g.tapDrillApproxMm.pretty(2)} мм")
                        DataPair("Радиальная высота для G76", "${g.radialThreadHeightMm.pretty(3)} мм")
                    }
                }
            }
            item { SectionTitle("ISO 965 — популярные мелкие резьбы") }
            items(CncCatalog.metricFine) { row ->
                InfoCard(row.designation) {
                    DataPair("Шаг", "${row.pitchMm.pretty(2)} мм")
                    DataPair("Средний диаметр", "${row.pitchDiameterMm.pretty(3)} мм")
                    DataPair("Мин. наружный", "${row.externalMinorMm.pretty(3)} мм")
                    DataPair("Мин. внутренний", "${row.internalMinorMm.pretty(3)} мм")
                }
            }
            item { SectionTitle("BSPP / G — ISO 228-1") }
            items(CncCatalog.bspp) { row ->
                InfoCard(row.designation) {
                    DataPair("TPI", row.tpi.toString())
                    DataPair("Наружный Ø", "${row.nominalOdMm.pretty(3)} мм")
                    DataPair("Шаг", "${row.pitchMm.pretty(3)} мм")
                    DataPair("Средний Ø", "${row.pitchDiameterMm.pretty(3)} мм")
                    DataPair("Внутренний D1", "${row.internalMinorMm.pretty(3)} мм")
                    Bullet("Профиль Whitworth 55°, резьба параллельная. Для герметизации обычно нужна шайба/O-ring.")
                }
            }
            item {
                SourceCard("TR Fastenings: metric coarse/fine according to ISO 965. BSPP: ISO 228-1 dimensions. Не считай G и NPT взаимозаменяемыми: угол и/или шаг отличаются.")
            }
        }
    }
}

@Composable
fun FanucScreen(onBack: () -> Unit) {
    var nominal by remember { mutableStateOf("12") }
    var pitch by remember { mutableStateOf("1.0") }
    var zEnd by remember { mutableStateOf("-18") }
    var side by remember { mutableStateOf(ThreadSide.EXTERNAL) }
    var hand by remember { mutableStateOf(ThreadHand.RIGHT) }
    var finishPasses by remember { mutableStateOf("2") }
    var minCut by remember { mutableStateOf("0.05") }
    var finishAllowance by remember { mutableStateOf("0.05") }
    var firstCut by remember { mutableStateOf("0.15") }

    val result = runCatching {
        FanucThreadGenerator.generate(
            FanucThreadInput(
                nominalDiameterMm = nominal.asDouble(),
                pitchMm = pitch.asDouble(),
                zEndMm = zEnd.asSignedDouble(),
                side = side,
                hand = hand,
                finishPasses = finishPasses.toIntOrNull() ?: 2,
                minCutMm = minCut.asDouble(),
                finishAllowanceMm = finishAllowance.asDouble(),
                firstCutMm = firstCut.asDouble(),
            )
        )
    }.getOrNull()

    V02Scaffold("Fanuc 0i-TF", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WarningCard("Генератор выдаёт проверочный шаблон. Перед станком обязательно Single Block / Dry Run, безопасная позиция X/Z и сверка с руководством именно твоего станка.")
            }
            item { SectionTitle("Тип резьбы") }
            item { ChipRow(ThreadSide.entries, side, { it.title }) { side = it } }
            item { ChipRow(ThreadHand.entries, hand, { it.title }) { hand = it } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(nominal, "Номинал", "мм", Modifier.weight(1f)) { nominal = it }
                    NumberField(pitch, "Шаг", "мм", Modifier.weight(1f)) { pitch = it }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SignedNumberField(zEnd, "Z конец", "мм", Modifier.weight(1f)) { zEnd = it }
                    NumberField(finishPasses, "Чистовых", "", Modifier.weight(1f)) { finishPasses = it }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(minCut, "Q min", "мм", Modifier.weight(1f)) { minCut = it }
                    NumberField(firstCut, "Q 1-й", "мм", Modifier.weight(1f)) { firstCut = it }
                    NumberField(finishAllowance, "R finish", "мм", Modifier.weight(1f)) { finishAllowance = it }
                }
            }
            result?.let { r ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("Final X", r.calculatedFinalX.pretty(3), Modifier.weight(1f))
                        MetricCard("P height", r.radialHeight.pretty(3), Modifier.weight(1f))
                    }
                }
                item { CodeCard("G76 — двухстрочный", r.g76) }
                item { CodeCard("G92 — проходы", r.g92) }
                item {
                    InfoCard("Проверить перед запуском") {
                        r.warnings.forEach { Bullet(it) }
                    }
                }
                item {
                    SourceCard("Формат Fanuc 0i-TF: G92 X(U) Z(W) F Q; G76 P(m)(r)(a) QΔdmin R(d), затем G76 X Z R P(k) QΔd F(L). Направление M03/M04 для левой резьбы не подставляется автоматически из-за зависимости от ориентации инструмента.")
                }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf(CncCatalog.diagnostics.first()) }

    V02Scaffold("Диагностика", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { WarningCard("Меняй по одному фактору за раз. Иначе невозможно понять первопричину и легко получить новую проблему.") }
            item { ChipRow(CncCatalog.diagnostics, selected, { it.symptom }) { selected = it } }
            item {
                InfoCard(selected.symptom) {
                    Text(selected.description, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .75f))
                    SubTitle("Вероятные причины")
                    selected.likelyCauses.forEach { Bullet(it) }
                    SubTitle("Что проверить первым")
                    selected.checksFirst.forEach { Bullet(it) }
                    SubTitle("Что менять")
                    selected.actions.forEach { Bullet(it) }
                    Text("Источник: ${selected.sourceLabel}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
                }
            }
            item { SourceCard("Kennametal Turning Troubleshooting: edge wear, chipping, thermal deformation, built-up edge, crater wear. Вибрация и увод размера дополнены последовательностью проверки жёсткости, вылета и теплового состояния.") }
        }
    }
}

@Composable
fun ToolingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    var entries by remember { mutableStateOf(store.loadToolEntries()) }

    var insertName by remember { mutableStateOf("CNMG 120408") }
    var materialName by remember { mutableStateOf("30ХГСА") }
    var vc by remember { mutableStateOf("100") }
    var feed by remember { mutableStateOf("0.18") }
    var ap by remember { mutableStateOf("1.0") }
    var parts by remember { mutableStateOf("0") }
    var resultText by remember { mutableStateOf("") }

    V02Scaffold("Инструмент", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle("Быстрый справочник") }
            items(CncCatalog.toolReferences) { ref ->
                InfoCard(ref.title) {
                    Text(ref.subtitle, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    ref.points.forEach { Bullet(it) }
                }
            }
            item { SectionTitle("Добавить запись стойкости") }
            item {
                InfoCard("Новая запись") {
                    TextFieldSimple(insertName, "Пластина / инструмент") { insertName = it }
                    TextFieldSimple(materialName, "Материал") { materialName = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(vc, "Vc", "м/мин", Modifier.weight(1f)) { vc = it }
                        NumberField(feed, "f", "мм/об", Modifier.weight(1f)) { feed = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(ap, "ap", "мм", Modifier.weight(1f)) { ap = it }
                        NumberField(parts, "Деталей", "шт", Modifier.weight(1f)) { parts = it }
                    }
                    TextFieldSimple(resultText, "Результат / причина замены") { resultText = it }
                    Button(
                        onClick = {
                            if (insertName.isNotBlank()) {
                                store.saveToolEntry(
                                    ToolLifeEntry(
                                        id = UUID.randomUUID().toString(),
                                        createdAt = System.currentTimeMillis(),
                                        insertName = insertName.trim(),
                                        materialName = materialName.trim(),
                                        vc = vc.trim(),
                                        feed = feed.trim(),
                                        ap = ap.trim(),
                                        partsCount = parts.toIntOrNull() ?: 0,
                                        result = resultText.trim(),
                                    )
                                )
                                entries = store.loadToolEntries()
                                resultText = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Сохранить в журнал") }
                }
            }
            item { SectionTitle("Журнал стойкости (${entries.size})") }
            if (entries.isEmpty()) {
                item { EmptyCard("Пока нет записей. После первой серии сохрани фактическую стойкость — так CNC Master начнёт превращаться в твою собственную технологическую базу.") }
            }
            items(entries, key = { it.id }) { entry ->
                InfoCard(entry.insertName) {
                    Text("${entry.materialName} • ${formatDate(entry.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                    DataPair("Vc", entry.vc.ifBlank { "—" })
                    DataPair("f", entry.feed.ifBlank { "—" })
                    DataPair("ap", entry.ap.ifBlank { "—" })
                    DataPair("Стойкость", "${entry.partsCount} деталей")
                    if (entry.result.isNotBlank()) Text(entry.result, fontSize = 12.sp)
                    TextButton(
                        onClick = {
                            store.deleteToolEntry(entry.id)
                            entries = store.loadToolEntries()
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Удалить") }
                }
            }
        }
    }
}

@Composable
fun PartsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    var parts by remember { mutableStateOf(store.loadParts()) }

    var name by remember { mutableStateOf("") }
    var material by remember { mutableStateOf("30ХГСА") }
    var machine by remember { mutableStateOf("FELLER / Fanuc 0i-TF") }
    var program by remember { mutableStateOf("") }
    var cycle by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    V02Scaffold("Мои детали", onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoCard("Новая технологическая карточка") {
                    TextFieldSimple(name, "Название детали") { name = it }
                    TextFieldSimple(material, "Материал") { material = it }
                    TextFieldSimple(machine, "Станок") { machine = it }
                    TextFieldSimple(program, "Номер/имя программы") { program = it }
                    NumberField(cycle, "Время цикла", "сек", Modifier.fillMaxWidth()) { cycle = it }
                    TextFieldSimple(notes, "Наладка / инструмент / замечания") { notes = it }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                store.savePart(
                                    PartCard(
                                        id = UUID.randomUUID().toString(),
                                        createdAt = System.currentTimeMillis(),
                                        name = name.trim(),
                                        material = material.trim(),
                                        machine = machine.trim(),
                                        programNumber = program.trim(),
                                        cycleSeconds = cycle.asDouble(),
                                        notes = notes.trim(),
                                    )
                                )
                                parts = store.loadParts()
                                name = ""
                                program = ""
                                cycle = ""
                                notes = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Сохранить деталь") }
                }
            }
            item { SectionTitle("Сохранённые детали (${parts.size})") }
            if (parts.isEmpty()) {
                item { EmptyCard("Здесь появятся твои реальные наладки: материал, программа, станок, время цикла и заметки.") }
            }
            items(parts, key = { it.id }) { part ->
                InfoCard(part.name) {
                    Text("${part.material} • ${formatDate(part.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                    DataPair("Станок", part.machine.ifBlank { "—" })
                    DataPair("Программа", part.programNumber.ifBlank { "—" })
                    DataPair("Цикл", if (part.cycleSeconds > 0) "${part.cycleSeconds.pretty(1)} сек" else "—")
                    if (part.cycleSeconds > 0) DataPair("Теоретически", "${(3600.0 / part.cycleSeconds).toInt()} дет/ч без простоев")
                    if (part.notes.isNotBlank()) Text(part.notes, fontSize = 12.sp, lineHeight = 18.sp)
                    TextButton(
                        onClick = {
                            store.deletePart(part.id)
                            parts = store.loadParts()
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Удалить") }
                }
            }
        }
    }
}

@Composable
fun RpmScreenV02(onBack: () -> Unit) {
    var vc by remember { mutableStateOf("150") }
    var diameter by remember { mutableStateOf("30") }
    val rpm = CncMath.rpm(vc.asDouble(), diameter.asDouble())
    V02Scaffold("Обороты шпинделя", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("n = 1000 × Vc / (π × D)", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) }
            item { NumberField(vc, "Скорость резания Vc", "м/мин", Modifier.fillMaxWidth()) { vc = it } }
            item { NumberField(diameter, "Диаметр D", "мм", Modifier.fillMaxWidth()) { diameter = it } }
            item { MetricCard("RPM", rpm.pretty(0), Modifier.fillMaxWidth()) }
            item { WarningCard("Учитывай ограничение оборотов станка, способ зажима, дисбаланс и каталог инструмента.") }
        }
    }
}

@Composable
fun FeedScreenV02(onBack: () -> Unit) {
    var f by remember { mutableStateOf("0.20") }
    var rpm by remember { mutableStateOf("1500") }
    val feedRate = CncMath.feedRate(f.asDouble(), rpm.asDouble())
    V02Scaffold("Подача", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("F = f × n", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) }
            item { NumberField(f, "Подача на оборот", "мм/об", Modifier.fillMaxWidth()) { f = it } }
            item { NumberField(rpm, "Обороты", "об/мин", Modifier.fillMaxWidth()) { rpm = it } }
            item { MetricCard("Линейная подача", "${feedRate.pretty(1)} мм/мин", Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
fun CuttingSpeedScreenV02(onBack: () -> Unit) {
    var rpm by remember { mutableStateOf("1500") }
    var diameter by remember { mutableStateOf("30") }
    val vc = CncMath.cuttingSpeed(rpm.asDouble(), diameter.asDouble())
    V02Scaffold("Скорость резания", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Vc = π × D × n / 1000", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) }
            item { NumberField(rpm, "Обороты", "об/мин", Modifier.fillMaxWidth()) { rpm = it } }
            item { NumberField(diameter, "Диаметр", "мм", Modifier.fillMaxWidth()) { diameter = it } }
            item { MetricCard("Vc", "${vc.pretty(1)} м/мин", Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun <T> ChipRow(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelected(item) },
                label = { Text(label(item)) },
            )
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    suffix: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            onChange(raw.filter { it.isDigit() || it == '.' || it == ',' })
        },
        label = { Text(label) },
        suffix = { if (suffix.isNotBlank()) Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun SignedNumberField(
    value: String,
    label: String,
    suffix: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val filtered = raw.filterIndexed { index, c -> c.isDigit() || c == '.' || c == ',' || (c == '-' && index == 0) }
            onChange(filtered)
        },
        label = { Text(label) },
        suffix = { if (suffix.isNotBlank()) Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun TextFieldSimple(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF171E20)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .60f), fontSize = 12.sp)
            Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2112)), shape = RoundedCornerShape(16.dp)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(14.dp), color = Color(0xFFFFC66D), fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun SourceCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101820)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Основа данных", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Text(text, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        }
    }
}

@Composable
private fun CodeCard(title: String, code: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1218)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(16.dp), fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
}

@Composable
private fun SubTitle(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
}

@Composable
private fun Bullet(text: String) {
    Text("• $text", fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .84f))
}

@Composable
private fun DataPair(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

private fun String.asDouble(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun String.asSignedDouble(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun Double.pretty(decimals: Int): String = "% .${decimals}f".format(Locale.US, this).trim()
private fun formatDate(epochMs: Long): String = runCatching {
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))
}.getOrDefault("—")
