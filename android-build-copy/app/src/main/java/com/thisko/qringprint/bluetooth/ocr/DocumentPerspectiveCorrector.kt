package com.thisko.qringprint.bluetooth.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 文档透视校正：把歪斜拍摄的纸页拉成正矩形。
 *
 * 流程：缩略图 -> Otsu 阈值 -> 取内容区域 -> 找最大连通域 -> 用 4 个极端点估 4 角 -> 透视变换回原图。
 */
object DocumentPerspectiveCorrector {

    fun correct(src: Bitmap, detectLongSide: Int = 600): Bitmap {
        if (src.width < 100 || src.height < 100) return src
        val scale: Float
        val sw: Int
        val sh: Int
        if (max(src.width, src.height) > detectLongSide) {
            scale = detectLongSide.toFloat() / max(src.width, src.height)
            sw = (src.width * scale).toInt().coerceAtLeast(1)
            sh = (src.height * scale).toInt().coerceAtLeast(1)
        } else {
            scale = 1f
            sw = src.width
            sh = src.height
        }
        val small = if (scale != 1f) Bitmap.createScaledBitmap(src, sw, sh, true) else src

        val corners = try { detectQuad(small) } catch (e: Exception) { null }
        if (corners == null) {
            if (small !== src) small.recycle()
            return src
        }
        val (tl, tr, br, bl) = corners
        val ratio = 1f / scale
        val tl2 = PointF(tl.x * ratio, tl.y * ratio)
        val tr2 = PointF(tr.x * ratio, tr.y * ratio)
        val br2 = PointF(br.x * ratio, br.y * ratio)
        val bl2 = PointF(bl.x * ratio, bl.y * ratio)
        if (small !== src) small.recycle()

        val w1 = dist(tl2, tr2); val w2 = dist(bl2, br2)
        val h1 = dist(tl2, bl2); val h2 = dist(tr2, br2)
        val w = ((w1 + w2) / 2f).toInt().coerceAtLeast(1)
        val h = ((h1 + h2) / 2f).toInt().coerceAtLeast(1)
        if (w < src.width / 3 || h < src.height / 3) return src
        if (w > src.width * 3 || h > src.height * 3) return src
        return warp(src, tl2, tr2, br2, bl2, w, h)
    }

    private data class Quad(val tl: PointF, val tr: PointF, val br: PointF, val bl: PointF)

    private fun detectQuad(bmp: Bitmap): Quad? {
        val w = bmp.width; val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h) { Color.red(pixels[it]) }
        val t = otsuThreshold(gray)
        val median = percentile(gray, 0.5)
        val isBackgroundDark = median < 128
        // 浅背景（白纸）→ mask true 是深（文字/桌）
        val mask = BooleanArray(w * h) { gray[it] < t }
        val contentMask = if (isBackgroundDark) {
            BooleanArray(w * h) { i -> !mask[i] }
        } else {
            mask
        }
        val points = largestConnectedComponent(contentMask, w, h) ?: return null
        return approximateQuad(points, w, h)
    }

    private fun largestConnectedComponent(mask: BooleanArray, w: Int, h: Int): IntArray? {
        val labels = IntArray(w * h)
        val queue = IntArray(w * h)
        var bestSize = 0
        var bestPoints: IntArray? = null
        var label = 0
        val dx = intArrayOf(1, -1, 0, 0)
        val dy = intArrayOf(0, 0, 1, -1)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (mask[i] && labels[i] == 0) {
                    label++
                    var head = 0
                    var tail = 0
                    queue[tail++] = i
                    labels[i] = label
                    var points: IntArray = IntArray(256)
                    var pCount = 0
                    points[pCount++] = i
                    while (head < tail) {
                        val cur = queue[head++]
                        val cx = cur % w
                        val cy = cur / w
                        for (d in 0..3) {
                            val nx = cx + dx[d]
                            val ny = cy + dy[d]
                            if (nx in 0 until w && ny in 0 until h) {
                                val ni = ny * w + nx
                                if (mask[ni] && labels[ni] == 0) {
                                    labels[ni] = label
                                    queue[tail++] = ni
                                    if (pCount >= points.size) points = points.copyOf(points.size * 2)
                                    points[pCount++] = ni
                                }
                            }
                        }
                    }
                    if (pCount > bestSize) {
                        bestSize = pCount
                        bestPoints = points.copyOf(pCount)
                    }
                }
            }
        }
        if (bestPoints == null || bestSize < (w * h / 100)) return null
        return bestPoints
    }

    private fun approximateQuad(points: IntArray, w: Int, h: Int): Quad? {
        var leftIdx = points[0]
        var rightIdx = points[0]
        var topIdx = points[0]
        var bottomIdx = points[0]
        for (p in points) {
            val x = p % w
            val y = p / w
            if (x < (leftIdx % w)) leftIdx = p
            if (x > (rightIdx % w)) rightIdx = p
            if (y < (topIdx / w)) topIdx = p
            if (y > (bottomIdx / w)) bottomIdx = p
        }
        val leftPt = PointF((leftIdx % w).toFloat(), (leftIdx / w).toFloat())
        val rightPt = PointF((rightIdx % w).toFloat(), (rightIdx / w).toFloat())
        val topPt = PointF((topIdx % w).toFloat(), (topIdx / w).toFloat())
        val bottomPt = PointF((bottomIdx % w).toFloat(), (bottomIdx / w).toFloat())
        val corners = listOf(leftPt, rightPt, topPt, bottomPt)
        val tl = corners.minByOrNull { it.x + it.y } ?: return null
        val br = corners.maxByOrNull { it.x + it.y } ?: return null
        val tr = corners.maxByOrNull { it.x - it.y } ?: return null
        val bl = corners.minByOrNull { it.x - it.y } ?: return null
        return Quad(tl, tr, br, bl)
    }

    private fun dist(a: PointF, b: PointF): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun warp(src: Bitmap, tl: PointF, tr: PointF, br: PointF, bl: PointF, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcW = src.width; val srcH = src.height
        val srcPixels = IntArray(srcW * srcH)
        src.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)
        val outPixels = IntArray(w * h)
        val white = Color.rgb(255, 255, 255)
        java.util.Arrays.fill(outPixels, white)
        val Hmat = solveHomography(tl, tr, br, bl, w.toFloat(), h.toFloat()) ?: return out
        for (y in 0 until h) {
            for (x in 0 until w) {
                val denom = Hmat[6] * x + Hmat[7] * y + Hmat[8]
                if (denom == 0f) continue
                val srcX = (Hmat[0] * x + Hmat[1] * y + Hmat[2]) / denom
                val srcY = (Hmat[3] * x + Hmat[4] * y + Hmat[5]) / denom
                if (srcX < 0 || srcX > srcW - 1 || srcY < 0 || srcY > srcH - 1) continue
                outPixels[y * w + x] = bilinearSample(srcPixels, srcW, srcH, srcX, srcY)
            }
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun solveHomography(
        tl: PointF, tr: PointF, br: PointF, bl: PointF,
        W: Float, H: Float
    ): FloatArray? {
        val srcs = arrayOf(tl, tr, br, bl)
        val dsts = arrayOf(
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(W.toDouble(), 0.0),
            doubleArrayOf(W.toDouble(), H.toDouble()),
            doubleArrayOf(0.0, H.toDouble())
        )
        val A = Array(8) { DoubleArray(8) }
        val b = DoubleArray(8)
        for (i in 0..3) {
            val s = srcs[i]
            val d = dsts[i]
            val sx = s.x.toDouble()
            val sy = s.y.toDouble()
            val dx = d[0]
            val dy = d[1]
            A[i * 2][0] = sx
            A[i * 2][1] = sy
            A[i * 2][2] = 1.0
            A[i * 2][3] = 0.0
            A[i * 2][4] = 0.0
            A[i * 2][5] = 0.0
            A[i * 2][6] = -dx * sx
            A[i * 2][7] = -dx * sy
            b[i * 2] = dx
            A[i * 2 + 1][0] = 0.0
            A[i * 2 + 1][1] = 0.0
            A[i * 2 + 1][2] = 0.0
            A[i * 2 + 1][3] = sx
            A[i * 2 + 1][4] = sy
            A[i * 2 + 1][5] = 1.0
            A[i * 2 + 1][6] = -dy * sx
            A[i * 2 + 1][7] = -dy * sy
            b[i * 2 + 1] = dy
        }
        val h = solveLinearSystem(A, b) ?: return null
        return floatArrayOf(
            h[0].toFloat(), h[1].toFloat(), h[2].toFloat(),
            h[3].toFloat(), h[4].toFloat(), h[5].toFloat(),
            h[6].toFloat(), h[7].toFloat(), 1f
        )
    }

    private fun solveLinearSystem(A: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = A.size
        val M = Array(n) { A[it].copyOf(n + 1) }
        for (i in 0 until n) M[i][n] = b[i]
        for (i in 0 until n) {
            var pivot = i
            for (k in i + 1 until n) {
                if (abs(M[k][i]) > abs(M[pivot][i])) pivot = k
            }
            if (abs(M[pivot][i]) < 1e-9) return null
            if (pivot != i) {
                val tmp = M[i]; M[i] = M[pivot]; M[pivot] = tmp
            }
            for (k in i + 1 until n) {
                val factor = M[k][i] / M[i][i]
                if (factor == 0.0) continue
                for (j in i..n) M[k][j] -= factor * M[i][j]
            }
        }
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            var sum = M[i][n]
            for (j in i + 1 until n) sum -= M[i][j] * x[j]
            x[i] = sum / M[i][i]
        }
        return x
    }

    private fun bilinearSample(pixels: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = min(x0 + 1, w - 1)
        val y1 = min(y0 + 1, h - 1)
        val dx = x - x0
        val dy = y - y0
        val c00 = pixels[y0 * w + x0]
        val c10 = pixels[y0 * w + x1]
        val c01 = pixels[y1 * w + x0]
        val c11 = pixels[y1 * w + x1]
        val a = Color.alpha(c00)
        val r00 = Color.red(c00).toFloat()
        val g00 = Color.green(c00).toFloat()
        val b00 = Color.blue(c00).toFloat()
        val r10 = Color.red(c10).toFloat()
        val g10 = Color.green(c10).toFloat()
        val b10 = Color.blue(c10).toFloat()
        val r01 = Color.red(c01).toFloat()
        val g01 = Color.green(c01).toFloat()
        val b01 = Color.blue(c01).toFloat()
        val r11 = Color.red(c11).toFloat()
        val g11 = Color.green(c11).toFloat()
        val b11 = Color.blue(c11).toFloat()
        val rr = (r00 * (1 - dx) + r10 * dx) * (1 - dy) + (r01 * (1 - dx) + r11 * dx) * dy
        val gg = (g00 * (1 - dx) + g10 * dx) * (1 - dy) + (g01 * (1 - dx) + g11 * dx) * dy
        val bb = (b00 * (1 - dx) + b10 * dx) * (1 - dy) + (b01 * (1 - dx) + b11 * dx) * dy
        return Color.argb(a, rr.toInt().coerceIn(0, 255), gg.toInt().coerceIn(0, 255), bb.toInt().coerceIn(0, 255))
    }

    private fun percentile(gray: IntArray, p: Double): Int {
        val sorted = gray.copyOf().also { java.util.Arrays.sort(it) }
        return sorted[(sorted.size * p).toInt().coerceIn(0, sorted.size - 1)]
    }

    private fun otsuThreshold(gray: IntArray): Int {
        val hist = IntArray(256)
        for (v in gray) hist[v]++
        val total = gray.size
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
            if (between > maxVar) { maxVar = between; bestT = t }
        }
        return bestT
    }
}