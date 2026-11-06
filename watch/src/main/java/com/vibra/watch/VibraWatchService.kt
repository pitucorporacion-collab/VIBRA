package com.vibra.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.UUID

class VibraWatchService : Service() {
    private val serviceUuid = UUID.fromString("7b6a1000-7d4a-4f8a-9c2e-4f6a9d001000")
    private val patternUuid = UUID.fromString("7b6a1001-7d4a-4f8a-9c2e-4f6a9d001000")
    private var gattServer: BluetoothGattServer? = null
    private var advertiserCallback: AdvertiseCallback? = null

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS && service.uuid == serviceUuid) startAdvertising()
        }

        override fun onCharacteristicWriteRequest(
            device: android.bluetooth.BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == patternUuid && offset == 0 && value.isNotEmpty()) {
                playPattern(value[0].toInt() and 0xff)
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        openGattServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startAsForeground() {
        val channelId = "vibra_watch_receiver"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "VIBRA Watch", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
                .setContentTitle("VIBRA Watch está activo")
                .setContentText("Listo para recibir señales")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("VIBRA Watch está activo")
                .setContentText("Listo para recibir señales")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setOngoing(true)
                .build()
        }
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(21, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(21, notification)
        }
    }

    private fun openGattServer() {
        try {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter ?: return
            if (!adapter.isEnabled) return
            gattServer = manager.openGattServer(this, gattCallback)
            val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val characteristic = BluetoothGattCharacteristic(
                patternUuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(characteristic)
            gattServer?.addService(service)
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun startAdvertising() {
        try {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val advertiser = manager.adapter?.bluetoothLeAdvertiser ?: return
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(serviceUuid))
                .build()
            advertiserCallback = object : AdvertiseCallback() {}
            advertiser.startAdvertising(settings, data, advertiserCallback)
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun playPattern(code: Int) {
        val vibrator = getVibrator()
        if (!vibrator.hasVibrator()) return
        if (code == 0) {
            vibrator.cancel()
            return
        }
        when (code) {
            1 -> {
                vibrator.cancel()
                vibrator.vibrate(VibrationEffect.createOneShot(300, 150))
            }
            in 2..5 -> {
                val beats = code - 1
                val timings = ArrayList<Long>()
                val amplitudes = ArrayList<Int>()
                timings.add(0L)
                amplitudes.add(0)
                repeat(beats) { index ->
                    timings.add(105L); amplitudes.add(255)
                    timings.add(85L); amplitudes.add(0)
                    timings.add(175L); amplitudes.add(210)
                    if (index < beats - 1) {
                        timings.add(500L); amplitudes.add(0)
                    }
                }
                vibrator.cancel()
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        timings.toLongArray(),
                        amplitudes.toIntArray(),
                        -1
                    )
                )
            }
            6 -> {
                vibrator.cancel()
                vibrator.vibrate(VibrationEffect.createOneShot(600, 220))
            }
        }
    }

    override fun onDestroy() {
        try {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            manager.adapter?.bluetoothLeAdvertiser?.let { advertiser ->
                advertiserCallback?.let { advertiser.stopAdvertising(it) }
            }
        } catch (_: Exception) {}
        gattServer?.close()
        try { getVibrator().cancel() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
