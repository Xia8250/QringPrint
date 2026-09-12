package com.thisko.qringprint.common

import android.content.ContentResolver
import android.util.Log
import android.graphics.Bitmap
import android.net.Uri
import com.google.zxing.common.BitMatrix
import com.thisko.qringprint.bluetooth.DitherMode
import com.thisko.qringprint.bluetooth.GrayImage
import com.thisko.qringprint.bluetooth.THRESHOLD_IMAGE
import com.thisko.qringprint.bluetooth.THRESHOLD_TEXT
import com.thisko.qringprint.bluetooth.WIDTH_DOTS
import com.thisko.qringprint.bluetooth.binaryToPreviewBitmap
import com.thisko.qringprint.bluetooth.bitmapToGray
import com.thisko.qringprint.bluetooth.bitmapToGrayRaw
import com.thisko.qringprint.bluetooth.blitBinary
import com.thisko.qringprint.bluetooth.createBinaryCanvas
import com.thisko.qringprint.bluetooth.decodeImageToPrintWidth
import com.thisko.qringprint.bluetooth.ditherToBinary
import com.thisko.qringprint.bluetooth.measureTextContentWidth
import com.thisko.qringprint.bluetooth.renderTextToPixelMapIn
import com.thisko.qringprint.bluetooth.scaleGrayArea
import com.thisko.qringprint.bluetooth.scaleGrayNearest
import com.thisko.qringprint.bluetooth.squeezeRows
import com.thisko.qringprint.model.CanvasDoc
import com.thisko.qringprint.model.CanvasElement
import com.thisko.qringprint.model.CodeType
import com.thisko.qringprint.model.DEFAULT_IMAGE_WIDTH
import com.thisko.qringprint.model.ElementKind
import com.thisko.qringprint.model.centeredX
import com.thisko.qringprint.model.encodeBarcodeMatrix

/** 一维码 / "自然"宽高比(w/h)。生成后压缩到 140 高,显示时按它推宽(保持长条形) */
fun codeOneDAspect(): Double = 384.0 / 140.0

private const val CODE_GEN_SIZE = 384
private const val ONE_D_NATURAL_HEIGHT = 140

/**
 * 生成条码的原始灰度图。
 */
fun renderCodeGray(content: String, type: CodeType): GrayImage {
    return try {
        val matrix: BitMatrix = encodeBarcodeMatrix(content, type, CODE_GEN_SIZE)
        // 重要:ZXing 返回的实际尺寸不一定等于请求尺寸
        // (PDF417 会按数据量自定列/行,二维码少数据时可能不到 384)。
        // 用 matrix 的实际宽高,不然二值化后会拉出边缘。
        val gray = bitMatrixToGray(matrix, matrix.width, matrix.height)
        if (type.category == com.thisko.qringprint.model.CodeCategory.ONE_D)
            squeezeRows(gray, ONE_D_NATURAL_HEIGHT) else gray
    } catch (e: Throwable) {
        // 数据太长 / 字符不合规 / ZXing 内部异常 ——
        // 不让预览或打印时闪退,返回空图让 UI 显示错误
        Log.w("RenderCode", "encode ${type.label} failed: ${e.message}")
        GrayImage(ByteArray(CODE_GEN_SIZE * CODE_GEN_SIZE) { 255.toByte() }, CODE_GEN_SIZE, CODE_GEN_SIZE)
    }
}

private fun bitMatrixToGray(matrix: BitMatrix, width: Int, height: Int): GrayImage {
    val data = ByteArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            // ZXing 是 bit=true = 黑,灰度里 0 = 黑,所以 true 写 0
            data[y * width + x] = if (matrix.get(x, y)) 0 else 255.toByte()
        }
    }
    return GrayImage(data, width, height)
}

/** 解码图片到 384 宽的灰度缓存 */
fun loadImageGray(resolver: ContentResolver, uri: Uri): GrayImage {
    val bitmap = decodeImageToPrintWidth(resolver, uri)
    val gray = bitmapToGray(bitmap)
    bitmap.recycle()
    return gray
}

data class CanvasComposite(
    val binary: ByteArray,
    val width: Int,
    val height: Int,
)

/**
 * 把所有元素合成成一张 384 x H 的二值图。
 *
 * 预览和打印都吃这一份数据,所以"所见即所打"是真的逐点一致,不是近似。
 */
fun composeCanvas(doc: CanvasDoc): CanvasComposite {
    val height = maxOf(doc.contentHeight(), doc.elements.maxOfOrNull { el ->
        val width = maxOf(1, el.dotW)
        val binaryRows = el.binary?.let { bits -> maxOf(1, bits.size / width) }
        val elementHeight = when {
            binaryRows != null -> maxOf(1, el.dotH, binaryRows)
            el.kind == ElementKind.LINE -> maxOf(2, el.dotH)
            el.kind == ElementKind.TEXT && el.text.isEmpty() -> maxOf(2, el.dotH)
            else -> maxOf(1, el.dotH)
        }
        el.dotY + elementHeight
    } ?: 1)
    val canvas = createBinaryCanvas(WIDTH_DOTS, height)

    for (el in doc.elements) {
        val bits = el.binary
        if (bits == null && ((el.kind == com.thisko.qringprint.model.ElementKind.TEXT && el.text.isEmpty()) || el.kind == com.thisko.qringprint.model.ElementKind.LINE)) {
            val w = maxOf(1, el.dotW)
            val h = maxOf(2, if (el.kind == com.thisko.qringprint.model.ElementKind.LINE) 2 else el.dotH)
            val lineBits = ByteArray(w * h) { 0x00.toByte() }
            blitBinary(canvas, WIDTH_DOTS, height, lineBits, w, h, el.dotX, el.dotY)
            continue
        }
        val b = bits ?: continue
        val w = maxOf(1, el.dotW)
        val h = maxOf(1, el.dotH)
        val rows = minOf(h, b.size / w)
        blitBinary(canvas, WIDTH_DOTS, height, b, w, rows, el.dotX, el.dotY)
    }

    return CanvasComposite(canvas, WIDTH_DOTS, height)
}

fun compositeToBitmap(composite: CanvasComposite): Bitmap =
    binaryToPreviewBitmap(composite.binary, composite.width, composite.height)

/** 生成模板缩略图,返回 PNG 字节。历史打印记录用"打出来的内容"存成缩略图,调用方决定落盘文件名 */
fun thumbnailBytes(doc: CanvasDoc): ByteArray? {
    val composite = composeCanvas(doc)
    val maxW = 300
    val maxH = 300
    val ratio = minOf(maxW.toDouble() / composite.width, maxH.toDouble() / composite.height, 1.0)
    val tw = maxOf(1, (composite.width * ratio).toInt())
    val th = maxOf(1, (composite.height * ratio).toInt())
    val gray = GrayImage(
        data = composite.binary.copyOf(),
        width = composite.width,
        height = composite.height
    )
    val scaled = scaleGrayNearest(gray, tw, th)
    val bitmap = binaryToPreviewBitmap(scaled.data, scaled.width, scaled.height)
    val out = java.io.ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
    bitmap.recycle()
    return out.toByteArray()
}

/** 元素渲染 */
suspend fun renderElement(
    el: CanvasElement,
    targetW: Int,
    targetH: Int,
    resolver: ContentResolver,
    onStage: ((String) -> Unit)? = null,
) {
    el.rendering = true
    try {
        if (el.kind == ElementKind.TEXT) {
           onStage?.invoke("排版文字")
            var contentW = maxOf(1, el.dotW)
            if (el.dotW <= 0) {
                contentW = measureTextContentWidth(el.text, el.textOptions, WIDTH_DOTS)
            }
            val rendered = renderTextToPixelMapIn(el.text, el.textOptions, contentW.toFloat())
            onStage?.invoke("文字→灰度")
            val gray = bitmapToGrayRaw(rendered)
            val binary = ditherToBinary(gray, DitherMode.NONE, THRESHOLD_TEXT)
            el.dotW = gray.width
            el.dotH = gray.height
            el.aspect = if (gray.height > 0) gray.width.toDouble() / gray.height else 1.0
            applyBinary(el, binary, gray.width, gray.height, transparentWhite = true)
            rendered.recycle()
            return
        }

        if (el.kind == ElementKind.IMAGE) {
            val source = el.sourceGray ?: return
            onStage?.invoke("图片缩放")
            val scaled = scaleGrayArea(source, targetW, targetH)
            onStage?.invoke("图片抖动")
            val binary = ditherToBinary(scaled, el.ditherMode, THRESHOLD_IMAGE)
            applyBinary(el, binary, scaled.width, scaled.height)
            return
        }

        onStage?.invoke("生成条码")
        val codeGray = renderCodeGray(el.codeContent, el.codeType())
        onStage?.invoke("条码缩放")
        val scaledCode = scaleGrayNearest(codeGray, targetW, targetH)
        val binary = ditherToBinary(scaledCode, DitherMode.NONE, THRESHOLD_IMAGE)
        applyBinary(el, binary, scaledCode.width, scaledCode.height)
    } finally {
        el.rendering = false
    }
}

private fun applyBinary(
    el: CanvasElement,
    binary: ByteArray,
    width: Int,
    height: Int,
    transparentWhite: Boolean = false,
) {
    el.binary = binary
    el.preview = binaryToPreviewBitmap(binary, width, height, transparentWhite)
}

/**
 * 单元完整渲染入口 —— 模块级,不依赖组件 this。
 *
 * 首次渲染图片时按真实宽高比设置几何:
 * 解码(loadImageGray)得到 384 宽灰度的真实 aspect,再按它设 dotH,
 * 图片框就不是插入时的正方形占位,而是贴合照片本身的形状。
 * 换图(sourceGray 被清 null)时同样走这步,重新贴合新图比例。
 */
suspend fun renderElementNow(
    el: CanvasElement,
    resolver: ContentResolver,
    onStage: ((String) -> Unit)? = null,
) {
    el.rendering = true
    onStage?.invoke("开始渲染")
    try {
        if (el.kind == ElementKind.IMAGE && el.sourceGray == null && el.imageUri.isNotEmpty()) {
            onStage?.invoke("解码图片")
            val gray = loadImageGray(resolver, Uri.parse(el.imageUri))
            el.sourceGray = gray
            if (!el.geometryLocked) {
                el.aspect = if (gray.height > 0) gray.width.toDouble() / gray.height else 1.0
                el.dotW = DEFAULT_IMAGE_WIDTH
                el.dotH = maxOf(1, (DEFAULT_IMAGE_WIDTH / el.aspect).toInt())
                el.dotX = centeredX(el.dotW)
                el.geometryLocked = true
            }
        }
        renderElement(el, el.dotW, el.dotH, resolver, onStage)
        onStage?.invoke("渲染完成")
    } finally {
        el.rendering = false
    }
}
