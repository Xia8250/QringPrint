package com.thisko.qringprint.bluetooth.ocr

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "BaiduOcrClient"

private const val TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
private const val OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic"
private const val OCR_URL_ACCURATE = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic"

data class BaiduOcrResult(
    val words: List<String> = emptyList(),
    val error_msg: String? = null,
    val error_code: Int? = null
) {
    val text: String get() = words.joinToString("\n")
}

class BaiduOcrClient(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("ocr_prefs", Context.MODE_PRIVATE)
    }

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var tokenExpireAt: Long = 0L

    var apiKey: String?
        get() = prefs.getString("baidu_api_key", null)
        set(value) {
            prefs.edit().putString("baidu_api_key", value).apply()
            cachedToken = null
            tokenExpireAt = 0L
        }

    var secretKey: String?
        get() = prefs.getString("baidu_secret_key", null)
        set(value) {
            prefs.edit().putString("baidu_secret_key", value).apply()
            cachedToken = null
            tokenExpireAt = 0L
        }

    fun isConfigured(): Boolean = !apiKey.isNullOrBlank() && !secretKey.isNullOrBlank()

    fun saveCredentials(key: String, secret: String) {
        apiKey = key
        secretKey = secret
    }

    /** 高准确度模式（accurate_basic）。免费版未开通时会回退到 general_basic。 */
    var useAccurate: Boolean
        get() = prefs.getBoolean("baidu_use_accurate", true)
        set(value) { prefs.edit().putBoolean("baidu_use_accurate", value).apply() }

    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext "未配置 API Key 或 Secret Key"
        try {
            val token = getAccessToken()
            if (token.isNotBlank()) "API Key 有效，access_token 获取成功（${token.length} 字符）"
            else "access_token 为空"
        } catch (e: Exception) {
            Log.e(TAG, "testConnection failed", e)
            "连接失败：${e.message}"
        }
    }

    suspend fun recognize(imageBytes: ByteArray): BaiduOcrResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext BaiduOcrResult(error_msg = "请先在右上角配置百度 OCR API Key 和 Secret Key")
        }
        try {
            val accessToken = getAccessToken()
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            Log.d(TAG, "Image raw size: ${imageBytes.size} bytes, base64 size: ${base64Image.length} chars")

            val formBody = FormBody.Builder()
                .add("image", base64Image)
                .add("language_type", "CHN_ENG")
                .add("detect_direction", "false")
                .add("detect_language", "false")
                .add("paragraph", "false")
                .add("probability", "false")
                .build()

            val request = Request.Builder()
                .url((if (useAccurate) OCR_URL_ACCURATE else OCR_URL) + "?access_token=" + accessToken)
                .post(formBody)
                .header("Accept", "application/json")
                .build()

            okHttp.newCall(request).execute().use { response ->
                val body = response.body?.string()
                Log.d(TAG, "HTTP ${response.code} OCR Response: ${body?.take(500)}")
                if (body.isNullOrBlank()) {
                    return@withContext BaiduOcrResult(error_msg = "服务器返回为空（HTTP ${response.code}）")
                }
                if (!response.isSuccessful) {
                    return@withContext BaiduOcrResult(error_msg = "HTTP ${response.code}: ${body.take(200)}")
                }
                parseResponse(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
            BaiduOcrResult(error_msg = "识别失败：${e.message}")
        }
    }

    private fun parseResponse(body: String): BaiduOcrResult {
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            val errorCode: Int? = if (root.has("error_code") && !root.get("error_code").isJsonNull) {
                runCatching { root.get("error_code").asInt }.getOrNull()
                    ?: runCatching { root.get("error_code").asString.toInt() }.getOrNull()
            } else null
            val errorMsg: String? = if (root.has("error_msg") && !root.get("error_msg").isJsonNull) {
                root.get("error_msg").asString
            } else null

            if (errorCode != null) {
                val hint = when (errorCode) {
                    1, 2 -> "（服务器内部错误，请重试）"
                    6 -> "（API Key 无权限：请到 console.bce.baidu.com 开通『通用文字识别』服务）"
                    17 -> "（每日调用量超限）"
                    18 -> "（QPS 超限，稍后再试）"
                    19 -> "（包月服务已到期）"
                    100 -> "（参数错误）"
                    110 -> "（access_token 过期）"
                    111 -> "（access_token 权限不足）"
                    216100 -> "（必传参数为空，请检查图片）"
                    216201 -> "（图片转码失败：图片太大或格式错误，建议重新拍照或换更小图）"
                    216202 -> "（图片尺寸错误：边长超过 4096px）"
                    216630 -> "（识别引擎错误）"
                    282000 -> "（网络超时或图片过大）"
                    282003 -> "（请求参数缺失）"
                    else -> ""
                }
                return BaiduOcrResult(
                    error_msg = listOfNotNull(errorMsg, hint).joinToString(" "),
                    error_code = errorCode
                )
            }

            val words = mutableListOf<String>()
            if (root.has("words_result") && root.get("words_result").isJsonArray) {
                val arr = root.getAsJsonArray("words_result")
                for (item in arr) {
                    val obj = item.asJsonObject
                    if (obj.has("words")) words.add(obj.get("words").asString)
                }
            }
            BaiduOcrResult(words = words)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response: " + body, e)
            BaiduOcrResult(error_msg = "解析响应失败：${e.message}\n原始：${body.take(200)}")
        }
    }

    @Synchronized
    private fun getAccessToken(): String {
        cachedToken?.let { token ->
            if (System.currentTimeMillis() < tokenExpireAt) return token
        }
        val key = apiKey ?: throw RuntimeException("未配置 API Key")
        val secret = secretKey ?: throw RuntimeException("未配置 Secret Key")

        val url = TOKEN_URL + "?grant_type=client_credentials&client_id=" + key + "&client_secret=" + secret
        val request = Request.Builder().url(url).get().build()
        val response = okHttp.newCall(request).execute()
        val body = response.body?.string() ?: throw RuntimeException("获取 token 失败：响应为空")
        Log.d(TAG, "Token response: " + body)

        if (!response.isSuccessful) {
            throw RuntimeException("获取 token 失败：HTTP ${response.code} ${body.take(200)}")
        }

        try {
            val json = JsonParser.parseString(body).asJsonObject
            if (json.has("error") && !json.get("error").isJsonNull) {
                val err = json.get("error").asString
                val desc = if (json.has("error_description")) json.get("error_description").asString else ""
                throw RuntimeException("鉴权失败：" + err + " " + desc)
            }
            val token = json.get("access_token").asString
            val expiresIn = if (json.has("expires_in") && !json.get("expires_in").isJsonNull) {
                json.get("expires_in").asLong
            } else 2592000L
            cachedToken = token
            tokenExpireAt = System.currentTimeMillis() + (expiresIn - 86400).coerceAtLeast(60L) * 1000
            return token
        } catch (e: Exception) {
            throw RuntimeException("解析 token 失败：${e.message}")
        }
    }
}
