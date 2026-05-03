package com.snake9x.espremote

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

enum class BleStatus {
    IDLE, SCANNING, CONNECTING, DISCOVERING, READY, DISCONNECTED
}

@SuppressLint("MissingPermission")
class BLEKeyboardBridge(private val context: Context) {

    companion object {
        private const val TAG = "BLEBridge"
        val SERVICE_UUID: UUID = UUID.fromString("2D2A0001-8A5A-4E76-A2E3-1E57D9A1B001")
        val WRITE_CHAR_UUID: UUID = UUID.fromString("2D2A0002-8A5A-4E76-A2E3-1E57D9A1B001")

        // v2 protocol constants (matches ESP32 firmware exactly)
        const val V2_MAGIC: Byte    = 0xAA.toByte()
        const val V2_VERSION: Byte  = 0x01
        const val V2_SET_MODIFIERS: Byte = 0x01
        const val V2_KEY_DOWN: Byte      = 0x02
        const val V2_KEY_UP: Byte        = 0x03
        const val V2_KEY_TAP: Byte       = 0x04
        const val V2_MOUSE_MOVE: Byte    = 0x10
        const val V2_MOUSE_SCROLL: Byte  = 0x11
        const val V2_MOUSE_CLICK: Byte   = 0x12
        const val V2_MOUSE_DOWN: Byte    = 0x13
        const val V2_MOUSE_UP: Byte      = 0x14
    }

    var onStatusChanged: ((BleStatus, String) -> Unit)? = null

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var bleScanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    var status: BleStatus = BleStatus.IDLE
        private set(value) {
            field = value
        }

    // ─── Public API ────────────────────────────────────────────

    fun start() {
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        startScan()
    }

    fun disconnect() {
        gatt?.close()
        gatt = null
        writeChar = null
    }

    fun sendKeyTap(modifiers: Byte, keycode: Byte) {
        writeV2(listOf(byteArrayOf(V2_KEY_TAP, 0x02, modifiers, keycode)))
    }

    fun sendKeyTaps(taps: List<Pair<Byte, Byte>>) {
        val frames = taps.map { (mod, kc) -> byteArrayOf(V2_KEY_TAP, 0x02, mod, kc) }
        writeV2(frames)
    }

    fun sendKeyDown(modifiers: Byte, keycode: Byte) {
        val frames = mutableListOf<ByteArray>()
        frames.add(byteArrayOf(V2_SET_MODIFIERS, 0x01, modifiers))
        frames.add(byteArrayOf(V2_KEY_DOWN, 0x01, keycode))
        writeV2(frames)
    }

    fun sendKeyUp(keycode: Byte) {
        writeV2(listOf(byteArrayOf(V2_KEY_UP, 0x01, keycode)))
    }

    fun sendMouseMove(dx: Byte, dy: Byte) {
        writeV2(listOf(byteArrayOf(V2_MOUSE_MOVE, 0x02, dx, dy)))
    }

    fun sendMouseClick(button: Byte) {
        writeV2(listOf(byteArrayOf(V2_MOUSE_CLICK, 0x01, button)))
    }

    fun sendMouseScroll(dx: Byte, dy: Byte) {
        writeV2(listOf(byteArrayOf(V2_MOUSE_SCROLL, 0x02, dx, dy)))
    }

    // ─── v2 Protocol framing ───────────────────────────────────

    /** Sends frames with [0xAA, 0x01] header, batching into MTU-sized packets */
    private fun writeV2(frames: List<ByteArray>) {
        val char = writeChar ?: run {
            Log.w(TAG, "writeV2: not connected")
            return
        }
        val g = gatt ?: return

        val writeType = if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        else
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        val maxLen = 512 // BLE 4.2+ supports up to 512 bytes; 20 is safe minimum
        var i = 0
        while (i < frames.size) {
            val packet = mutableListOf(V2_MAGIC, V2_VERSION)
            while (i < frames.size) {
                val f = frames[i]
                if (packet.size + f.size > maxLen) break
                f.forEach { packet.add(it) }
                i++
            }
            val data = packet.map { it }.toByteArray()
            char.writeType = writeType
            char.value = data
            g.writeCharacteristic(char)
        }
    }

    // ─── BLE Scan ──────────────────────────────────────────────

    private fun startScan() {
        updateStatus(BleStatus.SCANNING, "Scanning for KBBridge-ESP32S3...")
        val filter = ScanFilter.Builder()
            .setServiceUuid(android.os.ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        bleScanner?.startScan(listOf(filter), settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            bleScanner?.stopScan(this)
            updateStatus(BleStatus.CONNECTING, "Connecting to ${result.device.name ?: result.device.address}...")
            result.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
        override fun onScanFailed(errorCode: Int) {
            updateStatus(BleStatus.DISCONNECTED, "Scan failed (code $errorCode)")
        }
    }

    // ─── GATT Callbacks ────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt = g
                    updateStatus(BleStatus.DISCOVERING, "Discovering services...")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt?.close()
                    gatt = null
                    writeChar = null
                    updateStatus(BleStatus.DISCONNECTED, "Disconnected, rescanning...")
                    mainHandler.postDelayed({ startScan() }, 2000)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(SERVICE_UUID) ?: run {
                updateStatus(BleStatus.DISCONNECTED, "Service not found, retrying...")
                g.disconnect()
                return
            }
            val char = service.getCharacteristic(WRITE_CHAR_UUID) ?: run {
                updateStatus(BleStatus.DISCONNECTED, "Characteristic not found")
                g.disconnect()
                return
            }
            writeChar = char
            updateStatus(BleStatus.READY, "✓ Connected — ready to type!")
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Write failed: $status")
            }
        }
    }

    private fun updateStatus(s: BleStatus, msg: String) {
        status = s
        mainHandler.post { onStatusChanged?.invoke(s, msg) }
    }
}
