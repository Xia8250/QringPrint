package com.thisko.qringprint.bluetooth

/**
 * 图像处理选项 —— 在二值化前对灰度图做增强。
 *
 * 应用顺序(每步都允许被关闭):
 *   1. 自动对比度(直方图 1%~99% 截断拉伸) —— 一次操作,后续不再用
 *   2. 对比度(围绕中点 128 线性拉伸)        —— 提反差
 *   3. 亮度(整体加减)                       —— 调深浅
 *   4. 伽马(非线性亮度重映射)                —— 提暗部/压高光
 *   5. 反色                                 —— 适合深色底浅色字
 *
 * 之后才走 ditherToBinary -> packBinaryToRaster。
 *
 * 阈值(threshold)只对 NONE 模式生效,给硬阈值二值化调判定线。
 * 抖动模式的中点固定 128 —— 误差扩散的前提是量化点落在灰阶中点,
 * 改它会让整张图偏移一档,反而失去抖动意义。
 */
data class ImageProcessOptions(
    val ditherMode: DitherMode = DitherMode.FLOYD_STEINBERG,
    /** 亮度偏移,-100..100。负值加暗(出图更深),正值加亮 */
    val brightness: Int = 0,
    /** 对比度系数,0.5..2.0。1.0 = 不变 */
    val contrast: Float = 1.0f,
    /** 伽马值,0.5..2.0。1.0 = 不变;< 1.0 提亮暗部,> 1.0 加深暗部 */
    val gamma: Float = 1.0f,
    /** 硬阈值(仅 NONE 模式生效),1..254。值越低,越多像素被判黑(出图越深) */
    val threshold: Int = 128,
    /** 直方图两端 1% 截断拉伸到 0..255 —— 处理偏色/偏暗/偏亮的照片 */
    val autoContrast: Boolean = false,
    /** 反色:黑变白白变黑。深色底浅色字的截图/截屏用 */
    val invert: Boolean = false,
    /**
     * Unsharp mask 突加量,0..2.0。
     * 0 = 不加。>0 提升高频细节,二值化后边缘明显剑。
     * 照片轿(含文字、软调) 适合加一点(0.5~1.5)。
     */
    val sharpen: Float = 0f,
)

/**
 * 预设模板 —— 一键切换常用组合。key 是稳定标识,用于持久化。
 */
data class ImagePreset(
    val key: String,
    val label: String,
    val hint: String,
    val options: ImageProcessOptions,
)

val IMAGE_PRESETS: List<ImagePreset> = listOf(
    // 照片:默认,Floyd + 中性设置
    ImagePreset(
        "photo", "\u7167\u7247", "Floyd \u00b7 \u7ec6\u817b",
        ImageProcessOptions(ditherMode = DitherMode.FLOYD_STEINBERG)
    ),
    // 深色照片:亮-15,对比 +0.2,让偏浅照片更深
    ImagePreset(
        "photo_dark", "\u6df1\u8272\u7167\u7247", "Floyd \u00b7 \u5bf9\u6bd4+0.2",
        ImageProcessOptions(
            ditherMode = DitherMode.FLOYD_STEINBERG,
            brightness = -15,
            contrast = 1.2f,
        )
    ),
    // 浅色照片:自动拉伸直方图,加亮
    ImagePreset(
        "photo_light", "\u6d45\u8272\u7167\u7247", "Floyd \u00b7 \u81ea\u52a8\u62c9\u4f38",
        ImageProcessOptions(
            ditherMode = DitherMode.FLOYD_STEINBERG,
            autoContrast = true,
            brightness = 8,
        )
    ),
    // 文档:硬阈值 + 较高判定线(白底黑字更锐利)
    ImagePreset(
        "document", "\u6587\u6863", "\u786c\u9608\u503c \u00b7 \u9510\u5229",
        ImageProcessOptions(
            ditherMode = DitherMode.NONE,
            threshold = 175,
        )
    ),
    // 加粗文档:阈值降低,字更黑
    ImagePreset(
        "document_bold", "\u52a0\u7c97\u6587\u6863", "\u786c\u9608\u503c \u00b7 \u6df1",
        ImageProcessOptions(
            ditherMode = DitherMode.NONE,
            threshold = 150,
        )
    ),
    // 票据:Atkinson 暗部更干净,对比 +0.1
    ImagePreset(
        "receipt", "\u7968\u636e", "Atkinson \u00b7 \u5e72\u51c0",
        ImageProcessOptions(
            ditherMode = DitherMode.ATKINSON,
            contrast = 1.1f,
        )
    ),
    // 深色截图:反色 + 硬阈值(深色背景浅色文字)
    ImagePreset(
        "screenshot_dark", "\u6df1\u8272\u622a\u56fe", "\u53cd\u8272 \u00b7 \u786c\u9608\u503c",
        ImageProcessOptions(
            ditherMode = DitherMode.NONE,
            threshold = 128,
            invert = true,
        )
    ),
    // 网点最少:Atkinson + 暗部补偿(适合带文字的照片)
    ImagePreset(
        "text_photo", "\u6587\u5b57\u7167\u7247", "Atkinson \u00b7 \u9ad8\u5bf9\u6bd4",
        ImageProcessOptions(
            ditherMode = DitherMode.ATKINSON,
            contrast = 1.3f,
            brightness = -10,
            sharpen = 0.6f,
        )
    ),

    // 最深黑白:brightness 大负 + contrast 拉 + 突加
    // 适合原图偏浅、全局都要黑一点的场景
    ImagePreset(
        "deepest", "最深", "Floyd · 黑白强化",
        ImageProcessOptions(
            ditherMode = DitherMode.FLOYD_STEINBERG,
            brightness = -20,
            contrast = 1.3f,
            sharpen = 0.5f,
        )
    )
)

/**
 * 应用图像处理选项到灰度图。返回新的 GrayImage,输入 data 不会被修改。
 *
 * 每一步都做浅拷贝 —— 整个管线在 IO 线程跑,内存峰值只是 GrayImage 的两份。
 */
fun processGray(input: GrayImage, opts: ImageProcessOptions): GrayImage {
    var data = input.data
    val total = data.size

    // 1. 自动对比度:直方图两端 1% 截断后线性拉伸到 0..255
    // 截断是必要的 —— 整图偏暗或偏亮时(>5% 像素贴近 0/255),
    // 不截断的话 min/max 就是 0/255,拉伸退化成无操作。
    if (opts.autoContrast) {
        val hist = IntArray(256)
        for (b in data) hist[b.toInt() and 0xFF]++
        val lowCut = (total * 0.01f).toInt().coerceAtLeast(1)
        val highCut = (total * 0.99f).toInt().coerceAtLeast(lowCut + 1)
        var cum = 0
        var lo = 0
        for (i in 0..255) {
            cum += hist[i]
            if (cum >= lowCut) { lo = i; break }
        }
        cum = 0
        var hi = 255
        for (i in 0..255) {
            cum += hist[i]
            if (cum >= highCut) { hi = i; break }
        }
        if (hi > lo) {
            data = data.copyOf()
            val scale = 255f / (hi - lo)
            for (k in 0 until total) {
                val v = (data[k].toInt() and 0xFF)
                val n = ((v - lo) * scale + 0.5f).toInt().coerceIn(0, 255)
                data[k] = n.toByte()
            }
        }
    }

    // 2. Unsharp mask —— 原始减去 3x3 盒模糊后乘 amount 加回,
    //    提升高频细节,二值化后边缘明显。
    //    拉在自动对比度之后、对比度/亮度之前,
    //    让后面的对比度拉伸顺带把突加出来的细节一起拉开。
    if (opts.sharpen > 0f) {
        data = data.copyOf()
        val amount = opts.sharpen
        val w = input.width
        val h = input.height
        val blurred = ByteArray(data.size)
        for (y in 0 until h) {
            val ym = if (y > 0) y - 1 else 0
            val yp = if (y < h - 1) y + 1 else h - 1
            for (x in 0 until w) {
                val xm = if (x > 0) x - 1 else 0
                val xp = if (x < w - 1) x + 1 else w - 1
                val s = (data[y * w + xm].toInt() and 0xFF) +
                        (data[y * w + xp].toInt() and 0xFF) +
                        (data[ym * w + x].toInt() and 0xFF) +
                        (data[yp * w + x].toInt() and 0xFF) +
                        (data[ym * w + xm].toInt() and 0xFF) +
                        (data[ym * w + xp].toInt() and 0xFF) +
                        (data[yp * w + xm].toInt() and 0xFF) +
                        (data[yp * w + xp].toInt() and 0xFF) +
                        (data[y * w + x].toInt() and 0xFF)
                blurred[y * w + x] = (s / 9).toByte()
            }
        }
        for (k in 0 until data.size) {
            val orig = data[k].toInt() and 0xFF
            val blur = blurred[k].toInt() and 0xFF
            val detail = orig - blur
            val n = orig + (detail * amount).toInt()
            data[k] = n.coerceIn(0, 255).toByte()
        }
    }

    // 3 + 4. 对比度 + 亮度
    if (opts.contrast != 1.0f || opts.brightness != 0) {
        data = data.copyOf()
        val c = opts.contrast
        val b = opts.brightness
        for (k in 0 until total) {
            val v = (data[k].toInt() and 0xFF)
            val n = ((v - 128f) * c + 128f).toInt() + b
            data[k] = n.coerceIn(0, 255).toByte()
        }
    }

    // 4. 伽马:预计算 256 项 LUT,O(1) 查表 —— 64k 像素也秒过
    if (opts.gamma != 1.0f) {
        data = data.copyOf()
        val inv = 1.0f / opts.gamma
        val lut = ByteArray(256)
        for (i in 0..255) {
            lut[i] = (255.0 * Math.pow(i / 255.0, inv.toDouble()) + 0.5)
                .toInt().coerceIn(0, 255).toByte()
        }
        for (k in 0 until total) {
            data[k] = lut[data[k].toInt() and 0xFF]
        }
    }

    // 6. 反色
    if (opts.invert) {
        data = data.copyOf()
        for (k in 0 until total) {
            val v = data[k].toInt() and 0xFF
            data[k] = (255 - v).toByte()
        }
    }

    return GrayImage(data, input.width, input.height)
}
