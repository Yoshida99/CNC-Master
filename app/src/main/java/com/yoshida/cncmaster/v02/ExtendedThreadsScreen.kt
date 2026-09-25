package com.yoshida.cncmaster.v02

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadsScreenV02Extended(onBack: () -> Unit) {
    var nominal by remember { mutableStateOf("12") }
    var pitch by remember { mutableStateOf("1.0") }
    val geometry = runCatching { ThreadCalculator.metricBasic(nominal.threadDouble(), pitch.threadDouble()) }.getOrNull()
    val coarsePitch = ThreadCalculator.standardCoarsePitch(nominal.threadDouble())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Резьбы", fontWeight = FontWeight.Bold) },
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
                ThreadNotice(
                    "Для метрической резьбы калькулятор показывает базовую геометрию профиля. " +
                        "Предельные размеры зависят от поля допуска и должны проверяться калибром."
                )
            }

            item { ThreadTitle("Калькулятор метрической 60°") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThreadNumberField(nominal, "Номинал M", "мм", Modifier.weight(1f)) { nominal = it }
                    ThreadNumberField(pitch, "Шаг P", "мм", Modifier.weight(1f)) { pitch = it }
                }
            }
            if (coarsePitch != null) {
                item {
                    OutlinedButton(
                        onClick = { pitch = coarsePitch.threadFmt(2) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Стандартный крупный шаг M${nominal}: ${coarsePitch.threadFmt(2)} мм")
                    }
                }
            }
            geometry?.let { g ->
                item {
                    ThreadCard("M${g.nominalMm.threadFmt(2)}×${g.pitchMm.threadFmt(2)} — базовый профиль") {
                        ThreadPair("Средний диаметр d2", "${g.pitchDiameterBasicMm.threadFmt(3)} мм")
                        ThreadPair("Малый Ø наружной d1", "${g.externalMinorBasicMm.threadFmt(3)} мм")
                        ThreadPair("Малый Ø внутренней D1", "${g.internalMinorBasicMm.threadFmt(3)} мм")
                        ThreadPair("Сверло приближённо D−P", "${g.tapDrillApproxMm.threadFmt(2)} мм")
                        ThreadPair("Радиальная высота профиля", "${g.radialThreadHeightMm.threadFmt(3)} мм")
                    }
                }
            }

            item { ThreadTitle("Metric fine — ISO 965") }
            items(CncCatalog.metricFine) { row ->
                ThreadCard(row.designation) {
                    ThreadPair("Шаг", "${row.pitchMm.threadFmt(2)} мм")
                    ThreadPair("Средний Ø", "${row.pitchDiameterMm.threadFmt(3)} мм")
                    ThreadPair("Малый Ø наружной", "${row.externalMinorMm.threadFmt(3)} мм")
                    ThreadPair("Малый Ø внутренней", "${row.internalMinorMm.threadFmt(3)} мм")
                }
            }

            item { ThreadTitle("UNC — Unified Coarse 60°") }
            items(ExtendedThreadCatalog.unc) { row -> UnifiedCard(row) }

            item { ThreadTitle("UNF — Unified Fine 60°") }
            items(ExtendedThreadCatalog.unf) { row -> UnifiedCard(row) }

            item { ThreadTitle("BSPP / G — ISO 228-1, параллельная 55°") }
            items(CncCatalog.bspp) { row ->
                ThreadCard(row.designation) {
                    ThreadPair("TPI", row.tpi.toString())
                    ThreadPair("Номинальный наружный Ø", "${row.nominalOdMm.threadFmt(3)} мм")
                    ThreadPair("Шаг", "${row.pitchMm.threadFmt(3)} мм")
                    ThreadPair("Средний Ø", "${row.pitchDiameterMm.threadFmt(3)} мм")
                    ThreadPair("Внутренний D1", "${row.internalMinorMm.threadFmt(3)} мм")
                    Text("Параллельная резьба: герметизация обычно шайбой или O-ring, а не натягом самой резьбы.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                }
            }

            item { ThreadTitle("BSPT / R — ISO 7-1, коническая 55°") }
            items(ExtendedThreadCatalog.bspt) { row -> TaperedCard(row) }

            item { ThreadTitle("NPT — ASME B1.20.1, коническая 60°") }
            items(ExtendedThreadCatalog.npt) { row -> TaperedCard(row) }

            item {
                ThreadNotice(
                    "Важно: BSPT и NPT не считать взаимозаменяемыми. Swagelok отдельно предупреждает, " +
                        "что 1/2 и 3/4 могут быть визуально очень похожи, поэтому идентификация только по наружному Ø ненадёжна."
                )
            }

            item {
                ThreadSource(
                    "Metric coarse/fine, UNC, UNF: TR Fastenings thread geometry tables. " +
                        "BSPP: ISO 228-1 reference dimensions. BSPT/NPT identification: Swagelok Thread and End Connection Identification Guide."
                )
            }
        }
    }
}

@Composable
private fun UnifiedCard(row: UnifiedThreadRow) {
    ThreadCard(row.designation) {
        ThreadPair("TPI / шаг", "${row.tpi} / ${row.pitchMm.threadFmt(3)} мм")
        ThreadPair("Большой Ø", "${row.majorIn.threadFmt(4)}″ / ${row.majorMm.threadFmt(3)} мм")
        ThreadPair("Средний Ø", "${row.pitchDiameterIn.threadFmt(4)}″ / ${row.pitchDiameterMm.threadFmt(3)} мм")
        ThreadPair("Малый Ø наружной", "${row.externalMinorIn.threadFmt(4)}″ / ${row.externalMinorMm.threadFmt(3)} мм")
        ThreadPair("Малый Ø внутренней", "${row.internalMinorIn.threadFmt(4)}″ / ${row.internalMinorMm.threadFmt(3)} мм")
    }
}

@Composable
private fun TaperedCard(row: TaperedPipeReference) {
    ThreadCard(row.designation) {
        ThreadPair("Стандарт", row.standard)
        ThreadPair("Профиль", "${row.flankAngleDeg}° ${row.profileLetter}")
        ThreadPair("TPI / шаг", "${row.tpi.threadFmt(1)} / ${row.pitchMm.threadFmt(3)} мм")
        ThreadPair("Справочный мужской Ø", "${row.nominalMaleDiameterMm.threadFmt(2)} мм")
        Text(
            "Ø приведён для идентификации резьбы, а не как готовый размер обработки/контроля. Для изготовления конической резьбы нужны стандартные gauge-plane размеры и допуски.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .60f),
        )
    }
}

@Composable
private fun ThreadCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
private fun ThreadPair(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        Spacer(Modifier.padding(horizontal = 5.dp))
        Text(value, modifier = Modifier.weight(1.1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun ThreadTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun ThreadNotice(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2112)), shape = RoundedCornerShape(16.dp)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(14.dp), color = Color(0xFFFFC66D), fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun ThreadSource(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101820)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Источники", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(5.dp))
            Text(text, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        }
    }
}

@Composable
private fun ThreadNumberField(
    value: String,
    label: String,
    suffix: String,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onChange(raw.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

private fun String.threadDouble(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun Double.threadFmt(decimals: Int): String = "% .${decimals}f".format(Locale.US, this).trim()
