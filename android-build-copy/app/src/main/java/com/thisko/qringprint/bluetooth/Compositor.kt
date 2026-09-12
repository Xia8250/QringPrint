package com.thisko.qringprint.bluetooth

/**
 * 画布合成工具 —— 纯数组运算,不依赖 ImageKit,可以在任何线程调用。
 *
 * ⛳ 两套极性别搞混:
 *   GrayImage.data  0 = 黑,255 = 白(灰度值)
 *   binary          1 = 黑(打这个点),0 = 白
 * 翻转发生在 ditherToBinary 那里。所以二值画布的"空白"是填 0,不是 0xFF。
 *
 * 项目里原先没有任何缩放/裁剪/拼接能力,这些都为自定义画布新写的。
 */

private const val GRAY_WHITE: Int = 255

/**
 * 最邻近缩放。
 *
 * 条码专用:一维码的信息全在黑白条的**边界位置**,任何插值都会在边界处糊出灰边,
 * 二值化后条宽就变了,直接扫不出来。哪怕禁阈也不能糊。
 */
fun scaleGrayNearest(src: GrayImage, targetW: Int, targetH: Int): GrayImage {
    val w = maxOf(1, targetW)
    val h = maxOf(1, targetH)
    if (w == src.width && h == src.height) return src
    val out = ByteArray(w * h)

    for (y in 0 until h) {
        val srcY = minOf(src.height - 1, (y * src.height / h))
        val srcRow = srcY * src.width
        val dstRow = y * w
        for (x in 0 until w) {
            val srcX = minOf(src.width - 1, (x * src.width / w))
            out[dstRow + x] = src.data[srcRow + srcX]
        }
    }
    return GrayImage(out, w, h)
}

/**
 * 面积平均缩放(box filter)。
 *
 * 图片专用:缩小时把落在同一个目标像素里的源像素取平均,保留灰阶层次,
 * 之后再跑 Floyd 扩散才有东西可抖。用最邻近的话细节直接一烧光,抖出来是一片亮点。
 *
 * 放大时退化成最邻近取样(没有新信息可取,这里不做双线性 —— 热敏机最终只有黑白两色,
 * 放大后的平滑过渡在二值化时会被吃掉,不值得那个开销)。
 */
fun scaleGrayArea(src: GrayImage, targetW: Int, targetH: Int): GrayImage {
    val w = maxOf(1, targetW)
    val h = maxOf(1, targetH)
    if (w == src.width && h == src.height) return src
    if (w >= src.width && h >= src.height) {
        return scaleGrayNearest(src, w, h)
    }

    val out = ByteArray(w * h)
    val xRatio = src.width.toFloat() / w
    val yRatio = src.height.toFloat() / h

    for (y in 0 until h) {
        val y0 = (y * yRatio).toInt()
        val y1 = maxOf(y0 + 1, minOf(src.height, ((y + 1) * yRatio).toFloat().let { kotlin.math.ceil(it).toInt() }))
        val dstRow = y * w

        for (x in 0 until w) {
            val x0 = (x * xRatio).toInt()
            val x1 = maxOf(x0 + 1, minOf(src.width, ((x + 1) * xRatio).toFloat().let { kotlin.math.ceil(it).toInt() }))

            var sum = 0
            var count = 0
            for (sy in y0 until y1) {
                val srcRow = sy * src.width
                for (sx in x0 until x1) {
                    sum += src.data[srcRow + sx].toInt() and 0xFF
                    count++
                }
            }
            out[dstRow + x] = if (count > 0) (sum / count).toByte() else GRAY_WHITE.toByte()
        }
    }
    return GrayImage(out, w, h)
}

/**
 * 纵向挤行压缩。一维码生成出来是 384 的方图,打之前压缩成条带。
 * 同一刻列内每行完全相同,按最邻近挤行是无损的;
 * 取平均反而会在黑白交界处抹出灰边。
 */
fun squeezeRows(src: GrayImage, targetHeight: Int): GrayImage {
    if (targetHeight >= src.height) return src
    val out = ByteArray(src.width * targetHeight)
    for (y in 0 until targetHeight) {
        val srcY = minOf(src.height - 1, (y * src.height / targetHeight))
        val from = srcY * src.width
        System.arraycopy(src.data, from, out, y * src.width, src.width)
    }
    return GrayImage(out, src.width, targetHeight)
}

/** 新建一张全白的二值画布。二值里 0 就是白,所以零值即可 */
fun createBinaryCanvas(width: Int, height: Int): ByteArray =
    ByteArray(maxOf(1, width) * maxOf(1, height))

/**
 * 把 src 叠到 dst 的 (originX, originY) 处。超出部分自动裁掉。
 *
 * 用 OR 而不是赋值:元素重叠时黑点应该保持。覆盖的话后画的元素会用白底把下面的内容抹掉,
 * 而热敏打印里"白"等于不打,抹出来的是一块空白,不是想要的效果。
 */
fun blitBinary(
    dst: ByteArray, dstW: Int, dstH: Int,
    src: ByteArray, srcW: Int, srcH: Int,
    originX: Int, originY: Int,
) {
    val ox = kotlin.math.round(originX.toFloat()).toInt()
    val oy = kotlin.math.round(originY.toFloat()).toInt()

    // 先把要拷的范围夹到画布内,循环里就不用每个点判越界
    val startX = maxOf(0, -ox)
    val startY = maxOf(0, -oy)
    val endX = minOf(srcW, dstW - ox)
    val endY = minOf(srcH, dstH - oy)

    for (y in startY until endY) {
        val srcRow = y * srcW
        val dstRow = (y + oy) * dstW
        for (x in startX until endX) {
            if (src[srcRow + x].toInt() == 1) {
                dst[dstRow + x + ox] = 1
            }
        }
    }
}
