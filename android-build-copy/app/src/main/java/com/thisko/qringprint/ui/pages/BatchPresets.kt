package com.thisko.qringprint.ui.pages

import com.thisko.qringprint.bluetooth.DitherMode
import com.thisko.qringprint.bluetooth.IMAGE_PRESETS
import com.thisko.qringprint.bluetooth.ImagePreset
import com.thisko.qringprint.bluetooth.ImageProcessOptions
import com.thisko.qringprint.bluetooth.TextAlign
import com.thisko.qringprint.bluetooth.TextRenderOptions

/**
 * 批量打印 · 5 种任务类型各自的预设模板。
 *
 * 设计原则:
 *   - key 是稳定标识,作为持久化/历史记录的引用;
 *   - 每个预设同时给出 label(显示名)和 hint(一句话说明),
 *     渲染时让用户看出「这个预设大概会把图变成什么样」。
 *   - IMAGE 复用 ImageProcess 的 IMAGE_PRESETS(它已经覆盖照片/文档/票据等);
 *   - TEXT / TABLE / RECEIPT / FILE 各有自己的一套;
 *   - FILE 走 TEXT 预设(每行是一条 text job,渲染参数与 TEXT 一致)。
 *
 * 用户在「添加任务」对话框里选预设 → 写进 BatchTask.presetKey + 任务自己的 options;
 * 任务列表上点一项可以再次进入「编辑任务」,重选预设 / 改浓度。
 */
data class BatchTaskPreset(
    val key: String,
    val label: String,
    val hint: String,
)

/** IMAGE —— 直接复用 ImageProcess.kt 里的 IMAGE_PRESETS 列表(7 个预设) */
val BATCH_IMAGE_PRESETS: List<Pair<BatchTaskPreset, ImagePreset>> =
    IMAGE_PRESETS.map { p ->
        BatchTaskPreset(p.key, p.label, p.hint) to p
    }

/** TEXT —— 5 个常用排版风格 */
val BATCH_TEXT_PRESETS: List<Pair<BatchTaskPreset, TextRenderOptions>> = listOf(
    Pair(
        BatchTaskPreset("standard", "标准", "24 号无衬线 · 6 点行距"),
        TextRenderOptions(fontFamily = "sans-serif", fontSize = 24, lineSpacing = 6f, align = TextAlign.LEFT),
    ),
    Pair(
        BatchTaskPreset("compact", "紧凑", "18 号 · 2 点行距 · 一次打更多"),
        TextRenderOptions(fontFamily = "sans-serif", fontSize = 18, lineSpacing = 2f, align = TextAlign.LEFT),
    ),
    Pair(
        BatchTaskPreset("title", "标题", "36 号加粗 · 居中"),
        TextRenderOptions(fontFamily = "sans-serif", fontSize = 36, bold = true, lineSpacing = 8f, align = TextAlign.CENTER),
    ),
    Pair(
        BatchTaskPreset("mono", "等宽", "22 号等宽 · 4 点行距 · 对齐代码/编号"),
        TextRenderOptions(fontFamily = "monospace", fontSize = 22, lineSpacing = 4f, align = TextAlign.LEFT),
    ),
    Pair(
        BatchTaskPreset("draft", "草稿", "14 号 · 2 点行距 · 印得快"),
        TextRenderOptions(fontFamily = "sans-serif", fontSize = 14, lineSpacing = 2f, align = TextAlign.LEFT),
    ),
)

/** TABLE —— 4 个密度/字号组合 */
data class TablePreset(
    val fontSize: Int,
    val lineWidth: Int,
    val boldFirstRow: Boolean = true,
)
val BATCH_TABLE_PRESETS: List<Pair<BatchTaskPreset, TablePreset>> = listOf(
    Pair(
        BatchTaskPreset("table_standard", "标准", "18 号 · 中线宽 · 表头加粗"),
        TablePreset(fontSize = 18, lineWidth = 2, boldFirstRow = true),
    ),
    Pair(
        BatchTaskPreset("table_compact", "紧凑", "14 号 · 细线 · 行高更小"),
        TablePreset(fontSize = 14, lineWidth = 1, boldFirstRow = true),
    ),
    Pair(
        BatchTaskPreset("table_spacious", "宽松", "22 号 · 粗线 · 易撕读"),
        TablePreset(fontSize = 22, lineWidth = 3, boldFirstRow = true),
    ),
    Pair(
        BatchTaskPreset("table_plain", "无表头", "16 号 · 中线 · 表头不加粗"),
        TablePreset(fontSize = 16, lineWidth = 2, boldFirstRow = false),
    ),
)

/** RECEIPT —— 3 个常用样式 */
data class ReceiptPreset(
    val fontSize: Int,
    val lineWidth: Int,
    val itemized: Boolean,
)
val BATCH_RECEIPT_PRESETS: List<Pair<BatchTaskPreset, ReceiptPreset>> = listOf(
    Pair(
        BatchTaskPreset("receipt_standard", "标准", "20 号 · 中线 · 明细展开"),
        ReceiptPreset(fontSize = 20, lineWidth = 2, itemized = true),
    ),
    Pair(
        BatchTaskPreset("receipt_minimal", "极简", "18 号 · 细线 · 只看总额"),
        ReceiptPreset(fontSize = 18, lineWidth = 1, itemized = false),
    ),
    Pair(
        BatchTaskPreset("receipt_large", "大字", "24 号 · 粗线 · 老人/食堂"),
        ReceiptPreset(fontSize = 24, lineWidth = 3, itemized = true),
    ),
)

/** FILE(CSV/Excel)—— 走 TEXT 预设,逻辑完全一致,只换 label 让用户能区分 */
val BATCH_FILE_PRESETS: List<Pair<BatchTaskPreset, TextRenderOptions>> = listOf(
    Pair(
        BatchTaskPreset("file_standard", "标准列表", "20 号无衬线 · 4 点行距"),
        TextRenderOptions(fontFamily = "sans-serif", fontSize = 20, lineSpacing = 4f, align = TextAlign.LEFT),
    ),
    Pair(
        BatchTaskPreset("file_compact", "紧凑列表", "14 号 · 2 点行距 · 一行多条"),
        TextRenderOptions(fontFamily = "sans-serif", fontSize = 14, lineSpacing = 2f, align = TextAlign.LEFT),
    ),
    Pair(
        BatchTaskPreset("file_mono", "等宽列表", "18 号等宽 · 字段对齐"),
        TextRenderOptions(fontFamily = "monospace", fontSize = 18, lineSpacing = 3f, align = TextAlign.LEFT),
    ),
    Pair(
        BatchTaskPreset("file_label", "大标签", "26 号加粗 · 6 点行距"),
        TextRenderOptions(fontFamily = "sans-serif", fontSize = 26, bold = true, lineSpacing = 6f, align = TextAlign.LEFT),
    ),
)

/** 拿任务当前预设的 label(用于列表上显示「当前用的模板」) */
fun presetLabelOf(type: BatchType, task: BatchTask): String {
    val list: List<Pair<BatchTaskPreset, *>> = when (type) {
        BatchType.IMAGE -> BATCH_IMAGE_PRESETS
        BatchType.TEXT -> BATCH_TEXT_PRESETS
        BatchType.FILE -> BATCH_FILE_PRESETS
        BatchType.TABLE -> BATCH_TABLE_PRESETS
        BatchType.RECEIPT -> BATCH_RECEIPT_PRESETS
    }
    val k = task.presetKey.ifEmpty { list.first().first.key }
    return list.firstOrNull { it.first.key == k }?.first?.label ?: list.first().first.label
}

/** 某类型的预设列表(只取 BatchTaskPreset 部分,给对话框选模板用) */
fun presetListOf(type: BatchType): List<BatchTaskPreset> = when (type) {
    BatchType.IMAGE -> BATCH_IMAGE_PRESETS.map { it.first }
    BatchType.TEXT -> BATCH_TEXT_PRESETS.map { it.first }
    BatchType.TABLE -> BATCH_TABLE_PRESETS.map { it.first }
    BatchType.RECEIPT -> BATCH_RECEIPT_PRESETS.map { it.first }
    BatchType.FILE -> BATCH_FILE_PRESETS.map { it.first }
}

/**
 * 把选中的预设 + 浓度 + 份数落到任务上(拷贝返回,不改原对象)。
 * 添加和编辑共用这一条路径,保证列表里看到的参数 = 打印时真正用的参数。
 */
fun BatchTask.withPreset(presetKey: String, thickness: Int, copies: Int): BatchTask {
    val key = presetKey.ifEmpty { presetListOf(type).first().key }
    return when (type) {
        BatchType.IMAGE -> {
            val p = BATCH_IMAGE_PRESETS.firstOrNull { it.first.key == key } ?: BATCH_IMAGE_PRESETS.first()
            copy(presetKey = p.first.key, imageOptions = p.second.options, thickness = thickness, jobCount = copies)
        }
        BatchType.TEXT -> {
            val p = BATCH_TEXT_PRESETS.firstOrNull { it.first.key == key } ?: BATCH_TEXT_PRESETS.first()
            copy(presetKey = p.first.key, textOptions = p.second, thickness = thickness, jobCount = copies)
        }
        BatchType.FILE -> {
            // FILE 的份数由表格行数决定,不手动改
            val p = BATCH_FILE_PRESETS.firstOrNull { it.first.key == key } ?: BATCH_FILE_PRESETS.first()
            copy(presetKey = p.first.key, textOptions = p.second, thickness = thickness)
        }
        BatchType.TABLE -> {
            val p = BATCH_TABLE_PRESETS.firstOrNull { it.first.key == key } ?: BATCH_TABLE_PRESETS.first()
            copy(
                presetKey = p.first.key,
                tableFontSize = p.second.fontSize,
                tableLineWidth = p.second.lineWidth,
                tableBoldFirstRow = p.second.boldFirstRow,
                thickness = thickness, jobCount = copies,
            )
        }
        BatchType.RECEIPT -> {
            val p = BATCH_RECEIPT_PRESETS.firstOrNull { it.first.key == key } ?: BATCH_RECEIPT_PRESETS.first()
            copy(
                presetKey = p.first.key,
                receiptFontSize = p.second.fontSize,
                receiptLineWidth = p.second.lineWidth,
                receiptItemized = p.second.itemized,
                thickness = thickness, jobCount = copies,
            )
        }
    }
}

/** 把 DEFAULT 状态封装出来,集中默认值 */
object BatchDefaults {
    const val THICKNESS: Int = 1

    fun imageOptions(): ImageProcessOptions = IMAGE_PRESETS.first().options
    fun textOptions(): TextRenderOptions = BATCH_TEXT_PRESETS.first().second
    fun tablePreset(): TablePreset = BATCH_TABLE_PRESETS.first().second
    fun receiptPreset(): ReceiptPreset = BATCH_RECEIPT_PRESETS.first().second
    fun fileOptions(): TextRenderOptions = BATCH_FILE_PRESETS.first().second
}
