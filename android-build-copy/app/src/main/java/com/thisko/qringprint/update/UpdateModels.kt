package com.thisko.qringprint.update

import com.google.gson.annotations.SerializedName

/** 云端 update.json 反序列化的对象. */
data class UpdateManifest(
    @SerializedName("versionCode") val versionCode: Int = 0,
    @SerializedName("versionName") val versionName: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("changelog") val changelog: String = "",
    @SerializedName("force") val force: Boolean = false,
    @SerializedName("minSupportedVersionCode") val minSupportedVersionCode: Int = 0,
    @SerializedName("url") val url: String = "",
    @SerializedName("sizeBytes") val sizeBytes: Long = 0L,
    @SerializedName("sha256") val sha256: String = "",
)

/** 应用内"检查更新"UI 状态. */
sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class UpToDate(val current: String) : UpdateUiState()
    data class Available(
        val manifest: UpdateManifest,
        val currentVersionName: String,
    ) : UpdateUiState()
    data class Downloading(val progress: Int) : UpdateUiState()
    data class ReadyToInstall(val filePath: String) : UpdateUiState()
    data class Failed(val reason: String) : UpdateUiState()
}
