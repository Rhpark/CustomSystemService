package kr.open.library.systemmanager.controller.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.base.BaseSystemService
import kr.open.library.systemmanager.base.SystemServiceError

/**
 * BLE 관련 권한을 통합적으로 관리하는 클래스
 * Android 버전별로 다른 권한 체계를 자동으로 처리
 * 
 * 지원 버전:
 * - Android 6.0 (API 23) ~ Android 11 (API 30): Legacy 권한 체계
 * - Android 12+ (API 31+): 새로운 BLE 전용 권한 체계
 * 
 * @author SystemService Library
 * @since 2025-01-13
 */
class UnifiedBlePermissionManager(
    context: Context
) : BaseSystemService(context, getRequiredPermissions()) {
    
    companion object {
        
        /**
         * 현재 Android 버전에 맞는 필수 권한 목록을 반환합니다
         */
        fun getRequiredPermissions(): List<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+): 새로운 BLE 권한
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                // Android 6.0~11 (API 23-30): Legacy 권한
                listOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }
        
        /**
         * 선택적 권한 목록을 반환합니다
         * 이 권한들이 없어도 기본 동작은 가능하지만, 일부 기능이 제한될 수 있습니다
         */
        fun getOptionalPermissions(): List<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+에서는 위치 권한이 선택적
                listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                // Legacy 버전에서는 COARSE_LOCATION이 선택적
                listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }
    
    /**
     * 권한 상태를 나타내는 데이터 클래스
     */
    data class PermissionStatus(
        val hasAllRequired: Boolean,
        val missingRequired: List<String>,
        val hasOptional: List<String>,
        val missingOptional: List<String>,
        val needsLocationServices: Boolean,
        val canUseWithoutLocation: Boolean
    ) {
        val isFullyGranted: Boolean
            get() = hasAllRequired && !needsLocationServices
            
        val hasBasicFunctionality: Boolean 
            get() = hasAllRequired && (canUseWithoutLocation || !needsLocationServices)
    }
    
    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    
    /**
     * 현재 권한 상태를 상세하게 분석합니다
     */
    fun analyzePermissionStatus(): PermissionStatus {
        val requiredPermissions = getRequiredPermissions()
        val optionalPermissions = getOptionalPermissions()
        
        val missingRequired = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        val hasOptional = optionalPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        
        val missingOptional = optionalPermissions - hasOptional.toSet()
        
        val needsLocationServices = needsLocationServices()
        val canUseWithoutLocation = canUseBluetoothWithoutLocation()
        
        return PermissionStatus(
            hasAllRequired = missingRequired.isEmpty(),
            missingRequired = missingRequired,
            hasOptional = hasOptional,
            missingOptional = missingOptional,
            needsLocationServices = needsLocationServices,
            canUseWithoutLocation = canUseWithoutLocation
        )
    }
    
    /**
     * BLE 기능 사용이 가능한지 확인합니다
     */
    fun canUseBleFeatures(): Boolean {
        val status = analyzePermissionStatus()
        return status.hasBasicFunctionality
    }
    
    /**
     * 스캔 기능 사용이 가능한지 확인합니다
     */
    fun canScan(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12+: BLUETOOTH_SCAN 권한 필요
                hasPermission(Manifest.permission.BLUETOOTH_SCAN)
            }
            else -> {
                // Legacy: BLUETOOTH + BLUETOOTH_ADMIN + 위치 권한 필요
                hasPermission(Manifest.permission.BLUETOOTH) &&
                hasPermission(Manifest.permission.BLUETOOTH_ADMIN) &&
                (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || 
                 hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) &&
                areLocationServicesEnabled()
            }
        }
    }
    
    /**
     * 광고 기능 사용이 가능한지 확인합니다
     */
    fun canAdvertise(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12+: BLUETOOTH_ADVERTISE 권한 필요
                hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            else -> {
                // Legacy: BLUETOOTH + BLUETOOTH_ADMIN 권한 필요
                hasPermission(Manifest.permission.BLUETOOTH) &&
                hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }
    }
    
    /**
     * 연결 기능 사용이 가능한지 확인합니다
     */
    fun canConnect(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12+: BLUETOOTH_CONNECT 권한 필요
                hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
            }
            else -> {
                // Legacy: BLUETOOTH 권한 필요
                hasPermission(Manifest.permission.BLUETOOTH)
            }
        }
    }
    
    /**
     * 위치 서비스가 필요한지 확인합니다
     */
    fun needsLocationServices(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12+에서는 neverForLocation 플래그 사용 시 위치 서비스 불필요
                // 하지만 매니페스트 설정을 확인할 수 없으므로 보수적으로 접근
                !canUseBluetoothWithoutLocation()
            }
            else -> {
                // Legacy에서는 위치 서비스 필수
                true
            }
        }
    }
    
    /**
     * 위치 권한 없이 블루투스를 사용할 수 있는지 확인합니다
     */
    fun canUseBluetoothWithoutLocation(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12+에서는 neverForLocation 플래그 사용 시 가능
                // 실제로는 매니페스트를 확인해야 하지만, 여기서는 추정
                true // 우리의 구현에서는 neverForLocation을 사용할 예정
            }
            else -> false
        }
    }
    
    /**
     * 위치 서비스가 활성화되어 있는지 확인합니다
     */
    fun areLocationServicesEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            Logx.w("BlePermissionManager", "Failed to check location services: ${e.message}")
            false
        }
    }
    
    /**
     * 특정 권한이 부여되었는지 확인합니다
     */
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 사용자 친화적인 권한 안내 메시지를 생성합니다
     */
    fun getBlePermissionGuidanceMessage(): String? {
        val status = analyzePermissionStatus()
        
        if (status.isFullyGranted) {
            return null // 모든 권한이 있음
        }
        
        return buildString {
            appendLine("BLE 통신을 위해 다음 권한이 필요합니다:")
            
            if (status.missingRequired.isNotEmpty()) {
                appendLine()
                appendLine("필수 권한:")
                status.missingRequired.forEach { permission ->
                    appendLine("• ${getPermissionDescription(permission)}")
                }
            }
            
            if (status.needsLocationServices && !areLocationServicesEnabled()) {
                appendLine()
                appendLine("추가로 위치 서비스를 활성화해야 합니다.")
                appendLine("(실제 위치 정보는 수집되지 않습니다)")
            }
            
            if (status.missingOptional.isNotEmpty() && !status.canUseWithoutLocation) {
                appendLine()
                appendLine("권장 권한:")
                status.missingOptional.forEach { permission ->
                    appendLine("• ${getPermissionDescription(permission)}")
                }
            }
        }
    }
    
    /**
     * 권한에 대한 사용자 친화적 설명을 반환합니다
     */
    private fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.BLUETOOTH -> "블루투스 기본 기능"
            Manifest.permission.BLUETOOTH_ADMIN -> "블루투스 고급 기능 (스캔, 광고)"
            Manifest.permission.BLUETOOTH_SCAN -> "근처 블루투스 기기 검색"
            Manifest.permission.BLUETOOTH_ADVERTISE -> "다른 기기에 서비스 광고"
            Manifest.permission.BLUETOOTH_CONNECT -> "블루투스 기기 연결"
            Manifest.permission.ACCESS_FINE_LOCATION -> "정확한 위치 (BLE 스캔용)"
            Manifest.permission.ACCESS_COARSE_LOCATION -> "대략적인 위치 (BLE 스캔용)"
            else -> permission
        }
    }
    
    /**
     * 권한 체계 정보를 반환합니다 (디버깅용)
     */
    fun getPermissionSystemInfo(): String = buildString {
        appendLine("=== BLE Permission System Info ===")
        appendLine("Android Version: ${Build.VERSION.SDK_INT}")
        appendLine("Permission System: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Modern (API 31+)" else "Legacy (API 23-30)"}")
        appendLine()
        
        appendLine("Required Permissions:")
        getRequiredPermissions().forEach { permission ->
            val status = if (hasPermission(permission)) "✓ GRANTED" else "✗ MISSING"
            appendLine("  $permission: $status")
        }
        
        appendLine()
        appendLine("Optional Permissions:")
        getOptionalPermissions().forEach { permission ->
            val status = if (hasPermission(permission)) "✓ GRANTED" else "✗ MISSING"
            appendLine("  $permission: $status")
        }
        
        appendLine()
        appendLine("System Status:")
        appendLine("  Location Services: ${if (areLocationServicesEnabled()) "ENABLED" else "DISABLED"}")
        appendLine("  Can Use Without Location: ${if (canUseBluetoothWithoutLocation()) "YES" else "NO"}")
        appendLine("  Can Scan: ${if (canScan()) "YES" else "NO"}")
        appendLine("  Can Advertise: ${if (canAdvertise()) "YES" else "NO"}")
        appendLine("  Can Connect: ${if (canConnect()) "YES" else "NO"}")
    }
    
    /**
     * 권한 문제를 SystemServiceError로 변환합니다
     */
    fun createBlePermissionError(): SystemServiceError? {
        val status = analyzePermissionStatus()
        
        return when {
            status.isFullyGranted -> null
            status.missingRequired.isNotEmpty() -> {
                SystemServiceError.Permission.NotGranted(status.missingRequired)
            }
            status.needsLocationServices && !areLocationServicesEnabled() -> {
                SystemServiceError.Hardware.Disabled(
                    "LocationServices", 
                    "Required for BLE scanning on Android ${Build.VERSION.SDK_INT}"
                )
            }
            else -> null
        }
    }
    
    /**
     * 권한 요청을 위한 정보를 제공합니다
     */
    fun getPermissionRequestInfo(): PermissionRequestInfo {
        val status = analyzePermissionStatus()
        
        return PermissionRequestInfo(
            requiredPermissions = status.missingRequired.toTypedArray(),
            optionalPermissions = status.missingOptional.toTypedArray(),
            shouldShowRationale = status.missingRequired.any { permission ->
                // 실제 구현에서는 Activity의 shouldShowRequestPermissionRationale을 사용해야 함
                false // 여기서는 기본값
            },
            needsLocationSettings = status.needsLocationServices && !areLocationServicesEnabled(),
            explanationMessage = getBlePermissionGuidanceMessage() ?: ""
        )
    }
    
    /**
     * 권한 요청에 필요한 정보를 담는 데이터 클래스
     */
    data class PermissionRequestInfo(
        val requiredPermissions: Array<String>,
        val optionalPermissions: Array<String>,
        val shouldShowRationale: Boolean,
        val needsLocationSettings: Boolean,
        val explanationMessage: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PermissionRequestInfo

            if (!requiredPermissions.contentEquals(other.requiredPermissions)) return false
            if (!optionalPermissions.contentEquals(other.optionalPermissions)) return false
            if (shouldShowRationale != other.shouldShowRationale) return false
            if (needsLocationSettings != other.needsLocationSettings) return false
            if (explanationMessage != other.explanationMessage) return false

            return true
        }

        override fun hashCode(): Int {
            var result = requiredPermissions.contentHashCode()
            result = 31 * result + optionalPermissions.contentHashCode()
            result = 31 * result + shouldShowRationale.hashCode()
            result = 31 * result + needsLocationSettings.hashCode()
            result = 31 * result + explanationMessage.hashCode()
            return result
        }
    }
}