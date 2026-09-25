package com.yoshida.cncmaster

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal class UpdateRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun check(): AppUpdateInfo = withContext(Dispatchers.IO) {
        val separator = if (BuildConfig.UPDATE_MANIFEST_URL.contains("?")) "&" else "?"
        val request = Request.Builder()
            .url(BuildConfig.UPDATE_MANIFEST_URL + separator + "t=" + System.currentTimeMillis())
            .header("Cache-Control", "no-cache")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Сервер обновлений вернул код ${response.code}.")
            }

            val payload = response.body.string()
            if (payload.isBlank()) error("Пустой ответ сервера обновлений.")
            parse(JSONObject(payload))
        }
    }

    private fun parse(json: JSONObject): AppUpdateInfo {
        val changelogJson = json.optJSONArray("changelog")
        val changelog = buildList {
            if (changelogJson != null) {
                for (i in 0 until changelogJson.length()) {
                    val item = changelogJson.optString(i).trim()
                    if (item.isNotBlank()) add(item)
                }
            }
        }

        return AppUpdateInfo(
            available = json.optBoolean("available", true),
            latestVersionCode = json.optInt("latest_version_code", 0),
            latestVersionName = json.optString("latest_version_name", "—"),
            minSupportedVersionCode = json.optInt("min_supported_version_code", 1),
            mandatory = json.optBoolean("mandatory", false),
            downloadUrl = json.optString("download_url", ""),
            sha256 = json.optString("sha256", ""),
            sizeBytes = json.optLong("size_bytes", 0L),
            changelog = changelog,
            publishedAt = json.optString("published_at", ""),
        )
    }
}
