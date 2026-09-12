package com.thisko.qringprint.model

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 条码类型与输入约束。
 *
 * 码制清单严格对应 ZXing 库实际存在的枚举值,不是凭印象列的。
 * 排除:
 *  - MULTIFUNCTIONAL_CODE(华为多功能码,内容规则不公开)
 *  - UPC_EAN_EXTENSION(扫描时用的扩展位,不能拿来做生成)
 */
enum class CodeCategory { ONE_D, TWO_D }

data class CodeType(
    val format: BarcodeFormat,
    val label: String,
    val category: CodeCategory,
    /** 输入限制说明,直接展示给用户 */
    val hint: String,
)

val CODE_TYPES: List<CodeType> = listOf(
    // 一维码
    CodeType(BarcodeFormat.EAN_13, "EAN-13", CodeCategory.ONE_D,
        "13 位纯数字(含校验位);也可只输 12 位,由生成器补校验位"),
    CodeType(BarcodeFormat.EAN_8, "EAN-8", CodeCategory.ONE_D,
        "8 位纯数字(含校验位);也可只输 7 位"),
    CodeType(BarcodeFormat.UPC_A, "UPC-A", CodeCategory.ONE_D,
        "12 位纯数字(含校验位);也可只输 11 位"),
    CodeType(BarcodeFormat.UPC_E, "UPC-E", CodeCategory.ONE_D,
        "8 位纯数字,首位必须是 0 或 1;也可只输 6 位数据段"),
    CodeType(BarcodeFormat.ITF, "ITF-14", CodeCategory.ONE_D,
        "14 位纯数字。ITF 按两位一组编码,位数必须是偶数"),
    CodeType(BarcodeFormat.CODE_128, "Code 128", CodeCategory.ONE_D,
        "任意 ASCII 字符(0~127),长度不限。一维码里兼容性最好的选择"),
    CodeType(BarcodeFormat.CODE_39, "Code 39", CodeCategory.ONE_D,
        "数字、大写字母 A~Z,以及 - . $ / + % 和空格"),
    CodeType(BarcodeFormat.CODE_93, "Code 93", CodeCategory.ONE_D,
        "字符集同 Code 39,但编码更紧凑"),
    CodeType(BarcodeFormat.CODABAR, "Codabar", CodeCategory.ONE_D,
        "数字及 - $ : / . +,首尾必须各带一个起止字符 A/B/C/D"),
    // 二维码
    CodeType(BarcodeFormat.QR_CODE, "QR Code", CodeCategory.TWO_D,
        "任意文本,最大约 2953 字节。最通用的二维码"),
    CodeType(BarcodeFormat.DATA_MATRIX, "Data Matrix", CodeCategory.TWO_D,
        "任意文本,最大约 2335 字符。小尺寸下密度高,常用于工业标签"),
    CodeType(BarcodeFormat.PDF_417, "PDF417", CodeCategory.TWO_D,
        "任意文本,最大约 1850 字符。横向长条形,常用于证件"),
    CodeType(BarcodeFormat.AZTEC, "Aztec", CodeCategory.TWO_D,
        "任意文本,最大约 3067 字符。无需静区,常用于票据"),
)

fun typesOf(category: CodeCategory): List<CodeType> =
    CODE_TYPES.filter { it.category == category }

// ===== 输入校验 ==============================================================

private val RE_DIGITS = Regex("^[0-9]+$")
private val RE_CODE39 = Regex("^[0-9A-Z\\-. \\$/+%]+$")
private val RE_CODABAR_BODY = Regex("^[0-9\\-$$:/.+]+$")
private val RE_ASCII = Regex("^[\\x00-\\x7F]+$")

private fun digitsOnly(content: String) = RE_DIGITS.matches(content)

/**
 * 校验用户输入。返回 null 表示通过;返回字符串是给用户看的错误提示。
 *
 * 客户端预校验**不代替** SDK 的校验 —— 它**还**会做长度/字符集之外的规则校验
 * (比如校验位算法),这里只是先把明显错的拦下。最终以生成结果为准,生成失败时仍会把 SDK 的错误显示出来。
 */
fun validateContent(type: CodeType, content: String): String? {
    if (content.isEmpty()) return "请输入要生成的内容"

    when (type.format) {
        BarcodeFormat.EAN_13 -> {
            if (!digitsOnly(content)) return "EAN-13 只能是纯数字"
            if (content.length != 12 && content.length != 13)
                return "EAN-13 需要 12 或 13 位数字,当前 ${content.length} 位"
        }
        BarcodeFormat.EAN_8 -> {
            if (!digitsOnly(content)) return "EAN-8 只能是纯数字"
            if (content.length != 7 && content.length != 8)
                return "EAN-8 需要 7 或 8 位数字,当前 ${content.length} 位"
        }
        BarcodeFormat.UPC_A -> {
            if (!digitsOnly(content)) return "UPC-A 只能是纯数字"
            if (content.length != 11 && content.length != 12)
                return "UPC-A 需要 11 或 12 位数字,当前 ${content.length} 位"
        }
        BarcodeFormat.UPC_E -> {
            if (!digitsOnly(content)) return "UPC-E 只能是纯数字"
            if (content.length != 6 && content.length != 8)
                return "UPC-E 需要 6 或 8 位数字,当前 ${content.length} 位"
            if (content.length == 8 && content[0] != '0' && content[0] != '1')
                return "UPC-E 的 8 位形式首位必须是 0 或 1"
        }
        BarcodeFormat.ITF -> {
            if (!digitsOnly(content)) return "ITF 只能是纯数字"
            if (content.length % 2 != 0)
                return "ITF 按两位一组编码,位数必须是偶数,当前 ${content.length} 位"
            if (content.length != 14)
                return "ITF-14 需要 14 位数字,当前 ${content.length} 位"
        }
        BarcodeFormat.CODE_39, BarcodeFormat.CODE_93 -> {
            if (!RE_CODE39.matches(content))
                return "只能包含数字、大写字母 A~Z,以及 - . $ / + % 和空格"
        }
        BarcodeFormat.CODABAR -> {
            if (content.length < 3)
                return "Codabar 至少需要「起止字符 + 数据 + 结束字符」三位"
            val head = content[0].uppercaseChar()
            val tail = content[content.length - 1].uppercaseChar()
            val valid = "ABCD"
            if (head !in valid || tail !in valid)
                return "Codabar 首尾必须各带一个起止字符 A/B/C/D,例如 A1234A"
            if (!RE_CODABAR_BODY.matches(content.substring(1, content.length - 1)))
                return "Codabar 中间部分只能是数字与 - $ : / . +"
        }
        BarcodeFormat.CODE_128 -> {
            if (!RE_ASCII.matches(content))
                return "Code 128 只支持 ASCII 字符(0~127),不能含中文"
        }
        BarcodeFormat.DATA_MATRIX -> {
            if (content.length > 2335) return "Data Matrix 最大约 2335 字符,当前 ${content.length}"
        }
        BarcodeFormat.PDF_417 -> {
            if (content.length > 1850) return "PDF417 最大约 1850 字符,当前 ${content.length}"
        }
        BarcodeFormat.AZTEC -> {
            if (content.length > 3067) return "Aztec 最大约 3067 字符,当前 ${content.length}"
        }
        BarcodeFormat.QR_CODE, null -> return null
        else -> return null
    }
    return null
}

/** 示例内容,方便快速试打 */
fun sampleContent(type: CodeType): String = when (type.format) {
    BarcodeFormat.EAN_13 -> "6901234567892"
    BarcodeFormat.EAN_8 -> "6901234"
    BarcodeFormat.UPC_A -> "012345678905"
    BarcodeFormat.UPC_E -> "01234565"
    BarcodeFormat.ITF -> "06901234567892"
    BarcodeFormat.CODABAR -> "A12345A"
    BarcodeFormat.CODE_39, BarcodeFormat.CODE_93 -> "QRING-001"
    else -> "https://example.com"
}

/** 生成二维码时调低纠错级别以容纳更多内容,跟原 HarmonyOS 行为一致 */
private fun hintsFor(type: CodeType): Map<EncodeHintType, Any> {
    val hints = mutableMapOf<EncodeHintType, Any>()
    if (type.format == BarcodeFormat.QR_CODE) {
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.L
        hints[EncodeHintType.MARGIN] = 2
    } else {
        hints[EncodeHintType.MARGIN] = 2
    }
    return hints
}

/** 用 ZXing 生成 BitMatrix,后续转灰度图 */
fun encodeBarcodeMatrix(content: String, type: CodeType, size: Int): BitMatrix {
    // PDF417 是堆叠式二维码,标准纵横比约 1:2(高:宽)。
    // 给 384x192 让 ZXing 按数据量自动算列数/行数。
    // 二维码(QR/DataMatrix/Aztec) 和一维码用正方形。
    val (w, h) = when (type.format) {
        BarcodeFormat.PDF_417 -> Pair(size, size / 2)
        else -> Pair(size, size)
    }
    return MultiFormatWriter().encode(content, type.format, w, h, hintsFor(type))
}
