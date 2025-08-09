package kr.open.library.systemmanager.controller.wifi

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.ACCESS_WIFI_STATE
import android.Manifest.permission.CHANGE_WIFI_STATE
import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.DhcpInfo
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.base.BaseSystemService
import kr.open.library.systemmanager.base.SystemServiceError
import kr.open.library.systemmanager.base.SystemServiceException
import kr.open.library.systemmanager.extenstions.getWifiManager
import kr.open.library.systemmanager.extenstions.safeCatch
import kr.open.library.systemmanager.extenstions.getConnectivityManager

/**
 * WifiController - WiFi 상태 정보 조회 및 제어 컨트롤러
 * WiFi State Information Query and Control Controller
 * 
 * WiFi 상태 조회, 네트워크 스캔, WiFi 켜기/끄기 등의 기능을 제공합니다.
 * Provides WiFi state query, network scanning, WiFi enable/disable functionality.
 * 
 * 주요 기능 / Main Features:
 * - WiFi 상태 정보 조회 / WiFi state information query
 * - WiFi 연결 정보 및 네트워크 스캔 / WiFi connection info and network scanning  
 * - WiFi 켜기/끄기 제어 / WiFi enable/disable control
 * - 네트워크 구성 관리 / Network configuration management
 * 
 * 필수 권한 / Required Permissions:
 * - android.permission.ACCESS_WIFI_STATE (WiFi 상태 조회)
 * - android.permission.CHANGE_WIFI_STATE (WiFi 제어)
 * - android.permission.ACCESS_FINE_LOCATION (스캔 결과 조회, API 23+)
 */
public open class WifiController(context: Context) : BaseSystemService(
    context,
    listOf(ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, ACCESS_FINE_LOCATION)
) {

    public val wifiManager: WifiManager by lazy { context.getWifiManager() }
    private val connectivityManager by lazy { context.getConnectivityManager() }

    /**
     * WiFi 활성화 여부를 확인합니다.
     * Checks if WiFi is enabled.
     * 
     * @return WiFi 활성화 상태 / WiFi enabled status
     */
    @RequiresPermission(ACCESS_WIFI_STATE)
    public fun isWifiEnabled(): Boolean = safeCatch("isWifiEnabled", false) {
        wifiManager.isWifiEnabled
    }

    /**
     * WiFi 활성화 여부를 안전하게 확인합니다 (Result 패턴).
     * Safely checks if WiFi is enabled (Result pattern).
     * 
     * @return Result<Boolean> WiFi 활성화 상태 결과 / WiFi enabled status result
     */
    @RequiresPermission(ACCESS_WIFI_STATE)
    public fun isWifiEnabledSafe(): Result<Boolean> {
        return safeExecuteWithPermissionGuidance(
            operation = "isWifiEnabledSafe"
        ) {
            wifiManager.isWifiEnabled
        }
    }

    /**
     * WiFi 상태를 가져옵니다.
     * Gets the WiFi state.
     * 
     * @return WiFi 상태 코드 (DISABLED, ENABLING, ENABLED, DISABLING, UNKNOWN)
     */
    @RequiresPermission(ACCESS_WIFI_STATE)
    public fun getWifiState(): Int = safeCatch("getWifiState", WifiManager.WIFI_STATE_UNKNOWN) {
        wifiManager.wifiState
    }

    /**
     * WiFi 상태를 안전하게 가져옵니다 (Result 패턴).
     * Safely gets the WiFi state (Result pattern).
     * 
     * @return Result<Int> WiFi 상태 결과
     */
    @RequiresPermission(ACCESS_WIFI_STATE)
    public fun getWifiStateSafe(): Result<Int> {
        return safeExecuteWithPermissionGuidance(
            operation = "getWifiStateSafe"
        ) {
            wifiManager.wifiState
        }
    }

    /**
     * WiFi를 활성화 또는 비활성화합니다.
     * Enables or disables WiFi.
     * 
     * @param enabled true면 활성화, false면 비활성화 / true to enable, false to disable
     * @return 설정 성공 여부 / Setting success status
     */
    @RequiresPermission(CHANGE_WIFI_STATE)
    public fun setWifiEnabled(enabled: Boolean): Boolean = safeCatch("setWifiEnabled", false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Logx.w("WiFi control is deprecated on API 29+. User must enable manually.")
            return@safeCatch false
        }
        
        @Suppress("DEPRECATION")
        wifiManager.setWifiEnabled(enabled)
    }

    /**
     * WiFi를 안전하게 활성화 또는 비활성화합니다 (Result 패턴).
     * Safely enables or disables WiFi (Result pattern).
     * 
     * @param enabled true면 활성화, false면 비활성화 / true to enable, false to disable
     * @param onPermissionRequired 권한이 필요할 때 호출되는 콜백 / Callback when permission required
     * @return Result<Boolean> 설정 결과 / Setting result
     */
    @RequiresPermission(CHANGE_WIFI_STATE)
    public fun setWifiEnabledSafe(
        enabled: Boolean,
        onPermissionRequired: ((String, String?) -> Unit)? = null
    ): Result<Boolean> {
        return safeExecuteWithPermissionGuidance(
            operation = "setWifiEnabledSafe",
            onPermissionRequired = { error ->
                onPermissionRequired?.invoke(error.permission, error.settingsAction)
            }
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                throw SystemServiceException(
                    SystemServiceError.SystemService.UnsupportedVersion(
                        serviceName = "WifiManager.setWifiEnabled",
                        requiredApi = Build.VERSION_CODES.P,
                        currentApi = Build.VERSION.SDK_INT
                    )
                )
            }
            
            @Suppress("DEPRECATION")
            wifiManager.setWifiEnabled(enabled)
        }
    }

    /**
     * 현재 WiFi 연결 정보를 가져옵니다.
     * Gets current WiFi connection information.
     * 
     * @return WiFi 연결 정보 또는 null / WiFi connection info or null
     */
    @RequiresPermission(ACCESS_WIFI_STATE)
    public fun getConnectionInfo(): WifiInfo? = safeCatch("getConnectionInfo", null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Logx.w("WifiInfo may be restricted on API 31+")
        }
        wifiManager.connectionInfo
    }

    /**
     * 현재 WiFi 연결 정보를 안전하게 가져옵니다 (Result 패턴).
     * Safely gets current WiFi connection information (Result pattern).
     * 
     * @return Result<WifiInfo?> WiFi 연결 정보 결과 / WiFi connection info result
     */
    @RequiresPermission(ACCESS_WIFI_STATE)
    public fun getConnectionInfoSafe(): Result<WifiInfo?> {
        return safeExecuteWithPermissionGuidance(
            operation = "getConnectionInfoSafe"
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Logx.w("WifiInfo may be restricted on API 31+")
            }
            wifiManager.connectionInfo
        }
    }

    /**
     * DHCP 정보를 가져옵니다.
     * Gets DHCP information.
     * 
     * @return DHCP 정보 또는 null / DHCP info or null
     */
    @RequiresPermission(ACCESS_WIFI_STATE)
    public fun getDhcpInfo(): DhcpInfo? = safeCatch("getDhcpInfo", null) {
        wifiManager.dhcpInfo
    }

    /**
     * WiFi 스캔을 시작합니다.
     * Starts WiFi scanning.
     * 
     * @return 스캔 시작 성공 여부 / Scan start success status
     */
    @RequiresPermission(CHANGE_WIFI_STATE)
    public fun startScan(): Boolean = safeCatch("startScan", false) {
        wifiManager.startScan()
    }

    /**
     * WiFi 스캔을 안전하게 시작합니다 (Result 패턴).
     * Safely starts WiFi scanning (Result pattern).
     * 
     * @param onPermissionRequired 권한이 필요할 때 호출되는 콜백 / Callback when permission required
     * @return Result<Boolean> 스캔 시작 결과 / Scan start result
     */
    @RequiresPermission(CHANGE_WIFI_STATE)
    public fun startScanSafe(
        onPermissionRequired: ((String, String?) -> Unit)? = null
    ): Result<Boolean> {
        return safeExecuteWithPermissionGuidance(
            operation = "startScanSafe",
            onPermissionRequired = { error ->
                onPermissionRequired?.invoke(error.permission, error.settingsAction)
            }
        ) {
            wifiManager.startScan()
        }
    }

    /**
     * WiFi 스캔 결과를 가져옵니다.
     * Gets WiFi scan results.
     * 
     * @return 스캔 결과 목록 / List of scan results
     */
    @RequiresPermission(allOf = [ACCESS_WIFI_STATE, ACCESS_FINE_LOCATION])
    public fun getScanResults(): List<ScanResult> = safeCatch("getScanResults", emptyList()) {
        wifiManager.scanResults ?: emptyList()
    }

    /**
     * WiFi 스캔 결과를 안전하게 가져옵니다 (Result 패턴).
     * Safely gets WiFi scan results (Result pattern).
     * 
     * @return Result<List<ScanResult>> 스캔 결과 / Scan results
     */
    @RequiresPermission(allOf = [ACCESS_WIFI_STATE, ACCESS_FINE_LOCATION])
    public fun getScanResultsSafe(): Result<List<ScanResult>> {
        return safeExecuteWithPermissionGuidance(
            operation = "getScanResultsSafe"
        ) {
            wifiManager.scanResults?.toList() ?: emptyList()
        }
    }

    /**
     * 설정된 네트워크 목록을 가져옵니다 (API 29 이하).
     * Gets configured networks list (API 29 and below).
     * 
     * @return 설정된 네트워크 목록 / List of configured networks
     */
    @RequiresPermission(allOf = [ACCESS_WIFI_STATE, ACCESS_FINE_LOCATION])
    public fun getConfiguredNetworks(): List<WifiConfiguration> = safeCatch("getConfiguredNetworks", emptyList()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Logx.w("getConfiguredNetworks is deprecated on API 29+")
            return@safeCatch emptyList()
        }
        
        @Suppress("DEPRECATION")
        wifiManager.configuredNetworks ?: emptyList()
    }

    /**
     * WiFi 연결 여부를 확인합니다.
     * Checks if WiFi is connected.
     * 
     * @return WiFi 연결 상태 / WiFi connection status
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public fun isConnectedWifi(): Boolean = safeCatch("isConnectedWifi", false) {
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
    }

    /**
     * WiFi 연결 여부를 안전하게 확인합니다 (Result 패턴).
     * Safely checks if WiFi is connected (Result pattern).
     * 
     * @return Result<Boolean> WiFi 연결 상태 결과 / WiFi connection status result
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public fun isConnectedWifiSafe(): Result<Boolean> {
        return safeExecuteWithPermissionGuidance(
            operation = "isConnectedWifiSafe"
        ) {
            val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        }
    }

    /**
     * 신호 강도를 레벨로 변환합니다.
     * Converts signal strength to level.
     * 
     * @param rssi 신호 강도 (dBm) / Signal strength in dBm
     * @param numLevels 레벨 수 (일반적으로 4 또는 5) / Number of levels (typically 4 or 5)
     * @return 신호 레벨 (0부터 numLevels-1) / Signal level (0 to numLevels-1)
     */
    public fun calculateSignalLevel(rssi: Int, numLevels: Int): Int = safeCatch("calculateSignalLevel", 0) {
        WifiManager.calculateSignalLevel(rssi, numLevels)
    }

    /**
     * 두 신호 강도를 비교합니다.
     * Compares two signal strengths.
     * 
     * @param rssiA 첫 번째 신호 강도 / First signal strength
     * @param rssiB 두 번째 신호 강도 / Second signal strength  
     * @return 비교 결과 (-1, 0, 1) / Comparison result (-1, 0, 1)
     */
    public fun compareSignalLevel(rssiA: Int, rssiB: Int): Int = safeCatch("compareSignalLevel", 0) {
        WifiManager.compareSignalLevel(rssiA, rssiB)
    }

    /**
     * 5GHz 대역 지원 여부를 확인합니다.
     * Checks if 5GHz band is supported.
     * 
     * @return 5GHz 대역 지원 여부 / 5GHz band support status
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public fun is5GHzBandSupported(): Boolean = safeCatch("is5GHzBandSupported", false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wifiManager.is5GHzBandSupported
        } else {
            false
        }
    }

    /**
     * 6GHz 대역 지원 여부를 확인합니다 (WiFi 6E).
     * Checks if 6GHz band is supported (WiFi 6E).
     * 
     * @return 6GHz 대역 지원 여부 / 6GHz band support status
     */
    @RequiresApi(Build.VERSION_CODES.R)
    public fun is6GHzBandSupported(): Boolean = safeCatch("is6GHzBandSupported", false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wifiManager.is6GHzBandSupported
        } else {
            false
        }
    }

    /**
     * WPA3 SAE 지원 여부를 확인합니다.
     * Checks if WPA3 SAE is supported.
     * 
     * @return WPA3 SAE 지원 여부 / WPA3 SAE support status
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public fun isWpa3SaeSupported(): Boolean = safeCatch("isWpa3SaeSupported", false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiManager.isWpa3SaeSupported
        } else {
            false
        }
    }

    /**
     * Enhanced Open 지원 여부를 확인합니다.
     * Checks if Enhanced Open is supported.
     * 
     * @return Enhanced Open 지원 여부 / Enhanced Open support status
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public fun isEnhancedOpenSupported(): Boolean = safeCatch("isEnhancedOpenSupported", false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiManager.isEnhancedOpenSupported
        } else {
            false
        }
    }

    /**
     * WiFi 재연결을 시도합니다.
     * Attempts to reconnect WiFi.
     * 
     * @return 재연결 시도 성공 여부 / Reconnection attempt success status
     */
    @RequiresPermission(CHANGE_WIFI_STATE)
    public fun reconnect(): Boolean = safeCatch("reconnect", false) {
        wifiManager.reconnect()
    }

    /**
     * WiFi 재결합을 시도합니다.
     * Attempts to reassociate WiFi.
     * 
     * @return 재결합 시도 성공 여부 / Reassociation attempt success status
     */
    @RequiresPermission(CHANGE_WIFI_STATE)
    public fun reassociate(): Boolean = safeCatch("reassociate", false) {
        wifiManager.reassociate()
    }

    /**
     * WiFi 연결을 해제합니다.
     * Disconnects WiFi.
     * 
     * @return 연결 해제 성공 여부 / Disconnection success status
     */
    @RequiresPermission(CHANGE_WIFI_STATE)
    public fun disconnect(): Boolean = safeCatch("disconnect", false) {
        wifiManager.disconnect()
    }

    /**
     * 현재 연결된 WiFi의 SSID를 가져옵니다.
     * Gets the SSID of currently connected WiFi.
     * 
     * @return WiFi SSID 또는 null / WiFi SSID or null
     */
    @RequiresPermission(ACCESS_WIFI_STATE)
    public fun getCurrentSsid(): String? = safeCatch("getCurrentSsid", null) {
        val wifiInfo = getConnectionInfo()
        wifiInfo?.ssid?.removeSurrounding("\"")
    }

    /**
     * 현재 연결된 WiFi의 BSSID를 가져옵니다.
     * Gets the BSSID of currently connected WiFi.
     * 
     * @return WiFi BSSID 또는 null / WiFi BSSID or null
     */
    @RequiresPermission(ACCESS_WIFI_STATE)
    public fun getCurrentBssid(): String? = safeCatch("getCurrentBssid", null) {
        getConnectionInfo()?.bssid
    }

    /**
     * 현재 연결된 WiFi의 신호 강도를 가져옵니다.
     * Gets the signal strength of currently connected WiFi.
     * 
     * @return 신호 강도 (dBm) / Signal strength in dBm
     */
    @RequiresPermission(ACCESS_WIFI_STATE)
    public fun getCurrentRssi(): Int = safeCatch("getCurrentRssi", -127) {
        getConnectionInfo()?.rssi ?: -127
    }

    /**
     * 현재 WiFi 연결의 링크 속도를 가져옵니다.
     * Gets the link speed of current WiFi connection.
     * 
     * @return 링크 속도 (Mbps) / Link speed in Mbps
     */
    @RequiresPermission(ACCESS_WIFI_STATE)
    public fun getCurrentLinkSpeed(): Int = safeCatch("getCurrentLinkSpeed", 0) {
        getConnectionInfo()?.linkSpeed ?: 0
    }

    /**
     * WiFi 컨트롤러의 모든 리소스를 정리합니다.
     * Cleans up all resources of WiFi controller.
     */
    override fun onDestroy() {
        try {
            Logx.d("WifiController resources cleaned up")
        } catch (e: Exception) {
            Logx.e("Error during WifiController cleanup: ${e.message}")
        } finally {
            super.onDestroy()
        }
    }
}