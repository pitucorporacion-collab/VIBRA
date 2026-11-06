package com.vibra.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayDeque
import java.util.UUID

class MainActivity : Activity() {
    private val serviceUuid = UUID.fromString("7b6a1000-7d4a-4f8a-9c2e-4f6a9d001000")
    private val patternUuid = UUID.fromString("7b6a1001-7d4a-4f8a-9c2e-4f6a9d001000")

    private var statusText: TextView? = null
    private var statusLight: TextView? = null
    private var devicesText: TextView? = null
    private var gattScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val connectedGatts = linkedMapOf<String, BluetoothGatt>()
    private val characteristics = mutableMapOf<String, BluetoothGattCharacteristic>()
    private val connectingAddresses = mutableSetOf<String>()
    private val writeQueues = mutableMapOf<String, ArrayDeque<ByteArray>>()
    private var senderMode = false
    private var wifiGatePassed = false
    private var receiverIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkWifiBeforeOpening()
    }

    override fun onResume() {
        super.onResume()
        if (!wifiGatePassed && isWifiEnabled()) {
            wifiGatePassed = true
            showRoleSelection()
        }
    }

    private fun isWifiEnabled(): Boolean {
        return try {
            val manager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            manager.isWifiEnabled
        } catch (_: Exception) {
            false
        }
    }

    private fun checkWifiBeforeOpening() {
        if (isWifiEnabled()) {
            wifiGatePassed = true
            showRoleSelection()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Activá el Wi-Fi")
            .setMessage("VIBRA necesita que el Wi-Fi esté encendido para continuar. No hace falta conectarse a una red.")
            .setCancelable(false)
            .setPositiveButton("Abrir ajustes") { _, _ ->
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            .setNegativeButton("Salir") { _, _ -> finish() }
            .show()
    }

    private fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
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

    private fun baseScreen(title: String, subtitle: String): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(28), dp(22), dp(22))
            setBackgroundColor(0xFF10131A.toInt())
        }
        layout.addView(TextView(this).apply {
            text = title
            textSize = 32f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        layout.addView(TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(0xFFB9C2D0.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(18))
        }, LinearLayout.LayoutParams(-1, -2))
        setContentView(layout)
        return layout
    }

    private fun showRoleSelection() {
        val layout = baseScreen("VIBRA", "COMUNICACIÓN SECRETA POR VIBRACIÓN")
        addButton(layout, "EMISOR", 0xFF2368E8.toInt()) {
            senderMode = true
            withPermissions { startSender() }
        }
        addButton(layout, "RECEPTOR", 0xFF293241.toInt()) {
            senderMode = false
            withPermissions { startReceiver() }
        }
    }

    private fun startSender() {
        closeSenderConnections()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(22), dp(16), dp(14))
            setBackgroundColor(0xFF10131A.toInt())
        }
        root.addView(TextView(this).apply {
            text = "VIBRA"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        }, LinearLayout.LayoutParams(-1, -2))

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(3))
        }
        statusLight = TextView(this).apply {
            text = "●"
            textSize = 19f
            setTextColor(0xFFE53935.toInt())
        }
        statusText = TextView(this).apply {
            text = " Sin dispositivos conectados"
            textSize = 14f
            setTextColor(0xFFFF7777.toInt())
        }
        statusRow.addView(statusLight)
        statusRow.addView(statusText)
        root.addView(statusRow)

        devicesText = TextView(this).apply {
            text = "Buscando receptores cercanos…"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFFB9C2D0.toInt())
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(devicesText)

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val buttons = listOf(
            Triple("MEDIUM", 0xFF2368E8.toInt(), 1),
            Triple("HEART ×1", 0xFFB8325A.toInt(), 2),
            Triple("HEART ×2", 0xFFB8325A.toInt(), 3),
            Triple("HEART ×3", 0xFFB8325A.toInt(), 4),
            Triple("HEART ×4", 0xFFB8325A.toInt(), 5),
            Triple("ZIG ZIG ZIG", 0xFF6D4BC3.toInt(), 6)
        )
        for (rowIndex in 0..2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (column in 0..1) {
                val item = buttons[rowIndex * 2 + column]
                val button = Button(this).apply {
                    text = item.first
                    textSize = 16f
                    isAllCaps = false
                    setTextColor(0xFFFFFFFF.toInt())
                    backgroundTintList = android.content.res.ColorStateList.valueOf(item.second)
                    setOnClickListener { sendPattern(item.third) }
                }
                row.addView(button, LinearLayout.LayoutParams(0, -1, 1f).apply {
                    setMargins(dp(7), dp(7), dp(7), dp(7))
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))

        addButton(root, "← VOLVER", 0xFF293241.toInt()) {
            stopScanning()
            closeSenderConnections()
            showRoleSelection()
        }
        setContentView(root)
        updateConnectionDisplay()
        startScan()
    }

    private fun startReceiver() {
        val intent = Intent(this, VibraService::class.java)
        receiverIntent = intent
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        val layout = baseScreen("VIBRA", "RECEPTOR ACTIVO")
        layout.addView(TextView(this).apply {
            text = "Este teléfono está listo para recibir.\n\nPodés apagar la pantalla: VIBRA seguirá escuchando en segundo plano."
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(20), dp(12), dp(20))
        })
        addButton(layout, "← VOLVER", 0xFF293241.toInt()) {
            stopService(intent)
            receiverIntent = null
            showRoleSelection()
        }
    }

    private fun startScan() {
        try {
            val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter ?: return updateConnectionDisplay("Bluetooth no disponible")
            if (!adapter.isEnabled) return updateConnectionDisplay("Activá Bluetooth en este teléfono")
            gattScanner = adapter.bluetoothLeScanner
                ?: return updateConnectionDisplay("Bluetooth LE no disponible")
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val address = result.device.address ?: return
                    if (connectedGatts.containsKey(address) || !connectingAddresses.add(address)) return
                    try {
                        val device = result.device
                        val name = try { device.name } catch (_: SecurityException) { null }
                        updateConnectionDisplay(if (name.isNullOrBlank()) "Conectando con un receptor…" else "Conectando con $name…")
                        val gatt = if (Build.VERSION.SDK_INT >= 23) {
                            device.connectGatt(this@MainActivity, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                        } else {
                            device.connectGatt(this@MainActivity, false, gattCallback)
                        }
                        if (gatt == null) connectingAddresses.remove(address)
                    } catch (_: SecurityException) {
                        connectingAddresses.remove(address)
                        updateConnectionDisplay("Faltan permisos Bluetooth")
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    updateConnectionDisplay("Error de búsqueda Bluetooth: $errorCode")
                }
            }
            gattScanner?.startScan(listOf(filter), settings, scanCallback)
            updateConnectionDisplay()
        } catch (_: SecurityException) {
            updateConnectionDisplay("Faltan permisos Bluetooth")
        } catch (_: Exception) {
            updateConnectionDisplay("No se pudo buscar receptores")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, statusCode: Int, newState: Int) {
            val address = gatt.device.address
            if (newState == BluetoothProfile.STATE_CONNECTED && statusCode == BluetoothGatt.GATT_SUCCESS) {
                try {
                    gatt.discoverServices()
                } catch (_: SecurityException) {
                    gatt.disconnect()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || statusCode != BluetoothGatt.GATT_SUCCESS) {
                connectingAddresses.remove(address)
                connectedGatts.remove(address)
                characteristics.remove(address)
                writeQueues.remove(address)
                try { gatt.close() } catch (_: Exception) {}
                runOnUiThread {
                    updateConnectionDisplay()
                    if (senderMode) startScan()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, statusCode: Int) {
            val address = gatt.device.address
            val found = gatt.getService(serviceUuid)?.getCharacteristic(patternUuid)
            runOnUiThread {
                connectingAddresses.remove(address)
                if (statusCode == BluetoothGatt.GATT_SUCCESS && found != null) {
                    connectedGatts[address] = gatt
                    characteristics[address] = found
                } else {
                    try { gatt.disconnect(); gatt.close() } catch (_: Exception) {}
                }
                updateConnectionDisplay()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeNext(gatt)
        }
    }

    private fun sendPattern(code: Int) {
        if (connectedGatts.isEmpty()) {
            Toast.makeText(this, "Todavía no hay receptores conectados", Toast.LENGTH_SHORT).show()
            return
        }
        val payload = byteArrayOf(code.toByte())
        connectedGatts.keys.toList().forEach { address ->
            val queue = writeQueues.getOrPut(address) { ArrayDeque() }
            queue.addLast(payload)
            connectedGatts[address]?.let { writeNext(it) }
        }
    }

    private fun writeNext(gatt: BluetoothGatt) {
        val address = gatt.device.address
        val queue = writeQueues[address] ?: return
        if (queue.isEmpty()) return
        val characteristic = characteristics[address] ?: return
        try {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = queue.first
            if (gatt.writeCharacteristic(characteristic)) {
                queue.removeFirst()
            }
        } catch (_: SecurityException) {
            runOnUiThread { updateConnectionDisplay("Permiso Bluetooth no disponible") }
        }
    }

    private fun updateConnectionDisplay(message: String? = null) {
        runOnUiThread {
            val count = connectedGatts.size
            if (count > 0) {
                statusLight?.setTextColor(0xFF20C76A.toInt())
                statusText?.text = " $count conectado(s)"
                statusText?.setTextColor(0xFF75E0A2.toInt())
                val names = connectedGatts.values.mapIndexed { index, gatt ->
                    val name = try { gatt.device.name } catch (_: SecurityException) { null }
                    name?.takeIf { it.isNotBlank() } ?: "Receptor ${index + 1}"
                }
                devicesText?.text = names.joinToString("  ·  ")
            } else {
                statusLight?.setTextColor(0xFFE53935.toInt())
                statusText?.text = " Sin dispositivos conectados"
                statusText?.setTextColor(0xFFFF7777.toInt())
                devicesText?.text = message ?: "Buscando receptores cercanos…"
            }
        }
    }

    private fun stopScanning() {
        try {
            scanCallback?.let { gattScanner?.stopScan(it) }
        } catch (_: Exception) {}
        scanCallback = null
    }

    private fun closeSenderConnections() {
        stopScanning()
        connectedGatts.values.toList().forEach {
            try { it.disconnect(); it.close() } catch (_: Exception) {}
        }
        connectedGatts.clear()
        characteristics.clear()
        connectingAddresses.clear()
        writeQueues.clear()
    }

    private fun addButton(parent: LinearLayout, label: String, color: Int, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(color)
            setPadding(dp(12), dp(16), dp(12), dp(16))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(7), 0, dp(7))
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        closeSenderConnections()
        receiverIntent?.let { stopService(it) }
        super.onDestroy()
    }
}
