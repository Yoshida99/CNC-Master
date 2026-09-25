package com.yoshida.cncmaster

data class AppUpdateInfo(
    val available: Boolean,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minSupportedVersionCode: Int,
    val mandatory: Boolean,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val changelog: List<String>,
    val publishedAt: String,
)
