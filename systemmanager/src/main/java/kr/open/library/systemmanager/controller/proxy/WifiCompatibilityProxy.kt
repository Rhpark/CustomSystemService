package kr.open.library.systemmanager.controller.proxy

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresPermission
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.extenstions.getConnectivityManager
import kr.open.library.systemmanager.extenstions.getWifiManager

/**
 * WiFi 관련 deprecated API들의 호환성 프록시
 * Compatibility proxy for WiFi-related deprecated APIs
 */
public class WifiCompatibilityProxy(context: Context) : BaseCompatibilityProxy(
    context, 
    Build.VERSION_CODES.Q // API 29+에서 대부분의 WiFi API가 제한됨
) {
    
    private val wifiManager: WifiManager by lazy { context.getWifiManager() }
    private val connectivityManager: ConnectivityManager by lazy { context.getConnectivityManager() }
    
    /**
     * WiFi 활성화/비활성화 프록시
     * WiFi enable/disable proxy
     */
    @RequiresPermission(android.Manifest.permission.CHANGE_WIFI_STATE)
    public fun setWifiEnabled(enabled: Boolean): Boolean = executeSafely(
        operation = "WifiProxy.setWifiEnabled",
        defaultValue = false,
        modernApi = {
            Logx.w("WiFi control is deprecated on API 29+. User must enable manually through settings.")
            false
        },
        legacyApi = {
            @Suppress("DEPRECATION")
            wifiManager.setWifiEnabled(enabled)
        }
    )
    
    /**
     * WiFi 연결 정보 프록시
     * WiFi connection info proxy
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_WIFI_STATE)
    public fun getConnectionInfo(): WifiInfo? = executeSafely(
        operation = "WifiProxy.getConnectionInfo",
        defaultValue = null,
        modernApi = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+에서는 NetworkCapabilities를 통한 제한적 접근만 가능
                Logx.w("WifiInfo access is restricted on API 31+. Using limited network-based approach.")
                getConnectionInfoFromNetwork()
            } else {
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo
            }
        },
        legacyApi = {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        }
    )
    
    /**
     * 설정된 네트워크 목록 프록시
     * Configured networks proxy
     */
    @RequiresPermission(allOf = [
        android.Manifest.permission.ACCESS_WIFI_STATE,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ])
    public fun getConfiguredNetworks(): List<WifiConfiguration> = executeSafely(
        operation = "WifiProxy.getConfiguredNetworks",
        defaultValue = emptyList(),
        modernApi = {
            Logx.w("getConfiguredNetworks is deprecated on API 29+. Use WiFi suggestion API instead.")
            emptyList()
        },
        legacyApi = {
            @Suppress("DEPRECATION")
            wifiManager.configuredNetworks ?: emptyList()
        }
    )
    
    /**
     * WiFi 스캔 시작 프록시
     * WiFi scan start proxy
     */
    @RequiresPermission(android.Manifest.permission.CHANGE_WIFI_STATE)
    public fun startScan(): Boolean = executeSafely(
        operation = "WifiProxy.startScan",
        defaultValue = false,
        modernApi = {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        },
        legacyApi = {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        }
    )
    
    /**
     * NetworkCapabilities를 통한 WiFi 정보 조회 (API 31+용)
     * WiFi info retrieval via NetworkCapabilities (for API 31+)
     */
    private fun getConnectionInfoFromNetwork(): WifiInfo? {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                // API 31+에서는 제한된 정보만 제공 가능
                null // 실제 WifiInfo 객체 생성은 불가능
            } else {
                null
            }
        } catch (e: Exception) {
            Logx.e("Failed to get WiFi info from network: ${e.message}")
            null
        }
    }
    
    /**
     * WiFi 네트워크 세부 정보 (현대적 접근)
     * WiFi network details (modern approach)
     */
    data class WifiNetworkDetails(
        val isConnected: Boolean,
        val hasInternet: Boolean,
        val isValidated: Boolean,
        val isMetered: Boolean,
        val linkDownstreamBandwidthKbps: Int,
        val linkUpstreamBandwidthKbps: Int,
        val interfaceName: String?,
        val dnsServers: List<String>,
        val domains: String?
    )
    
    /**
     * 현대적 방식으로 WiFi 네트워크 정보 조회
     * Get WiFi network info using modern approach
     */
    public fun getModernNetworkDetails(): WifiNetworkDetails? = executeSafely(
        operation = "WifiProxy.getModernNetworkDetails",
        defaultValue = null,
        modernApi = {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
                
                WifiNetworkDetails(
                    isConnected = true,
                    hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    isMetered = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).not(),
                    linkDownstreamBandwidthKbps = networkCapabilities.linkDownstreamBandwidthKbps,
                    linkUpstreamBandwidthKbps = networkCapabilities.linkUpstreamBandwidthKbps,
                    interfaceName = linkProperties?.interfaceName,
                    dnsServers = linkProperties?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList(),
                    domains = linkProperties?.domains
                )
            } else {
                null
            }
        }
    )
}