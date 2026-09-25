package com.yoshida.cncmaster

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun UpdateCenterCard(
    openRequested: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val repository = remember { UpdateRepository() }

    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    fun checkUpdates(openWhenAvailable: Boolean = false) {
        if (checking) return
        checking = true
        error = null
        message = null
        scope.launch {
            runCatching { repository.check() }
                .onSuccess { info ->
                    update = info
                    if (AppUpdateManager.isNewer(info)) {
                        message = "Доступна версия ${info.latestVersionName}"
                        if (openWhenAvailable) showDialog = true
                    } else {
                        message = "Установлена последняя версия CNC Master"
                    }
                }
                .onFailure {
                    error = friendlyUpdateError(it)
                }
            checking = false
        }
    }

    LaunchedEffect(openRequested) {
        checkUpdates(openRequested)
    }

    val available = AppUpdateManager.isNewer(update)
    val accent = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111A20)),
        border = BorderStroke(1.dp, if (available) accent.copy(alpha = .45f) else Color.White.copy(alpha = .06f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = .12f),
                ) {
                    Text(
                        if (available) "↑" else "✓",
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        color = accent,
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Обновления", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        when {
                            checking -> "Проверяем новую версию…"
                            available -> "CNC Master ${update?.latestVersionName} готов к установке"
                            else -> "Сейчас установлена ${BuildConfig.VERSION_NAME}"
                        },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .60f),
                        fontSize = 12.sp,
                    )
                }
            }

            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = if (available) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = .7f), fontSize = 12.sp)
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFFFB86B), fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { checkUpdates(false) },
                    enabled = !checking,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Проверить")
                }

                if (available && update != null) {
                    Button(
                        onClick = { showDialog = true },
                        modifier = Modifier.weight(1.25f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                    ) {
                        Text("Обновить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDialog && update != null) {
        UpdateDialog(
            update = update!!,
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun UpdateDialog(
    update: AppUpdateInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary

    var downloading by remember(update.latestVersionCode) { mutableStateOf(false) }
    var downloadedBytes by remember(update.latestVersionCode) { mutableLongStateOf(0L) }
    var totalBytes by remember(update.latestVersionCode) { mutableLongStateOf(update.sizeBytes) }
    var downloadedApk by remember(update.latestVersionCode) { mutableStateOf<File?>(null) }
    var statusText by remember(update.latestVersionCode) { mutableStateOf<String?>(null) }
    var downloadError by remember(update.latestVersionCode) { mutableStateOf<String?>(null) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apk = downloadedApk
        if (apk != null && apk.exists() && AppUpdateManager.canRequestPackageInstalls(context)) {
            statusText = "Разрешение получено. Открываем установку…"
            runCatching { AppUpdateManager.installApk(context, apk) }
                .onFailure { downloadError = friendlyUpdateError(it) }
        } else if (apk != null) {
            statusText = "Разреши CNC Master устанавливать приложения и повтори установку."
        }
    }

    fun installDownloaded(apk: File) {
        if (AppUpdateManager.canRequestPackageInstalls(context)) {
            statusText = "APK проверен. Открываем системную установку Android…"
            runCatching { AppUpdateManager.installApk(context, apk) }
                .onFailure { downloadError = friendlyUpdateError(it) }
        } else {
            downloadedApk = apk
            statusText = "APK скачан и проверен. Нужно один раз разрешить установку из CNC Master."
            installPermissionLauncher.launch(AppUpdateManager.installPermissionIntent(context))
        }
    }

    fun startUpdate() {
        if (downloading) return

        val ready = downloadedApk?.takeIf { it.exists() }
        if (ready != null) {
            installDownloaded(ready)
            return
        }

        downloading = true
        downloadedBytes = 0L
        totalBytes = update.sizeBytes
        statusText = "Скачиваем обновление внутри приложения…"
        downloadError = null

        scope.launch {
            runCatching {
                AppUpdateManager.downloadApk(context, update) { downloaded, total ->
                    scope.launch {
                        downloadedBytes = downloaded
                        if (total > 0L) totalBytes = total
                    }
                }
            }.onSuccess { apk ->
                downloadedApk = apk
                statusText = "Загрузка завершена. SHA-256 проверен."
                installDownloaded(apk)
            }.onFailure {
                statusText = null
                downloadError = friendlyUpdateError(it)
            }
            downloading = false
        }
    }

    Dialog(onDismissRequest = { if (!downloading && !update.mandatory) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF0D151B),
            border = BorderStroke(1.dp, accent.copy(alpha = .25f)),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Доступно обновление", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    "CNC Master ${update.latestVersionName}",
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                )
                Text(
                    "Сейчас ${BuildConfig.VERSION_NAME} • ${formatUpdateSize(update.sizeBytes)}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                    fontSize = 12.sp,
                )

                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF11251C),
                ) {
                    Text(
                        "Без браузера • HTTPS • SHA-256 • системная установка Android",
                        modifier = Modifier.padding(11.dp),
                        color = Color(0xFF8FE3A5),
                        fontSize = 11.sp,
                    )
                }

                if (downloading) {
                    Spacer(Modifier.height(16.dp))
                    val total = totalBytes
                    if (total > 0L) {
                        val progress = (downloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "${(progress * 100).toInt()}% • ${formatUpdateSize(downloadedBytes)} из ${formatUpdateSize(total)}",
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Загружаем APK…")
                        }
                    }
                }

                if (update.changelog.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Что нового", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    update.changelog.take(6).forEach { item ->
                        Text("• $item", fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }

                statusText?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFF8FE3A5), fontSize = 11.sp, lineHeight = 16.sp)
                }
                downloadError?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFFFFB86B), fontSize = 11.sp, lineHeight = 16.sp)
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!update.mandatory && !downloading) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(.7f)) {
                            Text("Позже")
                        }
                    }
                    Button(
                        onClick = { startUpdate() },
                        enabled = !downloading,
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (downloading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            when {
                                downloading -> "Скачиваем…"
                                downloadedApk?.exists() == true -> "Установить"
                                else -> "Скачать и обновить"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun UpdateNotificationPermissionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    var granted by remember { mutableStateOf(AppUpdateManager.notificationPermissionGranted(context)) }
    if (granted) return

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    OutlinedButton(
        onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text("Включить уведомления о новых версиях")
    }
}

internal fun formatUpdateSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 10.0) "${mb.toInt()} МБ" else String.format(Locale.US, "%.1f МБ", mb)
}

private fun friendlyUpdateError(error: Throwable): String = when (error) {
    is SecurityException -> error.message ?: "Файл обновления не прошёл проверку безопасности."
    else -> error.message ?: "Не удалось проверить или скачать обновление."
}
