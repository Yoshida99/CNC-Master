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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FanucScreenV02Polished(onBack: () -> Unit) {
    var nominal by remember { mutableStateOf("12") }
    var pitch by remember { mutableStateOf("1.0") }
    var zEnd by remember { mutableStateOf("-18") }
    var rpm by remember { mutableStateOf("500") }
    var side by remember { mutableStateOf(ThreadSide.EXTERNAL) }
    var hand by remember { mutableStateOf(ThreadHand.RIGHT) }
    var finishPasses by remember { mutableStateOf("2") }
    var minCut by remember { mutableStateOf("0.05") }
    var finishAllowance by remember { mutableStateOf("0.05") }
    var firstCut by remember { mutableStateOf("0.15") }

    val result = runCatching {
        FanucThreadGenerator.generate(
            FanucThreadInput(
                nominalDiameterMm = nominal.fanucDouble(),
                pitchMm = pitch.fanucDouble(),
                zEndMm = zEnd.fanucSignedDouble(),
                side = side,
                hand = hand,
                finishPasses = finishPasses.toIntOrNull() ?: 2,
                minCutMm = minCut.fanucDouble(),
                finishAllowanceMm = finishAllowance.fanucDouble(),
                firstCutMm = firstCut.fanucDouble(),
            )
        )
    }.getOrNull()

    val safeRpm = (rpm.toIntOrNull() ?: 0).coerceAtLeast(0)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Fanuc 0i-TF", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Text("‹", fontSize = 34.sp, modifier = Modifier.padding(horizontal = 16.dp).clickable { onBack() })
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
                FanucWarning(
                    "Шаблон не запускается вслепую. Перед первым проходом: правильный корректор, безопасная позиция X/Z, " +
                        "G97, низкие обороты, Single Block и Dry Run/воздух."
                )
            }
            item { FanucTitle("Тип резьбы") }
            item {
                FanucChips(ThreadSide.entries, side, { it.title }) { side = it }
            }
            item {
                FanucChips(ThreadHand.entries, hand, { it.title }) { hand = it }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FanucNumberField(nominal, "Номинал", "мм", false, Modifier.weight(1f)) { nominal = it }
                    FanucNumberField(pitch, "Шаг", "мм", false, Modifier.weight(1f)) { pitch = it }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FanucNumberField(zEnd, "Z конец", "мм", true, Modifier.weight(1f)) { zEnd = it }
                    FanucNumberField(rpm, "G97 S", "об/мин", false, Modifier.weight(1f)) { rpm = it }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FanucNumberField(finishPasses, "Чистовых", "", false, Modifier.weight(1f)) { finishPasses = it }
                    FanucNumberField(minCut, "Q min", "мм", false, Modifier.weight(1f)) { minCut = it }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FanucNumberField(firstCut, "Q 1-й", "мм", false, Modifier.weight(1f)) { firstCut = it }
                    FanucNumberField(finishAllowance, "R finish", "мм", false, Modifier.weight(1f)) { finishAllowance = it }
                }
            }

            result?.let { generated ->
                val g76Code = withRpm(generated.g76, safeRpm)
                val g92Code = withRpm(generated.g92, safeRpm)

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FanucMetric("Final X", generated.calculatedFinalX.fanucFmt(3), Modifier.weight(1f))
                        FanucMetric("P height", generated.radialHeight.fanucFmt(3), Modifier.weight(1f))
                    }
                }
                item { FanucCodeCard("G76 — двухстрочный", g76Code) }
                item { FanucCodeCard("G92 — последовательность проходов", g92Code) }
                item {
                    FanucInfo("Проверка перед запуском") {
                        generated.warnings.forEach { FanucBullet(it) }
                        if (safeRpm <= 0) {
                            FanucBullet("Обороты не заданы — перед запуском обязательно укажи безопасный S в G97.")
                        }
                    }
                }
                item {
                    FanucSource(
                        "Формат G76/G92 основан на Fanuc Series 0i-TF Operator Manual. CNC Master рассчитывает базовый 60° профиль; " +
                            "фактический размер по калибру корректируется полем допуска, геометрией пластины и износом."
                    )
                }
            }
        }
    }
}

@Composable
private fun FanucCodeCard(title: String, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF091118)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            SelectionContainer {
                Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (copied) "Скопировано" else "Копировать код")
            }
        }
    }
}

private fun withRpm(code: String, rpm: Int): String {
    val replacement = if (rpm > 0) {
        "G97 S$rpm (VERIFY SPINDLE DIRECTION)"
    } else {
        "G97 (SET SAFE RPM BEFORE THREADING)"
    }
    return code.replace("G97 (SET SAFE RPM BEFORE THREADING)", replacement)
        .replace("(START FROM A SAFE X/Z POSITION; USE G97)", "(START FROM A SAFE X/Z POSITION)\n$replacement")
}

@Composable
private fun FanucInfo(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
private fun FanucMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF171E20)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            Text(value, fontSize = 21.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FanucWarning(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2112)), shape = RoundedCornerShape(16.dp)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(14.dp), color = Color(0xFFFFC66D), fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun FanucSource(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101820)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Основа", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(5.dp))
            Text(text, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        }
    }
}

@Composable
private fun FanucTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun FanucBullet(text: String) {
    Text("• $text", fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .84f))
}

@Composable
private fun <T> FanucChips(items: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            FilterChip(selected = item == selected, onClick = { onSelected(item) }, label = { Text(label(item)) })
        }
    }
}

@Composable
private fun FanucNumberField(
    value: String,
    label: String,
    suffix: String,
    signed: Boolean,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val filtered = raw.filterIndexed { index, c ->
                c.isDigit() || c == '.' || c == ',' || (signed && c == '-' && index == 0)
            }
            onChange(filtered)
        },
        label = { Text(label) },
        suffix = { if (suffix.isNotBlank()) Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (signed) KeyboardType.Number else KeyboardType.Decimal),
        modifier = modifier,
    )
}

private fun String.fanucDouble(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun String.fanucSignedDouble(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun Double.fanucFmt(decimals: Int): String = "% .${decimals}f".format(Locale.US, this).trim()
