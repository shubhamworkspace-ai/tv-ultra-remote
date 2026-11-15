package com.shubh.tvultraremote

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
class HidController(context: Context, private val onStatusChange: (String) -> Unit) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private var hidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null

    private val hidDescriptor = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x02.toByte(),
        0xA1.toByte(), 0x01.toByte(), 0x85.toByte(), 0x01.toByte(),
        0x09.toByte(), 0x01.toByte(), 0xA1.toByte(), 0x00.toByte(),
        0x05.toByte(), 0x09.toByte(), 0x19.toByte(), 0x01.toByte(),
        0x29.toByte(), 0x03.toByte(), 0x15.toByte(), 0x00.toByte(),
        0x25.toByte(), 0x01.toByte(), 0x95.toByte(), 0x03.toByte(),
        0x75.toByte(), 0x01.toByte(), 0x81.toByte(), 0x02.toByte(),
        0x95.toByte(), 0x01.toByte(), 0x75.toByte(), 0x05.toByte(),
        0x81.toByte(), 0x01.toByte(), 0x05.toByte(), 0x01.toByte(),
        0x09.toByte(), 0x30.toByte(), 0x09.toByte(), 0x31.toByte(),
        0x15.toByte(), 0x81.toByte(), 0x25.toByte(), 0x7F.toByte(),
        0x75.toByte(), 0x08.toByte(), 0x95.toByte(), 0x02.toByte(),
        0x81.toByte(), 0x06.toByte(), 0xC0.toByte(), 0xC0.toByte()
    )

    fun initialize() {
        adapter?.getProfileProxy(null, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    val sdp = BluetoothHidDeviceAppSdpSettings(
                        "TV Ultra Remote", "Zero-Lag Trackpad & Remote", "Android",
                        BluetoothHidDevice.SUBCLASS1_COMBO, hidDescriptor
                    )
                    hidDevice?.registerApp(sdp, null, null, Executors.newCachedThreadPool(),
                        object : BluetoothHidDevice.Callback() {
                            override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                                if (state == BluetoothProfile.STATE_CONNECTED) {
                                    hostDevice = device
                                    onStatusChange("BT HID Connected: ${device.name ?: "TV"}")
                                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                                    hostDevice = null
                                    onStatusChange("BT HID Disconnected (Pair in TV Bluetooth Settings)")
                                }
                            }
                        }
                    )
                }
            }
            override fun onServiceDisconnected(profile: Int) { hidDevice = null }
        }, BluetoothProfile.HID_DEVICE)
    }

    fun sendMouseEvent(dx: Float, dy: Float, leftClick: Boolean = false, rightClick: Boolean = false) {
        val device = hostDevice ?: return
        val buttons = (if (leftClick) 1 else 0) or (if (rightClick) 2 else 0)
        val x = dx.roundToInt().coerceIn(-127, 127).toByte()
        val y = dy.roundToInt().coerceIn(-127, 127).toByte()
        hidDevice?.sendReport(device, 1, byteArrayOf(buttons.toByte(), x, y))
    }
}
