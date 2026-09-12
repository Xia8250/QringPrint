package com.thisko.qringprint.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 全局"检查更新"协调器.
 *  - 任何页面可以 collectAsState() 监听 [state] 决定要不要弹弹窗.
 *  - [check] 一次性检查, 可选 [manual] 决定是否忽略"已跳过"标记.
 *  - [skipThisVersion] 让用户在该 versionCode 下不再提示.
 */
object UpdateManager {

    private const val PREF = "update_prefs"
    private const val KEY_SKIPPED = "skipped_version_code"

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _promptVisible = MutableStateFlow(false)
    val promptVisible: StateFlow<Boolean> = _promptVisible.asStateFlow()

    private fun getCurrentVersionCode(context: Context): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }.getOrDefault(0)

    private fun getCurrentVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    fun check(context: Context, manual: Boolean = false) {
        val current = getCurrentVersionCode(context)
        val currentName = getCurrentVersionName(context)
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val skipped = sp.getInt(KEY_SKIPPED, 0)
        _state.value = UpdateUiState.Checking
        if (manual) _promptVisible.value = true

        GlobalScope.launch(Dispatchers.IO) {
            val manifest = UpdateClient.fetch()
            val s = if (manifest == null) {
                UpdateUiState.Failed(if (manual) "无法连接到更新服务器" else "")
            } else if (manifest.versionCode <= current) {
                UpdateUiState.UpToDate(currentName)
            } else if (!manual && manifest.versionCode == skipped && !manifest.force) {
                UpdateUiState.UpToDate(currentName)
            } else {
                UpdateUiState.Available(manifest, currentName)
            }
            _state.value = s
            if (manual) _promptVisible.value = true
            if (s is UpdateUiState.Available) _promptVisible.value = true
        }
    }

    fun dismissPrompt() {
        _promptVisible.value = false
    }

    fun skipThisVersion(context: Context, versionCode: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SKIPPED, versionCode)
            .apply()
        _promptVisible.value = false
    }

    suspend fun downloadAndInstall(context: Context, url: String) {
        _state.value = UpdateUiState.Downloading(0)
        val file = UpdateApkInstaller.download(context, url) { pct ->
            _state.value = UpdateUiState.Downloading(pct)
        }
        if (file == null) {
            _state.value = UpdateUiState.Failed("下载失败，请检查网络后重试")
            return
        }
        _state.value = UpdateUiState.ReadyToInstall(file.absolutePath)
        UpdateApkInstaller.install(context, file)
    }

    fun resetState() {
        _state.value = UpdateUiState.Idle
    }
}
