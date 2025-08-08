package kr.open.library.systemmanager.info.network.service

import android.os.Build
import android.telephony.TelephonyDisplayInfo
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import android.Manifest.permission.READ_PHONE_STATE
import kr.open.library.systemmanager.info.network.telephony.data.current.CurrentCellInfo
import kr.open.library.systemmanager.info.network.telephony.data.current.CurrentServiceState
import kr.open.library.systemmanager.info.network.telephony.data.current.CurrentSignalStrength
import kr.open.library.systemmanager.info.network.telephony.data.state.TelephonyNetworkState
import java.util.concurrent.Executor

/**
 * 텔레포니 콜백 관리 서비스 인터페이스
 * Interface for telephony callback management service
 */
public interface ITelephonyCallbackService {
    
    /**
     * 텔레포니 콜백 등록 (기본 USIM, API 31+)
     * Register telephony callback for default USIM (API 31+)
     */
    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(READ_PHONE_STATE)
    fun registerTelephonyCallBackFromDefaultUSim(
        executor: Executor,
        isGpsOn: Boolean,
        onActiveDataSubId: ((subId: Int) -> Unit)? = null,
        onDataConnectionState: ((state: Int, networkType: Int) -> Unit)? = null,
        onCellInfo: ((currentCellInfo: CurrentCellInfo) -> Unit)? = null,
        onSignalStrength: ((currentSignalStrength: CurrentSignalStrength) -> Unit)? = null,
        onServiceState: ((currentServiceState: CurrentServiceState) -> Unit)? = null,
        onCallState: ((callState: Int, phoneNumber: String?) -> Unit)? = null,
        onDisplayInfo: ((telephonyDisplayInfo: TelephonyDisplayInfo) -> Unit)? = null,
        onTelephonyNetworkState: ((telephonyNetworkState: TelephonyNetworkState) -> Unit)? = null
    ): Result<Unit>
    
    /**
     * 텔레포니 콜백 등록 (특정 슬롯, API 31+)
     * Register telephony callback for specific slot (API 31+)
     */
    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(READ_PHONE_STATE)
    fun registerTelephonyCallBack(
        simSlotIndex: Int,
        executor: Executor,
        isGpsOn: Boolean,
        onActiveDataSubId: ((subId: Int) -> Unit)? = null,
        onDataConnectionState: ((state: Int, networkType: Int) -> Unit)? = null,
        onCellInfo: ((currentCellInfo: CurrentCellInfo) -> Unit)? = null,
        onSignalStrength: ((currentSignalStrength: CurrentSignalStrength) -> Unit)? = null,
        onServiceState: ((currentServiceState: CurrentServiceState) -> Unit)? = null,
        onCallState: ((callState: Int, phoneNumber: String?) -> Unit)? = null,
        onDisplayInfo: ((telephonyDisplayInfo: TelephonyDisplayInfo) -> Unit)? = null,
        onTelephonyNetworkState: ((telephonyNetworkState: TelephonyNetworkState) -> Unit)? = null
    ): Result<Unit>
    
    /**
     * 텔레포니 리스너 등록 (기본 USIM, API 30 이하)
     * Register telephony listener for default USIM (API 30 and below)
     */
    @RequiresPermission(READ_PHONE_STATE)
    fun registerTelephonyListenFromDefaultUSim(
        isGpsOn: Boolean,
        onActiveDataSubId: ((subId: Int) -> Unit)? = null,
        onDataConnectionState: ((state: Int, networkType: Int) -> Unit)? = null,
        onCellInfo: ((currentCellInfo: CurrentCellInfo) -> Unit)? = null,
        onSignalStrength: ((currentSignalStrength: CurrentSignalStrength) -> Unit)? = null,
        onServiceState: ((currentServiceState: CurrentServiceState) -> Unit)? = null,
        onCallState: ((callState: Int, phoneNumber: String?) -> Unit)? = null,
        onDisplayInfo: ((telephonyDisplayInfo: TelephonyDisplayInfo) -> Unit)? = null,
        onTelephonyNetworkState: ((telephonyNetworkState: TelephonyNetworkState) -> Unit)? = null
    ): Result<Unit>
    
    /**
     * 텔레포니 리스너 등록 (특정 슬롯, API 30 이하)
     * Register telephony listener for specific slot (API 30 and below)
     */
    @RequiresPermission(READ_PHONE_STATE)
    fun registerTelephonyListen(
        simSlotIndex: Int,
        isGpsOn: Boolean,
        onActiveDataSubId: ((subId: Int) -> Unit)? = null,
        onDataConnectionState: ((state: Int, networkType: Int) -> Unit)? = null,
        onCellInfo: ((currentCellInfo: CurrentCellInfo) -> Unit)? = null,
        onSignalStrength: ((currentSignalStrength: CurrentSignalStrength) -> Unit)? = null,
        onServiceState: ((currentServiceState: CurrentServiceState) -> Unit)? = null,
        onCallState: ((callState: Int, phoneNumber: String?) -> Unit)? = null,
        onDisplayInfo: ((telephonyDisplayInfo: TelephonyDisplayInfo) -> Unit)? = null,
        onTelephonyNetworkState: ((telephonyNetworkState: TelephonyNetworkState) -> Unit)? = null
    ): Result<Unit>
    
    /**
     * 콜백 등록 해제 (기본 USIM)
     * Unregister callback for default USIM
     */
    @RequiresPermission(READ_PHONE_STATE)
    fun unregisterCallBackFromDefaultUSim(): Result<Unit>
    
    /**
     * 콜백 등록 해제 (특정 슬롯)
     * Unregister callback for specific slot
     */
    fun unregisterCallBack(simSlotIndex: Int): Result<Unit>
    
    /**
     * 리스너 등록 해제 (특정 슬롯)
     * Unregister listener for specific slot
     */
    fun unregisterListen(simSlotIndex: Int): Result<Unit>
    
    /**
     * 등록 상태 확인
     * Check registration status
     */
    fun isRegistered(simSlotIndex: Int): Boolean
    
    /**
     * 기본 USIM 등록 상태 확인
     * Check default USIM registration status
     */
    @RequiresPermission(READ_PHONE_STATE)
    fun isRegisteredDefaultUSim(): Result<Boolean>
    
    /**
     * 모든 콜백 정리
     * Clear all callbacks
     */
    @RequiresPermission(READ_PHONE_STATE)
    fun allClearCallback(): Result<Unit>
}