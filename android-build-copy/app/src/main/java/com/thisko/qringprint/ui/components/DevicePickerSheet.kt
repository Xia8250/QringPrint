package com.thisko.qringprint.ui.components

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.thisko.qringprint.bluetooth.UsbPrinterSupport
import com.thisko.qringprint.bluetooth.BtDevice
import com.thisko.qringprint.bluetooth.BtPermission
import com.thisko.qringprint.bluetooth.DEVICE_NAME_PREFIX
import com.thisko.qringprint.bluetooth.DiscoverySession
import com.thisko.qringprint.bluetooth.PrinterConnection
import com.thisko.qringprint.bluetooth.isBluetoothEnabled
import com.thisko.qringprint.bluetooth.listPairedDevices
import com.thisko.qringprint.bluetooth.watchBluetoothState
import com.thisko.qringprint.model.ConnState
import com.thisko.qringprint.model.loadUsbBaudRate
import com.thisko.qringprint.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * 设备选择半模态。
 *
 * 入口在状态卡的点击上 —— 不管连没连上,点一下都进这里换/查设备。
 * 进入即检查蓝牙开关和权限:关了提示打开,没权限提示授予;都好了再列设备。
 * 设备列表 = 已配对的 + 扫描结果(同名过滤),点行就连。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerSheet(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var btEnabled by remember { mutableStateOf(isBluetoothEnabled(context)) }
    var hasPerm by remember { mutableStateOf(BtPermission.hasAll(context)) }
    var devices by remember { mutableStateOf(listPairedDevices(context)) }
    var scanning by remember { mutableStateOf(false) }
    var connectingId by remember { mutableStateOf("") }
    var wifiHost by remember { mutableStateOf("") }
    var wifiPort by remember { mutableStateOf("9100") }
    var usbDevices by remember { mutableStateOf(listOf<UsbDevice>()) }
    var requestedUsbDevice by remember { mutableStateOf<UsbDevice?>(null) }
    val status by PrinterConnection.status.collectAsState()

    val session = remember { DiscoverySession(context) }

    // 蓝牙开关变化监听
    DisposableEffect(Unit) {
        val unwatch = watchBluetoothState(context) { enabled ->
            btEnabled = enabled
            if (enabled) {
                devices = listPairedDevices(context)
            } else {
                devices = emptyList()
                scanning = false
            }
        }
        onDispose { unwatch() }
    }

    // 半模态关闭时一定要停扫描
    DisposableEffect(Unit) {
        onDispose { session.stop() }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasPerm = BtPermission.hasAll(context)
        if (hasPerm && btEnabled) devices = listPairedDevices(context)
    }

    val usbManager = remember { context.getSystemService(android.content.Context.USB_SERVICE) as? UsbManager }
    val usbPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val device = requestedUsbDevice
        if (result.resultCode == android.app.Activity.RESULT_OK && device != null &&
            usbManager?.hasPermission(device) == true
        ) {
            scope.launch {
                val ok = PrinterConnection.connectUsb(context, device, loadUsbBaudRate(context))
                if (ok) onDismiss()
            }
        }
    }

    fun refreshUsbDevices() {
        usbDevices = usbManager?.let { UsbPrinterSupport.devices(it) }.orEmpty()
    }

    LaunchedEffect(Unit) { refreshUsbDevices() }

    fun requestOrConnectUsb(device: UsbDevice) {
        val manager = usbManager ?: return
        if (manager.hasPermission(device)) {
            scope.launch {
                connectingId = UsbPrinterSupport.deviceId(device)
                val ok = PrinterConnection.connectUsb(context, device, loadUsbBaudRate(context))
                connectingId = ""
                if (ok) onDismiss()
            }
        } else {
            requestedUsbDevice = device
            val intent = Intent("com.thisko.qringprint.USB_PERMISSION").setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(
                context,
                device.deviceId,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            manager.requestPermission(device, pending)
            // 系统对话框关闭后，launcher 会检查权限并继续连接。
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            // 标题
            Text(
                text = "选择打印机",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text("WiFi / 网络", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = wifiHost,
                    onValueChange = { wifiHost = it.trim() },
                    label = { Text("IP / 主机名") },
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = wifiPort,
                    onValueChange = { wifiPort = it.filter(Char::isDigit).take(5) },
                    label = { Text("端口") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (wifiHost.isBlank() || connectingId.isNotEmpty()) return@Button
                    connectingId = "wifi"
                    scope.launch {
                        val ok = PrinterConnection.connectNetwork(context, wifiHost, wifiPort.toIntOrNull() ?: 9100)
                        connectingId = ""
                        if (ok) onDismiss()
                    }
                },
                enabled = wifiHost.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (connectingId == "wifi") "连接中…" else "连接 WiFi 打印机")
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("USB-OTG", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = ::refreshUsbDevices) { Text("刷新") }
            }
            if (usbDevices.isEmpty()) {
                Text(
                    "请用 OTG 线接入 USB 打印机；支持 USB printer class 设备。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                usbDevices.forEach { device ->
                    val id = UsbPrinterSupport.deviceId(device)
                    Surface(
                        onClick = { requestOrConnectUsb(device) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Usb, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(UsbPrinterSupport.displayName(device), fontSize = 15.sp)
                                Text(id, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (connectingId == id) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("蓝牙打印机", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            when {
                !btEnabled -> {
                    HintBlock(
                        title = "蓝牙未开启",
                        detail = "请在系统设置或控制中心打开蓝牙,然后回到这里重试。",
                        actionLabel = "",
                        onAction = {},
                    )
                }
                !hasPerm -> {
                    val needSettings = !hasPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        BtPermission.requiredPermissions().any {
                            val granted = ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                            val showRationale = (context as? Activity)?.shouldShowRequestPermissionRationale(it) ?: false
                            !granted && !showRationale
                        }
                    HintBlock(
                        title = "需要蓝牙权限",
                        detail = if (needSettings) "权限已被永久拒绝,需要到系统设置里手动开启。"
                                 else "用于扫描并连接附近的蓝牙打印机。",
                        actionLabel = if (needSettings) "前往设置" else "授予权限",
                        onAction = {
                            if (needSettings) {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } else {
                                permLauncher.launch(BtPermission.requiredPermissions())
                            }
                        },
                    )
                }
                else -> {
                    // 头部:扫描控制
                    Row(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (scanning) "正在扫描附近设备…" else "仅显示 ${DEVICE_NAME_PREFIX} 设备",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (scanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "停止",
                                fontSize = 13.sp,
                                color = Palette.brand,
                                modifier = Modifier.clickable { session.stop(); scanning = false },
                            )
                        } else {
                            Text(
                                text = "扫描附近设备",
                                fontSize = 13.sp,
                                color = Palette.brand,
                                modifier = Modifier.clickable {
                                    session.stop()
                                    scanning = session.start(listPairedDevices(context)) { found ->
                                        devices = found
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    if (devices.isEmpty()) {
                        HintBlock(
                            title = "没有找到设备",
                            detail = "列表只显示名称以 ${DEVICE_NAME_PREFIX} 开头的打印机。请确认设备已开机,再点上方「扫描附近设备」。",
                            actionLabel = "",
                            onAction = {},
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(devices, key = { it.deviceId }) { device ->
                                DeviceRow(
                                    device = device,
                                    isCurrent = status.deviceId == device.deviceId && status.connState == ConnState.CONNECTED,
                                    isConnecting = connectingId == device.deviceId,
                                    onClick = {
                                        if (connectingId.isNotEmpty()) return@DeviceRow
                                        session.stop()
                                        scanning = false
                                        connectingId = device.deviceId
                                        scope.launch {
                                            val ok = PrinterConnection.connect(context, device.deviceId)
                                            connectingId = ""
                                            if (ok) onDismiss()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HintBlock(title: String, detail: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            detail,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.brand, contentColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Text(actionLabel, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BtDevice, isCurrent: Boolean, isConnecting: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Palette.brand.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Print,
                    contentDescription = null,
                    tint = Palette.brand,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name.ifEmpty { "未命名设备" },
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (device.paired) "已配对 · ${device.deviceId}" else device.deviceId,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            when {
                isConnecting -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Palette.brand,
                )
                isCurrent -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.online, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("已连接", fontSize = 12.sp, color = Palette.online)
                }
                else -> {
                    val isBtOn = isBluetoothEnabled(LocalContext.current)
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape)
                            .background(if (isBtOn) Palette.brand else Palette.offline(false)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isBtOn) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
