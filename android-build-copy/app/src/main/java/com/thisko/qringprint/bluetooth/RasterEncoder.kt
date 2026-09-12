package com.thisko.qringprint.bluetooth

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 文本与位图 → 光栅字节。
 *
 * 文字打印走"画 384 宽的临时位图 → 转灰度 → 阈值二值化 → 打行打包"这条流水线。
 * 位图先解码、按 384 等比缩到 384 宽,再交给抖动+打包。
 */

const val THRESHOLD_IMAGE: Int = 128
const val THRESHOLD_TEXT: Int = 212

data class TextRenderOptions(
    val fontFamily: String = "sans-serif",
    val fontSize: Int = 24,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val letterSpacing: Float = 0f,
    val lineSpacing: Float = 6f,
    val margin: Int = 8,
    val align: TextAlign = TextAlign.LEFT,
)

enum class TextAlign { LEFT, CENTER, RIGHT }
val DEFAULT_TEXT_OPTIONS = TextRenderOptions()

private fun buildFontSpec(options: TextRenderOptions): Typeface {
    val style = if (options.italic) Typeface.ITALIC else Typeface.NORMAL
    val base = if (options.bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.create(options.fontFamily, style)
    return if (options.bold && options.italic) {
        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
    } else if (options.bold) {
        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    } else if (options.italic) {
        Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
    } else {
        Typeface.create(options.fontFamily, Typeface.NORMAL)
    }
}

/** 按可用宽度按字符折行 */
private fun wrapText(text: String, paint: Paint, usableWidth: Float): List<String> {
    val lines = mutableListOf<String>()
    for (paragraph in text.split("\n")) {
        if (paragraph.isEmpty()) {
            lines.add("")
            continue
        }
        val current = StringBuilder()
        for (ch in paragraph) {
            val candidate = current.toString() + ch
            if (paint.measureText(candidate) <= usableWidth) {
                current.append(ch)
            } else {
                lines.add(current.toString())
                current.clear()
                current.append(ch)
            }
        }
        lines.add(current.toString())
    }
    return lines
}

private fun buildTextPaint(options: TextRenderOptions): Paint {
    val paint = Paint()
    paint.isAntiAlias = true
    paint.color = Color.BLACK
    paint.textSize = options.fontSize.toFloat()
    paint.letterSpacing = if (options.fontSize > 0) options.letterSpacing / options.fontSize else 0f
    paint.typeface = buildFontSpec(options)
    return paint
}

/**
 * 文本 → 384 宽位图,自动换行。对应 Python 的 text_to_image,并扩展了排版选项。
 */
fun renderTextToPixelMap(text: String, options: TextRenderOptions): Bitmap =
    renderTextToPixelMapIn(text, options, WIDTH_DOTS.toFloat())

/**
 * 量出文本在 maxWidth 内排版后的"内容自然宽度"。
 *
 * 文字元素默认不该占满整幅 384 —— 只有一行时宽度就是这行文字 + 两次边距,
 * 多行(手动 
 或超宽折行)时取最长一行的宽度。上限 maxWidth,不超过纸宽。
 */
fun measureTextContentWidth(text: String, options: TextRenderOptions, maxWidth: Int): Int {
    val width = max(1 + 2 * options.margin, maxWidth)
    val paint = buildTextPaint(options)
    val usable = (width - 2 * options.margin).toFloat()
    val lines = wrapText(text, paint, usable)

    var widest = 0f
    for (line in lines) {
        val w = paint.measureText(line)
        if (w > widest) widest = w
    }
    val content = max(options.fontSize.toFloat(), kotlin.math.ceil(widest))
    return min(width, content.toInt() + 2 * options.margin)
}

/**
 * 同上,但可指定排版宽度。
 *
 * 自定义画布里文字元素不一定占满整幅 384 —— 放在 x=200 处的文字只能用剩下的 184 点,
 * 折行必须按这个宽度算,否则会排到纸外面去被裁掉。
 *
 * boxWidth 是"元素的总宽",margin 在它内部再往里收。
 */
fun renderTextToPixelMapIn(text: String, options: TextRenderOptions, boxWidth: Float): Bitmap {
    val width = max(1 + 2 * options.margin, boxWidth.roundToInt())
    val paint = buildTextPaint(options)
    val usable = (width - 2 * options.margin).toFloat()
    val lines = wrapText(text, paint, usable)

    val lineHeight = options.fontSize + options.lineSpacing
    // 下划线粗细随字号缩放,固定 1px 在大字号下会细得几乎打不出来
    val underlineWeight = max(1, (options.fontSize / 14f).roundToInt())
    val underlineExtra = if (options.underline) 2 + underlineWeight else 0
    val textHeight = options.fontSize + max(0, lines.size - 1) * lineHeight + underlineExtra
    val height = max(1, options.margin * 2 + textHeight.roundToInt())

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    // 必须先填白底:位图默认透明,透明会被当成白,但显式填充更可靠
    canvas.drawColor(Color.WHITE)
    paint.color = Color.BLACK
    paint.textAlign = when (options.align) {
        TextAlign.LEFT -> Paint.Align.LEFT
        TextAlign.CENTER -> Paint.Align.CENTER
        TextAlign.RIGHT -> Paint.Align.RIGHT
    }
    val fm = paint.fontMetrics
    val baselineOffset = -fm.top
    val usableWidth = (width - 2 * options.margin).toFloat()
    val textX = when (options.align) {
        TextAlign.LEFT -> options.margin.toFloat()
        TextAlign.CENTER -> options.margin + usableWidth / 2f
        TextAlign.RIGHT -> (width - options.margin).toFloat()
    }
    for (i in lines.indices) {
        val y = options.margin + i * lineHeight + baselineOffset
        canvas.drawText(lines[i], textX, y, paint)
        if (options.underline && lines[i].isNotEmpty()) {
            val lineWidth = paint.measureText(lines[i])
            val ux = when (options.align) {
                TextAlign.LEFT -> options.margin.toFloat()
                TextAlign.CENTER -> textX - lineWidth / 2f
                TextAlign.RIGHT -> textX - lineWidth
            }
            val uy = options.margin + i * lineHeight + options.fontSize + 2f
            canvas.drawRect(ux, uy, ux + lineWidth, uy + underlineWeight, paint)
        }
    }
    return bitmap
}

/** Bitmap → 灰度图(按原宽读取) */
fun bitmapToGrayRaw(bitmap: Bitmap): GrayImage {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val gray = ByteArray(width * height)
    for (i in 0 until width * height) {
        val p = pixels[i]
        val a = Color.alpha(p)
        // 透明像素按白底合成
        val alpha = a / 255f
        val r = ((Color.red(p) * alpha) + (255 * (1 - alpha))).toInt()
        val g = (((Color.green(p) * alpha) + (255 * (1 - alpha))).toInt())
        val b = (((Color.blue(p) * alpha) + (255 * (1 - alpha))).toInt())
        gray[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255).toByte()
    }
    return GrayImage(gray, width, height)
}

/**
 * Bitmap → 灰度图,并把宽度归一到 384。
 *
 * 拆成独立一步是为了让抖动可以复用。切换抖动算法时不必重新解码图片。
 */
fun bitmapToGray(bitmap: Bitmap): GrayImage {
    val targetWidth = WIDTH_DOTS
    if (bitmap.width == targetWidth || bitmap.width <= 0) return bitmapToGrayRaw(bitmap)

    // 放大时仍用系统双线性插值；缩小照片用区域平均，
    // 能保留细线和文字边缘，避免普通采样每隔几行丢一个像素。
    if (bitmap.width < targetWidth) {
        val targetHeight = max(1, (bitmap.height.toFloat() * targetWidth / bitmap.width).roundToInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        val result = bitmapToGrayRaw(scaled)
        if (scaled !== bitmap) scaled.recycle()
        return result
    }

    val sourceWidth = bitmap.width
    val targetHeight = max(1, (bitmap.height.toFloat() * targetWidth / sourceWidth).roundToInt())
    val pixels = IntArray(sourceWidth * bitmap.height)
    bitmap.getPixels(pixels, 0, sourceWidth, 0, 0, sourceWidth, bitmap.height)
    val gray = ByteArray(targetWidth * targetHeight)

    for (targetY in 0 until targetHeight) {
        val sourceYStart = targetY * bitmap.height / targetHeight
        val sourceYEnd = max(sourceYStart + 1, (targetY + 1) * bitmap.height / targetHeight)
            .coerceAtMost(bitmap.height)
        for (targetX in 0 until targetWidth) {
            val sourceXStart = targetX * sourceWidth / targetWidth
            val sourceXEnd = max(sourceXStart + 1, (targetX + 1) * sourceWidth / targetWidth)
                .coerceAtMost(sourceWidth)
            var graySum = 0L
            var sampleCount = 0L

            for (sourceY in sourceYStart until sourceYEnd) {
                val rowBase = sourceY * sourceWidth
                for (sourceX in sourceXStart until sourceXEnd) {
                    val pixel = pixels[rowBase + sourceX]
                    val alpha = Color.alpha(pixel)
                    val alphaFraction = alpha / 255f
                    val compositedRed = Color.red(pixel) * alphaFraction + 255f * (1f - alphaFraction)
                    val compositedGreen = Color.green(pixel) * alphaFraction + 255f * (1f - alphaFraction)
                    val compositedBlue = Color.blue(pixel) * alphaFraction + 255f * (1f - alphaFraction)
                    graySum += (0.299f * compositedRed + 0.587f * compositedGreen + 0.114f * compositedBlue).toInt()
                    sampleCount++
                }
            }

            val value = (graySum / sampleCount.coerceAtLeast(1)).toInt().coerceIn(0, 255)
            gray[targetY * targetWidth + targetX] = value.toByte()
        }
    }
    return GrayImage(gray, targetWidth, targetHeight)
}

/**
 * 二值数据 → 光栅字节。
 *
 * 编码规则与 com.beeprt.sdk.d.b 一致:
 *   每行 48 字节,MSB first(bit7 = 最左像素),置 1 = 黑。
 */
fun packBinaryToRaster(binary: ByteArray, width: Int, height: Int): RasterData {
    val out = ByteArray(WIDTH_BYTES * height)
    val limit = min(width, WIDTH_DOTS)
    for (y in 0 until height) {
        val rowBase = y * width
        val outBase = y * WIDTH_BYTES
        for (x in 0 until limit) {
            if (binary[rowBase + x].toInt() == 1) {
                out[outBase + (x shr 3)] = (out[outBase + (x shr 3)].toInt() or (0x80 shr (x and 7))).toByte()
            }
        }
    }
    return RasterData(out, WIDTH_BYTES, height)
}

/** 便捷封装:Bitmap → 光栅。文字打印走这条,固定用纯阈值不抖动 */
fun bitmapToRaster(bitmap: Bitmap, threshold: Int): RasterData {
    val gray = bitmapToGray(bitmap)
    val binary = ditherToBinary(gray, DitherMode.NONE, threshold)
    return packBinaryToRaster(binary, gray.width, gray.height)
}

/** 二值数据 → 可显示的 Bitmap(用于预览) */
fun binaryToPreviewBitmap(
    binary: ByteArray,
    width: Int,
    height: Int,
    transparentWhite: Boolean = false,
): Bitmap {
    val pixels = IntArray(width * height)
    for (i in 0 until width * height) {
        if (binary[i].toInt() == 1) {
            pixels[i] = Color.BLACK
        } else if (transparentWhite) {
            pixels[i] = 0
        } else {
            pixels[i] = Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

data class RasterData(
    val data: ByteArray,
    val widthBytes: Int,
    val height: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RasterData) return false
        return widthBytes == other.widthBytes && height == other.height && data.contentEquals(other.data)
    }
    override fun hashCode(): Int = data.contentHashCode() * 31 + widthBytes * 17 + height
}

/** 比例缩放 URI 指向的图片到 384 宽,返回位图 */
fun decodeImageToPrintWidth(resolver: ContentResolver, uri: Uri): Bitmap {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IllegalArgumentException("无法解码图片: $uri")
    }

    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= WIDTH_DOTS) {
        sampleSize *= 2
    }
    val decodeOptions = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val source = resolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, decodeOptions)
    } ?: throw IllegalArgumentException("无法解码图片: $uri")

    val srcW = source.width
    val srcH = source.height
    if (srcW <= 0 || srcH <= 0) {
        source.recycle()
        throw IllegalArgumentException("图片尺寸异常 ${srcW}x${srcH}")
    }
    val targetH = max(1, (srcH.toFloat() * WIDTH_DOTS / srcW).roundToInt())
    if (srcW == WIDTH_DOTS && srcH == targetH) return source
    val scaled = Bitmap.createScaledBitmap(source, WIDTH_DOTS, targetH, true)
    if (scaled !== source) source.recycle()
    return scaled
}

// ===== 广告横带(Locked Template) =================================================

/**
 * 「广告横带」最大允许的横带长度(旋转后走纸方向上的点行数)。
 *
 * 字号越大,文字自然宽度越大,旋转 90° 后消耗的走纸长度越长。不加限制,
 * 一个长字符串在最大字号下能打出几米纸,既不实用也容易卡纸。
 * 4000 点 ≈ 50 cm,作为广告横带已经非常充裕。
 */
const val AD_BANNER_MAX_LENGTH: Int = 4000

/**
 * 把字符串里的换行/回车/制表统一压成单个空格,得到一条连续单行文字。
 *
 * 广告横带要求:不管用户怎么按回车,最终都视为同一行连续文字。
 * 多个空白字符也合并成一个,避免「按了回车又敲了空格」导致中间多出空段。
 */
private fun flattenAdBannerText(text: String): String {
    if (text.isEmpty()) return ""
    val replaced = text.replace(Regex("[\\r\\n\\t]+"), " ")
    return replaced.replace(Regex(" {2,}"), " ").trim()
}

/** 量出广告横带文字在指定字号下的自然宽度(打印点)。用作字号二分搜索判定函数 */
private fun measureAdBannerWidth(text: String, fontSize: Int): Int {
    if (text.isEmpty() || fontSize <= 0) return 0
    val paint = Paint().apply {
        isAntiAlias = true
        textSize = fontSize.toFloat()
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    return ceil(paint.measureText(text)).toInt()
}

/**
 * 二分搜索:在 [minSize, maxSize] 区间内,找出让整条广告文字宽度
 * 不超过 maxLength 的最大字号。
 *
 * 如果连 minSize 都装不下(文字极长),就返回 minSize —— 渲染时
 * 仍会按这个字号铺开,只是旋转后的横带会非常长,但至少能打。
 */
private fun pickAdBannerFontSize(text: String, minSize: Int, maxSize: Int, maxLength: Int): Int {
    if (text.isEmpty()) return minSize
    var lo = minSize
    var hi = maxSize
    var best = minSize
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val w = measureAdBannerWidth(text, mid)
        if (w <= maxLength) {
            best = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return best
}

/**
 * 「广告横带」渲染入口。
 *
 * 规则:
 *   1. 把所有换行/回车/制表压成空格,合并多余空格 → 一条连续单行文字。
 *   2. 字体固定 SANS_SERIF Bold,字号由系统最大化,用户不能改。
 *   3. 字号在 [12, WIDTH_DOTS] 区间内二分搜索,目标是让旋转 90° 后
 *      纸宽方向(fontSize)尽量大,且走纸方向 ≤ AD_BANNER_MAX_LENGTH。
 *      字号针对「整条文字」而不是按行(广告横带只有一行)。
 *   4. 直接在最终位图上用 canvas.translate + canvas.rotate 把坐标系
 *      顺时针转 90° 后画文字 —— 文字沿走纸方向从上到下铺开,
 *      整条文字作为一个整体,不会出现多列。
 *      画布尺寸 = textLength(走纸方向)× WIDTH_DOTS(纸宽方向),
 *      文字水平居中(在纸宽方向上)。
 *   5. 不换行、不拆列、不拆排 —— 一条连续广告文字就这一行。
 *
 * ⚠️ 之前的实现用「先横画文字 → 整图旋转 90°」,但横画时所有字符
 *    都在 y=baselineY 同一行,旋转后整条文字变成「一列只有 1 个
 *    字符高」的内容,其他字符全跑到画布外。已彻底改用坐标系旋转。
 *
 * 预览和打印走同一个函数,保证所见即所打。
 */
fun renderAdBannerToPixelMap(text: String): Bitmap {
    val flat = flattenAdBannerText(text)
    // 极小字号保底;极长字符串即使在 12 点的字号下旋转后也可能很长,但已经尽力压缩。
    val minSize = 12
    // 字号上限 = 纸宽:旋转后字号就是纸宽方向的厚度,不可能超过纸宽。
    val maxSize = WIDTH_DOTS
    val fontSize = pickAdBannerFontSize(flat, minSize, maxSize, AD_BANNER_MAX_LENGTH)

    // 空内容返回 1x1 白图,避免上层空指针。
    if (flat.isEmpty()) {
        val blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        blank.eraseColor(Color.WHITE)
        return blank
    }

    val paint = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
        textSize = fontSize.toFloat()
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }
    val fm = paint.fontMetrics
    val ascent = -fm.ascent
    val descent = fm.descent
    val textHeight = (ascent + descent).toInt()
    val textLength = max(1, ceil(paint.measureText(flat)).toInt())

    // 最终位图:
    //   宽(textLength) = 走纸方向(横带长度)
    //   高(WIDTH_DOTS) = 纸宽方向
    val finalW = textLength
    val finalH = WIDTH_DOTS
    val final = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(final)
    canvas.drawColor(Color.WHITE)

    // 用 canvas 平移 + 旋转,把坐标系转 90° CW 后再画文字。
    // 旋转后局部 +X 方向指向画布 +Y(下),所以画横排文字就是
    // 在画布上画出「从上到下」铺开的竖排文字:
    //   'g' 在 y=0(走纸方向起点)
    //   'k' 在 y=textLength-1(走纸方向终点)
    //
    // 水平居中:文字顶(x=0)投影到画布 x = tx,文字底(x=textHeight)
    // 投影到画布 x = tx - textHeight;文字水平中心 = tx - textHeight/2,
    // 令其等于 WIDTH_DOTS/2 → tx = (WIDTH_DOTS + textHeight) / 2。
    val tx = (WIDTH_DOTS + textHeight) / 2f
    canvas.save()
    canvas.translate(tx, 0f)
    canvas.rotate(90f)
    // 文字顶对齐局部 y=0,baseline 在局部 y=ascent
    canvas.drawText(flat, 0f, ascent, paint)
    canvas.restore()

    return final
}


