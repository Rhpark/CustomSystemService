package kr.open.library.systemmanager.info.network.service

import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import android.Manifest.permission.ACCESS_NETWORK_STATE
import kr.open.library.systemmanager.base.Result
import kr.open.library.systemmanager.info.network.connectivity.data.NetworkCapabilitiesData
import kr.open.library.systemmanager.info.network.connectivity.data.NetworkLinkPropertiesData

/**
 * 네트워크 연결 상태 관리 서비스 인터페이스
 * Interface for network connectivity state management service
 */
public interface INetworkConnectivityService {
    
    /**
     * 네트워크 연결 여부 확인
     * Check if network is connected
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    fun isNetworkConnected(): Result<Boolean>
    
    /**
     * 현재 네트워크의 NetworkCapabilities 반환
     * Get NetworkCapabilities of current network
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    fun getCapabilities(): Result<NetworkCapabilities?>
    
    /**
     * 현재 네트워크의 LinkProperties 반환
     * Get LinkProperties of current network
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    fun getLinkProperties(): Result<LinkProperties?>
    
    /**
     * WiFi 연결 여부 확인
     * Check WiFi connection
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    fun isConnectedWifi(): Result<Boolean>
    
    /**
     * 모바일 데이터 연결 여부 확인
     * Check mobile data connection
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    fun isConnectedMobile(): Result<Boolean>
    
    /**
     * VPN 연결 여부 확인
     * Check VPN connection
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    fun isConnectedVPN(): Result<Boolean>
    
    /**
     * 블루투스 연결 여부 확인
     * Check Bluetooth connection
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    fun isConnectedBluetooth(): Result<Boolean>
    
    /**
     * WiFi Aware 연결 여부 확인
     * Check WiFi Aware connection
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    fun isConnectedWifiAware(): Result<Boolean>
    
    /**
     * 이더넷 연결 여부 확인
     * Check Ethernet connection
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    fun isConnectedEthernet(): Result<Boolean>
    
    /**
     * LoWPAN 연결 여부 확인
     * Check LoWPAN connection
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    fun isConnectedLowPan(): Result<Boolean>
    
    /**
     * USB 연결 여부 확인 (API 31+)
     * Check USB connection (API 31+)
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    @RequiresApi(Build.VERSION_CODES.S)
    fun isConnectedUSB(): Result<Boolean>
    
    /**
     * 네트워크 상태 콜백 등록
     * Register network state callback
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    fun registerNetworkCallback(
        handler: Handler? = null,
        onNetworkAvailable: ((Network) -> Unit)? = null,
        onNetworkLosing: ((Network, Int) -> Unit)? = null,
        onNetworkLost: ((Network) -> Unit)? = null,
        onUnavailable: (() -> Unit)? = null,
        onNetworkCapabilitiesChanged: ((Network, NetworkCapabilitiesData) -> Unit)? = null,
        onLinkPropertiesChanged: ((Network, NetworkLinkPropertiesData) -> Unit)? = null,
        onBlockedStatusChanged: ((Network, Boolean) -> Unit)? = null,
    ): Result<Unit>
    
    /**
     * 기본 네트워크 상태 콜백 등록
     * Register default network state callback
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    fun registerDefaultNetworkCallback(
        handler: Handler? = null,
        onNetworkAvailable: ((Network) -> Unit)? = null,
        onNetworkLosing: ((Network, Int) -> Unit)? = null,
        onNetworkLost: ((Network) -> Unit)? = null,
        onUnavailable: (() -> Unit)? = null,
        onNetworkCapabilitiesChanged: ((Network, NetworkCapabilitiesData) -> Unit)? = null,
        onLinkPropertiesChanged: ((Network, NetworkLinkPropertiesData) -> Unit)? = null,
        onBlockedStatusChanged: ((Network, Boolean) -> Unit)? = null,
    ): Result<Unit>
    
    /**
     * 네트워크 콜백 등록 해제
     * Unregister network callback
     */
    fun unregisterNetworkCallback(): Result<Unit>
    
    /**
     * 기본 네트워크 콜백 등록 해제
     * Unregister default network callback
     */
    fun unregisterDefaultNetworkCallback(): Result<Unit>
}