package com.yoshida.cncmaster

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal object AppUpdateManager {
    private const val CHANNEL_ID = "cnc_master_updates"
    private const val NOTIFICATION_ID = 2509
    private const val PREFS = "cnc_master_app_updates"
    private const val KEY_LAST_NOTIFIED_CODE = "last_notified_version_code"
    private const val UNIQUE_WORK = "cnc_master_update_check"

    fun isNewer(update: AppUpdateInfo?): Boolean =
        update != null &&
            update.available &&
            update.latestVersionCode > BuildConfig.VERSION_CODE &&
            update.downloadUrl.startsWith("https://")

    fun notificationPermissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
    }

    fun notifyIfNeeded(context: Context, update: AppUpdateInfo) {
        if (!isNewer(update) || !notificationPermissionGranted(context)) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_LAST_NOTIFIED_CODE, 0) >= update.latestVersionCode) return

        createChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_updates", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            update.latestVersionCode,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = update.changelog.firstOrNull()
            ?: "Версия ${update.latestVersionName} готова к установке."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_cnc)
            .setContentTitle("Доступно обновление CNC Master")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)

        prefs.edit()
            .putInt(KEY_LAST_NOTIFIED_CODE, update.latestVersionCode)
            .apply()
    }

    suspend fun downloadApk(
        context: Context,
        update: AppUpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        require(update.downloadUrl.startsWith("https://")) {
            "Обновление должно загружаться по HTTPS."
        }

        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(updateDir, "CNC-Master-${update.latestVersionCode}.apk")
        val temp = File(updateDir, "CNC-Master-${update.latestVersionCode}.download")
        if (temp.exists()) temp.delete()

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder().url(update.downloadUrl).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Сервер вернул код ${response.code}.")
            }

            val body = response.body
            val total = when {
                update.sizeBytes > 0L -> update.sizeBytes
                body.contentLength() > 0L -> body.contentLength()
                else -> -1L
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastPercent = -1

            body.byteStream().use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read

                        val percent = if (total > 0L) {
                            ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(downloaded, total)
                        }
                    }
                    output.fd.sync()
                }
            }
            onProgress(downloaded, total)

            if (update.sizeBytes > 0L && downloaded != update.sizeBytes) {
                temp.delete()
                throw IllegalStateException("Размер APK не совпадает с опубликованной версией.")
            }

            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (update.sha256.isNotBlank() && !actualSha.equals(update.sha256.trim(), true)) {
                temp.delete()
                throw SecurityException("SHA-256 APK не совпадает. Установка отменена.")
            }
        }

        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        target
    }

    fun canRequestPackageInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )

    fun installApk(context: Context, apk: File) {
        check(apk.exists()) { "APK обновления не найден." }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Обновления CNC Master",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Уведомления о новых версиях CNC Master"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}

internal class AppUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val update = UpdateRepository().check()
        if (AppUpdateManager.isNewer(update)) {
            AppUpdateManager.notifyIfNeeded(applicationContext, update)
        }
        Result.success()
    }.getOrElse {
        Result.retry()
    }
}
