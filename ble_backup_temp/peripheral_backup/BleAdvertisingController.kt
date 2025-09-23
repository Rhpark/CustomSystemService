package kr.open.library.systemmanager.controller.bluetooth.peripheral

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.base.BluetoothBaseController
import kr.open.library.systemmanager.controller.bluetooth.base.BlePermissionManager
import kr.open.library.systemmanager.controller.bluetooth.base.BleResourceManager
import kr.open.library.systemmanager.controller.bluetooth.debug.BleDebugLogger
import kr.open.library.systemmanager.controller.bluetooth.error.BleServiceError
import kr.open.library.systemmanager.controller.bluetooth.base.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * BLE 광고 컨트롤러
 * BLE Advertising Controller
 * 
 * BLE Peripheral로서 다른 기기들에게 자신의 존재를 알리는 광고 기능을 담당합니다.
 * Handles BLE advertising functionality to announce device presence to other devices as a Peripheral.
 * 
 * 주요 기능:
 * Key features:
 * - 유연한 광고 데이터 구성
 * - 다양한 광고 모드 지원
 * - 광고 상태 실시간 모니터링
 * - 자동 광고 재시작
 * - 전력 효율적인 광고 관리
 */
class BleAdvertisingController(context: Context) : BluetoothBaseController(
    context, 
    BlePermissionManager.BleRole.PERIPHERAL
) {
    
    private val TAG = "BleAdvertisingController"
    
    /**
     * 광고 상태 정의
     */
    enum class AdvertisingState {
        IDLE,           // 대기 중
        STARTING,       // 시작 중
        ADVERTISING,    // 광고 중
        STOPPING,       // 중지 중
        ERROR           // 오류 상태
    }
    
    /**
     * 광고 설정 옵션
     */
    data class AdvertisingConfig(
        val advertiseMode: Int = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY,
        val txPowerLevel: Int = AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM,
        val connectable: Boolean = true,
        val timeout: Int = 0, // 0 = 무제한
        val includeDeviceName: Boolean = true,
        val includeTxPowerLevel: Boolean = true,
        val serviceUuids: List<String> = emptyList(),
        val serviceData: Map<String, ByteArray> = emptyMap(),
        val manufacturerData: Map<Int, ByteArray> = emptyMap(),
        val localName: String? = null,
        val autoRestart: Boolean = true,
        val restartDelay: Long = 1000L // 1초 후 재시작
    )
    
    /**
     * 광고 이벤트 리스너
     */
    interface AdvertisingListener {
        fun onAdvertisingStarted()
        fun onAdvertisingStartFailure(errorCode: Int, reason: String)
        fun onAdvertisingStopped()
        fun onAdvertisingStateChanged(state: AdvertisingState)
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    
    // 상태 관리
    private val _advertisingState = MutableStateFlow(AdvertisingState.IDLE)
    val advertisingState: StateFlow<AdvertisingState> = _advertisingState.asStateFlow()
    
    private val listeners = CopyOnWriteArrayList<AdvertisingListener>()
    private var currentAdvertiseCallback: AdvertiseCallback? = null
    private var currentConfig: AdvertisingConfig? = null
    private val restartHandler = Handler(Looper.getMainLooper())
    private var restartRunnable: Runnable? = null
    
    // 리소스 ID
    private var advertiseCallbackResourceId: String? = null
    
    init {
        // 광고 기능 초기화
        initializeAdvertiser()
        
        BleDebugLogger.logSystemState("BleAdvertisingController initialized")
    }
    
    /**
     * 광고 기능 초기화
     */
    private fun initializeAdvertiser() {
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            BleDebugLogger.logError(
                BleServiceError.HardwareError.BluetoothNotAvailable("BLE advertiser not available")
            )
        }
    }
    
    /**
     * 광고 리스너 등록
     */
    fun addAdvertisingListener(listener: AdvertisingListener) {
        listeners.addIfAbsent(listener)
    }
    
    /**
     * 광고 리스너 해제
     */
    fun removeAdvertisingListener(listener: AdvertisingListener) {
        listeners.remove(listener)
    }
    
    /**
     * 모든 리스너 해제
     */
    fun clearAdvertisingListeners() {
        listeners.clear()
    }
    
    /**
     * BLE 광고 시작
     */
    fun startAdvertising(config: AdvertisingConfig = AdvertisingConfig()): Result<Unit> {
        return try {
            BleDebugLogger.logAdvertisingStart(config.toString())
            
            // 전제 조건 검사
            val permissionResult = checkPermissions("ADVERTISE")
            if (permissionResult is Result.Failure) {
                return permissionResult
            }
            
            val systemResult = checkSystemState()
            if (systemResult is Result.Failure) {
                return systemResult
            }
            
            // 이미 광고 중이면 중지 후 새로 시작
            if (_advertisingState.value == AdvertisingState.ADVERTISING) {
                val stopResult = stopAdvertising()
                if (stopResult is Result.Failure) {
                    return stopResult
                }
            }
            
            // 광고 기능 유효성 검사
            val advertiser = bluetoothLeAdvertiser
            if (advertiser == null) {
                val error = BleServiceError.HardwareError.BluetoothNotAvailable("BLE advertiser not available")
                BleDebugLogger.logError(error)
                return Result.Failure(error)
            }
            
            // 광고 설정 및 데이터 생성
            val advertiseSettings = buildAdvertiseSettings(config)
            val advertiseData = buildAdvertiseData(config)
            val scanResponseData = buildScanResponseData(config)
            
            // 광고 콜백 생성
            val advertiseCallback = createAdvertiseCallback(config)
            
            _advertisingState.value = AdvertisingState.STARTING
            notifyStateChanged(AdvertisingState.STARTING)
            
            // 광고 시작
            advertiser.startAdvertising(advertiseSettings, advertiseData, scanResponseData, advertiseCallback)
            
            // 상태 및 설정 저장
            currentAdvertiseCallback = advertiseCallback
            currentConfig = config
            
            // 리소스 등록
            advertiseCallbackResourceId = resourceManager.registerResource(
                AdvertiseCallbackResource(advertiseCallback),
                BleResourceManager.ResourceType.ADVERTISE_CALLBACK
            )
            
            Result.Success(Unit)
            
        } catch (e: Exception) {
            val error = BleServiceError.AdvertiseError.AdvertiseStartFailed("Failed to start advertising", e)
            BleDebugLogger.logException(e, "Start advertising failed")
            _advertisingState.value = AdvertisingState.ERROR
            notifyStateChanged(AdvertisingState.ERROR)
            Result.Failure(error)
        }
    }
    
    /**
     * BLE 광고 중지
     */
    fun stopAdvertising(): Result<Unit> {
        return try {
            if (_advertisingState.value == AdvertisingState.IDLE) {
                return Result.Success(Unit)
            }
            
            _advertisingState.value = AdvertisingState.STOPPING
            notifyStateChanged(AdvertisingState.STOPPING)
            
            // 재시작 예약 취소
            cancelAdvertisingRestart()
            
            // 광고 중지
            val advertiser = bluetoothLeAdvertiser
            val callback = currentAdvertiseCallback
            
            if (advertiser != null && callback != null) {
                advertiser.stopAdvertising(callback)
            }
            
            // 리소스 해제
            advertiseCallbackResourceId?.let { resourceId ->
                resourceManager.disposeResource(resourceId)
                advertiseCallbackResourceId = null
            }
            
            // 상태 정리
            currentAdvertiseCallback = null
            _advertisingState.value = AdvertisingState.IDLE
            
            BleDebugLogger.logAdvertisingStop()
            
            // 리스너들에게 광고 중지 알림
            notifyStateChanged(AdvertisingState.IDLE)
            notifyAdvertisingStopped()
            
            Result.Success(Unit)
            
        } catch (e: Exception) {
            val error = BleServiceError.AdvertiseError.AdvertiseStopFailed("Failed to stop advertising", e)
            BleDebugLogger.logException(e, "Stop advertising failed")
            Result.Failure(error)
        }
    }
    
    /**
     * 광고 중인지 확인
     */
    fun isAdvertising(): Boolean {
        return _advertisingState.value == AdvertisingState.ADVERTISING
    }
    
    /**
     * 현재 광고 설정 반환
     */
    fun getCurrentConfig(): AdvertisingConfig? {
        return currentConfig
    }
    
    /**
     * 광고 설정 생성
     */
    private fun buildAdvertiseSettings(config: AdvertisingConfig): AdvertiseSettings {
        return AdvertiseSettings.Builder()
            .setAdvertiseMode(config.advertiseMode)
            .setTxPowerLevel(config.txPowerLevel)
            .setConnectable(config.connectable)
            .setTimeout(config.timeout)
            .build()
    }
    
    /**
     * 광고 데이터 생성
     */
    private fun buildAdvertiseData(config: AdvertisingConfig): AdvertiseData {
        val builder = AdvertiseData.Builder()
            .setIncludeDeviceName(config.includeDeviceName)
            .setIncludeTxPowerLevel(config.includeTxPowerLevel)
        
        // 서비스 UUID 추가
        config.serviceUuids.forEach { uuidString ->
            try {
                val uuid = UUID.fromString(uuidString)
                builder.addServiceUuid(ParcelUuid(uuid))
            } catch (e: IllegalArgumentException) {
                BleDebugLogger.logException(e, "Invalid service UUID: $uuidString")
            }
        }
        
        // 서비스 데이터 추가
        config.serviceData.forEach { (uuidString, data) ->
            try {
                val uuid = UUID.fromString(uuidString)
                builder.addServiceData(ParcelUuid(uuid), data)
            } catch (e: IllegalArgumentException) {
                BleDebugLogger.logException(e, "Invalid service data UUID: $uuidString")
            }
        }
        
        // 제조사 데이터 추가
        config.manufacturerData.forEach { (manufacturerId, data) ->
            builder.addManufacturerData(manufacturerId, data)
        }
        
        return builder.build()
    }
    
    /**
     * 스캔 응답 데이터 생성
     */
    private fun buildScanResponseData(config: AdvertisingConfig): AdvertiseData? {
        if (config.localName == null) {
            return null
        }
        
        return AdvertiseData.Builder()
            .setIncludeDeviceName(false) // 메인 광고 데이터에서 이미 포함
            .setIncludeTxPowerLevel(false)
            .build()
    }
    
    /**
     * 광고 콜백 생성
     */
    private fun createAdvertiseCallback(config: AdvertisingConfig): AdvertiseCallback {
        return object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                _advertisingState.value = AdvertisingState.ADVERTISING
                
                BleDebugLogger.logAdvertisingStart("Advertising started successfully")
                
                notifyStateChanged(AdvertisingState.ADVERTISING)
                notifyAdvertisingStarted()
            }
            
            override fun onStartFailure(errorCode: Int) {
                val reason = getAdvertiseFailureReason(errorCode)
                val error = BleServiceError.AdvertiseError.AdvertiseFailed("Advertising start failed: $reason", errorCode)
                
                BleDebugLogger.logAdvertisingFailed(errorCode, reason)
                
                _advertisingState.value = AdvertisingState.ERROR
                notifyStateChanged(AdvertisingState.ERROR)
                notifyAdvertisingStartFailure(errorCode, reason)
                
                // 자동 재시작 시도
                if (config.autoRestart) {
                    scheduleAdvertisingRestart(config)
                }
            }
        }
    }
    
    /**
     * 광고 재시작 예약
     */
    private fun scheduleAdvertisingRestart(config: AdvertisingConfig) {
        restartRunnable = Runnable {
            if (_advertisingState.value == AdvertisingState.ERROR) {
                BleDebugLogger.logSystemState("Attempting to restart advertising")
                startAdvertising(config)
            }
        }
        restartHandler.postDelayed(restartRunnable!!, config.restartDelay)
    }
    
    /**
     * 광고 재시작 취소
     */
    private fun cancelAdvertisingRestart() {
        restartRunnable?.let { runnable ->
            restartHandler.removeCallbacks(runnable)
            restartRunnable = null
        }
    }
    
    /**
     * 광고 실패 이유 반환
     */
    private fun getAdvertiseFailureReason(errorCode: Int): String = when (errorCode) {
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
        else -> "Unknown error ($errorCode)"
    }
    
    /**
     * 리스너들에게 상태 변경 알림
     */
    private fun notifyStateChanged(state: AdvertisingState) {
        listeners.forEach { listener ->
            try {
                listener.onAdvertisingStateChanged(state)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify state changed failed")
            }
        }
    }
    
    /**
     * 리스너들에게 광고 시작 알림
     */
    private fun notifyAdvertisingStarted() {
        listeners.forEach { listener ->
            try {
                listener.onAdvertisingStarted()
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify advertising started failed")
            }
        }
    }
    
    /**
     * 리스너들에게 광고 시작 실패 알림
     */
    private fun notifyAdvertisingStartFailure(errorCode: Int, reason: String) {
        listeners.forEach { listener ->
            try {
                listener.onAdvertisingStartFailure(errorCode, reason)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify advertising start failure failed")
            }
        }
    }
    
    /**
     * 리스너들에게 광고 중지 알림
     */
    private fun notifyAdvertisingStopped() {
        listeners.forEach { listener ->
            try {
                listener.onAdvertisingStopped()
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify advertising stopped failed")
            }
        }
    }
    
    /**
     * 상태 정보 반환
     */
    fun getStatusInfo(): String {
        return buildString {
            appendLine("=== BLE Advertising Controller Status ===")
            appendLine("Current State: ${_advertisingState.value}")
            appendLine("Advertising: ${isAdvertising()}")
            appendLine("Auto Restart: ${currentConfig?.autoRestart ?: false}")
            appendLine("Listeners: ${listeners.size}")
            currentConfig?.let { config ->
                appendLine("Mode: ${config.advertiseMode}")
                appendLine("TX Power: ${config.txPowerLevel}")
                appendLine("Connectable: ${config.connectable}")
                appendLine("Service UUIDs: ${config.serviceUuids.size}")
                appendLine("Manufacturer Data: ${config.manufacturerData.size}")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 광고 중지
        stopAdvertising()
        
        // 리스너 정리
        clearAdvertisingListeners()
        
        // 핸들러 정리
        restartHandler.removeCallbacksAndMessages(null)
        
        BleDebugLogger.logSystemState("BleAdvertisingController destroyed")
    }
    
    /**
     * 광고 콜백 리소스 래퍼
     */
    private class AdvertiseCallbackResource(private val callback: AdvertiseCallback) : BleResource {
        override fun cleanup() {
            // 광고 콜백은 별도 정리 작업이 필요없음
        }
    }
}