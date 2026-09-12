package com.thisko.qringprint.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.thisko.qringprint.model.ConnState
import com.thisko.qringprint.model.HardwareState
import com.thisko.qringprint.model.PaperState
import com.thisko.qringprint.model.PrinterStatus
import com.thisko.qringprint.model.applyQringStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.util.UUID

/**
 * Qring / BeePrt 打印机连接管理。全局单例 —— 同一时刻只连一台。
 *
 * 职责边界:
 *   本类            —— socket 生命周期、分包收发、查询/ACK 时序、轮询调度、持久化
 *   QringProtocol   —— 纯协议:拼字节/解字节
 *   RasterEncoder   —— 图像与文本 → 光栅字节
 *   UI             —— 只读 PrinterStatus,或调本类的高层打印方法
 *
 * 所有蓝牙 API 调用都包了 try/catch:模拟器里没有蓝牙能力,
 * 不包异常整个页面会白屏,连预览都办不到。
 */
object PrinterConnection {
    private const val TAG = "PrinterConnection"
    /** 串口服务标准 UUID,经典蓝牙 SPP 固定用这个 */
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    private const val PREF_NAME = "qringprint_printer"
    private const val KEY_LAST_DEVICE = "lastDeviceId"

    /** 状态轮询间隔 */
    private const val POLL_INTERVAL_MS: Long = 2000L
    /** 查询响应等待上限 */
    private const val QUERY_TIMEOUT_MS: Long = 1500L
    /** 发命令后等打印机准备响应的时间,照搬 SDK */
    private const val QUERY_SETTLE_MS: Long = 150L
    /** 等打印完成 ACK 的上限 */
    private const val ACK_TIMEOUT_MS: Long = 120000L

    /** 打印前后走纸点行,对应 Python 的 feed_before / feed_after
     *
     * FEED_AFTER 不宜过大:文本/图片的最后一行打完后,机器仍会再走这一段纸,
     * 走多了用户会感觉「内容已经打完了机器还在吐白纸」。
     * 24 点 = 3 mm,刚好让最后一行离开打印头、可干净撕纸,不会再明显多走。
     */
    private const val FEED_BEFORE: Int = 10
    private const val FEED_AFTER: Int = 24
    private const val FEED_SETTLE_MS: Long = 800L

    private val _status = MutableStateFlow(PrinterStatus())
    val status: StateFlow<PrinterStatus> = _status.asStateFlow()

    private var transport: PrintTransport? = null
    private var readJob: Job? = null
    private var pollJob: Job? = null
    private var foreground: Boolean = true
    /** 打印任务进行中 —— 期间暂停状态轮询,免得查询字节混进打印数据流 */
    @Volatile private var busy: Boolean = false

    /** 滚动接收缓冲。响应长度不固定,还会随时插入 FF xx 主动上报帧 */
    private val rxBuffer = mutableListOf<Int>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun isAlive(): Boolean = transport?.isConnected() == true
    fun isBusy(): Boolean = busy
    val currentStatus: PrinterStatus get() = _status.value

    fun onForeground() {
        foreground = true
        if (isAlive() && pollJob == null) startPolling()
    }

    fun onBackground() {
        foreground = false
        stopPolling()
    }

    fun init(context: Context) {
        // 不订阅 connection.on('batteryChange'):那是平台级 HFP/HID 电池通道,
        // 这台机器不走那条,只会给 -1,反而把私有协议 10 FF 50 F1 查到的真实电池盖掉。
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(context: Context, deviceId: String): Boolean {
        disconnect()

        _status.value = _status.value.copy(
            deviceId = deviceId,
            deviceName = resolveName(context, deviceId),
            connState = ConnState.CONNECTING,
            lastError = ""
        )

        val device = try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter?.getRemoteDevice(deviceId)
        } catch (err: Throwable) {
            null
        }
        if (device == null) {
            _status.value = _status.value.copy(
                connState = ConnState.DISCONNECTED,
                lastError = "无法获取设备"
            )
            return false
        }

        val newSocket = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (err: Throwable) {
            Log.e(TAG, "createRfcommSocket failed: ${err.message}")
            _status.value = _status.value.copy(
                connState = ConnState.DISCONNECTED,
                lastError = "创建 socket 失败"
            )
            return false
        }

        // 必须先 cancelDiscovery() 才能 connect() 成功,这是 Android 的坑
        try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.cancelDiscovery()
        } catch (_: SecurityException) {}
        try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.cancelDiscovery()
        } catch (_: Throwable) {}

        val connected = withContext(Dispatchers.IO) {
            try {
                newSocket.connect()
                true
            } catch (err: IOException) {
                Log.e(TAG, "socket connect failed: ${err.message}")
                try { newSocket.close() } catch (_: Throwable) {}
                false
            } catch (err: SecurityException) {
                Log.e(TAG, "socket connect permission denied: ${err.message}")
                false
            }
        }

        if (!connected) {
            _status.value = _status.value.copy(
                connState = ConnState.DISCONNECTED,
                lastError = "连接失败"
            )
            return false
        }

        _status.value = _status.value.copy(connState = ConnState.CONNECTED)
        persistDeviceId(context, deviceId)
        transport = BluetoothTransport(newSocket)
        startReadLoop()
        refreshAll()
        scope.launch { queryDeviceInfo() }
        if (foreground) startPolling()
        return true
    }

    suspend fun connectNetwork(context: Context, host: String, port: Int = 9100): Boolean {
        disconnect()
        val deviceId = "wifi:$host:$port"
        _status.value = _status.value.copy(
            deviceId = deviceId,
            deviceName = "$host:$port",
            connState = ConnState.CONNECTING,
            lastError = ""
        )

        val newTransport = withContext(Dispatchers.IO) {
            try {
                NetworkTransport(Socket(host, port))
            } catch (err: Throwable) {
                Log.e(TAG, "network connect failed: ${err.message}")
                null
            }
        } ?: run {
            _status.value = _status.value.copy(
                connState = ConnState.DISCONNECTED,
                lastError = "WiFi 连接失败"
            )
            return false
        }

        transport = newTransport
        _status.value = _status.value.copy(connState = ConnState.CONNECTED)
        persistDeviceId(context, deviceId)
        startReadLoop()
        refreshAll()
        scope.launch { queryDeviceInfo() }
        if (foreground) startPolling()
        return true
    }

    suspend fun connectUsb(context: Context, device: UsbDevice, baudRate: Int = 9600): Boolean {
        disconnect()
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        if (manager == null || !manager.hasPermission(device)) {
            _status.value = _status.value.copy(connState = ConnState.DISCONNECTED, lastError = "没有 USB 权限")
            return false
        }

        _status.value = _status.value.copy(
            deviceId = UsbPrinterSupport.deviceId(device),
            deviceName = UsbPrinterSupport.displayName(device),
            connState = ConnState.CONNECTING,
            lastError = ""
        )

        val newTransport = withContext(Dispatchers.IO) {
            try {
                UsbPrinterSupport.open(manager, device, baudRate)
            } catch (err: Throwable) {
                Log.e(TAG, "usb connect failed: ${err.message}")
                null
            }
        } ?: run {
            _status.value = _status.value.copy(
                connState = ConnState.DISCONNECTED,
                lastError = "USB 连接失败"
            )
            return false
        }

        transport = newTransport
        _status.value = _status.value.copy(connState = ConnState.CONNECTED)
        persistDeviceId(context, UsbPrinterSupport.deviceId(device))
        startReadLoop()
        refreshAll()
        scope.launch { queryDeviceInfo() }
        if (foreground) startPolling()
        return true
    }

    fun disconnect() {
        stopPolling()
        readJob?.cancel()
        readJob = null
        synchronized(rxBuffer) { rxBuffer.clear() }
        busy = false
        val active = transport
        transport = null
        if (active != null) {
            try { active.close() } catch (err: Throwable) {
                Log.w(TAG, "close socket failed: ${err.message}")
            }
        }
        _status.value = _status.value.copy(
            connState = ConnState.DISCONNECTED,
            paperState = PaperState.UNKNOWN,
            hardwareState = HardwareState.UNKNOWN,
            batteryPercent = null,
            printing = false
        )
    }

    /**
     * 冷启动静默重连上次的设备。失败不弹任何提示(用户点卡片时再手动连)。
     */
    @SuppressLint("MissingPermission")
    suspend fun autoReconnect(context: Context) {
        val deviceId = loadDeviceId(context)
        if (deviceId.isEmpty()) return
        if (deviceId.startsWith("wifi:")) {
            val parts = deviceId.removePrefix("wifi:").split(":")
            val host = parts.getOrNull(0) ?: return
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 9100
            connectNetwork(context, host, port)
            return
        }
        if (deviceId.startsWith("usb:")) return
        val paired = try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter?.bondedDevices.orEmpty()
        } catch (err: SecurityException) { return }
        if (paired.none { it.address == deviceId }) return
        // 与设备列表用同一套过滤规则,万一过滤前缀改了,
        // 别让历史保存的 deviceId 把一台已不匹配的设备重新连上
        if (!matchesDeviceFilter(resolveName(context, deviceId))) return
        connect(context, deviceId)
    }

    // ===== 底层收发 ============================================================

    private suspend fun send(data: ByteArray): Boolean {
        val os = transport?.output ?: return false
        return withContext(Dispatchers.IO) {
            var offset = 0
            while (offset < data.size) {
                val end = minOf(offset + CHUNK_SIZE, data.size)
                try {
                    os.write(data, offset, end - offset)
                    os.flush()
                } catch (err: Throwable) {
                    Log.e(TAG, "write failed: ${err.message}")
                    return@withContext false
                }
                offset = end
                delay(CHUNK_DELAY_MS)
            }
            true
        }
    }

    private suspend fun sendAll(commands: List<ByteArray>): Boolean {
        for (command in commands) {
            if (!send(command)) return false
        }
        return true
    }

    /** 等到至少 n 字节。超时就返回已收到的(可能不足 n) */
    private suspend fun waitBytes(n: Int, timeoutMs: Long): IntArray {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val arr = synchronized(rxBuffer) {
                if (rxBuffer.size >= n) rxBuffer.toIntArray().also { rxBuffer.clear() } else null
            }
            if (arr != null) return arr
            delay(20)
        }
        return synchronized(rxBuffer) {
            val arr = rxBuffer.toIntArray()
            rxBuffer.clear()
            arr
        }
    }

    /** 清空输入 → 发命令 → 短等 → 读响应。这是 SDK 的稳定套路 */
    private suspend fun query(command: ByteArray, nbytes: Int): IntArray {
        synchronized(rxBuffer) { rxBuffer.clear() }
        if (!send(command)) return intArrayOf()
        delay(QUERY_SETTLE_MS)
        return waitBytes(nbytes, QUERY_TIMEOUT_MS)
    }

    /** 等打印完成 ACK (0xAA),同时盯着 FF xx 故障帧 */
    private suspend fun waitAck(timeoutMs: Long): PrintResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            synchronized(rxBuffer) {
                if (rxBuffer.contains(ACK_PRINT_DONE)) {
                    rxBuffer.clear()
                    return PrintResult(ok = true, message = "打印完成")
                }
                for (i in 0 until rxBuffer.size - 1) {
                    if (rxBuffer[i] == FAULT_FRAME_HEAD) {
                        val code = rxBuffer[i + 1]
                        if (code in 0x01..0x04) {
                            rxBuffer.clear()
                            return PrintResult(ok = false, message = FaultCode.labelOf(code))
                        }
                    }
                }
            }
            delay(100)
        }
        return PrintResult(ok = false, message = "等待打印完成超时")
    }

    // ===== 查询 ================================================================

    suspend fun queryStatus(): QringStatus? {
        val response = query(CMD_STATUS, 1)
        if (response.size < 1) return null
        return parseStatus(response[0])
    }

    /** 电池:响应 2 字节,第 2 字节才是百分比 */
    suspend fun queryBattery(): Int? {
        val response = query(CMD_BATTERY, 2)
        if (response.size < 2) return null
        return response[1]
    }

    /** 字符串类查询 */
    private suspend fun queryString(command: ByteArray): String {
        val response = query(command, 64)
        val sb = StringBuilder()
        for (b in response) {
            if (b in 0x20..0x7E) sb.append(b.toChar())
        }
        return sb.toString().trim()
    }

    suspend fun queryDeviceInfo() {
        if (busy) return
        val model = queryString(CMD_MODEL)
        val firmware = queryString(CMD_FW_VERSION)
        _status.value = _status.value.copy(model = model, firmware = firmware)
    }

    suspend fun refreshAll() {
        val status = queryStatus()
        if (status != null) {
            applyQringStatus(_status.value, status)?.let { _status.value = it }
        }
        val battery = queryBattery()
        if (battery != null) {
            _status.value = _status.value.copy(batteryPercent = battery)
        }
    }

    suspend fun setShutdownTime(seconds: Int): Boolean {
        return sendAll(listOf(cmdShutdownTime(seconds)))
    }

    /**
     * 打印前体检。返回故障文档;null 表示可以打印。
     *
     * 这里**现查一次**而不是读轮询的缓存值 —— 轮询间隔 10s,
     * 用户完全可能刚掀开上盖或刚把纸用完就点打印,缓存值是过期的。
     */
    suspend fun preflightCheck(): String? {
        if (!isAlive()) return "打印机未连接"
        val status = queryStatus() ?: return null
        applyQringStatus(_status.value, status)?.let { _status.value = it }
        return faultMessage(status)
    }

    // ===== 打印 ================================================================

    /**
     * 打印一张已经转好的光栅位图。
     * 时序照搬 Python 的 print_image:
     *   enable → thickness → wakeup → feed(前) → 光栅 → feed(后) → stop → 等 ACK
     */
        /**
     * 队列调用：不做 busy 检查，由 PrintQueue 统一接口。依然保留连接检查。
     */
    suspend fun printRasterNow(raster: RasterData, thickness: Int?): PrintResult {
        if (!isAlive()) return PrintResult(ok = false, message = "打印机未连接")
        busy = true
        stopPolling()
        synchronized(rxBuffer) { rxBuffer.clear() }
        return printInner(raster, thickness)
    }

suspend fun printRaster(raster: RasterData, thickness: Int?): PrintResult {
        if (!isAlive()) return PrintResult(ok = false, message = "打印机未连接")
        if (busy) return PrintResult(ok = false, message = "上一个打印任务还没结束")
        busy = true
        stopPolling()
        synchronized(rxBuffer) { rxBuffer.clear() }
        return printInner(raster, thickness)
    }

    private suspend fun printInner(raster: RasterData, thickness: Int?): PrintResult {
        try {
            if (!sendAll(listOf(CMD_ENABLE, CMD_ENABLE2))) {
                return PrintResult(ok = false, message = "发送失败,连接可能已断开")
            }
            if (thickness != null) {
                send(cmdThickness(thickness))
            }
            send(CMD_WAKEUP)
            sendAll(cmdFeed(FEED_BEFORE))

            send(cmdRasterHeader(raster.widthBytes, raster.height, 0))
            if (!send(raster.data)) {
                return PrintResult(ok = false, message = "位图发送中断")
            }

            if (!sendAll(cmdFeed(FEED_AFTER))) {
                return PrintResult(ok = false, message = "出纸命令发送失败")
            }
            delay(FEED_SETTLE_MS)
            if (!send(CMD_STOP)) {
                return PrintResult(ok = false, message = "停止命令发送失败")
            }

            _status.value = _status.value.copy(printing = true)
            val result = waitAck(ACK_TIMEOUT_MS)
            _status.value = _status.value.copy(printing = false)
            if (!result.ok) {
                _status.value = _status.value.copy(lastError = result.message)
            }
            return result
        } finally {
            busy = false
            // 打完了刷新一次状态 —— 纸/电池会有变化
            refreshAll()
            if (foreground && isAlive()) startPolling()
        }
    }

    suspend fun printBitmap(bitmap: android.graphics.Bitmap, threshold: Int, thickness: Int?): PrintResult {
        return try {
            val raster = bitmapToRaster(bitmap, threshold)
            printRaster(raster, thickness)
        } catch (err: Throwable) {
            Log.e(TAG, "raster encode failed: ${err.message}")
            PrintResult(ok = false, message = "图像转换失败")
        }
    }

    // ===== 状态轮询 ===========================================================

    private fun startPolling() {
        stopPolling()
        pollJob = scope.launch {
            while (isActive && isAlive() && !busy) {
                refreshAll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun startReadLoop() {
        readJob = scope.launch(Dispatchers.IO) {
            val input = transport?.input ?: return@launch
            val buf = ByteArray(1024)
            while (isActive) {
                val n = try {
                    input.read(buf)
                } catch (err: IOException) {
                    Log.w(TAG, "read failed: ${err.message}")
                    break
                } catch (err: Throwable) {
                    break
                }
                if (n <= 0) continue
                synchronized(rxBuffer) {
                    for (i in 0 until n) {
                        rxBuffer.add(buf[i].toInt() and 0xFF)
                    }
                    // 缓冲兜底,防止长时间不读导致无限增长
                    if (rxBuffer.size > 4096) {
                        val drop = rxBuffer.size - 4096
                        repeat(drop) { rxBuffer.removeAt(0) }
                    }
                }
            }
        }
    }

    // ===== 工具 =================================================================

    @SuppressLint("MissingPermission")
    private fun resolveName(context: Context, deviceId: String): String {
        return try {
            val device = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter?.getRemoteDevice(deviceId)
            device?.name?.takeIf { it.isNotEmpty() } ?: deviceId
        } catch (err: SecurityException) {
            deviceId
        } catch (err: Throwable) {
            deviceId
        }
    }

    private fun persistDeviceId(context: Context, deviceId: String) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LAST_DEVICE, deviceId).apply()
        } catch (err: Throwable) {
            Log.w(TAG, "persist deviceId failed: ${err.message}")
        }
    }

    private fun loadDeviceId(context: Context): String {
        return try {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_DEVICE, "") ?: ""
        } catch (err: Throwable) {
            ""
        }
    }

    fun shutdown() {
        disconnect()
        scope.cancel()
    }
}

data class PrintResult(
    val ok: Boolean,
    val message: String,
)
