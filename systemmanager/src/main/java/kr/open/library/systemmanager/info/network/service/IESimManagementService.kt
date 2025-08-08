package kr.open.library.systemmanager.info.network.service

import kr.open.library.systemmanager.base.Result

/**
 * eSIM 관리 서비스 인터페이스
 * Interface for eSIM management service
 */
public interface IESimManagementService {
    
    /**
     * eSIM 지원 여부 확인
     * Check eSIM support
     */
    fun isESimSupport(): Result<Boolean>
    
    /**
     * eSIM 등록 여부 확인
     * Check eSIM registration status
     */
    fun isRegisterESim(eSimSlotIndex: Int): Result<Boolean>
    
    /**
     * 활성 SIM 상태 반환
     * Get active SIM status
     */
    fun getActiveSimStatus(slotIndex: Int): Result<Int>
    
    /**
     * eSIM 활성화 가능 여부 확인
     * Check if eSIM can be enabled
     */
    fun canEnableESim(): Result<Boolean>
    
    /**
     * eSIM 프로필 목록 반환
     * Get eSIM profile list
     */
    fun getESimProfiles(): Result<List<String>>
}