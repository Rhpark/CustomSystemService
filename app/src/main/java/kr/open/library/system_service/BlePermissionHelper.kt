package kr.open.library.system_service

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.base.BleComponent

/**
 * BLE 권한 요청을 위한 헬퍼 클래스
 * 실제 두 스마트폰 간 BLE 통신을 위해 필요
 */
class BlePermissionHelper(private val activity: AppCompatActivity) {
    
    private var onPermissionResult: ((Boolean) -> Unit)? = null
    
    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> = 
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            Logx.d("BlePermissionHelper", "Permission result: $permissions")
            
            if (allGranted) {
                checkLocationServices()
            } else {
                val deniedPermissions = permissions.filterValues { !it }.keys
                Logx.w("BlePermissionHelper", "Denied permissions: $deniedPermissions")
                onPermissionResult?.invoke(false)
            }
        }
    
    private val locationSettingsLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // 위치 설정에서 돌아온 후 다시 체크
            val locationEnabled = isLocationEnabled()
            Logx.d("BlePermissionHelper", "Location services enabled: $locationEnabled")
            onPermissionResult?.invoke(locationEnabled)
        }
    
    /**
     * BLE 권한 요청 시작
     * @param callback 권한 요청 완료 콜백 (true: 성공, false: 실패)
     */
    fun requestBlePermissions(callback: (Boolean) -> Unit) {
        onPermissionResult = callback
        
        val requiredPermissions = BleComponent.BLE_PERMISSIONS.toTypedArray()
        Logx.d("BlePermissionHelper", "Requesting permissions: ${requiredPermissions.toList()}")
        
        // 이미 모든 권한이 있는지 확인
        val deniedPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (deniedPermissions.isEmpty()) {
            // 권한은 있지만 위치 서비스 확인 필요
            checkLocationServices()
        } else {
            // 권한 요청
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }
    
    /**
     * 위치 서비스 활성화 확인 및 요청
     */
    private fun checkLocationServices() {
        // Android 12+에서는 위치 서비스가 필수가 아닐 수 있음
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Logx.d("BlePermissionHelper", "Android 12+ - Location services may not be required")
            onPermissionResult?.invoke(true)
            return
        }
        
        // Android 6.0-11에서는 위치 서비스 필수
        if (!isLocationEnabled()) {
            Logx.w("BlePermissionHelper", "Location services disabled - requesting user to enable")
            showLocationSettingsDialog()
        } else {
            onPermissionResult?.invoke(true)
        }
    }
    
    /**
     * 위치 서비스 활성화 상태 확인
     */
    private fun isLocationEnabled(): Boolean {
        val locationManager = activity.getSystemService(Activity.LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }
    
    /**
     * 위치 설정 화면으로 이동 다이얼로그
     */
    private fun showLocationSettingsDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(activity)
        builder.setTitle("위치 서비스 필요")
        builder.setMessage("BLE 스캔을 위해 위치 서비스를 활성화해야 합니다.\n(실제 위치 정보는 수집되지 않습니다)")
        builder.setPositiveButton("설정으로 이동") { _, _ ->
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            locationSettingsLauncher.launch(intent)
        }
        builder.setNegativeButton("취소") { _, _ ->
            onPermissionResult?.invoke(false)
        }
        builder.setCancelable(false)
        builder.show()
    }
    
    /**
     * 블루투스 활성화 확인
     */
    fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = activity.getSystemService(Activity.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        return bluetoothManager.adapter?.isEnabled == true
    }
    
    /**
     * 블루투스 활성화 요청
     */
    fun requestBluetoothEnable(): Boolean {
        if (isBluetoothEnabled()) return true
        
        try {
            val enableBtIntent = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivity(enableBtIntent)
            return false // 비동기 요청이므로 즉시 false 반환
        } catch (e: SecurityException) {
            Logx.e("BlePermissionHelper", "Cannot request Bluetooth enable: ${e.message}")
            return false
        }
    }
    
    /**
     * 모든 BLE 필수 조건 확인
     */
    fun checkAllBleRequirements(): String {
        val issues = mutableListOf<String>()
        
        // 블루투스 활성화 확인
        if (!isBluetoothEnabled()) {
            issues.add("❌ 블루투스가 비활성화됨")
        }
        
        // 권한 확인
        val deniedPermissions = BleComponent.BLE_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (deniedPermissions.isNotEmpty()) {
            issues.add("❌ 권한 부족: ${deniedPermissions.joinToString(", ")}")
        }
        
        // 위치 서비스 확인 (Android 6.0-11)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isLocationEnabled()) {
            issues.add("❌ 위치 서비스 비활성화 (Android ${Build.VERSION.SDK_INT}에서 필수)")
        }
        
        return if (issues.isEmpty()) {
            "✅ 모든 BLE 요구사항 충족"
        } else {
            "BLE 통신 문제:\n${issues.joinToString("\n")}"
        }
    }
}