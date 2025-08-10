package kr.open.library.systemmanager.info.telephony

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.READ_PHONE_NUMBERS
import android.Manifest.permission.READ_PHONE_STATE
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.telephony.CellInfo
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.base.BaseSystemService
import kr.open.library.systemmanager.base.SystemServiceError
import kr.open.library.systemmanager.base.SystemServiceException
import kr.open.library.systemmanager.extenstions.checkSdkVersion
import kr.open.library.systemmanager.extenstions.getTelephonyManager
import kr.open.library.systemmanager.info.network.telephony.calback.CommonTelephonyCallback
import kr.open.library.systemmanager.info.network.telephony.data.current.CurrentCellInfo
import kr.open.library.systemmanager.info.network.telephony.data.current.CurrentServiceState
import kr.open.library.systemmanager.info.network.telephony.data.current.CurrentSignalStrength
import kr.open.library.systemmanager.info.network.telephony.data.state.TelephonyNetworkState
import java.util.concurrent.Executor

/**
 * TelephonyInfo - 통합된 Telephony 정보 관리 클래스
 * Unified Telephony Information Management Class
 *
 * 이 클래스는 TelephonyManager의 복잡한 API를 단순화하고 통합된 인터페이스를 제공합니다.
 * This class simplifies TelephonyManager's complex APIs and provides a unified interface.
 *
 * 주요 기능 / Main Features:
 * - SIM 카드 정보 관리 / SIM card information management
 * - 통신사 정보 조회 / Carrier information retrieval  
 * - 신호 강도 모니터링 / Signal strength monitoring
 * - 네트워크 상태 추적 / Network state tracking
 * - API 호환성 처리 (TelephonyCallback vs PhoneStateListener) / API compatibility handling
 * - 멀티 SIM 지원 / Multi-SIM support
 *
 * 필수 권한 / Required Permissions:
 * - android.permission.READ_PHONE_STATE (필수/Required)
 * - android.permission.ACCESS_FINE_LOCATION (셀 정보/Cell info)
 * - android.permission.READ_PHONE_NUMBERS (전화번호/Phone numbers)
 *
 * 사용 예시 / Usage Example:
 * ```
 * val telephonyInfo = TelephonyInfo(context)
 * 
 * // 기본 정보 조회 / Basic info retrieval
 * val carrierName = telephonyInfo.getCarrierName()
 * val simState = telephonyInfo.getSimState()
 * 
 * // Result 패턴으로 안전한 조회 / Safe retrieval with Result pattern
 * telephonyInfo.getCarrierNameSafe().fold(
 *     onSuccess = { name -> /* 성공 처리 */ },
 *     onFailure = { error -> /* 에러 처리 */ }
 * )
 * 
 * // 실시간 모니터링 / Real-time monitoring
 * telephonyInfo.registerCallback { state ->
 *     // 상태 변경 처리 / Handle state changes
 * }
 * ```
 */
public class TelephonyInfo(context: Context) : BaseSystemService(
    context,
    listOf(READ_PHONE_STATE, ACCESS_FINE_LOCATION, READ_PHONE_NUMBERS)
) {

    // =================================================
    // Core Components
    // =================================================
    
    /**
     * Main TelephonyManager instance
     * 기본 TelephonyManager 인스턴스
     */
    public val telephonyManager: TelephonyManager by lazy { 
        context.getTelephonyManager() 
    }
    
    /**
     * SubscriptionManager for multi-SIM support
     * 멀티 SIM 지원을 위한 SubscriptionManager
     */
    public val subscriptionManager: SubscriptionManager by lazy {
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    }
    
    /**
     * CommonTelephonyCallback instance for unified callback handling
     * 통합된 콜백 처리를 위한 CommonTelephonyCallback 인스턴스
     */
    private val telephonyCallback by lazy { 
        CommonTelephonyCallback(telephonyManager) 
    }
    
    // =================================================
    // State Management
    // =================================================
    
    private val _currentSignalStrength = MutableStateFlow<SignalStrength?>(null)
    public val currentSignalStrength: StateFlow<SignalStrength?> = _currentSignalStrength.asStateFlow()
    
    private val _currentServiceState = MutableStateFlow<ServiceState?>(null)
    public val currentServiceState: StateFlow<ServiceState?> = _currentServiceState.asStateFlow()
    
    private val _currentNetworkState = MutableStateFlow<TelephonyNetworkState?>(null)
    public val currentNetworkState: StateFlow<TelephonyNetworkState?> = _currentNetworkState.asStateFlow()
    
    private var isCallbackRegistered = false
    
    // =================================================
    // Carrier Information / 통신사 정보
    // =================================================
    
    /**
     * Gets the carrier name for the default SIM.
     * 기본 SIM의 통신사 이름을 가져옵니다.
     *
     * @return Carrier name or null if unavailable / 통신사 이름 또는 사용 불가 시 null
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getCarrierName(): String? = safeExecuteOrDefault(
        operation = "getCarrierName",
        defaultValue = null,
        requiresPermission = true
    ) {
        telephonyManager.networkOperatorName?.takeIf { it.isNotBlank() }
    }
    
    /**
     * Safely gets the carrier name with Result pattern.
     * Result 패턴으로 통신사 이름을 안전하게 가져옵니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getCarrierNameSafe(): Result<String?> {
        return safeExecute(
            operation = "getCarrierNameSafe",
            requiresPermission = true
        ) {
            telephonyManager.networkOperatorName?.takeIf { it.isNotBlank() }
        }
    }
    
    /**
     * Gets the Mobile Country Code (MCC) from the default SIM.
     * 기본 SIM에서 Mobile Country Code (MCC)를 가져옵니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getMobileCountryCode(): String? = safeExecuteOrDefault(
        operation = "getMobileCountryCode",
        defaultValue = null,
        requiresPermission = true
    ) {
        telephonyManager.networkOperator?.take(3)?.takeIf { it.length == 3 }
    }
    
    /**
     * Gets the Mobile Network Code (MNC) from the default SIM.
     * 기본 SIM에서 Mobile Network Code (MNC)를 가져옵니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getMobileNetworkCode(): String? = safeExecuteOrDefault(
        operation = "getMobileNetworkCode",
        defaultValue = null,
        requiresPermission = true
    ) {
        val operator = telephonyManager.networkOperator
        if (operator?.length in 5..6) {
            operator.substring(3)
        } else null
    }
    
    // =================================================
    // SIM Information / SIM 정보
    // =================================================
    
    /**
     * Gets the SIM state of the default SIM.
     * 기본 SIM의 상태를 가져옵니다.
     *
     * @return SIM state constant from TelephonyManager / TelephonyManager의 SIM 상태 상수
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getSimState(): Int = safeExecuteOrDefault(
        operation = "getSimState",
        defaultValue = TelephonyManager.SIM_STATE_UNKNOWN,
        requiresPermission = true
    ) {
        telephonyManager.simState
    }
    
    /**
     * Safely gets the SIM state with Result pattern.
     * Result 패턴으로 SIM 상태를 안전하게 가져옵니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getSimStateSafe(): Result<Int> {
        return safeExecute(
            operation = "getSimStateSafe",
            requiresPermission = true
        ) {
            telephonyManager.simState
        }
    }
    
    /**
     * Checks if SIM is ready.
     * SIM이 준비되었는지 확인합니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun isSimReady(): Boolean = getSimState() == TelephonyManager.SIM_STATE_READY
    
    /**
     * Gets the SIM operator name.
     * SIM 운영자 이름을 가져옵니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getSimOperatorName(): String? = safeExecuteOrDefault(
        operation = "getSimOperatorName",
        defaultValue = null,
        requiresPermission = true
    ) {
        telephonyManager.simOperatorName?.takeIf { it.isNotBlank() }
    }
    
    /**
     * Gets the ISO country code for the SIM provider.
     * SIM 제공업체의 ISO 국가 코드를 가져옵니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getSimCountryIso(): String? = safeExecuteOrDefault(
        operation = "getSimCountryIso", 
        defaultValue = null,
        requiresPermission = true
    ) {
        telephonyManager.simCountryIso?.takeIf { it.isNotBlank() }
    }
    
    // =================================================
    // Phone Information / 전화 정보
    // =================================================
    
    /**
     * Gets the phone number of the default SIM.
     * 기본 SIM의 전화번호를 가져옵니다.
     * 
     * Note: This may return null or empty string depending on SIM configuration
     * 참고: SIM 구성에 따라 null이나 빈 문자열을 반환할 수 있습니다.
     */
    @RequiresPermission(anyOf = [READ_PHONE_STATE, READ_PHONE_NUMBERS])
    public fun getPhoneNumber(): String? = safeExecuteOrDefault(
        operation = "getPhoneNumber",
        defaultValue = null,
        requiresPermission = true
    ) {
        @Suppress("DEPRECATION")
        telephonyManager.line1Number?.takeIf { it.isNotBlank() }
    }
    
    /**
     * Gets the call state.
     * 통화 상태를 가져옵니다.
     */
    public fun getCallState(): Int = safeExecuteOrDefault(
        operation = "getCallState",
        defaultValue = TelephonyManager.CALL_STATE_IDLE,
        requiresPermission = false
    ) {
        telephonyManager.callState
    }
    
    // =================================================
    // Network Information / 네트워크 정보
    // =================================================
    
    /**
     * Gets the current network type.
     * 현재 네트워크 타입을 가져옵니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getNetworkType(): Int = executeWithDeprecatedFallback(
        operation = "getNetworkType",
        minimumApiLevel = Build.VERSION_CODES.R,
        modernBlock = {
            telephonyManager.dataNetworkType
        },
        deprecatedBlock = {
            @Suppress("DEPRECATION")
            telephonyManager.networkType
        },
        requiresPermission = true
    ).getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
    
    /**
     * Gets the current data network type.
     * 현재 데이터 네트워크 타입을 가져옵니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getDataNetworkType(): Int = safeExecuteOrDefault(
        operation = "getDataNetworkType",
        defaultValue = TelephonyManager.NETWORK_TYPE_UNKNOWN,
        requiresPermission = true
    ) {
        telephonyManager.dataNetworkType
    }
    
    /**
     * Checks if the device is roaming.
     * 디바이스가 로밍 중인지 확인합니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun isNetworkRoaming(): Boolean = safeExecuteOrDefault(
        operation = "isNetworkRoaming",
        defaultValue = false,
        requiresPermission = true
    ) {
        telephonyManager.isNetworkRoaming
    }
    
    // =================================================
    // Signal Information / 신호 정보
    // =================================================
    
    /**
     * Gets the current signal strength.
     * 현재 신호 강도를 가져옵니다.
     * 
     * Note: This returns the cached value from callbacks. Register callback first.
     * 참고: 콜백에서 캐시된 값을 반환합니다. 먼저 콜백을 등록하세요.
     */
    public fun getCurrentSignalStrength(): SignalStrength? = _currentSignalStrength.value
    
    /**
     * Gets the current service state.
     * 현재 서비스 상태를 가져옵니다.
     */
    public fun getCurrentServiceState(): ServiceState? = _currentServiceState.value
    
    // =================================================
    // Multi-SIM Support / 멀티 SIM 지원
    // =================================================
    
    /**
     * Gets the number of active SIM cards.
     * 활성화된 SIM 카드 수를 가져옵니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getActiveSimCount(): Int = safeExecuteOrDefault(
        operation = "getActiveSimCount",
        defaultValue = 0,
        requiresPermission = true
    ) {
        subscriptionManager.activeSubscriptionInfoCount
    }
    
    /**
     * Gets active subscription info list.
     * 활성 구독 정보 목록을 가져옵니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getActiveSubscriptionInfoList(): List<SubscriptionInfo> = safeExecuteOrDefault(
        operation = "getActiveSubscriptionInfoList",
        defaultValue = emptyList(),
        requiresPermission = true
    ) {
        subscriptionManager.activeSubscriptionInfoList ?: emptyList()
    }
    
    /**
     * Gets subscription info for the default data SIM.
     * 기본 데이터 SIM의 구독 정보를 가져옵니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getDefaultDataSubscriptionInfo(): SubscriptionInfo? = safeExecuteOrDefault(
        operation = "getDefaultDataSubscriptionInfo",
        defaultValue = null,
        requiresPermission = true
    ) {
        val subId = checkSdkVersion(
            Build.VERSION_CODES.R,
            positiveWork = { telephonyManager.subscriptionId },
            negativeWork = { 
                getActiveSubscriptionInfoList().firstOrNull()?.subscriptionId 
            }
        )
        subId?.let { subscriptionManager.getActiveSubscriptionInfo(it) }
    }
    
    // =================================================
    // Callback Management / 콜백 관리
    // =================================================
    
    /**
     * Registers telephony callback for real-time updates.
     * 실시간 업데이트를 위한 telephony 콜백을 등록합니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun registerCallback(
        handler: Handler? = null,
        onSignalStrengthChanged: ((SignalStrength) -> Unit)? = null,
        onServiceStateChanged: ((ServiceState) -> Unit)? = null,
        onNetworkStateChanged: ((TelephonyNetworkState) -> Unit)? = null
    ): Result<Unit> {
        return executeWithApiCompatibility(
            operation = "registerCallback",
            supportedApiLevel = Build.VERSION_CODES.S,
            modernApi = {
                registerModernCallback(handler, onSignalStrengthChanged, onServiceStateChanged, onNetworkStateChanged)
            },
            legacyApi = {
                registerLegacyCallback(onSignalStrengthChanged, onServiceStateChanged, onNetworkStateChanged)
            },
            requiresPermission = true
        )
    }
    
    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerModernCallback(
        handler: Handler?,
        onSignalStrengthChanged: ((SignalStrength) -> Unit)?,
        onServiceStateChanged: ((ServiceState) -> Unit)?,
        onNetworkStateChanged: ((TelephonyNetworkState) -> Unit)?
    ) {
        setupCallbackListeners(onSignalStrengthChanged, onServiceStateChanged, onNetworkStateChanged)
        
        val executor = handler?.let { h -> Executor { h.post(it) } } 
            ?: context.mainExecutor
            
        telephonyManager.registerTelephonyCallback(
            executor, 
            telephonyCallback.baseTelephonyCallback
        )
        
        isCallbackRegistered = true
        Logx.d("TelephonyInfo: Modern callback registered")
    }
    
    @Suppress("DEPRECATION")
    private fun registerLegacyCallback(
        onSignalStrengthChanged: ((SignalStrength) -> Unit)?,
        onServiceStateChanged: ((ServiceState) -> Unit)?,
        onNetworkStateChanged: ((TelephonyNetworkState) -> Unit)?
    ) {
        setupCallbackListeners(onSignalStrengthChanged, onServiceStateChanged, onNetworkStateChanged)
        
        val events = android.telephony.PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                    android.telephony.PhoneStateListener.LISTEN_SERVICE_STATE or
                    android.telephony.PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                    
        telephonyManager.listen(telephonyCallback.basePhoneStateListener, events)
        isCallbackRegistered = true
        Logx.d("TelephonyInfo: Legacy callback registered")
    }
    
    private fun setupCallbackListeners(
        onSignalStrengthChanged: ((SignalStrength) -> Unit)?,
        onServiceStateChanged: ((ServiceState) -> Unit)?,
        onNetworkStateChanged: ((TelephonyNetworkState) -> Unit)?
    ) {
        telephonyCallback.setOnSignalStrength { currentSignalStrength ->
            _currentSignalStrength.value = currentSignalStrength.signalStrength
            currentSignalStrength.signalStrength?.let { onSignalStrengthChanged?.invoke(it) }
        }
        
        telephonyCallback.setOnServiceState { currentServiceState ->
            _currentServiceState.value = currentServiceState.serviceState
            currentServiceState.serviceState?.let { onServiceStateChanged?.invoke(it) }
        }
        
        telephonyCallback.setOnTelephonyNetworkType { networkState ->
            _currentNetworkState.value = networkState
            onNetworkStateChanged?.invoke(networkState)
        }
    }
    
    /**
     * Unregisters telephony callback.
     * telephony 콜백을 해제합니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun unregisterCallback(): Result<Unit> {
        return safeExecute(
            operation = "unregisterCallback",
            requiresPermission = true
        ) {
            if (!isCallbackRegistered) {
                Logx.w("TelephonyInfo: No callback registered")
                return@safeExecute
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager.unregisterTelephonyCallback(telephonyCallback.baseTelephonyCallback)
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(
                    telephonyCallback.basePhoneStateListener, 
                    android.telephony.PhoneStateListener.LISTEN_NONE
                )
            }
            
            isCallbackRegistered = false
            Logx.d("TelephonyInfo: Callback unregistered")
        }
    }
    
    // =================================================
    // Utility Methods / 유틸리티 메서드
    // =================================================
    
    /**
     * Gets network type as human-readable string.
     * 네트워크 타입을 읽기 쉬운 문자열로 가져옵니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getNetworkTypeString(): String = when (getNetworkType()) {
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
        TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO_0"
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO_A"
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO_B"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
        TelephonyManager.NETWORK_TYPE_IDEN -> "IDEN"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "EHRPD"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
        TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD_SCDMA"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
        20 /* NETWORK_TYPE_NR */ -> "5G NR"
        19 /* NETWORK_TYPE_LTE_CA */ -> "LTE_CA"
        else -> "UNKNOWN"
    }
    
    /**
     * Gets SIM state as human-readable string.
     * SIM 상태를 읽기 쉬운 문자열로 가져옵니다.
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getSimStateString(): String = when (getSimState()) {
        TelephonyManager.SIM_STATE_UNKNOWN -> "UNKNOWN"
        TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
        TelephonyManager.SIM_STATE_READY -> "READY"
        TelephonyManager.SIM_STATE_NOT_READY -> "NOT_READY"
        TelephonyManager.SIM_STATE_PERM_DISABLED -> "PERM_DISABLED"
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "CARD_IO_ERROR"
        TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "CARD_RESTRICTED"
        else -> "UNKNOWN"
    }
    
    // =================================================
    // Cleanup / 정리
    // =================================================
    
    override fun onDestroy() {
        try {
            if (isCallbackRegistered) {
                unregisterCallback()
            }
            Logx.d("TelephonyInfo destroyed")
        } catch (e: Exception) {
            Logx.e("Error during TelephonyInfo cleanup: ${e.message}")
        } finally {
            super.onDestroy()
        }
    }
}