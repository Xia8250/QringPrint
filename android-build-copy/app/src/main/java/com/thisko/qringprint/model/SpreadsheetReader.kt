package com.thisko.qringprint.model

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import java.io.InputStreamReader
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

data class SpreadsheetData(
    val headers: List<String>,
    val rows: List<List<String>>,
)

object SpreadsheetReader {
    fun read(context: Context, uri: Uri, fileName: String): SpreadsheetData {
        return if (fileName.endsWith(".xlsx", ignoreCase = true)) {
            readXlsx(context, uri)
        } else {
            readCsv(context, uri)
        }
    }

    private fun readCsv(context: Context, uri: Uri): SpreadsheetData {
        val all: List<Array<String>> = context.contentResolver.openInputStream(uri)?.use { stream ->
            InputStreamReader(stream, Charset.forName("GBK")).use { reader ->
                parseCsv(reader.readText())
            }
        } ?: throw IllegalArgumentException("无法读取文件")
        return SpreadsheetData(
            headers = all.getOrNull(0)?.toList() ?: emptyList(),
            rows = all.drop(1).map { it.toList() },
        )
    }

    /**
     * 极简 CSV 解析器。支持引号、双引号转义、CRLF/LF 换行。
     * 不依赖 opencsv,避免外部 jar 不可用的闪退风险。
     */
    private fun parseCsv(text: String): List<Array<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        var cell = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < n && text[i + 1] == '"' -> { cell.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> cell.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row.add(cell.toString()); cell = StringBuilder() }
                c == '\r' -> { /* skip, handle on \n */ }
                c == '\n' -> { row.add(cell.toString()); rows.add(row); row = mutableListOf(); cell = StringBuilder() }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows.map { it.toTypedArray() }
    }

    private fun readXlsx(context: Context, uri: Uri): SpreadsheetData {
        val sharedStrings = context.contentResolver.openInputStream(uri)?.use { loadSharedStrings(it) } ?: emptyList()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                var worksheetName: String? = null
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "xl/worksheets/sheet1.xml") worksheetName = entry.name
                    if (worksheetName == null && entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml")) {
                        worksheetName = entry.name
                    }
                }
                if (worksheetName == null) throw IllegalArgumentException("Excel 中没有工作表")
            }
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name != "xl/worksheets/sheet1.xml" && !entry.name.startsWith("xl/worksheets/")) continue
                    if (!entry.name.endsWith(".xml")) continue
                    val rows = parseWorksheet(zip, sharedStrings)
                    return SpreadsheetData(headers = rows.firstOrNull().orEmpty(), rows = rows.drop(1))
                }
            }
        }
        throw IllegalArgumentException("无法解析 Excel")
    }

    private fun loadSharedStrings(stream: InputStream): List<String> {
        val parser = android.util.Xml.newPullParser()
        parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)
        val values = mutableListOf<String>()
        var current = StringBuilder()
        var insideItem = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> if (parser.name == "si") {
                    insideItem = true
                    current.clear()
                }
                XmlPullParser.TEXT -> if (insideItem) current.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") {
                    values += current.toString()
                    insideItem = false
                }
            }
            parser.next()
        }
        return values
    }

    private fun parseWorksheet(stream: InputStream, sharedStrings: List<String>): List<List<String>> {
        val parser = android.util.Xml.newPullParser()
        parser.setInput(stream, null)
        val rows = mutableListOf<MutableList<String>>()
        var row: MutableList<String>? = null
        var cellText = StringBuilder()
        var cellReference = ""
        var cellType = ""
        var insideCell = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        row = mutableListOf()
                        rows += row
                    }
                    "c" -> {
                        insideCell = true
                        cellReference = parser.getAttributeValue(null, "r").orEmpty()
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        cellText.clear()
                    }
                }
                XmlPullParser.TEXT -> if (insideCell) cellText.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> {
                        val targetRow = row ?: mutableListOf<String>().also { row = it; rows += it }
                        val value = when (cellType) {
                            "s" -> sharedStrings.getOrNull(cellText.toString().toIntOrNull() ?: -1).orEmpty()
                            else -> cellText.toString()
                        }
                        repeat(columnIndex(cellReference) - targetRow.size) { targetRow.add("") }
                        targetRow.add(value)
                        insideCell = false
                    }
                    "row" -> row = null
                }
            }
            parser.next()
        }
        return rows.filter { it.any { value -> value.isNotBlank() } }.map { row -> row.toList() }
    }

    private fun columnIndex(reference: String): Int {
        val letters = reference.takeWhile(Char::isLetter).uppercase()
        if (letters.isEmpty()) return 0
        return letters.fold(0) { acc, letter -> acc * 26 + (letter - 'A' + 1) } - 1
    }
}
