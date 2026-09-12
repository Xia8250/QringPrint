package com.thisko.qringprint.model

import com.thisko.qringprint.bluetooth.QringStatus

/**
 * SPP 连接状态机
 */
enum class ConnState {
    DISCONNECTED, CONNECTING, CONNECTED
}

/**
 * 纸仓状态。
 * Qring 状态字节只给一"缺纸"位,没有"有纸"位。
 * 所以这里只有三态。
 */
enum class PaperState { UNKNOWN, OK, EMPTY }

/** 机器状态,来自 10 FF 40 状态字节的各个位 */
enum class HardwareState { UNKNOWN, NORMAL, COVER_OPEN, OVERHEAT, LOW_BATTERY }

/**
 * 打印机全局状态。单一数据源:
 *   写方 —— bluetooth.PrinterConnection
 *   读方 —— view.PrinterStatusCard、各打印页
 * UI 层与蓝牙层不直接互相引用,只通过这个对象通信。
 */
data class PrinterStatus(
    val deviceName: String = "",
    val deviceId: String = "",
    val connState: ConnState = ConnState.DISCONNECTED,
    /**
     * 电池百分比,来自 Qring 私有指令 10 FF 50 F1(响应第 2 字节)。
     *
     * 提:标准 ESC/POS 指令集确实没有电池查询,但这台机器走的是 Qring 私有协议,
     * 协议里有独立的电池命令 —— 所以参考图那个"电池 85%"是拿得到的真实数据。
     * null 表示还没查询到(未连接 / 打印机没回包)。
     */
    val batteryPercent: Int? = null,
    val paperState: PaperState = PaperState.UNKNOWN,
    val hardwareState: HardwareState = HardwareState.UNKNOWN,
    /** 打印机正在出纸 */
    val printing: Boolean = false,
    /** 最近一次错误信息,供设备选择半模态和打印页展示 */
    val lastError: String = "",
    /** 型号 / 固件,连接后查一次,用于"自定义打印"页展示 */
    val model: String = "",
    val firmware: String = "",
)

/**
 * 把协议层解析出的状态位映射给 UI 模型。
 * 返回新的状态(不可变)而不是就地修改。
 */
fun applyQringStatus(current: PrinterStatus, status: QringStatus): PrinterStatus {
    return current.copy(
        printing = status.printing,
        paperState = if (status.noPaper) PaperState.EMPTY else PaperState.OK,
        hardwareState = when {
            status.coverOpen -> HardwareState.COVER_OPEN
            status.overheat -> HardwareState.OVERHEAT
            status.lowBattery -> HardwareState.LOW_BATTERY
            else -> HardwareState.NORMAL
        }
    )
}

// ===== 展示文案映射 ==========================================================

fun connLabel(state: ConnState): String = when (state) {
    ConnState.CONNECTED -> "已连接"
    ConnState.CONNECTING -> "连接中"
    ConnState.DISCONNECTED -> "未连接"
}

fun paperLabel(state: PaperState): String = when (state) {
    PaperState.OK -> "纸仓充足"
    PaperState.EMPTY -> "缺纸"
    PaperState.UNKNOWN -> "纸仓未知"
}

fun hardwareLabel(state: HardwareState): String = when (state) {
    HardwareState.NORMAL -> "正常"
    HardwareState.COVER_OPEN -> "开盖"
    HardwareState.OVERHEAT -> "过热"
    HardwareState.LOW_BATTERY -> "低电量"
    HardwareState.UNKNOWN -> "未知"
}

/** 电池文案 —— 拿不到真值时显示占位符,不编造数字 */
fun batteryLabel(percent: Int?): String =
    if (percent == null) "电量 --" else "电量 $percent%"

/** 指标是否为"未"还是"未知",决定是否降低透明度显示 */
fun isPaperUnknown(state: PaperState): Boolean = state == PaperState.UNKNOWN
fun isHardwareUnknown(state: HardwareState): Boolean = state == HardwareState.UNKNOWN
