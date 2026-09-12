package com.thisko.qringprint

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.thisko.qringprint.bluetooth.PrintQueue
import com.thisko.qringprint.bluetooth.PrintQueueService
import com.thisko.qringprint.bluetooth.QueueState
import com.thisko.qringprint.bluetooth.PrinterConnection
import com.thisko.qringprint.model.DisplayMode
import com.thisko.qringprint.model.applyDisplayMode
import com.thisko.qringprint.model.loadDisplayMode

class QringPrintApp : Application() {
    private val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Language switch via reflection so we do not hard-import appcompat
        runCatching {
            val language = getSharedPreferences("qring_print_settings", MODE_PRIVATE).getString("language", "") ?: ""
            if (language.isNotEmpty()) {
                val cls = Class.forName("androidx.appcompat.app.AppCompatDelegate")
                val lcls = Class.forName("androidx.core.os.LocaleListCompat")
                val forLang = lcls.getMethod("forLanguageTags", String::class.java).invoke(null, language)
                cls.getMethod("setApplicationLocales", lcls).invoke(null, forLang)
            }
        }
        // 启动时应用上次的显示模式
        val saved = DisplayMode.fromInt(loadDisplayMode(this))
        applyDisplayMode(saved, this)
        // 初始化蓝牙模块,接收 foreground/background 通知
        PrinterConnection.init(this)
        installCrashHandler()

        queueScope.launch {
            PrintQueue.state.collect { state ->
                if (state is QueueState.Running) {
                    PrintQueueService.start(this@QringPrintApp)
                } else {
                    PrintQueueService.stop(this@QringPrintApp)
                }
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        queueScope.cancel()
    }
}


/**
 * 全局兜底：捕获未处理异常，写入 logcat 与本地文件，便于下次排错。
 * 即便我们所有 Composable 已经包了 try-catch，仍然可能有遗漏。
 */
private fun installCrashHandler() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            android.util.Log.e("QringPrint", "FATAL on " + thread.name, throwable)
            val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOCUMENTS), "QringPrint")
            if (!dir.exists()) dir.mkdirs()
            val f = java.io.File(dir, "last_crash.txt")
            val sw = java.io.StringWriter()
            throwable.printStackTrace(java.io.PrintWriter(sw))
            f.writeText(
                "thread: " + thread.name + "\n" +
                "time: " + java.util.Date() + "\n" +
                "stack:\n" + sw.toString()
            )
        } catch (_: Throwable) { /* ignore */ }
        previous?.uncaughtException(thread, throwable)
    }
}
