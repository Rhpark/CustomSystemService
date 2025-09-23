package kr.open.library.systemmanager.controller.bluetooth.base

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.error.BleServiceError

/**
 * BLE 권한 관리 시스템
 * BLE Permission Management System
 * 
 * Android 버전별로 다른 BLE 권한 체계를 통합 관리합니다.
 * Manages different BLE permission systems across Android versions.
 * 
 * 권한 체계:
 * Permission System:
 * - API 31+ : BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT
 * - API 23-30: BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION + 위치 서비스
 * - API 22-  : BLUETOOTH, BLUETOOTH_ADMIN
 */
object BlePermissionManager {
    
    /**
     * BLE 권한 정의
     * BLE Permission Definitions
     */
    object Permissions {
        // API 31+ 권한 (상수로 정의, 런타임에 사용 여부 결정)
        const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
        const val BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
        const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
        
        // Legacy 권한
        const val BLUETOOTH = Manifest.permission.BLUETOOTH
        const val BLUETOOTH_ADMIN = Manifest.permission.BLUETOOTH_ADMIN
        const val ACCESS_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
        const val ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
    }
    
    /**
     * BLE 역할별 권한 그룹
     * Permission groups by BLE role
     */
    enum class BleRole {
        CENTRAL,    // 클라이언트 (스캔, 연결)
        PERIPHERAL, // 서버 (광고, GATT 서버)
        BOTH        // 양방향 (Central + Peripheral)
    }
    
    /**
     * 권한 상태 결과
     * Permission status result
     */
    data class PermissionStatus(
        val isAllGranted: Boolean,
        val missingPermissions: List<String>,
        val isLocationServiceRequired: Boolean,
        val isLocationServiceEnabled: Boolean,
        val suggestedAction: String?
    )
    
    /**
     * 특정 역할에 필요한 권한 목록 반환
     * Returns required permissions for specific role
     */
    fun getRequiredPermissions(role: BleRole): List<String> {
        return when {
            Build.VERSION.SDK_INT >= 31 -> getApi31Permissions(role)
            Build.VERSION.SDK_INT >= 23 -> getApi23Permissions(role)
            else -> getApi22Permissions(role)
        }
    }
    
    /**
     * API 31+ 권한 (Android 12+)
     */
    private fun getApi31Permissions(role: BleRole): List<String> {
        return buildList {
            when (role) {
                BleRole.CENTRAL -> {
                    add(Permissions.BLUETOOTH_SCAN)
                    add(Permissions.BLUETOOTH_CONNECT)
                }
                BleRole.PERIPHERAL -> {
                    add(Permissions.BLUETOOTH_ADVERTISE)
                    add(Permissions.BLUETOOTH_CONNECT)
                }
                BleRole.BOTH -> {
                    add(Permissions.BLUETOOTH_SCAN)
                    add(Permissions.BLUETOOTH_ADVERTISE)
                    add(Permissions.BLUETOOTH_CONNECT)
                }
            }
        }
    }
    
    /**
     * API 23-30 권한 (Android 6.0-11)
     */
    private fun getApi23Permissions(role: BleRole): List<String> {
        return buildList {
            add(Permissions.BLUETOOTH)
            add(Permissions.BLUETOOTH_ADMIN)
            // 스캐닝에는 위치 권한 필수
            if (role == BleRole.CENTRAL || role == BleRole.BOTH) {
                add(Permissions.ACCESS_FINE_LOCATION)
            }
        }
    }
    
    /**
     * API 22 이하 권한 (Android 5.1 이하)
     */
    private fun getApi22Permissions(role: BleRole): List<String> {
        return listOf(
            Permissions.BLUETOOTH,
            Permissions.BLUETOOTH_ADMIN
        )
    }
    
    /**
     * 권한 상태 확인
     * Check permission status
     */
    fun checkPermissionStatus(context: Context, role: BleRole): PermissionStatus {
        val requiredPermissions = getRequiredPermissions(role)
        val missingPermissions = mutableListOf<String>()
        
        // 권한 확인
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission)
            }
        }
        
        // 위치 서비스 확인 (API 23+ 스캐닝)
        val isLocationServiceRequired = isLocationServiceRequired(role)
        val isLocationServiceEnabled = if (isLocationServiceRequired) {
            isLocationServiceEnabled(context)
        } else {
            true // 필요 없으면 항상 true
        }
        
        val isAllGranted = missingPermissions.isEmpty() && 
                          (!isLocationServiceRequired || isLocationServiceEnabled)
        
        val suggestedAction = getSuggestedAction(missingPermissions, isLocationServiceRequired, isLocationServiceEnabled)
        
        return PermissionStatus(
            isAllGranted = isAllGranted,
            missingPermissions = missingPermissions,
            isLocationServiceRequired = isLocationServiceRequired,
            isLocationServiceEnabled = isLocationServiceEnabled,
            suggestedAction = suggestedAction
        )
    }
    
    /**
     * 위치 서비스가 필요한지 확인
     */
    private fun isLocationServiceRequired(role: BleRole): Boolean {
        return Build.VERSION.SDK_INT in 23..30 && 
               (role == BleRole.CENTRAL || role == BleRole.BOTH)
    }
    
    /**
     * 위치 서비스 활성화 상태 확인
     */
    private fun isLocationServiceEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    /**
     * 권한 상태에 따른 제안 조치
     */
    private fun getSuggestedAction(
        missingPermissions: List<String>,
        isLocationServiceRequired: Boolean,
        isLocationServiceEnabled: Boolean
    ): String? {
        return when {
            missingPermissions.isNotEmpty() -> "앱 설정에서 권한을 허용해주세요"
            isLocationServiceRequired && !isLocationServiceEnabled -> "설정에서 위치 서비스를 켜주세요"
            else -> null
        }
    }
    
    /**
     * 누락된 권한에 따른 BLE 오류 생성
     * Generate BLE error based on missing permissions
     */
    fun createPermissionError(permissionStatus: PermissionStatus): BleServiceError.Permission {
        if (permissionStatus.isAllGranted) {
            throw IllegalArgumentException("No permission error when all permissions are granted")
        }
        
        val missingPermissions = permissionStatus.missingPermissions
        
        return when {
            // 위치 서비스 비활성화
            permissionStatus.isLocationServiceRequired && !permissionStatus.isLocationServiceEnabled -> {
                BleServiceError.Permission.LocationServiceDisabled
            }
            
            // 단일 권한 누락
            missingPermissions.size == 1 -> {
                when (missingPermissions.first()) {
                    Permissions.BLUETOOTH_SCAN -> BleServiceError.Permission.ScanPermissionMissing
                    Permissions.BLUETOOTH_ADVERTISE -> BleServiceError.Permission.AdvertisePermissionMissing
                    Permissions.BLUETOOTH_CONNECT -> BleServiceError.Permission.ConnectPermissionMissing
                    Permissions.ACCESS_FINE_LOCATION -> BleServiceError.Permission.LocationPermissionMissing
                    else -> BleServiceError.Permission.MultiplePermissionsMissing(missingPermissions)
                }
            }
            
            // 다중 권한 누락
            else -> BleServiceError.Permission.MultiplePermissionsMissing(missingPermissions)
        }
    }
    
    /**
     * 권한 요청을 위한 Intent 생성
     * Create intent for permission request
     */
    fun createPermissionIntent(context: Context, role: BleRole): Intent? {
        val permissionStatus = checkPermissionStatus(context, role)
        
        return when {
            // 위치 서비스 설정
            permissionStatus.isLocationServiceRequired && !permissionStatus.isLocationServiceEnabled -> {
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            }
            
            // 앱 권한 설정
            permissionStatus.missingPermissions.isNotEmpty() -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
            
            else -> null
        }
    }
    
    /**
     * 사용자 친화적 권한 안내 메시지
     * User-friendly permission guidance message
     */
    fun getPermissionGuidanceMessage(context: Context, role: BleRole): String? {
        val permissionStatus = checkPermissionStatus(context, role)
        
        if (permissionStatus.isAllGranted) {
            return null
        }
        
        return buildString {
            when (role) {
                BleRole.CENTRAL -> append("Bluetooth 기기 검색을 위해서는")
                BleRole.PERIPHERAL -> append("Bluetooth 광고를 위해서는")
                BleRole.BOTH -> append("Bluetooth 기능을 위해서는")
            }
            
            when {
                permissionStatus.isLocationServiceRequired && !permissionStatus.isLocationServiceEnabled -> {
                    append(" 위치 서비스를 켜주세요.")
                }
                
                permissionStatus.missingPermissions.contains(Permissions.ACCESS_FINE_LOCATION) -> {
                    append(" 위치 권한이 필요합니다. (기기 검색용)")
                }
                
                permissionStatus.missingPermissions.isNotEmpty() -> {
                    append(" 다음 권한들이 필요합니다:\n")
                    permissionStatus.missingPermissions.forEach { permission ->
                        append("- ${getPermissionDisplayName(permission)}\n")
                    }
                }
            }
        }
    }
    
    /**
     * 권한 표시 이름 반환
     */
    private fun getPermissionDisplayName(permission: String): String = when (permission) {
        Permissions.BLUETOOTH_SCAN -> "Bluetooth 스캔"
        Permissions.BLUETOOTH_ADVERTISE -> "Bluetooth 광고"
        Permissions.BLUETOOTH_CONNECT -> "Bluetooth 연결"
        Permissions.BLUETOOTH -> "Bluetooth"
        Permissions.BLUETOOTH_ADMIN -> "Bluetooth 관리"
        Permissions.ACCESS_FINE_LOCATION -> "정확한 위치"
        Permissions.ACCESS_COARSE_LOCATION -> "대략적 위치"
        else -> permission
    }
    
    /**
     * 권한 상태 로깅
     * Log permission status
     */
    fun logPermissionStatus(context: Context, role: BleRole, tag: String = "BlePermissionManager") {
        val status = checkPermissionStatus(context, role)
        
        Logx.d(tag, "=== BLE Permission Status for $role ===")
        Logx.d(tag, "Android API Level: ${Build.VERSION.SDK_INT}")
        Logx.d(tag, "All permissions granted: ${status.isAllGranted}")
        
        if (status.missingPermissions.isNotEmpty()) {
            Logx.w(tag, "Missing permissions: ${status.missingPermissions}")
        }
        
        if (status.isLocationServiceRequired) {
            Logx.d(tag, "Location service required: ${status.isLocationServiceRequired}")
            Logx.d(tag, "Location service enabled: ${status.isLocationServiceEnabled}")
        }
        
        status.suggestedAction?.let { action ->
            Logx.i(tag, "Suggested action: $action")
        }
        
        Logx.d(tag, "=====================================")
    }
}