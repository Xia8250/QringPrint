package com.thisko.qringprint.model

object HistoryBus {
    private var pendingText: String? = null
    private var pendingCode: String? = null
    private var pendingImageUri: String? = null
    private var pendingAdBanner: String? = null

    fun setText(payload: String) {
        pendingText = payload
    }

    fun setCode(payload: String) {
        pendingCode = payload
    }

    fun setImage(payload: String) {
        pendingImageUri = payload
    }

    /** 历史记录跳回广告横带页时,把 payload 透传过去 */
    fun setAdBanner(payload: String) {
        pendingAdBanner = payload
    }

    fun consumeText(): String? {
        val value = pendingText
        pendingText = null
        return value
    }

    fun consumeCode(): String? {
        val value = pendingCode
        pendingCode = null
        return value
    }

    fun consumeImage(): String? {
        val value = pendingImageUri
        pendingImageUri = null
        return value
    }

    fun consumeAdBanner(): String? {
        val value = pendingAdBanner
        pendingAdBanner = null
        return value
    }
}
