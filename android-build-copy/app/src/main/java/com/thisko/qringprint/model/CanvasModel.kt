package com.thisko.qringprint.model

import android.graphics.Bitmap
import com.thisko.qringprint.bluetooth.DitherMode
import com.thisko.qringprint.bluetooth.DEFAULT_TEXT_OPTIONS
import com.thisko.qringprint.bluetooth.TextRenderOptions
import com.thisko.qringprint.bluetooth.WIDTH_DOTS

/**
 * 自定义画布的文档模型。
 *
 * 坐标单位一律是**打印点**(1 点 = 1/8 mm),画布固定 384 点宽 —— 和打印头一致。
 * 屏幕上再按"显示宽度/384"缩放显示。所有几何量都存点数,
 * 这样合成时不用做任何单位换算,拖拽产生的 vp 位移在视图层除以缩放比即可。
 *
 * 用"单个类 + kind 判别"而不是"继承 ArkTS 那种 @ObservedV2/@Trace 配合类继承" —— Kotlin 中
 * 元素种类就三种、字段也不多,搞个扁平结构反而更省事。
 */
enum class ElementKind(val value: Int) {
    TEXT(0), IMAGE(1), CODE(2), LINE(3);

    companion object {
        fun fromInt(v: Int): ElementKind = values().firstOrNull { it.value == v } ?: TEXT
    }
}

/** 画布底部留白(点),避免内容抵着切纸刀 */
const val CANVAS_BOTTOM_PAD: Int = 16
/** 画布最小长度的可调范围 */
const val MIN_LENGTH_FLOOR: Int = 80
const val MIN_LENGTH_CEIL: Int = 1200
/**
 * 画布高度硬上限。
 * 协议里高度是 16 位字段(65535)但**没有校验**,超了会静默回绕成一个小数字,
 * 打出来是一堆乱码。真正的瓶颈其实是 ACK 超时 120s,所以这里取一个远小于二者的保守值。
 */
const val MAX_CANVAS_HEIGHT: Int = 4000

/** 元素最小尺寸(点),再小就没法拖了 */
const val MIN_ELEMENT_SIZE: Int = 24

private var nextIdCounter: Int = 1
private fun nextId(): String = "e${System.currentTimeMillis()}_${nextIdCounter++}"

/**
 * 画布元素。
 *
 * 几何量用 dotX/dotY/dotW/dotH 而不是 x/y/width/height —— 一是标明单位是打印点,
 * 二是避开 width/height 这种名字(万一以后要扔进组件里,会和 Component 的属性方法撞)。
 */
data class CanvasElement(
    val id: String = nextId(),
    val kind: ElementKind,
    var dotX: Int = 0,
    var dotY: Int = 0,
    var dotW: Int = 0,
    var dotH: Int = 0,
    /** 原始宽高比(w/h)。锁定比例缩放时按它推导高度 */
    var aspect: Double = 1.0,
    /**
     * 图片几何是否已锁定。
     * 新插入的图片首次解码后要按真实比例重设(见 renderElementNow);
     * 从模板加载的图片几何来自保存的数据,不能被解码步骤再覆盖。
     */
    var geometryLocked: Boolean = false,
    /** 屏幕显示用的位图。**必须在删除元素和退页时 release**,这是多元件场景最大的释放点 */
    var preview: Bitmap? = null,
    /** 合成用的二值位图(1 = 黑,长度 dotW * dotH)。null 表示还没渲染出来 */
    var binary: ByteArray? = null,
    /** 渲染中标记,用于显示转圈 */
    var rendering: Boolean = false,
    // 文字元素
    var text: String = "",
    var textOptions: TextRenderOptions = DEFAULT_TEXT_OPTIONS,
    // 图片元素
    var imageUri: String = "",
    var ditherMode: DitherMode = DitherMode.FLOYD_STEINBERG,
    /** 解码后的灰度缓存(384 宽)。缩放和换抖动算法都从它重算,不必重新解码 —— 手机照片解全尺寸要几十 MB。 */
    var sourceGray: com.thisko.qringprint.bluetooth.GrayImage? = null,
    // 条码元素
    var codeContent: String = "",
    var codeTypeIndex: Int = 0,
) {
    fun codeType(): CodeType {
        return if (codeTypeIndex in 0 until CODE_TYPES.size) CODE_TYPES[codeTypeIndex] else CODE_TYPES[0]
    }

    fun release() {
        preview = null
        binary = null
        sourceGray = null
    }
}

/** 画布文档 */
data class CanvasDoc(
    var elements: List<CanvasElement> = emptyList(),
    /** 最小长度(点)。内容不足这么长时按它出纸,方便固定长度的标签 */
    var minLength: Int = 200,
    /** 当前选中的元素 id,空串表示没选中 */
    var selectedId: String = "",
) {
    fun add(element: CanvasElement): CanvasDoc = copy(elements = elements + element, selectedId = element.id)

    fun remove(id: String): CanvasDoc { elements.firstOrNull { it.id == id }?.release(); return copy(elements = elements.filterNot { it.id == id }, selectedId = if (selectedId == id) "" else selectedId) }

    /** 置顶:移到数组末尾。合成按数组顺序 blit,后面的盖前面的,所以置顶 = 盖住其他元素 */
    fun toTop(id: String): CanvasDoc { val idx = elements.indexOfFirst { it.id == id }; if (idx < 0) return this; val list = elements.toMutableList(); val el = list.removeAt(idx); list.add(el); return copy(elements = list) }

    /** 置底:移到数组开头。被其他元素盖住 */
    fun toBottom(id: String): CanvasDoc { val idx = elements.indexOfFirst { it.id == id }; if (idx < 0) return this; val list = elements.toMutableList(); val el = list.removeAt(idx); list.add(0, el); return copy(elements = list) }

    fun find(id: String): CanvasElement? = elements.firstOrNull { it.id == id }

    fun selected(): CanvasElement? = if (selectedId.isNotEmpty()) find(selectedId) else null

    fun releaseAll() {
        for (el in elements) el.release()
        elements = emptyList()
        selectedId = ""
    }

    /**
     * 画布当前长度(点)。
     * 取"最靠下元素的底边 + 留白"和"最小长度"中的大者,所以拖到底部会自动拉长。
     */
    fun height(): Int {
        val bottom = elements.maxOfOrNull { it.dotY + it.dotH } ?: 0
        val fitted = if (bottom > 0) bottom + CANVAS_BOTTOM_PAD else 0
        return minOf(MAX_CANVAS_HEIGHT, maxOf(minLength, fitted))
    }

    /**
     * 内容实际高度(点):最靠下元素的底边 + 留白,**不含最小长度**。
     *
     * 打印用这个而不是 height():height() 会按视口高度把空白也卷进去,
     * 内容不满一屏时打印会白纸扔出一大段白纸。
     * 编辑器里的画布显示仍用 height()(留出可拖拽的工作区)。
     */
    fun contentHeight(): Int {
        val bottom = elements.maxOfOrNull { it.dotY + it.dotH } ?: 0
        return if (bottom > 0) minOf(MAX_CANVAS_HEIGHT, bottom + CANVAS_BOTTOM_PAD) else 0
    }

    /** 内容是否已经超出高度上限被截断? */
    fun overflowed(): Boolean {
        val bottom = elements.maxOfOrNull { it.dotY + it.dotH } ?: 0
        return bottom + CANVAS_BOTTOM_PAD > MAX_CANVAS_HEIGHT
    }
}

/** 新元素的默认落点:横向居中,纵向接在现有内容下方,免得叠加在一起 */
fun nextInsertY(doc: CanvasDoc): Int {
    val bottom = doc.elements.maxOfOrNull { it.dotY + it.dotH } ?: 0
    return if (bottom > 0) bottom + 8 else 8
}

fun centeredX(dotW: Int): Int = maxOf(0, ((WIDTH_DOTS - dotW) / 2))

