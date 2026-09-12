package com.thisko.qringprint.ui.pages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

internal fun tableLineThickness(thickness: Int): Float = when (thickness.coerceIn(1, 3)) {
    1 -> 2f
    2 -> 3f
    else -> 4f
}

internal fun parseTableRows(raw: String): List<List<String>> {
    return raw.lines()
        .filter { it.isNotBlank() }
        .map { line -> line.split('|', ',', '\t').map { it.trim() } }
}

internal fun renderTableToBitmap(
    rows: List<List<String>>,
    fontSize: Int,
    lineWidth: Float = 2f,
    boldFirstRow: Boolean = true,
): Bitmap {
    val columnCount = rows.maxOfOrNull { it.size } ?: return Bitmap.createBitmap(384, 48, Bitmap.Config.ARGB_8888)
    val normalized = rows.map { row -> (0 until columnCount).map { row.getOrNull(it).orEmpty() } }
    val width = 384
    val margin = 8f
    val tableWidth = width - margin * 2
    val cellPadding = 6f
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = fontSize.coerceIn(12, 30).toFloat()
    }
    val boldPaint = TextPaint(textPaint).apply { isFakeBoldText = true }

    val desiredWidths = (0 until columnCount).map { column ->
        normalized.maxOf { row ->
            val value = row[column]
            if (value.isBlank()) 24f else boldPaint.measureText(value)
        } + cellPadding * 2
    }.map { it.coerceAtLeast(40f) }
    val columnWidths = distributeWidths(desiredWidths, tableWidth)

    val firstRow = normalized.firstOrNull()
    val cellLayouts = normalized.map { row ->
        row.mapIndexed { column, value ->
            val paint = if (boldFirstRow && row === firstRow) boldPaint else textPaint
            textLayout(value, paint, (columnWidths[column] - cellPadding * 2).toInt().coerceAtLeast(16))
        }
    }
    val rowHeights = cellLayouts.map { row -> row.maxOf { it.height } + 14 }

    val tableHeight = rowHeights.sum()
    val bitmap = Bitmap.createBitmap(width, (tableHeight + margin * 2).toInt().coerceAtLeast(32), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = lineWidth
    }
    val top = margin
    val left = margin
    val right = width - margin
    val bottom = top + tableHeight
    var y = top
    cellLayouts.forEachIndexed { rowIndex, row ->
        var x = left
        row.forEachIndexed { columnIndex, layout ->
            canvas.save()
            canvas.translate(x + cellPadding, y + 7f)
            layout.draw(canvas)
            canvas.restore()
            x += columnWidths[columnIndex]
        }
        y += rowHeights[rowIndex]
    }

    canvas.drawLine(left, top, right, top, linePaint)
    canvas.drawLine(left, bottom, right, bottom, linePaint)
    canvas.drawLine(left, top, left, bottom, linePaint)
    canvas.drawLine(right, top, right, bottom, linePaint)

    y = top
    rowHeights.dropLast(1).forEach { height ->
        y += height
        canvas.drawLine(left, y, right, y, linePaint)
    }
    var x = left
    columnWidths.dropLast(1).forEach { columnWidth ->
        x += columnWidth
        canvas.drawLine(x, top, x, bottom, linePaint)
    }
    return bitmap
}

internal fun renderLabelToBitmap(
    title: String,
    content: String,
    fontSize: Int,
    lineWidth: Float = 2f,
): Bitmap {
    val width = 384
    val margin = 36f
    val contentWidth = (width - margin * 2).toInt()
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = (fontSize.coerceIn(14, 30) + 4).toFloat()
        isFakeBoldText = true
    }
    val contentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = fontSize.coerceIn(14, 30).toFloat()
    }
    val titleLayout = textLayout(title.ifBlank { " " }, titlePaint, contentWidth, centered = true)
    val contentLayout = textLayout(content.ifBlank { " " }, contentPaint, contentWidth, centered = true)
    val top = 18f
    val dividerY = top + titleLayout.height + 18f
    val contentY = dividerY + 18f
    val height = (contentY + contentLayout.height + 18f).toInt().coerceAtLeast(64)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    drawLayout(canvas, titleLayout, margin, top)
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    canvas.drawRect(margin, dividerY, width - margin, dividerY + lineWidth.coerceIn(1f, 4f), linePaint)
    drawLayout(canvas, contentLayout, margin, contentY)
    return bitmap
}

internal fun renderTitledTableToBitmap(
    title: String,
    subtitle: String,
    rows: List<List<String>>,
    footer: String,
    fontSize: Int,
    lineWidth: Float = 2f,
): Bitmap {
    val width = 384
    val margin = 10f
    val contentWidth = (width - margin * 2).toInt()
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = (fontSize.coerceIn(12, 30) + 2).toFloat()
        isFakeBoldText = true
    }
    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = fontSize.coerceIn(12, 30).toFloat()
    }
    val titleLayout = title.takeIf { it.isNotBlank() }?.let { textLayout(it, titlePaint, contentWidth, centered = true) }
    val subtitleLayout = subtitle.takeIf { it.isNotBlank() }?.let { textLayout(it, bodyPaint, contentWidth) }
    val footerLayout = footer.takeIf { it.isNotBlank() }?.let { textLayout(it, bodyPaint, contentWidth) }
    val table = renderTableToBitmap(rows, fontSize, lineWidth)
    val gap = 10f

    var height = margin * 2
    titleLayout?.let { height += it.height + gap }
    subtitleLayout?.let { height += it.height + gap }
    height += table.height
    footerLayout?.let { height += gap + it.height }

    val bitmap = Bitmap.createBitmap(width, (height + 2).toInt().coerceAtLeast(48), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    var y = margin

    titleLayout?.let { layout ->
        drawLayout(canvas, layout, margin, y)
        y += layout.height + gap
    }
    subtitleLayout?.let { layout ->
        drawLayout(canvas, layout, margin, y)
        y += layout.height + gap
    }

    // renderTableToBitmap already owns the full 384px print width and its own margins.
    // Drawing it again at x=margin pushed the right border outside the printable bitmap.
    canvas.drawBitmap(table, 0f, y, null)
    y += table.height
    footerLayout?.let { layout ->
        drawLayout(canvas, layout, margin, y + gap)
    }
    return bitmap
}

internal fun renderReceiptToBitmap(
    title: String,
    date: String,
    items: List<ReceiptLine>,
    total: Double,
    payment: String,
    footer: String,
    itemized: Boolean,
    fontSize: Int,
    lineWidth: Float,
): Bitmap {
    val width = 384
    val left = 10f
    val right = width - 6f
    val center = width / 2f
    val bodySize = fontSize.coerceIn(14, 30).toFloat()
    val titleSize = bodySize + 2f

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = titleSize
        typeface = if (itemized) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = bodySize
        typeface = if (itemized) Typeface.DEFAULT else Typeface.MONOSPACE
    }
    val boldPaint = Paint(bodyPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val centerBodyPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.CENTER }
    val rightBodyPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
    val rightBoldPaint = Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = lineWidth.coerceIn(1.5f, 4f)
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(7f, 6f), 0f)
    }

    fun paintHeight(paint: Paint): Float = paint.fontMetrics.descent - paint.fontMetrics.ascent
    fun baselineY(topY: Float, paint: Paint): Float = topY - paint.fontMetrics.ascent

    val titleHeight = paintHeight(titlePaint) + 12f
    val dateHeight = paintHeight(bodyPaint) + 10f
    val separatorHeight = 14f
    val rowCount = if (itemized) items.size + 1 else items.size
    val rowHeight = paintHeight(bodyPaint) + 12f
    val totalHeight = paintHeight(boldPaint) + 10f
    val paymentHeight = if (!itemized) paintHeight(bodyPaint) + 8f else 0f
    val footerHeight = paintHeight(bodyPaint) + 10f
    val extraFooterHeight = if (!itemized) paintHeight(bodyPaint) + 10f else 0f
    val bottomMargin = 12f

    val height = (
        titleHeight + dateHeight + separatorHeight +
            rowCount * rowHeight + separatorHeight +
            totalHeight + paymentHeight + footerHeight + extraFooterHeight + bottomMargin
        ).toInt().coerceAtLeast(64)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    var y = 10f

    canvas.drawText(title, center, baselineY(y, titlePaint), titlePaint)
    y += titleHeight

    canvas.drawText(date, left, baselineY(y, bodyPaint), bodyPaint)
    y += dateHeight

    canvas.drawLine(left, y + separatorHeight / 2f, right, y + separatorHeight / 2f, linePaint)
    y += separatorHeight

    if (itemized) {
        canvas.drawText("商品", left, baselineY(y, boldPaint), boldPaint)
        canvas.drawText("数量", 245f, baselineY(y, boldPaint), centerBodyPaint)
        canvas.drawText("金额", right, baselineY(y, boldPaint), rightBoldPaint)
        y += rowHeight

        items.forEach { item ->
            val amount = (item.quantity.toDoubleOrNull() ?: 0.0) * (item.price.toDoubleOrNull() ?: 0.0)
            canvas.drawText(item.name, left, baselineY(y, bodyPaint), bodyPaint)
            canvas.drawText(item.quantity, 245f, baselineY(y, bodyPaint), centerBodyPaint)
            canvas.drawText("%.2f".format(amount), right, baselineY(y, bodyPaint), rightBodyPaint)
            y += rowHeight
        }
    } else {
        items.forEach { item ->
            val amount = (item.quantity.toDoubleOrNull() ?: 0.0) * (item.price.toDoubleOrNull() ?: 0.0)
            canvas.drawText(item.name, left, baselineY(y, bodyPaint), bodyPaint)
            canvas.drawText("${item.quantity}×${item.price}", 245f, baselineY(y, bodyPaint), centerBodyPaint)
            canvas.drawText("%.2f".format(amount), right, baselineY(y, bodyPaint), rightBodyPaint)
            y += rowHeight
        }
    }

    canvas.drawLine(left, y + separatorHeight / 2f, right, y + separatorHeight / 2f, linePaint)
    y += separatorHeight

    if (itemized) {
        canvas.drawText("合计: %.2f".format(total), left, baselineY(y, boldPaint), boldPaint)
    } else {
        canvas.drawText("合计", left, baselineY(y, boldPaint), boldPaint)
        canvas.drawText("%.2f".format(total), right, baselineY(y, boldPaint), rightBoldPaint)
    }
    y += totalHeight

    if (!itemized) {
        canvas.drawText("支付方式: $payment", left, baselineY(y, bodyPaint), bodyPaint)
        y += paymentHeight
        canvas.drawText(footer, center, baselineY(y, centerBodyPaint), centerBodyPaint)
        y += footerHeight
        canvas.drawText("欢迎再次光临", center, baselineY(y, centerBodyPaint), centerBodyPaint)
        y += extraFooterHeight
    } else {
        canvas.drawText(footer, left, baselineY(y, bodyPaint), bodyPaint)
        y += footerHeight
    }

    return bitmap
}

internal fun schedulePrintRows(periodCount: Int, cells: List<String>): List<List<String>> {
    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val rows = mutableListOf(listOf("节次", *days.toTypedArray()))
    repeat(periodCount) { period ->
        rows += listOf(
            "第${period + 1}节",
            *days.mapIndexed { day, _ -> cells.getOrNull(period * 7 + day)?.ifBlank { "-" } ?: "-" }.toTypedArray(),
        )
    }
    return rows
}

private fun distributeWidths(desired: List<Float>, available: Float): List<Float> {
    val totalDesired = desired.sum().coerceAtLeast(1f)
    val scaled = desired.map { it / totalDesired * available }
    val minimum = 38f
    val fixed = scaled.map { it >= minimum }
    var result = scaled.mapIndexed { index, value -> if (fixed[index]) value else minimum }
    val fixedTotal = result.filterIndexed { index, _ -> fixed[index] }.sum()
    val minimumTotal = result.mapIndexed { index, _ -> if (!fixed[index]) 1 else 0 }.sum() * minimum
    val flexibleAvailable = (available - fixedTotal - minimumTotal).coerceAtLeast(0f)
    val flexibleDesired = desired.filterIndexed { index, _ -> !fixed[index] }.sum().coerceAtLeast(1f)
    result = result.mapIndexed { index, value ->
        if (fixed[index]) value
        else minimum + desired[index] / flexibleDesired * flexibleAvailable
    }
    val difference = available - result.sum()
    val largestIndex = result.indices.maxBy { result[it] }
    return result.mapIndexed { index, value -> if (index == largestIndex) value + difference else value }
}

private fun textLayout(text: String, paint: TextPaint, width: Int, centered: Boolean = false): StaticLayout {
    val safeWidth = width.coerceAtLeast(16)
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
        .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1f)
        .setIncludePad(false)
        .build()
}

private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
}
