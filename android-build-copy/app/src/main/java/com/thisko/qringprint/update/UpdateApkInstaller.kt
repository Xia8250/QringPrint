package com.thisko.qringprint.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 下载新版本 APK 到 App 内部 files 子目录, 然后通过 FileProvider 调起系统安装器.
 * 不需要写外部存储权限, 也不需要适配 scoped storage.
 */
object UpdateApkInstaller {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(UpdateConfig.networkTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(UpdateConfig.networkTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    /** 下载到 [Context.getExternalFilesDir] / updates/huanxong-update.apk, 进度回调 0..100. */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: suspend (Int) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val baseDir = context.getExternalFilesDir("updates")
                ?: context.filesDir.resolve("updates").also { it.mkdirs() }
            if (!baseDir.exists()) baseDir.mkdirs()
            val target = File(baseDir, "huanxong-update.apk")

            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                val total = body.contentLength().coerceAtLeast(1L)
                val input = body.byteStream()
                FileOutputStream(target).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var sum: Long = 0L
                    var lastPercent = -1
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        sum += read
                        val pct = ((sum * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != lastPercent) {
                            lastPercent = pct
                            onProgress(pct)
                        }
                    }
                    out.flush()
                }
                target
            }
        }.getOrNull()
    }

    /** 调起系统安装器. 需要先在 Manifest 加 REQUEST_INSTALL_PACKAGES. */
    fun install(context: Context, apk: File) {
        val authority = context.packageName + ".fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 系统安装器需要明确 grant
            context.grantUriPermission(
                context.packageManager.queryIntentActivities(intent, 0)
                    .firstOrNull()?.activityInfo?.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        context.startActivity(intent)
    }
}
