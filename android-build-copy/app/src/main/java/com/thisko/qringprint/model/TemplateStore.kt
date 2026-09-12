package com.thisko.qringprint.model

import android.content.Context
import android.graphics.Bitmap
import com.thisko.qringprint.bluetooth.DitherMode
import com.thisko.qringprint.bluetooth.DEFAULT_TEXT_OPTIONS
import com.thisko.qringprint.bluetooth.TextRenderOptions
import com.thisko.qringprint.common.composeCanvas
import com.thisko.qringprint.common.compositeToBitmap
import com.thisko.qringprint.common.thumbnailBytes
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * 模板持久化。
 *
 * 用 SharedPreferences 存一个 JSON 数组:key = 'template_list',值 = TemplateRecord[]。
 * 每个模板是 CanvasDoc 的快照 —— 元素序列化成本地扁平结构(Android 里手动按字段转),
 * 不用对象展开,加载时重建 CanvasElement 再走渲染管线填回位图。
 *
 * 提:元素里只有"数据"(几何/文字/图片uri/码内容),不含运行时状态
 * (preview 位图、sourceGray 缓存、rendering 标记) —— 那些不可序列化也不该存。
 */
private const val PREF_NAME = "qring_templates"
private const val KEY_LIST = "template_list"

data class TemplateElementData(
    val kind: Int,
    val dotX: Int,
    val dotY: Int,
    val dotW: Int,
    val dotH: Int,
    val aspect: Double,
    // 文字
    val text: String,
    val fontFamily: String,
    val fontSize: Int,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val letterSpacing: Float,
    val lineSpacing: Float,
    val pageMargin: Int,
    // 图片
    val imageUri: String,
    val ditherMode: Int,
    // 条码
    val codeContent: String,
    val codeTypeIndex: Int,
)

data class TemplateRecord(
    val id: String,
    val name: String,
    val minLength: Int,
    val elements: List<TemplateElementData>,
    /** 保存/修改时间戳(ms)。列表显示日期用 */
    val updatedAt: Long,
    /** 缩略图 PNG 文件路径。旧记录可能没有,列表显示占位 */
    val thumbnailPath: String,
)

private fun textOptionsFrom(d: TemplateElementData): TextRenderOptions = TextRenderOptions(
    fontFamily = if (d.fontFamily.isNotEmpty()) d.fontFamily else DEFAULT_TEXT_OPTIONS.fontFamily,
    fontSize = if (d.fontSize > 0) d.fontSize else DEFAULT_TEXT_OPTIONS.fontSize,
    bold = d.bold,
    italic = d.italic,
    underline = d.underline,
    letterSpacing = d.letterSpacing,
    lineSpacing = d.lineSpacing,
    margin = d.pageMargin,
)

/** CanvasDoc → 可序列化的元素数组 */
fun docToTemplateData(doc: CanvasDoc): List<TemplateElementData> = doc.elements.map { el ->
    TemplateElementData(
        kind = el.kind.value,
        dotX = el.dotX, dotY = el.dotY, dotW = el.dotW, dotH = el.dotH,
        aspect = el.aspect,
        text = el.text,
        fontFamily = el.textOptions.fontFamily,
        fontSize = el.textOptions.fontSize,
        bold = el.textOptions.bold,
        italic = el.textOptions.italic,
        underline = el.textOptions.underline,
        letterSpacing = el.textOptions.letterSpacing,
        lineSpacing = el.textOptions.lineSpacing,
        pageMargin = el.textOptions.margin,
        imageUri = el.imageUri,
        ditherMode = el.ditherMode.value,
        codeContent = el.codeContent,
        codeTypeIndex = el.codeTypeIndex,
    )
}

/** 模板元素数据 → CanvasElement 数组(不含位图,需再覆渲) */
fun templateToElements(rec: TemplateRecord): List<CanvasElement> = rec.elements.map { d ->
    val el = CanvasElement(kind = ElementKind.fromInt(d.kind))
    el.dotX = d.dotX
    el.dotY = d.dotY
    el.dotW = d.dotW
    el.dotH = d.dotH
    el.aspect = d.aspect
    // 图片几何来自模板,renderElementNow 不能按真实比例重设
    el.geometryLocked = true
    when (el.kind) {
        ElementKind.TEXT -> {
            el.text = d.text
            el.textOptions = textOptionsFrom(d)
        }
        ElementKind.IMAGE -> {
            el.imageUri = d.imageUri
            el.ditherMode = DitherMode.fromInt(d.ditherMode)
        }
        ElementKind.CODE -> {
            el.codeContent = d.codeContent
            el.codeTypeIndex = d.codeTypeIndex
        }
        ElementKind.LINE -> { /* LINE 元素无文字/图片/条码字段,模板还原即位置+尺寸足够 */ }
    }
    el
}

private fun readAll(ctx: Context): List<TemplateRecord> {
    return try {
        val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LIST, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val result = mutableListOf<TemplateRecord>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val elements = mutableListOf<TemplateElementData>()
            val elArr = o.optJSONArray("elements") ?: JSONArray()
            for (j in 0 until elArr.length()) {
                val e = elArr.getJSONObject(j)
                elements.add(
                    TemplateElementData(
                        kind = e.optInt("kind"),
                        dotX = e.optInt("dotX"),
                        dotY = e.optInt("dotY"),
                        dotW = e.optInt("dotW"),
                        dotH = e.optInt("dotH"),
                        aspect = e.optDouble("aspect", 1.0),
                        text = e.optString("text", ""),
                        fontFamily = e.optString("fontFamily", ""),
                        fontSize = e.optInt("fontSize"),
                        bold = e.optBoolean("bold", false),
                        italic = e.optBoolean("italic", false),
                        underline = e.optBoolean("underline", false),
                        letterSpacing = e.optDouble("letterSpacing", 0.0).toFloat(),
                        lineSpacing = e.optDouble("lineSpacing", 0.0).toFloat(),
                        pageMargin = e.optInt("pageMargin"),
                        imageUri = e.optString("imageUri", ""),
                        ditherMode = e.optInt("ditherMode"),
                        codeContent = e.optString("codeContent", ""),
                        codeTypeIndex = e.optInt("codeTypeIndex"),
                    )
                )
            }
            result.add(
                TemplateRecord(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    minLength = o.optInt("minLength"),
                    elements = elements,
                    updatedAt = o.optLong("updatedAt"),
                    thumbnailPath = o.optString("thumbnailPath", ""),
                )
            )
        }
        result
    } catch (err: Throwable) { emptyList() }
}

private fun writeAll(ctx: Context, records: List<TemplateRecord>) {
    val arr = JSONArray()
    for (r in records) {
        val o = JSONObject()
        o.put("id", r.id)
        o.put("name", r.name)
        o.put("minLength", r.minLength)
        o.put("updatedAt", r.updatedAt)
        o.put("thumbnailPath", r.thumbnailPath)
        val elArr = JSONArray()
        for (e in r.elements) {
            val eo = JSONObject()
            eo.put("kind", e.kind)
            eo.put("dotX", e.dotX)
            eo.put("dotY", e.dotY)
            eo.put("dotW", e.dotW)
            eo.put("dotH", e.dotH)
            eo.put("aspect", e.aspect)
            eo.put("text", e.text)
            eo.put("fontFamily", e.fontFamily)
            eo.put("fontSize", e.fontSize)
            eo.put("bold", e.bold)
            eo.put("italic", e.italic)
            eo.put("underline", e.underline)
            eo.put("letterSpacing", e.letterSpacing.toDouble())
            eo.put("lineSpacing", e.lineSpacing.toDouble())
            eo.put("pageMargin", e.pageMargin)
            eo.put("imageUri", e.imageUri)
            eo.put("ditherMode", e.ditherMode)
            eo.put("codeContent", e.codeContent)
            eo.put("codeTypeIndex", e.codeTypeIndex)
            elArr.put(eo)
        }
        o.put("elements", elArr)
        arr.put(o)
    }
    val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_LIST, arr.toString()).apply()
}

/**
 * 保存模板。id 为空则新建,否则覆盖同名 id(编辑已有模板后保存 = 更新)。
 */
suspend fun saveTemplate(
    ctx: Context,
    name: String,
    doc: CanvasDoc,
    id: String? = null,
): TemplateRecord {
    val records = readAll(ctx).toMutableList()
    // 生成缩略图。失败不阻断保存(缩略图只是列表装饰,列表会显示占位)
    var thumb = ""
    try {
        val bytes = thumbnailBytes(doc)
        if (bytes != null) {
            val file = File(ctx.filesDir, "tpl_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { it.write(bytes) }
            thumb = file.absolutePath
        }
    } catch (err: Throwable) { thumb = "" }
    val rec = TemplateRecord(
        id = if (!id.isNullOrEmpty()) id else "t${System.currentTimeMillis()}",
        name = name,
        minLength = doc.minLength,
        elements = docToTemplateData(doc),
        updatedAt = System.currentTimeMillis(),
        thumbnailPath = thumb,
    )
    records.removeAll { it.id == rec.id }
    // 最新的排最前,后保存的显示在前面。编辑已有模板 = 覆盖原位置
    records.add(0, rec)
    writeAll(ctx, records)
    return rec
}

suspend fun loadTemplates(ctx: Context): List<TemplateRecord> {
    // 按更新时间倒序:后保存/后修改的排最前
    return readAll(ctx).sortedByDescending { it.updatedAt }
}

suspend fun findTemplate(ctx: Context, id: String): TemplateRecord? =
    readAll(ctx).firstOrNull { it.id == id }

suspend fun deleteTemplate(ctx: Context, id: String) {
    val records = readAll(ctx)
    val kept = mutableListOf<TemplateRecord>()
    for (r in records) {
        if (r.id == id) {
            // 顺手删掉缩略图文件,避免堆叠
            if (r.thumbnailPath.isNotEmpty()) {
                try { File(r.thumbnailPath).delete() } catch (_: Throwable) {}
            }
        } else {
            kept.add(r)
        }
    }
    writeAll(ctx, kept)
}

suspend fun renameTemplate(ctx: Context, id: String, newName: String) {
    val records = readAll(ctx).map { r ->
        if (r.id == id) r.copy(name = newName, updatedAt = System.currentTimeMillis()) else r
    }
    writeAll(ctx, records)
}
