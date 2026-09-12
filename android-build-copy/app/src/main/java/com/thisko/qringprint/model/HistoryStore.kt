package com.thisko.qringprint.model

import android.content.Context
import android.graphics.Bitmap
import com.thisko.qringprint.bluetooth.GrayImage
import com.thisko.qringprint.bluetooth.scaleGrayNearest
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * 打印历史。
 *
 * 复用模板的持久化模式:SharedPreferences 存 JSON 数组,读取/写入/删除同一套。
 * 每条记录 = 打印类型名称 + 缩略图 + 时间戳。
 * 打印成功时记录一次,最多保留 MAX_RECORDS 条(超出删最旧的)。
 */
private const val PREF_NAME = "qring_history"
private const val KEY_LIST = "history_list"
/** 最多保留的打印记录数 */
const val MAX_RECORDS: Int = 20

data class HistoryRecord(
    val id: String,
    /** 打印类型名称,如「图片打印」「文字打印」「条码打印」「自定义打印」 */
    val typeName: String,
    /** 缩略图文件路径 */
    val thumbnailPath: String,
    /** 打印时间戳(ms) */
    val printedAt: Long,
    /** 打印内容快照(JSON 字符串),点记录重新打印时恢复 */
    val payload: String,
)

/** 各打印类型的路由名 —— 历史页跳转用 */
const val HIST_TYPE_TEXT = "文字打印"
const val HIST_TYPE_IMAGE = "图片打印"
const val HIST_TYPE_CODE = "条码打印"
const val HIST_TYPE_CUSTOM = "自定义打印"
const val HIST_TYPE_AD_BANNER = "广告横带"

private fun readAll(ctx: Context): List<HistoryRecord> {
    return try {
        val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LIST, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val result = mutableListOf<HistoryRecord>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            result.add(
                HistoryRecord(
                    id = o.optString("id"),
                    typeName = o.optString("typeName"),
                    thumbnailPath = o.optString("thumbnailPath", ""),
                    printedAt = o.optLong("printedAt"),
                    payload = o.optString("payload", ""),
                )
            )
        }
        result
    } catch (err: Throwable) { emptyList() }
}

private fun writeAll(ctx: Context, records: List<HistoryRecord>) {
    val arr = JSONArray()
    for (r in records) {
        val o = JSONObject()
        o.put("id", r.id)
        o.put("typeName", r.typeName)
        o.put("thumbnailPath", r.thumbnailPath)
        o.put("printedAt", r.printedAt)
        o.put("payload", r.payload)
        arr.put(o)
    }
    val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_LIST, arr.toString()).apply()
}

/**
 * 把 GrayImage 编码成缩略图 PNG 写盘,返回文件路径。
 * 缩略图失败返回空串(记录仍会保存,列表显示占位)。
 */
private fun encodeThumb(g: GrayImage, ctx: Context): String {
    return try {
        val maxW = 300
        val ratio = minOf(1f, maxW.toFloat() / g.width)
        val tw = maxOf(1, (g.width * ratio).toInt())
        val th = maxOf(1, (g.height * ratio).toInt())
        val scaled = scaleGrayNearest(g, tw, th)
        val pixels = IntArray(scaled.width * scaled.height)
        for (i in 0 until scaled.width * scaled.height) {
            val v = scaled.data[i].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val bitmap = Bitmap.createBitmap(pixels, scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val file = File(ctx.filesDir, "hist_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        bitmap.recycle()
        file.absolutePath
    } catch (err: Throwable) { "" }
}

suspend fun addHistoryGray(
    ctx: Context,
    typeName: String,
    g: GrayImage,
    payload: String,
) {
    val records = readAll(ctx).toMutableList()
    val thumbPath = encodeThumb(g, ctx)
        var finalPayload = payload
    if (typeName == HIST_TYPE_IMAGE && thumbPath.isNotEmpty()) {
        finalPayload = thumbPath + "|" + payload
    }
val rec = HistoryRecord(
        id = "h${System.currentTimeMillis()}",
        typeName = typeName,
        thumbnailPath = thumbPath,
        printedAt = System.currentTimeMillis(),
        payload = finalPayload,
    )
    val next = listOf(rec) + records
    val capped = next.take(MAX_RECORDS)
    writeAll(ctx, capped)
}

suspend fun addHistory(
    ctx: Context,
    typeName: String,
    bitmap: Bitmap,
    payload: String,
) {
    val records = readAll(ctx).toMutableList()
    val thumbPath = try {
        val file = File(ctx.filesDir, "hist_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        file.absolutePath
    } catch (err: Throwable) { "" }
    val rec = HistoryRecord(
        id = "h${System.currentTimeMillis()}",
        typeName = typeName,
        thumbnailPath = thumbPath,
        printedAt = System.currentTimeMillis(),
        payload = payload,
    )
    val next = listOf(rec) + records
    val capped = next.take(MAX_RECORDS)
    writeAll(ctx, capped)
}

suspend fun loadHistory(ctx: Context): List<HistoryRecord> = readAll(ctx)

suspend fun deleteHistory(ctx: Context, id: String) {
    val records = readAll(ctx)
    val kept = records.filterNot { r ->
        if (r.id == id) {
            if (r.thumbnailPath.isNotEmpty()) {
                try { File(r.thumbnailPath).delete() } catch (_: Throwable) {}
            }
            true
        } else false
    }
    writeAll(ctx, kept)
}
