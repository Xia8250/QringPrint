package com.thisko.qringprint.update

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 从 [UpdateConfig.manifestUrl] 拉 [UpdateManifest].
 * 任何网络/解析错误都返回 null, 调用方按 "没有更新" 处理.
 */
object UpdateClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(UpdateConfig.networkTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(UpdateConfig.networkTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private val gson = Gson()

    suspend fun fetch(): UpdateManifest? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(UpdateConfig.manifestUrl)
                .header("User-Agent", "HuanxongKuaiyin-Android/4.0 (update-check)")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                try {
                    gson.fromJson(body, UpdateManifest::class.java)
                } catch (_: JsonSyntaxException) {
                    null
                }
            }
        }.getOrNull()
    }
}
