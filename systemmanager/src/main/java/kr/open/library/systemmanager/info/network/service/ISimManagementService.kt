package kr.open.library.systemmanager.info.network.service

import android.telephony.SubscriptionInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import android.Manifest.permission.READ_PHONE_STATE
import android.Manifest.permission.READ_PHONE_NUMBERS
import kr.open.library.systemmanager.base.Result

/**
 * SIM 카드 및 구독 정보 관리 서비스 인터페이스
 * Interface for SIM card and subscription information management service
 */
public interface ISimManagementService {
    
    /**
     * 듀얼 SIM 지원 여부 확인
     * Check dual SIM support
     */
    fun isDualSim(): Boolean
    
    /**
     * 단일 SIM 여부 확인  
     * Check single SIM
     */
    fun isSingleSim(): Boolean
    
    /**
     * 멀티 SIM 여부 확인
     * Check multi SIM support
     */
    fun isMultiSim(): Boolean
    
    /**
     * 최대 SIM 슬롯 수 반환
     * Get maximum SIM slot count
     */
    fun getMaximumUSimCount(): Int
    
    /**
     * 활성 SIM 수 반환
     * Get active SIM count
     */
    @RequiresPermission(READ_PHONE_STATE)
    fun getActiveSimCount(): Result<Int>
    
    /**
     * 활성 SIM 슬롯 인덱스 목록 반환
     * Get active SIM slot index list
     */
    @RequiresPermission(READ_PHONE_STATE)
    fun getActiveSimSlotIndexList(): Result<List<Int>>
    
    /**
     * 기본 USIM의 구독 ID 반환
     * Get subscription ID from default USIM
     */
    @RequiresPermission(READ_PHONE_STATE)
    fun getSubIdFromDefaultUSim(): Result<Int?>
    
    /**
     * 특정 SIM 슬롯의 구독 ID 반환
     * Get subscription ID for specific SIM slot
     */
    @RequiresPermission(READ_PHONE_STATE)
    fun getSubId(simSlotIndex: Int): Result<Int?>
    
    /**
     * 활성 구독 정보 목록 반환
     * Get active subscription info list
     */
    @RequiresPermission(READ_PHONE_STATE)
    fun getActiveSubscriptionInfoList(): Result<List<SubscriptionInfo>>
    
    /**
     * 특정 구독 ID의 구독 정보 반환
     * Get subscription info for specific subscription ID
     */
    @RequiresPermission(READ_PHONE_STATE)
    fun getActiveSubscriptionInfoSubId(subId: Int): Result<SubscriptionInfo?>
    
    /**
     * 특정 SIM 슬롯의 구독 정보 반환
     * Get subscription info for specific SIM slot
     */
    @RequiresPermission(READ_PHONE_STATE)
    fun getActiveSubscriptionInfoSimSlot(slotIndex: Int): Result<SubscriptionInfo?>
    
    /**
     * 전화번호 반환 (기본 USIM)
     * Get phone number from default USIM
     */
    @RequiresPermission(anyOf = [READ_PHONE_STATE, READ_PHONE_NUMBERS])
    fun getPhoneNumberFromDefaultUSim(): Result<String?>
    
    /**
     * 전화번호 반환 (특정 슬롯)
     * Get phone number for specific slot
     */
    @RequiresPermission(anyOf = [READ_PHONE_STATE, READ_PHONE_NUMBERS])
    fun getPhoneNumber(slotIndex: Int): Result<String?>
    
    /**
     * 특정 슬롯의 TelephonyManager 반환
     * Get TelephonyManager for specific slot
     */
    fun getTelephonyManagerFromUSim(slotIndex: Int): TelephonyManager?
    
    /**
     * SIM 정보 읽기 가능 여부
     * Check if SIM info can be read
     */
    fun isCanReadSimInfo(): Boolean
}