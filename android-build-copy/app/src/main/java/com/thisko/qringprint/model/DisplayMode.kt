package com.thisko.qringprint.model

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局主题状态。显式浅色/深色使用 darkOverride；跟随系统时交给 Compose
 * 的 isSystemInDarkTheme() 读取当前 Configuration，避免浅色选择被系统深色覆盖。
 */
object ThemeState {
    private val _isDark = MutableStateFlow(false)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    private val _darkOverride = MutableStateFlow<Boolean?>(null)
    val darkOverride: StateFlow<Boolean?> = _darkOverride.asStateFlow()

    fun setDark(dark: Boolean) {
        _isDark.value = dark
    }

    fun setDarkOverride(dark: Boolean?) {
        _darkOverride.value = dark
        if (dark != null) _isDark.value = dark
    }
}

enum class DisplayMode(val value: Int) {
    FOLLOW(0), LIGHT(1), DARK(2);

    companion object {
        fun fromInt(v: Int): DisplayMode = values().firstOrNull { it.value == v } ?: FOLLOW
    }
}

private const val STORE_NAME = "qring_settings"
private const val KEY_MODE = "display_mode"

fun loadDisplayMode(ctx: Context): Int {
    return try {
        ctx.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MODE, DisplayMode.FOLLOW.value)
    } catch (_: Throwable) {
        DisplayMode.FOLLOW.value
    }
}

fun saveDisplayMode(ctx: Context, mode: DisplayMode) {
    try {
        ctx.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MODE, mode.value).apply()
    } catch (_: Throwable) {
        // 下次启动时使用默认值。
    }
}

/** 应用显示模式，同时更新 Compose 状态和系统组件的夜间模式。 */
fun applyDisplayMode(mode: DisplayMode, @Suppress("UNUSED_PARAMETER") context: Context? = null) {
    val setMode: (Int) -> Unit = { nightMode ->
        runCatching {
            val cls = Class.forName("androidx.appcompat.app.AppCompatDelegate")
            val method = cls.getMethod("setDefaultNightMode", Int::class.javaPrimitiveType!!)
            method.invoke(null, nightMode)
        }
    }
    when (mode) {
        DisplayMode.LIGHT -> {
            ThemeState.setDarkOverride(false)
            setMode(1)
        }
        DisplayMode.DARK -> {
            ThemeState.setDarkOverride(true)
            setMode(2)
        }
        DisplayMode.FOLLOW -> {
            ThemeState.setDarkOverride(null)
            setMode(-1)
        }
    }
}

/** 兼容旧调用方：只在没有显式覆盖时同步系统状态。 */
fun syncDarkFromSystem(dark: Boolean) {
    if (ThemeState.darkOverride.value == null) ThemeState.setDark(dark)
}
