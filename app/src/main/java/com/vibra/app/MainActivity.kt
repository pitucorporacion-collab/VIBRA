package com.vibra.app

import android.Manifest
import android.app.Activity
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
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class MainActivity : Activity() {
    private val serviceUuid = UUID.fromString("7b6a1000-7d4a-4f8a-9c2e-4f6a9d001000")
    private val patternUuid = UUID.fromString("7b6a1001-7d4a-4f8a-9c2e-4f6a9d001000")
    private var status: TextView? = null
    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var senderMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showRoleSelection()
    }

    private fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        buildList {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasPermissions() = requiredPermissions().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun withPermissions(action: () -> Unit) {
        if (hasPermissions()) action() else requestPermissions(requiredPermissions(), 41)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 41 && results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (senderMode) startSender() else startReceiver()
        } else if (requestCode == 41) {
            Toast.makeText(this, "VIBRA necesita permisos Bluetooth.", Toast.LENGTH_LONG).show()
        }
    }

    private fun screen(title: String, subtitle: String): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 36, 28, 36)
            setBackgroundColor(0xFF10131A.toInt())
        }
        layout.addView(TextView(this).apply {
            text = title; textSize = 34f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
        })
        status = TextView(this).apply {
            text = subtitle; textSize = 14f; setTextColor(0xFFB9C2D0.toInt())
            gravity = Gravity.CENTER; setPadding(0, 10, 0, 28)
        }
        layout.addView(status)
        setContentView(layout)
        return layout
    }

    private fun showRoleSelection() {
        val layout = screen("VIBRA", "TRANSMITÍ PATRONES DE VIBRACIÓN")
        addButton(layout, "EMISOR", 0xFF2368E8.toInt()) {
            senderMode = true; withPermissions { startSender() }
        }
        addButton(layout, "RECEPTOR", 0xFF293241.toInt()) {
            senderMode = false; withPermissions { startReceiver() }
        }
    }

    private fun startSender() {
        val layout = screen("VIBRA", "EMISOR · Buscando un receptor cercano…")
        addButton(layout, "MEDIUM", 0xFF2368E8.toInt()) { sendPattern(1) }
        addButton(layout, "♥  HEART BEAT", 0xFFB8325A.toInt()) { sendPattern(2) }
        addButton(layout, "ZIG ZIG ZIG", 0xFF6D4BC3.toInt()) { sendPattern(3) }
        startScan()
    }

    private fun startReceiver() {
        val intent = Intent(this, VibraService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        val layout = screen("VIBRA", "RECEPTOR ACTIVO")
        layout.addView(TextView(this).apply {
            text = "Este teléfono está listo para recibir.\n\nPodés apagar la pantalla: VIBRA seguirá escuchando en segundo plano."
            textSize = 18f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            setPadding(12, 20, 12, 20)
        })
        addButton(layout, "VOLVER", 0xFF293241.toInt()) {
            stopService(intent); showRoleSelection()
        }
    }

    private fun startScan() {
        try {
            val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter ?: return updateStatus("Bluetooth no disponible")
            if (!adapter.isEnabled) return updateStatus("Activá Bluetooth en este teléfono")
            scanner = adapter.bluetoothLeScanner ?: return updateStatus("Bluetooth LE no disponible")
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    scanner?.stopScan(this)
                    updateStatus("Receptor encontrado. Conectando…")
                    gatt?.close()
                    gatt = result.device.connectGatt(this@MainActivity, false, gattCallback)
                }
                override fun onScanFailed(errorCode: Int) { updateStatus("Error de búsqueda Bluetooth: $errorCode") }
            }
            scanner?.startScan(listOf(filter), settings, scanCallback)
        } catch (_: SecurityException) {
            updateStatus("Faltan permisos Bluetooth")
        } catch (_: Exception) {
            updateStatus("No se pudo buscar el receptor")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                g.close()
                if (gatt === g) gatt = null
                characteristic = null
                runOnUiThread { updateStatus("Desconectado. Buscando receptor…"); startScan() }
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
            val found = g.getService(serviceUuid)?.getCharacteristic(patternUuid)
            runOnUiThread {
                if (statusCode == BluetoothGatt.GATT_SUCCESS && found != null) {
                    gatt = g; characteristic = found
                    updateStatus("CONECTADO · Elegí un patrón")
                } else updateStatus("No se encontró el servicio VIBRA")
            }
        }
    }

    private fun sendPattern(code: Int) {
        val current = gatt
        val target = characteristic
        if (current == null || target == null) return updateStatus("Todavía no hay receptor conectado")
        try {
            target.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            target.value = byteArrayOf(code.toByte())
            if (!current.writeCharacteristic(target)) updateStatus("No se pudo enviar el patrón")
        } catch (_: SecurityException) {
            updateStatus("Permiso Bluetooth no disponible")
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread { status?.text = "EMISOR · $message" }
    }

    private fun addButton(parent: LinearLayout, label: String, color: Int, action: () -> Unit) {
        val button = Button(this).apply {
            text = label; textSize = 17f; isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(color)
            setPadding(12, 18, 12, 18)
            setOnClickListener { action() }
        }
        val params = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 10, 0, 10) }
        parent.addView(button, params)
    }

    override fun onDestroy() {
        try {
            scanCallback?.let { scanner?.stopScan(it) }
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
