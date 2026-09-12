package com.thisko.qringprint.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 设备名前缀 —— 只显示自家 Qring 打印机。过滤掉音箱/手环等无关蓝牙设备。
 * 要放宽或改规则,改这个常量(或下面的 matchesDeviceFilter)即可。
 */
const val DEVICE_NAME_PREFIX: String = "Qring"

/**
 * 大小写不敏感匹配 —— 不同批次固件可能上报 Qring / QRing / QRING,
 * 严格区分大小写会刷掉设备。
 */
fun matchesDeviceFilter(name: String): Boolean {
    if (name.isEmpty()) return false
    return name.lowercase().startsWith(DEVICE_NAME_PREFIX.lowercase())
}

data class BtDevice(
    val deviceId: String,
    val name: String,
    val paired: Boolean,
)

/**
 * 蓝牙开关状态。
 * 在没有蓝牙的设备/模拟器上 isEnabled() 会抛,我们吞掉并返回 false —— 否则整个页面会白屏。
 */
fun isBluetoothEnabled(context: Context): Boolean {
    val adapter = bluetoothAdapter(context) ?: return false
    return try {
        adapter.isEnabled
    } catch (err: Throwable) {
        Log.w("PrinterDiscovery", "isEnabled failed: ${err.message}")
        false
    }
}

@SuppressLint("MissingPermission")
private fun bluetoothAdapter(context: Context): BluetoothAdapter? {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    return try {
        manager?.adapter
    } catch (err: Throwable) {
        null
    }
}

/**
 * 系统中已配对、且名字通过过滤器的设备。
 * 打印机通常已在系统设置里配对,这份列表优先展示。
 */
@SuppressLint("MissingPermission")
fun listPairedDevices(context: Context): List<BtDevice> {
    if (!hasConnectPermission(context)) return emptyList()
    val adapter = bluetoothAdapter(context) ?: return emptyList()
    return try {
        adapter.bondedDevices.orEmpty().mapNotNull { device ->
            val name = try {
                device.name ?: ""
            } catch (err: SecurityException) {
                ""
            }
            if (!matchesDeviceFilter(name)) return@mapNotNull null
            BtDevice(device.address, name, paired = true)
        }
    } catch (err: SecurityException) {
        Log.w("PrinterDiscovery", "get bondedDevices failed: ${err.message}")
        emptyList()
    }
}

private fun hasConnectPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}

private fun hasScanPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * 一次扫描会话。
 *
 * 关键:回调里通过 ACTION_FOUND 拿到远端设备的 MAC,再单独查一次 name —— 这正是
 * 鸿蒙 SDK 的"discoveryResult 带 deviceName"在 Android 上的等价做法。直接用
 * EXTRA_NAME 也不是不行,但部分 ROM 上广播顺序是分两次发的,这里按兼容最好的方式。
 *
 * 关键:Receiver 必须是**实名引用**,匿名函数无法 unregister。
 * 半模态关闭 / 组件销毁时必须 stop(),否则扫描会一直跑着费电。
 */
class DiscoverySession(private val context: Context) {
    private val seenIds = mutableListOf<String>()
    private val devices = mutableListOf<BtDevice>()
    private var running = false
    var onUpdate: (List<BtDevice>) -> Unit = {}

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_FOUND) return
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: device?.name ?: ""
            if (!matchesDeviceFilter(name)) return
            val addr = device?.address ?: return
            if (seenIds.contains(addr)) return
            seenIds.add(addr)
            devices.add(BtDevice(addr, name, paired = false))
            onUpdate(devices.toList())
        }
    }

    fun isRunning(): Boolean = running

    @SuppressLint("MissingPermission")
    fun start(paired: List<BtDevice>, onUpdate: (List<BtDevice>) -> Unit): Boolean {
        if (running) return true
        if (!hasScanPermission(context)) return false
        val adapter = bluetoothAdapter(context) ?: return false
        this.onUpdate = onUpdate
        seenIds.clear()
        devices.clear()
        for (device in paired) {
            seenIds.add(device.deviceId)
            devices.add(device)
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        try {
            context.registerReceiver(receiver, filter)
        } catch (err: Throwable) {
            Log.e("PrinterDiscovery", "registerReceiver failed: ${err.message}")
            return false
        }
        return try {
            adapter.startDiscovery()
            running = true
            true
        } catch (err: SecurityException) {
            Log.e("PrinterDiscovery", "startDiscovery failed: ${err.message}")
            try { context.unregisterReceiver(receiver) } catch (_: Throwable) {}
            running = false
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!running) return
        running = false
        val adapter = bluetoothAdapter(context)
        try { adapter?.cancelDiscovery() } catch (err: Throwable) {
            Log.w("PrinterDiscovery", "cancelDiscovery failed: ${err.message}")
        }
        try { context.unregisterReceiver(receiver) } catch (err: Throwable) {
            Log.w("PrinterDiscovery", "unregisterReceiver failed: ${err.message}")
        }
    }
}

/**
 * 监听蓝牙开关变化。返回反注册函数。
 * 用户在设置里关掉蓝牙时页面要能立刻反应,不能等到下次操作才发现连不上。
 */
fun watchBluetoothState(context: Context, onChange: (enabled: Boolean) -> Unit): () -> Unit {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                onChange(state == BluetoothAdapter.STATE_ON)
            }
        }
    }
    val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
    context.registerReceiver(receiver, filter)
    return { try { context.unregisterReceiver(receiver) } catch (_: Throwable) {} }
}
