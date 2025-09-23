package kr.open.library.systemmanager.controller.bluetooth.base

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat

class BlePermissionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BlePermissionManager"
        
        // Android 12(API 31) 이상에서 필요한 새로운 권한들
        val REQUIRED_PERMISSIONS_API31_PLUS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    
    fun checkAllPermissions(): Boolean {
        Log.d(TAG, "Checking BLE permissions...")
        
        // 1. Bluetooth 하드웨어 지원 확인
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(TAG, "BLE is not supported on this device")
            return false
        }
        
        // 2. BluetoothAdapter 확인
        if (bluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter is null")
            return false
        }
        
        // 3. Bluetooth 활성화 확인
        if (!bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth is not enabled")
            return false
        }
        
        // 4. 권한 확인
        val missingPermissions = getMissingPermissions()
        if (missingPermissions.isNotEmpty()) {
            Log.e(TAG, "Missing permissions: ${missingPermissions.joinToString(", ")}")
            return false
        }
        
        Log.d(TAG, "All BLE permissions are granted")
        return true
    }
    
    fun getMissingPermissions(): List<String> {
        return REQUIRED_PERMISSIONS_API31_PLUS.filter { permission ->
            ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun getRequiredPermissions(): Array<String> {
        return REQUIRED_PERMISSIONS_API31_PLUS
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    fun isBleSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }
    
    fun checkSpecificPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    fun canScan(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSpecificPermission(Manifest.permission.BLUETOOTH_SCAN) &&
            checkSpecificPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            checkSpecificPermission(Manifest.permission.BLUETOOTH) &&
            checkSpecificPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    
    fun canAdvertise(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSpecificPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            checkSpecificPermission(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }
    
    fun canConnect(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSpecificPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            checkSpecificPermission(Manifest.permission.BLUETOOTH)
        }
    }
    
    fun getPermissionStatus(): String {
        val status = StringBuilder()
        status.appendLine("=== BLE Permission Status ===")
        status.appendLine("BLE Supported: ${isBleSupported()}")
        status.appendLine("Bluetooth Enabled: ${isBluetoothEnabled()}")
        status.appendLine("Android Version: ${Build.VERSION.SDK_INT}")
        status.appendLine("")
        status.appendLine("Required Permissions:")
        
        REQUIRED_PERMISSIONS_API31_PLUS.forEach { permission ->
            val granted = checkSpecificPermission(permission)
            val status_icon = if (granted) "✓" else "✗"
            status.appendLine("  $status_icon $permission")
        }
        
        status.appendLine("")
        status.appendLine("Capability Check:")
        status.appendLine("  Can Scan: ${canScan()}")
        status.appendLine("  Can Advertise: ${canAdvertise()}")
        status.appendLine("  Can Connect: ${canConnect()}")
        
        return status.toString()
    }
}