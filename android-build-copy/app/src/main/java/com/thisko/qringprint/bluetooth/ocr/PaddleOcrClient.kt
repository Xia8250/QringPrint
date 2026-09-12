package com.thisko.qringprint.bluetooth.ocr

import android.content.Context
import android.util.Log

private const val TAG = "PaddleOcrClient"

class PaddleOcrClient(private val context: Context) {
    fun ensureReady(): String? = "本地OCR已移除"
    fun recognize(imageData: ByteArray): BaiduOcrResult = BaiduOcrResult(error_msg = "本地OCR已移除，请使用百度云")
    suspend fun testConnection(): String = "本地OCR已移除"
    fun release() {}
}
