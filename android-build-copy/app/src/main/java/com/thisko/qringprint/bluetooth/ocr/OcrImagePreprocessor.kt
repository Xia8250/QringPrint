package com.thisko.qringprint.bluetooth.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * OCR 图像预处理。目标是稳定地把图变成「黑字白底」二值图。
 *
 *  1. 缩放到 3600 边长内（保细节）
 *  2. 转灰度
 *  3. 全局对比度拉伸（autoContrast，简单但有效）
 *  4. 自动反色（只在 >80% 像素偏暗时反，多用于深色模式截图）
 *  5. 自动裁边（找非主色区域，纸页在深色桌面上也能正确裁）
 *  6. Sauvola 局部二值化（k=0.5，更宽容）
 *  7. 健全性检查：若 95% 以上单色，自动切到 Otsu
 */
object OcrImagePreprocessor {

    enum class Mode { Aggressive, OcrFriendly }

    /**
     * 预处理入口。
     * - Aggressive：旧的二值化流程（适合纯黑白截图）
     * - OcrFriendly（默认）：只做 toGray + 轻度裁边，把灰度图直接交给 OCR，
     *   百度 OCR 内部自带二值化逻辑，对实拍照片（蓝底白字、阴影、轻微透视）
     *   比客户端提前二值化鲁棒得多，避免把整张图二值化成全黑/全白。
     */
    fun preprocess(src: Bitmap, maxDim: Int = 3600, mode: Mode = Mode.OcrFriendly): Bitmap {
        if (src.isRecycled) return src
        if (src.width <= 0 || src.height <= 0) return src
        // 整体 try-catch：任何内部步骤抛错（包括 getPixels 撞到 recycled buffer）
        // 都退回到 src 的一份独立 copy，绝不向上抛异常。
        return try {
            when (mode) {
                Mode.Aggressive -> preprocessAggressive(src, maxDim)
                Mode.OcrFriendly -> preprocessForOcr(src, maxDim)
            }
        } catch (t: Throwable) {
            Log.w("OcrPre", "preprocess failed, fallback to raw copy: " + t.message)
            try {
                if (!src.isRecycled) src.copy(Bitmap.Config.ARGB_8888, false) else src
            } catch (t2: Throwable) {
                src
            }
        }
    }

    /** 旧逻辑：强二值化。仅对纯黑白截图有用。 */
    private fun preprocessAggressive(src: Bitmap, maxDim: Int): Bitmap {
        return preprocessInternal(src, maxDim)
    }

    /**
     * 给 OCR 用的轻度预处理：
     * 1) 独立 copy（防共享 buffer）
     * 2) 长边 > maxDim 时缩放
     * 3) toGray（BT.601）
     * 4) 极轻裁边：只有当四周有非常均匀的边框时才裁，否则保持原样
     * 5) **不做** autoContrast / isDarkDominant 反色 / Sauvola / Otsu
     *    这些步骤对实拍照片（彩色背景、不均匀光照、轻微透视）破坏性极大，
     *    百度 OCR 通用版本身就支持彩色/灰度图输入，
     *    内部已有自适应二值化，把原图给过去效果最好。
     */
    private fun preprocessForOcr(src: Bitmap, maxDim: Int): Bitmap {
        // 1) 独立 copy（显式 getPixels/setPixels）
        val work: Bitmap = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        try {
            val srcPixels = IntArray(src.width * src.height)
            src.getPixels(srcPixels, 0, src.width, 0, 0, src.width, src.height)
            work.setPixels(srcPixels, 0, src.width, 0, 0, src.width, src.height)
        } catch (oom: OutOfMemoryError) {
            if (!work.isRecycled) work.recycle()
            return src.copy(Bitmap.Config.ARGB_8888, false)
        }

        // 2) 缩放
        val work2: Bitmap = if (work.width > maxDim || work.height > maxDim) {
            val scale = maxDim.toFloat() / max(work.width, work.height)
            val scaled = Bitmap.createScaledBitmap(
                work,
                (work.width * scale).toInt().coerceAtLeast(1),
                (work.height * scale).toInt().coerceAtLeast(1),
                true
            )
            if (scaled !== work) work.recycle()
            scaled
        } else work

        // 3) toGray
        val gray: Bitmap = toGray(work2)
        if (gray !== work2) work2.recycle()

        // 4) 极轻裁边：只有当四周留白明显才裁一点点
        val cropped: Bitmap = trimUniformBorder(gray)
        if (cropped !== gray) gray.recycle()
        return cropped
    }

    /**
     * 极轻裁边：只在四周都是同一主色（容差小）时才向外找边界。
     * 不会做基于内容包围盒的裁剪（之前的 autoCrop 经常把实拍图裁到只剩噪声）。
     */
    private fun trimUniformBorder(gray: Bitmap): Bitmap {
        val w = gray.width
        val h = gray.height
        if (w < 80 || h < 80) return gray  // 太小的图不裁
        val pixels = IntArray(w * h)
        try {
            gray.getPixels(pixels, 0, w, 0, 0, w, h)
        } catch (t: Throwable) {
            return gray
        }

        fun rowMainGray(y: Int): Int {
            // 取该行首尾各 10% 的中位数
            val n = (w * 0.1f).toInt().coerceAtLeast(1)
            var s = 0
            for (i in 0 until n) s += Color.red(pixels[y * w + i])
            for (i in 0 until n) s += Color.red(pixels[y * w + (w - 1 - i)])
            return s / (2 * n)
        }

        fun colMainGray(x: Int): Int {
            val n = (h * 0.1f).toInt().coerceAtLeast(1)
            var s = 0
            for (i in 0 until n) s += Color.red(pixels[i * w + x])
            for (i in 0 until n) s += Color.red(pixels[(h - 1 - i) * w + x])
            return s / (2 * n)
        }

        // 判断"四周是均匀背景"：用最外 5% 边带的方差做指标
        fun borderVar(borderPixels: IntArray): Int {
            val mean = borderPixels.average()
            var v = 0.0
            for (p in borderPixels) {
                val d = p - mean
                v += d * d
            }
            return (v / borderPixels.size).toInt()
        }

        val topVar = borderVar(IntArray((w * 0.05f).toInt().coerceAtLeast(2)) { i -> rowMainGray(0).let { Color.red(pixels[0 * w + i]) } })
        // 简化：直接测四边首尾 1 行/列 的灰度，若非常接近主色（容差 8）
        // 且与对边差异不大，就只裁 2% 边距；否则完全不裁
        val topMid = rowMainGray(0)
        val botMid = rowMainGray(h - 1)
        val leftMid = colMainGray(0)
        val rightMid = colMainGray(w - 1)
        val maxDelta = maxOf(
            kotlin.math.abs(topMid - botMid),
            kotlin.math.abs(leftMid - rightMid)
        )
        if (maxDelta > 20) return gray  // 四边差异大，说明有内容，直接不裁

        val pad = (minOf(w, h) * 0.02f).toInt().coerceAtLeast(2)
        val cw = w - 2 * pad
        val ch = h - 2 * pad
        if (cw < 40 || ch < 40) return gray
        val out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val cropPixels = IntArray(cw * ch)
        for (y in 0 until ch) {
            val srcOff = (y + pad) * w + pad
            System.arraycopy(pixels, srcOff, cropPixels, y * cw, cw)
        }
        out.setPixels(cropPixels, 0, cw, 0, 0, cw, ch)
        return out
    }

    private fun preprocessInternal(src: Bitmap, maxDim: Int): Bitmap {

        // 1) 防御：永远做一份独立 copy。Canvas.drawBitmap 在某些 ROM 上不真正 copy 像素，
        //    只是注册引用；调用方 recycle src 后，work 的 native buffer 也被释放，
        //    后续 getPixels/setPixels 会抛 `Can't call getPixels() on a recycled bitmap`。
        //    改成显式 getPixels/setPixels，强制把所有像素值复制到 work 自己的 buffer。
        var work: Bitmap = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        try {
            val srcPixels = IntArray(src.width * src.height)
            src.getPixels(srcPixels, 0, src.width, 0, 0, src.width, src.height)
            work.setPixels(srcPixels, 0, src.width, 0, 0, src.width, src.height)
        } catch (oom: OutOfMemoryError) {
            if (!work.isRecycled) work.recycle()
            work = src.copy(Bitmap.Config.ARGB_8888, false)
        }

        // 2) 缩放
        if (work.width > maxDim || work.height > maxDim) {
            val scale = maxDim.toFloat() / max(work.width, work.height)
            val scaled = Bitmap.createScaledBitmap(
                work,
                (work.width * scale).toInt().coerceAtLeast(1),
                (work.height * scale).toInt().coerceAtLeast(1),
                true
            )
            if (scaled !== work) work.recycle()
            work = scaled
        }

        // 3) 灰度
        val gray = toGray(work)
        if (gray !== work) work.recycle()
        work = gray

        // 4) 全局对比度拉伸（用 2%/98% 截断的直方图）
        val stretched = autoContrast(work)
        if (stretched !== work) work.recycle()
        work = stretched

        // 5) 自动反色（80% 阈值，纸页在深色桌面上不会误判）
        if (isDarkDominant(work, percent = 80)) {
            val inv = invert(work)
            work.recycle()
            work = inv
        }

        // 6) 自动裁边（找非主色区域）
        val cropped = autoCrop(work, paddingPx = 16)
        if (cropped !== work) {
            work.recycle()
            work = cropped
        }

        // 7) Sauvola 二值化，失败 fallback Otsu
        val binary = sauvolaBinarize(work, windowSize = 31, k = 0.5f, r = 128f)
        if (binary !== work) work.recycle()
        if (isMonoChrome(binary, threshold = 0.95f)) {
            binary.recycle()
            val otsu = otsuBinarize(work)
            work.recycle()
            return otsu
        }
        work = binary
        return work
    }

    // ---- 工具函数 ----

    private fun toGray(bmp: Bitmap): Bitmap {
        // 不用 Canvas + ColorMatrix：部分 ROM 上 drawBitmap 可能不会真正写像素，
        // 当 bmp 是上游 recycle 后的视图时这里会拿到未初始化的 buffer。
        // 改成显式 getPixels + 手算亮度写回 out，物理上独立。
        val w = bmp.width
        val h = bmp.height
        val inPx = IntArray(w * h)
        bmp.getPixels(inPx, 0, w, 0, 0, w, h)
        val outPx = IntArray(w * h)
        for (i in inPx.indices) {
            val p = inPx[i]
            // ITU-R BT.601 亮度
            val l = ((Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000)
                .coerceIn(0, 255)
            outPx[i] = Color.rgb(l, l, l)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        return out
    }

    /** 2%/98% 截断的直方图拉伸 */
    private fun autoContrast(gray: Bitmap): Bitmap {
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)
        val hist = IntArray(256)
        for (p in pixels) hist[Color.red(p)]++
        val total = pixels.size
        val lowCut = total * 2 / 100
        val highCut = total * 98 / 100
        var lo = 0
        var hi = 255
        var acc = 0
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= lowCut) { lo = i; break }
        }
        acc = 0
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= highCut) { hi = i; break }
        }
        if (hi <= lo) return gray
        val scale = 255f / (hi - lo)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val nr = ((r - lo) * scale).toInt().coerceIn(0, 255)
            val ng = ((g - lo) * scale).toInt().coerceIn(0, 255)
            val nb = ((b - lo) * scale).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(nr, ng, nb)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /** 检测图是否「深色主导」：>percent% 像素亮度 < 110 */
    private fun isDarkDominant(gray: Bitmap, percent: Int): Boolean {
        val w = gray.width
        val h = gray.height
        val step = max(1, (w * h) / 50000)
        var dark = 0
        var total = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val v = Color.red(gray.getPixel(x, y))
                if (v < 110) dark++
                total++
                x += step
            }
            y += step
        }
        return total > 0 && dark * 100 >= total * percent
    }

    private fun invert(gray: Bitmap): Bitmap {
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            pixels[i] = Color.rgb(255 - Color.red(p), 255 - Color.green(p), 255 - Color.blue(p))
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * 自动裁边：找非主色区域的包围盒。
     * - 浅色主色（图大部分是白纸）→ 找深色像素（文字）
     * - 深色主色（图大部分是深色背景）→ 找浅色像素（白纸/白底）
     * 同时要求裁剪后尺寸 > 原图的 50%（避免裁掉正文）
     */
    private fun autoCrop(gray: Bitmap, paddingPx: Int): Bitmap {
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)
        val otsu = otsuThreshold(pixels)
        var dark = 0
        for (p in pixels) if (Color.red(p) < otsu) dark++
        val isBackgroundLight = dark * 2 < pixels.size  // 浅色占多数

        val step = 2
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val v = Color.red(pixels[y * w + x])
                val isContent = if (isBackgroundLight) v < otsu else v > otsu
                if (isContent) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (minX >= maxX || minY >= maxY) return gray
        minX = max(0, minX - paddingPx)
        minY = max(0, minY - paddingPx)
        maxX = min(w - 1, maxX + paddingPx)
        maxY = min(h - 1, maxY + paddingPx)
        // 安全：裁剪后还要 ≥ 原图一半
        if (maxX - minX < w / 2 || maxY - minY < h / 2) return gray
        // 必须用 Canvas 显式 copy：createBitmap(gray,...) 返回的是 view，
        // 共享 gray 的 native 内存，gray 被 recycle 后 view 上的 getPixels 会崩
        val cropW = maxX - minX + 1
        val cropH = maxY - minY + 1
        // 显式 getPixels/setPixels：Canvas 在某些 ROM 上会共享底层内存，recycle 后会炸
        val cropPixels = IntArray(cropW * cropH)
        gray.getPixels(cropPixels, 0, cropW, minX, minY, cropW, cropH)
        val crop = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        crop.setPixels(cropPixels, 0, cropW, 0, 0, cropW, cropH)
        return crop
    }

    /** Sauvola 局部阈值二值化，k=0.5 比较宽容 */
    private fun sauvolaBinarize(gray: Bitmap, windowSize: Int, k: Float, r: Float): Bitmap {
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)
        val integral = LongArray(w * h)
        val integralSq = LongArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val v = Color.red(pixels[idx])
                val l = if (x > 0) integral[idx - 1] else 0L
                val u = if (y > 0) integral[idx - w] else 0L
                val d = if (x > 0 && y > 0) integral[idx - w - 1] else 0L
                integral[idx] = v + l + u - d
                val vs = v.toLong() * v
                val lSq = if (x > 0) integralSq[idx - 1] else 0L
                val uSq = if (y > 0) integralSq[idx - w] else 0L
                val dSq = if (x > 0 && y > 0) integralSq[idx - w - 1] else 0L
                integralSq[idx] = vs + lSq + uSq - dSq
            }
        }
        val half = windowSize / 2
        val out = IntArray(w * h)
        val black = Color.rgb(0, 0, 0)
        val white = Color.rgb(255, 255, 255)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val x0 = max(0, x - half)
                val y0 = max(0, y - half)
                val x1 = min(w - 1, x + half)
                val y1 = min(h - 1, y + half)
                val count = ((x1 - x0 + 1) * (y1 - y0 + 1)).toDouble()
                val i1 = y1 * w + x1
                val i2 = y0 * w + x0
                val i3 = y0 * w + x1
                val i4 = y1 * w + x0
                val sum = integral[i1] - integral[i2] - integral[i3] + integral[i4]
                val sumSq = integralSq[i1] - integralSq[i2] - integralSq[i3] + integralSq[i4]
                val mean = sum / count
                val variance = (sumSq / count) - (mean * mean)
                val std = if (variance > 0) sqrt(variance) else 0.0
                val threshold = mean * (1.0 + k * (std / r - 1.0))
                val v = Color.red(pixels[y * w + x])
                out[y * w + x] = if (v.toDouble() < threshold) black else white
            }
        }
        val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBmp.setPixels(out, 0, w, 0, 0, w, h)
        return outBmp
    }

    /** Otsu 二值化作为 fallback */
    private fun otsuBinarize(gray: Bitmap): Bitmap {
        val w = gray.width
        val h = gray.height
        val pixels = IntArray(w * h)
        gray.getPixels(pixels, 0, w, 0, 0, w, h)
        val t = otsuThreshold(pixels)
        val out = IntArray(w * h)
        val black = Color.rgb(0, 0, 0)
        val white = Color.rgb(255, 255, 255)
        for (i in pixels.indices) {
            out[i] = if (Color.red(pixels[i]) < t) black else white
        }
        val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBmp.setPixels(out, 0, w, 0, 0, w, h)
        return outBmp
    }

    /** 检查二值图是否单色占比 > threshold */
    private fun isMonoChrome(binary: Bitmap, threshold: Float): Boolean {
        val w = binary.width
        val h = binary.height
        val step = max(1, (w * h) / 20000)
        var black = 0
        var total = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val v = Color.red(binary.getPixel(x, y))
                if (v < 128) black++
                total++
                x += step
            }
            y += step
        }
        if (total == 0) return true
        val blackRatio = black.toFloat() / total
        return blackRatio > threshold || blackRatio < 1f - threshold
    }

    private fun otsuThreshold(pixels: IntArray): Int {
        val hist = IntArray(256)
        for (p in pixels) hist[Color.red(p)]++
        val total = pixels.size
        var sumAll = 0.0
        for (i in 0..255) sumAll += i * hist[i]
        var sumBg = 0.0
        var weightBg = 0
        var maxVar = -1.0
        var bestT = 128
        for (t in 0..255) {
            weightBg += hist[t]
            if (weightBg == 0) continue
            val weightFg = total - weightBg
            if (weightFg == 0) break
            sumBg += t * hist[t]
            val meanBg = sumBg / weightBg
            val meanFg = (sumAll - sumBg) / weightFg
            val between = weightBg.toDouble() * weightFg * (meanBg - meanFg) * (meanBg - meanFg)
            if (between > maxVar) {
                maxVar = between
                bestT = t
            }
        }
        return bestT
    }
}