package com.vibra.watch

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class WatchMainActivity : Activity() {
    private val serviceUuid = UUID.fromString("7b6a1000-7d4a-4f8a-9c2e-4f6a9d001000")
    private val patternUuid = UUID.fromString("7b6a1001-7d4a-4f8a-9c2e-4f6a9d001000")

    private var senderMode = false
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var patternCharacteristic: BluetoothGattCharacteristic? = null
    private var statusView: TextView? = null

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            permissions += Manifest.permission.BLUETOOTH_ADVERTISE
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        return permissions.toTypedArray()
    }

    private fun hasPermissions() = requiredPermissions().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun withPermissions(action: () -> Unit) {
        if (hasPermissions()) action() else requestPermissions(requiredPermissions(), 71)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerRelayReceiver()
        withPermissions { startDualMode() }
        intent?.getIntExtra("vibra_alert_code", -1)?.takeIf { it >= 0 }?.let { showIncomingAlert(it) }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.getIntExtra("vibra_alert_code", -1)?.takeIf { it >= 0 }?.let { showIncomingAlert(it) }
    }

    private fun startDualMode() {
        senderMode = true
        val serviceIntent = Intent(this, VibraWatchService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
        showSender()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 71 && grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startDualMode()
        } else if (requestCode == 71) {
            Toast.makeText(this, "VIBRA necesita permisos Bluetooth y notificaciones.", Toast.LENGTH_LONG).show()
        }
    }

    private fun makeScreen(title: String, subtitle: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(18, 24, 18, 18)
            setBackgroundColor(0xFF10131A.toInt())
        }
        root.addView(TextView(this).apply {
            text = title
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        })
        root.addView(TextView(this).apply {
            text = subtitle
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFFB9C2D0.toInt())
            setPadding(4, 8, 4, 14)
        })
        setContentView(root)
        return root
    }

    private fun addButton(root: LinearLayout, label: String, color: Int, action: () -> Unit) {
        root.addView(Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(color)
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, 46).apply {
            setMargins(4, 4, 4, 4)
        })
    }

    private val relayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val code = intent?.getIntExtra("code", -1) ?: return
            val hops = intent.getIntExtra("hops", 0)
            val source = intent.getStringExtra("source")
            if (code >= 1 && hops > 0) sendPattern(code, hops - 1, source)
        }
    }

    private fun registerRelayReceiver() {
        val filter = IntentFilter("com.vibra.watch.RELAY_SIGNAL")
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(relayReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(relayReceiver, filter)
        }
    }

    private fun showIncomingAlert(code: Int) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val label = when (code) {
            1 -> "MEDIUM"
            2 -> "HEART ×1"
            3 -> "HEART ×2"
            4 -> "HEART ×3"
            5 -> "HEART ×4"
            6 -> "ZIG ZIG ZIG"
            7 -> "ALERTA"
            else -> "SEÑAL RECIBIDA"
        }
        val root = makeScreen("VIBRA", "SEÑAL RECIBIDA")
        root.addView(TextView(this).apply {
            text = label
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(4, 18, 4, 18)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        addButton(root, "TOCAR PARA CERRAR", 0xFF2368E8.toInt()) {
            getSystemService(android.app.NotificationManager::class.java).cancel(30 + code)
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(false)
                setTurnScreenOn(false)
            }
            showSender()
        }
    }

    private fun showSender() {
        closeSender()
        val root = makeScreen("VIBRA", "LISTO · ENVIAR Y RECIBIR")
        statusView = TextView(this).apply {
            text = "Buscando teléfono…"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFD166.toInt())
            setPadding(0, 2, 0, 4)
        }
        root.addView(statusView)
        val scroll = ScrollView(this)
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        addButton(controls, "HEART ×1", 0xFFB8325A.toInt()) { sendPattern(2) }
        addButton(controls, "HEART ×2", 0xFFB8325A.toInt()) { sendPattern(3) }
        addButton(controls, "HEART ×3", 0xFFB8325A.toInt()) { sendPattern(4) }
        addButton(controls, "HEART ×4", 0xFFB8325A.toInt()) { sendPattern(5) }
        addButton(controls, "MEDIUM", 0xFF2368E8.toInt()) { sendPattern(1) }
        addButton(controls, "ZIG ZIG ZIG", 0xFF6D4BC3.toInt()) { sendPattern(6) }
        addButton(controls, "ALERTA", 0xFFB85C38.toInt()) { sendPattern(7) }
        scroll.addView(controls)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        startScan()
    }

    private fun startScan() {
        try {
            val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter
            if (adapter == null || !adapter.isEnabled) {
                updateStatus("Activá Bluetooth")
                return
            }
            scanner = adapter.bluetoothLeScanner
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (gatt != null) return
                    try {
                        updateStatus("Conectando…")
                        gatt = result.device.connectGatt(
                            this@WatchMainActivity, false, gattCallback, BluetoothDevice.TRANSPORT_LE
                        )
                    } catch (_: SecurityException) {
                        updateStatus("Faltan permisos Bluetooth")
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    updateStatus("Error Bluetooth: $errorCode")
                }
            }
            scanner?.startScan(listOf(filter), settings, scanCallback)
        } catch (_: Exception) {
            updateStatus("No se pudo iniciar Bluetooth LE")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            bluetoothGatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                try {
                    bluetoothGatt.discoverServices()
                } catch (_: SecurityException) {
                    bluetoothGatt.disconnect()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                patternCharacteristic = null
                gatt = null
                try { bluetoothGatt.close() } catch (_: Exception) {}
                runOnUiThread { updateStatus("Desconectado · buscando…") }
                if (senderMode) runOnUiThread { startScan() }
            }
        }

        override fun onServicesDiscovered(bluetoothGatt: BluetoothGatt, status: Int) {
            val characteristic = bluetoothGatt.getService(serviceUuid)?.getCharacteristic(patternUuid)
            runOnUiThread {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                    patternCharacteristic = characteristic
                    updateStatus("CONECTADO")
                    stopScan()
                } else {
                    updateStatus("Receptor incompatible")
                    bluetoothGatt.disconnect()
                }
            }
        }
    }

    private fun sendPattern(code: Int, hops: Int = 1, excludeAddress: String? = null) {
        val currentGatt = gatt
        val characteristic = patternCharacteristic
        if (currentGatt == null || characteristic == null || currentGatt.device.address == excludeAddress) {
            Toast.makeText(this, "Esperando conexión…", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            @Suppress("DEPRECATION")
            characteristic.value = byteArrayOf(code.toByte(), hops.coerceIn(0, 1).toByte())
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            val accepted = currentGatt.writeCharacteristic(characteristic)
            if (!accepted) Toast.makeText(this, "No se pudo enviar", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Permiso Bluetooth no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatus(message: String) {
        statusView?.text = message
        statusView?.setTextColor(if (message == "CONECTADO") 0xFF54D68B.toInt() else 0xFFFFD166.toInt())
    }

    private fun stopScan() {
        try { scanCallback?.let { scanner?.stopScan(it) } } catch (_: Exception) {}
        scanCallback = null
    }

    private fun closeSender() {
        try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}
        gatt = null
        patternCharacteristic = null
    }

    override fun onDestroy() {
        stopScan()
        closeSender()
        try { unregisterReceiver(relayReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
