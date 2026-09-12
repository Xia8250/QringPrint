package com.thisko.qringprint.bluetooth

import android.bluetooth.BluetoothSocket
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

interface PrintTransport {
    val input: InputStream
    val output: OutputStream
    fun isConnected(): Boolean
    fun close()
}

class BluetoothTransport(private val socket: BluetoothSocket) : PrintTransport {
    override val input: InputStream = socket.inputStream
    override val output: OutputStream = socket.outputStream
    override fun isConnected(): Boolean = socket.isConnected
    override fun close() = socket.close()
}

class NetworkTransport(private val socket: Socket) : PrintTransport {
    override val input: InputStream = socket.getInputStream()
    override val output: OutputStream = socket.getOutputStream()
    override fun isConnected(): Boolean = !socket.isClosed && socket.isConnected
    override fun close() = socket.close()
}


class UsbPrinterTransport(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val inEndpoint: UsbEndpoint?,
    private val outEndpoint: UsbEndpoint,
) : PrintTransport {
    init {
        connection.claimInterface(usbInterface, true)
    }

    override val input: InputStream = object : InputStream() {
        private val buffer = ByteArray(1)
        override fun read(): Int {
            if (inEndpoint == null) return -1
            val count = connection.bulkTransfer(inEndpoint, buffer, buffer.size, 50)
            return if (count == 1) buffer[0].toInt() and 0xFF else 0
        }
    }

    override val output: OutputStream = object : OutputStream() {
        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

        override fun write(buffer: ByteArray, offset: Int, count: Int) {
            val sent = connection.bulkTransfer(outEndpoint, buffer, offset, count, USB_TRANSFER_TIMEOUT_MS)
            if (sent != count) throw IOException("USB 写入失败 ($sent/$count)")
        }
    }

    override fun isConnected(): Boolean = true

    override fun close() {
        runCatching { connection.releaseInterface(usbInterface) }
        connection.close()
    }

    companion object {
        const val USB_TRANSFER_TIMEOUT_MS = 5000
    }
}

object UsbPrinterSupport {
    fun devices(manager: UsbManager): List<UsbDevice> {
        return manager.deviceList.values.filter { device ->
            (0 until device.interfaceCount).any { index ->
                printerOutEndpoint(device.getInterface(index)) != null
            }
        }
    }

    fun open(manager: UsbManager, device: UsbDevice, baudRate: Int): PrintTransport {
        val connection = manager.openDevice(device) ?: throw IOException("无法打开 USB 设备")
        var selectedInterface: UsbInterface? = null
        var selectedIn: UsbEndpoint? = null
        var selectedOut: UsbEndpoint? = null

        for (index in 0 until device.interfaceCount) {
            val candidate = device.getInterface(index)
            val output = printerOutEndpoint(candidate)
            if (output != null) {
                selectedInterface = candidate
                selectedOut = output
                selectedIn = (0 until candidate.endpointCount).map(candidate::getEndpoint).firstOrNull {
                    it.direction == UsbConstants.USB_DIR_IN && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                }
                break
            }
        }

        val usbInterface = selectedInterface ?: throw IOException("未找到 USB 打印接口")
        val out = selectedOut ?: throw IOException("未找到 USB 输出端点")
        return UsbPrinterTransport(connection, usbInterface, selectedIn, out)
    }

    fun displayName(device: UsbDevice): String {
        val name = device.productName
        return if (name.isNullOrBlank()) "USB 打印机 ${device.vendorId}:${device.productId}" else name
    }

    fun deviceId(device: UsbDevice): String = "usb:${device.vendorId}:${device.productId}"

    private fun printerOutEndpoint(candidate: UsbInterface): UsbEndpoint? {
        if (candidate.interfaceClass != UsbConstants.USB_CLASS_PRINTER) return null
        return (0 until candidate.endpointCount).map(candidate::getEndpoint).firstOrNull {
            it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
        }
    }
}
