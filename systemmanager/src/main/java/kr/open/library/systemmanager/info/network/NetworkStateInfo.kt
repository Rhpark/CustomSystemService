package kr.open.library.systemmanager.info.network

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.Manifest.permission.READ_PHONE_NUMBERS
import android.Manifest.permission.READ_PHONE_STATE
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.telephony.euicc.EuiccManager
import android.util.SparseArray
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.util.forEach
import kr.open.library.logcat.Logx
import kr.open.library.permissions.extensions.hasPermissions
import kr.open.library.systemmanager.base.BaseSystemService

import kr.open.library.systemmanager.extenstions.checkSdkVersion
import kr.open.library.systemmanager.extenstions.getConnectivityManager
import kr.open.library.systemmanager.extenstions.getEuiccManager
import kr.open.library.systemmanager.extenstions.getSubscriptionManager
import kr.open.library.systemmanager.extenstions.getTelephonyManager
import kr.open.library.systemmanager.controller.wifi.WifiController
import kr.open.library.systemmanager.info.network.connectivity.callback.NetworkStateCallback
import kr.open.library.systemmanager.info.network.connectivity.data.NetworkCapabilitiesData
import kr.open.library.systemmanager.info.network.connectivity.data.NetworkLinkPropertiesData
import kr.open.library.systemmanager.info.network.telephony.calback.CommonTelephonyCallback
import kr.open.library.systemmanager.info.network.telephony.data.current.CurrentCellInfo
import kr.open.library.systemmanager.info.network.telephony.data.current.CurrentServiceState
import kr.open.library.systemmanager.info.network.telephony.data.current.CurrentSignalStrength
import kr.open.library.systemmanager.info.network.telephony.data.state.TelephonyNetworkState

import java.util.concurrent.Executor

/**
 * request Permission
 * <uses-permission android:name="android.permission.INTERNET"/>
 * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 */
public open class NetworkStateInfo(
    context: Context,
) : BaseSystemService(
    context,
    listOf(READ_PHONE_STATE, READ_PHONE_NUMBERS, ACCESS_FINE_LOCATION)
) {
    public val telephonyManager: TelephonyManager by lazy { context.getTelephonyManager() }
    public val subscriptionManager: SubscriptionManager by lazy {   context.getSubscriptionManager() }
    public val connectivityManager: ConnectivityManager by lazy {   context.getConnectivityManager() }
    public val wifiController: WifiController by lazy { WifiController(context) }
    public val euiccManager: EuiccManager by lazy { context.getEuiccManager() }

    private val uSimTelephonyManagerList = SparseArray<TelephonyManager>()
    private val uSimTelephonyCallbackList =  SparseArray<CommonTelephonyCallback>()
    private val isRegistered =  SparseArray<Boolean>()

    /** SIM 정보를 읽을 수 있는지 여부를 나타낸다.
     * Indicates whether SIM information can be read.
     **/
    private var isReadSimInfoFromDefaultUSim = false


    /** 네트워크 상태 콜백
     *  Network state callback */
    private var networkCallBack: NetworkStateCallback? = null

    /** 기본 네트워크 상태 콜백
     *  Default network state callback */
    private var networkDefaultCallback: NetworkStateCallback? = null

    init {

        initialization()
    }

    @RequiresPermission(READ_PHONE_STATE)
    private fun initialization() {
        if(!context.hasPermissions(READ_PHONE_STATE)) {
            Logx.e("Can not read uSim Chip!")
        } else {
            getSubIdFromDefaultUSim()
            updateUSimTelephonyManagerList()
        }
    }

    public fun isCanReadSimInfo(): Boolean = isReadSimInfoFromDefaultUSim

    public fun isDualSim(): Boolean = getMaximumUSimCount() == 2
    public fun isSingleSim(): Boolean = getMaximumUSimCount() == 1
    public fun isMultiSim(): Boolean = getMaximumUSimCount() > 1

    public fun getMaximumUSimCount(): Int = subscriptionManager.activeSubscriptionInfoCountMax

    @RequiresPermission(READ_PHONE_STATE)
    public fun getActiveSimCount(): Int = getActiveSimCountInternal().getOrElse { 0 }
    
    /**
     * 활성 SIM 수 반환 (Result 패턴)
     * Get active SIM count (Result pattern)
     */
    @RequiresPermission(READ_PHONE_STATE)
    private fun getActiveSimCountInternal(): Result<Int> {
        return NetworkStateInfoInternal.safeExecuteWithPermission(
            operation = "getActiveSimCount",
            requiredPermissions = listOf(READ_PHONE_STATE)
        ) {
            subscriptionManager.activeSubscriptionInfoCount
        }
    }


    @RequiresPermission(READ_PHONE_STATE)
    public fun getActiveSimSlotIndexList() :List<Int> = getActiveSubscriptionInfoList().map { it->it.simSlotIndex }.toList()

    @RequiresPermission(READ_PHONE_STATE)
    public fun updateUSimTelephonyManagerList() {
        Logx.d("USimInfo","activeSubscriptionInfoList size ${getActiveSubscriptionInfoList().size}")
        getActiveSubscriptionInfoList().forEach {
            Logx.d("USimInfo","SubID ${it.toString()}\n")
            uSimTelephonyManagerList[it.simSlotIndex] = telephonyManager.createForSubscriptionId(it.subscriptionId)
            uSimTelephonyCallbackList[it.simSlotIndex] = CommonTelephonyCallback(uSimTelephonyManagerList[it.simSlotIndex])
        }
    }

    /**
     * tested single sim, dual sim(pSim + eSim)
     * return
     * @see TelephonyManager.SIM_STATE_%%
     * dualsim(psim,psin),(psim,esim)
     */
    private fun getActiveSimStatus(isAbleEsim:Boolean, isRegisterESim:Boolean, slotIndex:Int) :Int{

        val status = telephonyManager.getSimState(slotIndex)

        return if(isAbleEsim && slotIndex == 0 && status == TelephonyManager.SIM_STATE_UNKNOWN) {
            Logx.w("SimSlot 0, may be pSim is not ready")
            TelephonyManager.SIM_STATE_NOT_READY
        } else if (!isRegisterESim) {
            Logx.w("SimSlot $slotIndex, may be eSim is not register")
            TelephonyManager.SIM_STATE_UNKNOWN
        } else status
    }

//    @RequiresPermission(READ_PHONE_STATE)
//    public fun getActiveSimStatus(simSlotIndex: Int): Int {
//        getActiveSubscriptionInfoSimSlot(simSlotIndex)?.let { subscriptionInfo ->
//            val status = telephonyManager.getSimState(simSlotIndex)
//            return if (isMultiSim() && !subscriptionInfo.isEmbedded && simSlotIndex == 0 && status == TelephonyManager.SIM_STATE_UNKNOWN) {
//                TelephonyManager.SIM_STATE_NOT_READY
//            } else {
//                status
//            }
//        } ?: return TelephonyManager.SIM_STATE_UNKNOWN
//    }

    /**
     * 기본 subscription ID를 반환.
     * Returns the default subscription ID.
     *
     * @return Default subscription ID or null
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getSubIdFromDefaultUSim(): Int? = getSubIdFromDefaultUSimInternal().getOrNull()
    
    /**
     * 기본 subscription ID를 반환 (Result 패턴)
     * Returns the default subscription ID (Result pattern)
     */
    @RequiresPermission(READ_PHONE_STATE)
    private fun getSubIdFromDefaultUSimInternal(): Result<Int?> {
        return NetworkStateInfoInternal.safeExecuteTelephonyOperation(
            operation = "getSubIdFromDefaultUSim"
        ) {
            isReadSimInfoFromDefaultUSim = false

            val id = checkSdkVersion(Build.VERSION_CODES.R,
                positiveWork = { telephonyManager.subscriptionId },
                negativeWork = { getActiveSubscriptionInfoListInternal().getOrNull()?.firstOrNull()?.subscriptionId }
            )
            isReadSimInfoFromDefaultUSim = id != null
            id
        }
    }

    @RequiresPermission(READ_PHONE_STATE)
    public fun getSubId(simSlotIndex: Int): Int? = try {
        checkSdkVersion(Build.VERSION_CODES.R,
            positiveWork = {   uSimTelephonyManagerList[simSlotIndex]?.subscriptionId    },
            negativeWork = {    getActiveSubscriptionInfoSimSlot(simSlotIndex)?.subscriptionId   }
        )
    } catch (e: NoSuchMethodError) {
        Logx.e("Can not read uSim Chip, subId = -1, e = $e")
        null
    }

    @RequiresPermission(READ_PHONE_STATE)
    public fun subIdToSimSlotIndex(currentSubId:Int):Int? = getActiveSubscriptionInfoSubId(currentSubId)?.simSlotIndex

    /**
     * 활성화된 모든 subscription 정보 목록을 반환.
     * Returns a list of all active subscription information.
     *
     * @return 활성화된 subscription 정보 목록 /
     *
     * @return List of active subscription information
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getActiveSubscriptionInfoList(): List<SubscriptionInfo> = 
        getActiveSubscriptionInfoListInternal().getOrElse { emptyList() }
    
    /**
     * 활성화된 모든 subscription 정보 목록 반환 (Result 패턴)
     * Returns a list of all active subscription information (Result pattern)
     */
    @RequiresPermission(READ_PHONE_STATE)
    private fun getActiveSubscriptionInfoListInternal(): Result<List<SubscriptionInfo>> {
        return NetworkStateInfoInternal.safeExecuteWithPermission(
            operation = "getActiveSubscriptionInfoList",
            requiredPermissions = listOf(READ_PHONE_STATE)
        ) {
            subscriptionManager.activeSubscriptionInfoList ?: emptyList()
        }
    }


    /**
     * 기본 subscription에 대한 SubscriptionInfo 객체를 반환.
     * Returns the SubscriptionInfo object for the default subscription.
     *
     * @return SubscriptionInfo 객체 또는 IllegalArgumentException 예외 발생
     *
     * @return SubscriptionInfo object or IllegalArgumentException exception
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getSubscriptionInfoSubIdFromDefaultUSim(): SubscriptionInfo? =
        getSubIdFromDefaultUSim()?.let { getActiveSubscriptionInfoSubId(it) }
            ?: throw IllegalArgumentException("Can not read uSim Chip")

    /**
     * 주어진 subscription ID에 대한 SubscriptionInfo 객체를 반환.
     * Returns the SubscriptionInfo object for the given subscription ID.
     *
     * @param subId subscription ID
     * @return SubscriptionInfo object or null
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getActiveSubscriptionInfoSubId(subId: Int): SubscriptionInfo? =
        subscriptionManager.getActiveSubscriptionInfo(subId)


    /**
     * 주어진 SIM 슬롯 인덱스에 대한 SubscriptionInfo 객체를 반환.
     * Returns the SubscriptionInfo object for the given SIM slot index.
     *
     * @param slotIndex SIM slot index
     *
     * @return SubscriptionInfo object or null
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getActiveSubscriptionInfoSimSlot(slotIndex: Int): SubscriptionInfo? =
        subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slotIndex)

    /**
     * 기본 SIM 슬롯에 대한 SubscriptionInfo 객체를 반환.
     * Returns the SubscriptionInfo object for the default SIM slot.
     *
     *@return SubscriptionInfo object or NoSuchMethodError exception
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun getSubscriptionInfoSimSlotFromDefaultUSim(): SubscriptionInfo? =
        getSubIdFromDefaultUSim()?.let { getActiveSubscriptionInfoSimSlot(it) }
            ?: throw NoSuchMethodError("Can not read uSim Chip")


    /**
     * return
     * @see TelephonyManager.SIM_STATE_%%
     */
    public fun getStatusFromDefaultUSim(): Int = telephonyManager.simState

    @RequiresPermission(READ_PHONE_STATE)
    public fun getMccFromDefaultUSimString(): String? = getSubscriptionInfoSubIdFromDefaultUSim()?.let {
        checkSdkVersion(Build.VERSION_CODES.Q,
            positiveWork = {    it.mccString    },
            negativeWork = {    it.mcc.toString()   }
        )
    }

    @RequiresPermission(READ_PHONE_STATE)
    public fun getMncFromDefaultUSimString(): String? = getSubscriptionInfoSubIdFromDefaultUSim()?.let {
        checkSdkVersion(Build.VERSION_CODES.Q,
            positiveWork = {    it.mncString    },
            negativeWork = {    it.mnc.toString()   }
        )
    }

    @RequiresPermission(READ_PHONE_STATE)
    public fun getMcc(slotIndex:Int) :String? = getActiveSubscriptionInfoSimSlot(slotIndex)?.let {
        checkSdkVersion(Build.VERSION_CODES.Q,
            positiveWork = {    it.mccString    },
            negativeWork = {    it.mcc.toString()   }
        )
    }

    @RequiresPermission(READ_PHONE_STATE)
    public fun getMnc(slotIndex:Int) :String? = getActiveSubscriptionInfoSimSlot(slotIndex)?.let {
        checkSdkVersion(Build.VERSION_CODES.Q,
            positiveWork = {    it.mncString    },
            negativeWork = {    it.mnc.toString()   }
        )
    }

    @RequiresPermission(anyOf = [READ_PHONE_STATE, READ_PHONE_NUMBERS])
    public fun getPhoneNumberFromDefaultUSim(): String? =
        telephonyManager?.line1Number /*Required SDK Version 1 ~ 33 */
            ?: getSubscriptionInfoSubIdFromDefaultUSim()?.number /* number Required SDK Version 30 ~ */

    /**
     * Target SDK verion  < 29 READ_PHONE_STATE
     * else READ_PHONE_NUMBERS or READ_SMS
     */
    @RequiresPermission(anyOf = [READ_PHONE_STATE, READ_PHONE_NUMBERS])
    public fun getPhoneNumber(slotIndex: Int):String? =
        getTelephonyManagerFromUSim(slotIndex)?.line1Number /* line1Number Required SDK Version 1 ~ 33 */
            ?: getActiveSubscriptionInfoSimSlot(slotIndex)?.number /* number Required SDK Version 30 ~ 33 */

    public fun getTelephonyManagerFromUSim(slotIndex: Int): TelephonyManager? = uSimTelephonyManagerList[slotIndex]

    @RequiresPermission(READ_PHONE_STATE)
    public fun getDisplayNameFromDefaultUSim(): String? = getSubscriptionInfoSubIdFromDefaultUSim()?.displayName?.toString()

    @RequiresPermission(READ_PHONE_STATE)
    public fun getCountryIsoFromDefaultUSim(): String? = getSubscriptionInfoSimSlotFromDefaultUSim()?.countryIso

    @RequiresPermission(READ_PHONE_STATE)
    public fun isNetworkRoamingFromDefaultUSim(): Boolean =
        getSubIdFromDefaultUSim()?.let { subscriptionManager.isNetworkRoaming(it) } ?: false

    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(READ_PHONE_STATE)
    public fun registerTelephonyCallBackFromDefaultUSim(
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
    ) {
        getSubscriptionInfoSubIdFromDefaultUSim()?.let { subInfo ->
            registerTelephonyCallBack(subInfo.simSlotIndex, executor, isGpsOn, onActiveDataSubId,
                onDataConnectionState, onCellInfo, onSignalStrength, onServiceState, onCallState,
                onDisplayInfo, onTelephonyNetworkState)
        }
    }

    /**
     * SDK_INT >= Build.VERSION_CODES.S
     */
    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(READ_PHONE_STATE)
    public fun registerTelephonyCallBack(
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
    ) {
        val tm = uSimTelephonyManagerList[simSlotIndex] ?: throw IllegalStateException("TelephonyManager [$simSlotIndex] is null")
        val callback = uSimTelephonyCallbackList[simSlotIndex] ?: throw IllegalStateException("telephonyCallbackList [$simSlotIndex] is null")

        unregisterCallBack(simSlotIndex)

        if(isGpsOn) {
            tm.registerTelephonyCallback(executor, callback.baseGpsTelephonyCallback)
        } else {
            tm.registerTelephonyCallback(executor, callback.baseTelephonyCallback)
        }

        setOnActiveDataSubId(simSlotIndex, onActiveDataSubId)
        setOnDataConnectionState(simSlotIndex, onDataConnectionState)
        setOnCellInfo(simSlotIndex, onCellInfo)
        setOnSignalStrength(simSlotIndex, onSignalStrength)
        setOnServiceState(simSlotIndex, onServiceState)
        setOnCallState(simSlotIndex, onCallState)
        setOnDisplayState(simSlotIndex, onDisplayInfo)
        setOnTelephonyNetworkType(simSlotIndex, onTelephonyNetworkState)
        isRegistered[simSlotIndex] = true
    }


    /**
     * SDK_INT < Build.VERSION_CODES.S
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun registerTelephonyListenFromDefaultUSim(isGpsOn:Boolean,
                                             onActiveDataSubId: ((subId: Int) -> Unit)? = null,
                                             onDataConnectionState: ((state: Int, networkType: Int) -> Unit)? = null,
                                             onCellInfo: ((currentCellInfo: CurrentCellInfo) -> Unit)? = null,
                                             onSignalStrength: ((currentSignalStrength: CurrentSignalStrength) -> Unit)? = null,
                                             onServiceState: ((currentServiceState: CurrentServiceState) -> Unit)? = null,
                                             onCallState: ((callState: Int, phoneNumber: String?) -> Unit)? = null,
                                             onDisplayInfo: ((telephonyDisplayInfo: TelephonyDisplayInfo) -> Unit)? = null,
                                             onTelephonyNetworkState: ((telephonyNetworkState: TelephonyNetworkState) -> Unit)? = null) {

        getSubscriptionInfoSubIdFromDefaultUSim()?.let { subInfo ->
            registerTelephonyListen(subInfo.simSlotIndex, isGpsOn, onActiveDataSubId, onDataConnectionState, onCellInfo,
                onSignalStrength, onServiceState, onCallState, onDisplayInfo, onTelephonyNetworkState)
        }
    }

    /**
     * SDK_INT < Build.VERSION_CODES.S
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun registerTelephonyListen(simSlotIndex: Int, isGpsOn:Boolean,
                              onActiveDataSubId: ((subId: Int) -> Unit)? = null,
                              onDataConnectionState: ((state: Int, networkType: Int) -> Unit)? = null,
                              onCellInfo: ((currentCellInfo: CurrentCellInfo) -> Unit)? = null,
                              onSignalStrength: ((currentSignalStrength: CurrentSignalStrength) -> Unit)? = null,
                              onServiceState: ((currentServiceState: CurrentServiceState) -> Unit)? = null,
                              onCallState: ((callState: Int, phoneNumber: String?) -> Unit)? = null,
                              onDisplayInfo: ((telephonyDisplayInfo: TelephonyDisplayInfo) -> Unit)? = null,
                              onTelephonyNetworkState: ((telephonyNetworkState: TelephonyNetworkState) -> Unit)? = null) {

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Logx.w("SDK_INT >= Build.VERSION_CODES.S Your Version is ${Build.VERSION.SDK_INT}")
        }
        val tm =uSimTelephonyManagerList[simSlotIndex]
        val callback =uSimTelephonyCallbackList[simSlotIndex]
        if(callback == null || tm == null) {
            throw Exception("telephonyCallbackList[$simSlotIndex] is null")
        }

        val event = if(isGpsOn) {
            PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                    PhoneStateListener.LISTEN_DATA_CONNECTION_STATE or
                    PhoneStateListener.LISTEN_DATA_ACTIVITY or
                    PhoneStateListener.LISTEN_SERVICE_STATE or
                    PhoneStateListener.LISTEN_CALL_STATE or
                    PhoneStateListener.LISTEN_CELL_INFO or
                    PhoneStateListener.LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE
        } else {
            PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                    PhoneStateListener.LISTEN_DATA_CONNECTION_STATE or
                    PhoneStateListener.LISTEN_DATA_ACTIVITY or
                    PhoneStateListener.LISTEN_SERVICE_STATE or
                    PhoneStateListener.LISTEN_CALL_STATE or
                    PhoneStateListener.LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE
        }
        tm.listen(callback.basePhoneStateListener, event)

        setOnActiveDataSubId(simSlotIndex, onActiveDataSubId)
        setOnDataConnectionState(simSlotIndex, onDataConnectionState)
        setOnCellInfo(simSlotIndex, onCellInfo)
        setOnSignalStrength(simSlotIndex, onSignalStrength)
        setOnServiceState(simSlotIndex, onServiceState)
        setOnCallState(simSlotIndex, onCallState)
        setOnDisplayState(simSlotIndex, onDisplayInfo)
        setOnTelephonyNetworkType(simSlotIndex, onTelephonyNetworkState)
    }

    /**
     * SDK_INT < Build.VERSION_CODES.S
     */
    public fun unregisterListen(simSlotIndex: Int) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            throw Exception("SDK_INT >= Build.VERSION_CODES.S Your Version is ${Build.VERSION.SDK_INT}")
        }
        val tm =uSimTelephonyManagerList[simSlotIndex]
        val callback =uSimTelephonyCallbackList[simSlotIndex]
        if(callback == null || tm == null) {
            throw Exception("telephonyCallbackList[$simSlotIndex] is null")
        }
        try {
            tm.listen(callback.basePhoneStateListener,PhoneStateListener.LISTEN_NONE)
        }catch (e:Exception) {
            e.printStackTrace()
        }
    }

    /**
     * SDK_INT < Build.VERSION_CODES.S
     */
    @RequiresPermission(READ_PHONE_STATE)
    public fun unregisterListenFromDefaultUSim() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            throw Exception("SDK_INT >= Build.VERSION_CODES.S Your Version is ${Build.VERSION.SDK_INT}")
        }
        getSubscriptionInfoSubIdFromDefaultUSim()?.let {
            unregisterListen(it.simSlotIndex)
        }
    }

    /**
     * SDK_INT >= Build.VERSION_CODES.S
     */
    @RequiresPermission(READ_PHONE_STATE)
    @RequiresApi(Build.VERSION_CODES.S)
    public fun unregisterCallBackFromDefaultUSim() {
        getSubscriptionInfoSubIdFromDefaultUSim()?.let {
            unregisterCallBack(it.simSlotIndex)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    public fun unregisterCallBack(simSlotIndex: Int) {
        val tm = uSimTelephonyManagerList[simSlotIndex]
        val callback = uSimTelephonyCallbackList[simSlotIndex]
        if (callback == null || tm == null) {
            throw Exception("telephonyCallbackList[$simSlotIndex] is null")
        }
        try {
            tm.unregisterTelephonyCallback(callback.baseTelephonyCallback)
        } catch (e: SecurityException) {
            Logx.w("Permission issue during unregistering callback", e)
        } catch (e: IllegalArgumentException) {
            Logx.w("Invalid callback provided", e)
        }

        try {
            tm.unregisterTelephonyCallback(callback.baseGpsTelephonyCallback)
        } catch (e: SecurityException) {
            Logx.w("Permission issue during unregistering callback", e)
        } catch (e: IllegalArgumentException) {
            Logx.w("Invalid callback provided", e)
        }
        isRegistered[simSlotIndex] = false
    }

    @RequiresPermission(READ_PHONE_STATE)
    public fun allClearCallback() {
        uSimTelephonyCallbackList.forEach { key, value ->
            setOnActiveDataSubId(key,null)
            setOnDataConnectionState(key,null)
            setOnCellInfo(key,null)
            setOnSignalStrength(key,null)
            setOnServiceState(key,null)
            setOnCallState(key,null)
        }
    }
    /**
     * You must call
     * TelephonyStateInfo.registerCallBack() or TelephonyStateInfo.registerListen() first before using it.
     * **/
    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnActiveDataSubId(simSlotIndex: Int, onActiveDataSubId: ((subId: Int) -> Unit)? = null) {
        uSimTelephonyCallbackList[simSlotIndex]?.let { it.setOnActiveDataSubId(onActiveDataSubId) }
            ?: throw Exception("setOnActiveDataSubId telephonyCallbackList[$simSlotIndex] is null")
    }

    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnActiveDataSubIdFromDefaultUSim(onActiveDataSubId: ((subId: Int) -> Unit)? = null) {
        getSubscriptionInfoSubIdFromDefaultUSim()?.let {
            uSimTelephonyCallbackList[it.simSlotIndex]?.let { it.setOnActiveDataSubId(onActiveDataSubId) }
                ?: throw Exception("setOnActiveDataSubId telephonyCallbackList[${it.simSlotIndex}] is null")
        }
    }


    /**
     * You must call
     * TelephonyStateInfo.registerCallBack() or TelephonyStateInfo.registerListen() first before using it.
     * **/
    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnDataConnectionState(simSlotIndex: Int, onDataConnectionState: ((state: Int, networkType: Int) -> Unit)? = null) {
        uSimTelephonyCallbackList[simSlotIndex]?.let { it.setOnDataConnectionState(onDataConnectionState) }
            ?: throw Exception("setOnDataConnectionState telephonyCallbackList[$simSlotIndex] is null")
    }

    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnDataConnectionStateFromDefaultUSim(onDataConnectionState: ((state: Int, networkType: Int) -> Unit)? = null) {
        getSubscriptionInfoSubIdFromDefaultUSim()?.let{
            uSimTelephonyCallbackList[it.simSlotIndex]?.let { it.setOnDataConnectionState(onDataConnectionState) }
                ?: throw Exception("setOnDataConnectionState telephonyCallbackList[${it.simSlotIndex}] is null")
        }
    }

    /**
     * You must call
     * TelephonyStateInfo.registerCallBack() or TelephonyStateInfo.registerListen() first before using it.
     * **/
    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnCellInfo(simSlotIndex: Int, onCellInfo: ((currentCellInfo: CurrentCellInfo) -> Unit)? = null) {
        uSimTelephonyCallbackList[simSlotIndex]?.let { it.setOnCellInfo(onCellInfo) }
            ?: throw Exception("setOnCellInfo telephonyCallbackList[$simSlotIndex] is null")
    }

    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnCellInfoFromDefaultUSim(onCellInfo: ((currentCellInfo: CurrentCellInfo) -> Unit)? = null) {
        getSubscriptionInfoSubIdFromDefaultUSim()?.let {
            uSimTelephonyCallbackList[it.simSlotIndex]?.let { it.setOnCellInfo(onCellInfo) }
                ?: throw Exception("setOnCellInfo telephonyCallbackList[${it.simSlotIndex}] is null")
        }
    }

    /**
     * You must call
     * TelephonyStateInfo.registerCallBack() or TelephonyStateInfo.registerListen() first before using it.
     * **/
    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnSignalStrength(simSlotIndex: Int, onSignalStrength: ((currentSignalStrength: CurrentSignalStrength) -> Unit)? = null) {
        uSimTelephonyCallbackList[simSlotIndex]?.let { it.setOnSignalStrength(onSignalStrength) }
            ?: throw Exception("setOnSignalStrength telephonyCallbackList[$simSlotIndex] is null")
    }

    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnSignalStrengthFromDefaultUSim(onSignalStrength: ((currentSignalStrength: CurrentSignalStrength) -> Unit)? = null) {
        getSubscriptionInfoSubIdFromDefaultUSim()?.let {
            uSimTelephonyCallbackList[it.simSlotIndex]?.let { it.setOnSignalStrength(onSignalStrength) }
                ?: throw Exception("setOnSignalStrength telephonyCallbackList[${it.simSlotIndex}] is null")
        }
    }


    /**
     * You must call
     * TelephonyStateInfo.registerCallBack() or TelephonyStateInfo.registerListen() first before using it.
     * **/
    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnServiceState(simSlotIndex: Int, onServiceState: ((currentServiceState: CurrentServiceState) -> Unit)? = null) {
        uSimTelephonyCallbackList[simSlotIndex]?.let { it.setOnServiceState(onServiceState) }
            ?: throw Exception("setOnServiceState telephonyCallbackList[$simSlotIndex] is null")
    }

    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnServiceStateFromDefaultUSim(onServiceState: ((currentServiceState: CurrentServiceState) -> Unit)? = null) {
        getSubscriptionInfoSubIdFromDefaultUSim()?.let {
            uSimTelephonyCallbackList[it.simSlotIndex]?.let { it.setOnServiceState(onServiceState) }
                ?: throw Exception("setOnServiceState telephonyCallbackList[${it.simSlotIndex}] is null")
        }
    }

    /**
     * You must call
     * TelephonyStateInfo.registerCallBack() or TelephonyStateInfo.registerListen() first before using it.
     * **/
    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnCallState(simSlotIndex: Int, onCallState:  ((callState: Int, phoneNumber: String?) -> Unit)? = null) {
        uSimTelephonyCallbackList[simSlotIndex]?.let { it.setOnCallState(onCallState) }
            ?: throw Exception("setOnCallState telephonyCallbackList[$simSlotIndex] is null")
    }

    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnCallState(onCallState:  ((callState: Int, phoneNumber: String?) -> Unit)? = null) {
        getSubscriptionInfoSubIdFromDefaultUSim()?.let {
            uSimTelephonyCallbackList[it.simSlotIndex]?.let { it.setOnCallState(onCallState) }
                ?: throw Exception("setOnCallState telephonyCallbackList[${it.simSlotIndex}] is null")
        }
    }

    /**
     * You must call
     * TelephonyStateInfo.registerCallBack() or TelephonyStateInfo.registerListen() first before using it.
     * **/
    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnDisplayState(simSlotIndex: Int, onDisplay: ((telephonyDisplayInfo: TelephonyDisplayInfo) -> Unit)? = null) {
        uSimTelephonyCallbackList[simSlotIndex]?.let { it.setOnDisplay(onDisplay) }
            ?: throw Exception("setOnDisplayState telephonyCallbackList[$simSlotIndex] is null")
    }

    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnDisplayStateFromDefaultUSim(onDisplay: ((telephonyDisplayInfo: TelephonyDisplayInfo) -> Unit)? = null) {
        getSubscriptionInfoSubIdFromDefaultUSim()?.let {
            uSimTelephonyCallbackList[it.simSlotIndex]?.let { it.setOnDisplay(onDisplay) }
                ?: throw Exception("setOnDisplayState telephonyCallbackList[${it.simSlotIndex}] is null")
        }
    }

    /**
     * You must call
     * TelephonyStateInfo.registerCallBack() or TelephonyStateInfo.registerListen() first before using it.
     * **/
    public fun setOnTelephonyNetworkType(simSlotIndex: Int, onTelephonyNetworkType: ((telephonyNetworkState: TelephonyNetworkState) -> Unit)? = null) {
        uSimTelephonyCallbackList[simSlotIndex]?.let { it.setOnTelephonyNetworkType(onTelephonyNetworkType) }
            ?: throw Exception("setOnTelephonyNetworkType telephonyCallbackList[$simSlotIndex] is null")
    }

    @RequiresPermission(READ_PHONE_STATE)
    public fun setOnTelephonyNetworkTypeFromDefaultUSim(onTelephonyNetworkType: ((telephonyNetworkState: TelephonyNetworkState) -> Unit)? = null) {
        getSubscriptionInfoSubIdFromDefaultUSim()?.let {
            uSimTelephonyCallbackList[it.simSlotIndex]?.let { it.setOnTelephonyNetworkType(onTelephonyNetworkType) }
                ?: throw Exception("setOnTelephonyNetworkType telephonyCallbackList[${it.simSlotIndex}] is null")
        }
    }

    public fun isRegistered(simSlotIndex: Int):Boolean = isRegistered[simSlotIndex]?:false

    @RequiresPermission(READ_PHONE_STATE)
    public fun isRegisteredDefaultUSim(): Boolean =
        getSubscriptionInfoSubIdFromDefaultUSim()?.let { isRegistered[it.simSlotIndex] ?: false } ?: false


    /**
     * 네트워크 연결 여부를 확인합니다.
     * Checks if the network is connected.
     *
     * @return Boolean - 네트워크가 연결되어 있으면 true.
     * @Returns true if the network is connected.
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    public fun isNetworkConnected(): Boolean = isNetworkConnectedInternal().getOrElse { false }
    
    /**
     * 네트워크 연결 여부를 확인 (Result 패턴)
     * Checks if the network is connected (Result pattern)
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    private fun isNetworkConnectedInternal(): Result<Boolean> {
        return NetworkStateInfoInternal.safeExecuteNetworkOperation("isNetworkConnected") {
            val caps = getCapabilitiesInternal().getOrNull()
            val linkProperties = getLinkPropertiesInternal().getOrNull()
            (caps != null) && (linkProperties != null)
        }
    }

    /**
     * 현재 네트워크의 NetworkCapabilities를 반환.
     * Returns the NetworkCapabilities of the current network.
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    public fun getCapabilities(): NetworkCapabilities? = getCapabilitiesInternal().getOrNull()
    
    /**
     * 현재 네트워크의 NetworkCapabilities를 반환 (Result 패턴)
     * Returns the NetworkCapabilities of the current network (Result pattern)
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    private fun getCapabilitiesInternal(): Result<NetworkCapabilities?> {
        return NetworkStateInfoInternal.safeExecuteNetworkOperation("getCapabilities") {
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        }
    }

    @RequiresPermission(ACCESS_NETWORK_STATE)
    public fun getLinkProperties(): LinkProperties? = getLinkPropertiesInternal().getOrNull()
    
    /**
     * 현재 네트워크의 LinkProperties를 반환 (Result 패턴)
     * Returns the LinkProperties of the current network (Result pattern)
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    private fun getLinkPropertiesInternal(): Result<LinkProperties?> {
        return NetworkStateInfoInternal.safeExecuteNetworkOperation("getLinkProperties") {
            connectivityManager.getLinkProperties(connectivityManager.activeNetwork)
        }
    }

    @RequiresPermission(ACCESS_NETWORK_STATE)
    public fun isConnectedWifi(): Boolean = getCapabilities()?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false

    @RequiresPermission(ACCESS_NETWORK_STATE)
    public fun isConnectedMobile(): Boolean = getCapabilities()?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false

    @RequiresPermission(ACCESS_NETWORK_STATE)
    public fun isConnectedVPN(): Boolean = getCapabilities()?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false

    @RequiresPermission(ACCESS_NETWORK_STATE)
    public fun isConnectedBluetooth(): Boolean = getCapabilities()?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ?: false

    @RequiresPermission(ACCESS_NETWORK_STATE)
    public fun isConnectedWifiAware(): Boolean = getCapabilities()?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE) ?: false

    @RequiresPermission(ACCESS_NETWORK_STATE)
    public fun isConnectedEthernet(): Boolean = getCapabilities()?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ?: false

    @RequiresPermission(ACCESS_NETWORK_STATE)
    public fun isConnectedLowPan(): Boolean = getCapabilities()?.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN) ?: false

    @RequiresPermission(ACCESS_NETWORK_STATE)
    @RequiresApi(Build.VERSION_CODES.S)
    public fun isConnectedUSB(): Boolean = getCapabilities()?.hasTransport(NetworkCapabilities.TRANSPORT_USB) ?: false

    public fun isWifiOn(): Boolean = wifiController.isWifiEnabled()

    /**
     * 네트워크 상태 콜백을 등록.
     * Registers a network state callback.
     *
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    public fun registerNetworkCallback(
        handler: Handler? = null,
        onNetworkAvailable: ((Network) -> Unit)? = null,
        onNetworkLosing: ((Network, Int) -> Unit)? = null,
        onNetworkLost: ((Network) -> Unit)? = null,
        onUnavailable: (() -> Unit)? = null,
        onNetworkCapabilitiesChanged: ((Network, NetworkCapabilitiesData) -> Unit)? = null,
        onLinkPropertiesChanged: ((Network, NetworkLinkPropertiesData) -> Unit)? = null,
        onBlockedStatusChanged: ((Network, Boolean) -> Unit)? = null,
    ) {
        unregisterNetworkCallback()
        networkCallBack = NetworkStateCallback(
            onNetworkAvailable, onNetworkLosing, onNetworkLost, onUnavailable,
            onNetworkCapabilitiesChanged, onLinkPropertiesChanged, onBlockedStatusChanged
        )

        val networkRequest = NetworkRequest.Builder().build()
        networkCallBack?.let { callback->
            handler?.let {
                connectivityManager.registerNetworkCallback(networkRequest, callback, it)
            } ?: connectivityManager.registerNetworkCallback(networkRequest, callback)
        }
    }

    /**
     * 기본 네트워크 상태 콜백을 등록.
     * Registers a default network state callback.
     *
     */
    @RequiresPermission(ACCESS_NETWORK_STATE)
    public fun registerDefaultNetworkCallback(
        handler: Handler? = null,
        onNetworkAvailable: ((Network) -> Unit)? = null,
        onNetworkLosing: ((Network, Int) -> Unit)? = null,
        onNetworkLost: ((Network) -> Unit)? = null,
        onUnavailable: (() -> Unit)? = null,
        onNetworkCapabilitiesChanged: ((Network, NetworkCapabilitiesData) -> Unit)? = null,
        onLinkPropertiesChanged: ((Network, NetworkLinkPropertiesData) -> Unit)? = null,
        onBlockedStatusChanged: ((Network, Boolean) -> Unit)? = null,
    ) {
        unregisterDefaultNetworkCallback()

        networkDefaultCallback = NetworkStateCallback(
            onNetworkAvailable, onNetworkLosing, onNetworkLost, onUnavailable,
            onNetworkCapabilitiesChanged, onLinkPropertiesChanged, onBlockedStatusChanged
        )

        networkDefaultCallback?.let { callback->
            handler?.let {
                connectivityManager.registerDefaultNetworkCallback(callback, it)
            }?: connectivityManager.registerDefaultNetworkCallback(callback)
        }
    }

    public fun unregisterNetworkCallback() {
        networkCallBack?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
        networkCallBack = null
    }

    public fun unregisterDefaultNetworkCallback() {
        networkDefaultCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
        networkDefaultCallback = null
    }

    public override fun onDestroy() {
        super.onDestroy()
        uSimTelephonyCallbackList.forEach { key, value ->
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                unregisterCallBack(key)
            } else {
                unregisterListen(key)
            }
        }
        unregisterNetworkCallback()
        unregisterDefaultNetworkCallback()
        wifiController.onDestroy()
    }

    /**
     * Can add ESIM
     */
    public fun isESimSupport(): Boolean = (euiccManager.euiccInfo != null && euiccManager.isEnabled)

    /**
     * Used for multisim with PSIM(Slot index is 0)
     * ESIM Added ?
     */
    public fun isRegisterESim(eSimSlotIndex: Int): Boolean =
        !(isESimSupport() && eSimSlotIndex != 0 && subscriptionManager.accessibleSubscriptionInfoList == null)

    public fun getActiveSimStatus(slotIndex: Int): Int =
        getActiveSimStatus(isESimSupport(), isRegisterESim(slotIndex), slotIndex)

//    @RequiresPermission(READ_PHONE_STATE)
//    public fun getActiveSimStatus(subId:Int): Int {
//        return subIdToSimSlotIndex(subId)?.let { slotIndex->
//            getActiveSimStatus(isAbleEsim(), isRegisterESim(slotIndex), slotIndex)
//        } ?: TelephonyManager.SIM_STATE_UNKNOWN
//    }

}