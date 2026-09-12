package com.thisko.qringprint.bluetooth.ocr

import android.content.Context

class OcrEngineSelector(context: Context) {
    enum class Engine { Baidu }

    var engine: Engine
        get() = Engine.Baidu
        set(@Suppress("UNUSED_PARAMETER") value) = Unit

    val baidu: BaiduOcrClient = BaiduOcrClient(context)

    fun pick(): BaiduOcrClient = baidu
}
