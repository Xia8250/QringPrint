package com.thisko.qringprint.model

import com.thisko.qringprint.bluetooth.DitherMode
import com.thisko.qringprint.bluetooth.DEFAULT_TEXT_OPTIONS
import com.thisko.qringprint.bluetooth.TextRenderOptions
import com.thisko.qringprint.common.codeOneDAspect

/** 元素默认插入尺寸(点) —— 集中在此,跨模块引用 */
const val DEFAULT_IMAGE_WIDTH: Int = 240
const val DEFAULT_CODE_2D_SIZE: Int = 160
const val DEFAULT_CODE_1D_WIDTH: Int = 280

/** 文本元素默认排版参数(从 RasterEncoder 拿默认值) */
val DEFAULT_CANVAS_TEXT_OPTIONS: TextRenderOptions = DEFAULT_TEXT_OPTIONS
