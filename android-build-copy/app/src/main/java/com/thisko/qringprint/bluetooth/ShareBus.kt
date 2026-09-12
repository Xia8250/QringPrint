package com.thisko.qringprint.bluetooth

import android.net.Uri
import java.util.concurrent.atomic.AtomicReference

/**
 * \u8de8\u9875\u4f20\u9012\u4e00\u6b21\u6027\u6570\u636e\u3002
 * \u4e3b\u8981\u7528\u4e8e Share Intent \u4ece MainActivity \u4f20\u5230\u6253\u5370\u9875\u3002
 *
 * \u6bcf\u6b21\u8bfb\u53d6\u540e\u8fd4\u56de\u5e76\u6e05\u7a7a\uff0c\u907f\u514d\u4e0b\u6b21\u8fdb\u5165\u9875\u9762\u53c8\u62ff\u5230\u540c\u4e00\u4efd\u3002
 */
object ShareBus {
    private val pendingImages = AtomicReference<List<Uri>?>(null)
    private val pendingText = AtomicReference<String?>(null)

    fun setImages(uris: List<Uri>) { pendingImages.set(uris) }
    fun consumeImages(): List<Uri>? = pendingImages.getAndSet(null)

    fun setText(text: String) { pendingText.set(text) }
    fun consumeText(): String? = pendingText.getAndSet(null)
}
