package kr.open.library.systemmanager.info.network.service

import kr.open.library.systemmanager.base.Result

/**
 * WiFi 관리 서비스 인터페이스
 * Interface for WiFi management service
 */
public interface IWifiManagementService {
    
    /**
     * WiFi 활성화 여부 확인
     * Check if WiFi is enabled
     */
    fun isWifiOn(): Result<Boolean>
    
    /**
     * WiFi 활성화
     * Enable WiFi
     */
    fun enableWifi(): Result<Boolean>
    
    /**
     * WiFi 비활성화
     * Disable WiFi
     */
    fun disableWifi(): Result<Boolean>
}