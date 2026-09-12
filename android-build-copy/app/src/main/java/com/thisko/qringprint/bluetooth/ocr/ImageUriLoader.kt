package com.thisko.qringprint.bluetooth.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * 从 URI 加载全分辨率 Bitmap。API 28+ 用 ImageDecoder（自动 EXIF 旋转），旧版回退 MediaStore。
 * 强制走 CPU 分配（否则 getPixels/setPixels 会爆 Software rendering doesn't support hardware bitmaps）。
 */
object ImageUriLoader {
    // 3600：百度 OCR 允许 ≤4096，3600 是经验和体积的平衡点
    fun load(context: Context, uri: Uri, maxDim: Int = 3600): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > maxDim) {
                    val sample = (longest / maxDim).coerceAtLeast(1)
                    decoder.setTargetSampleSize(sample)
                }
                decoder.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }
}
