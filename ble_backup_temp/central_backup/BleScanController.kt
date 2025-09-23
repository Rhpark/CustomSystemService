package kr.open.library.systemmanager.controller.bluetooth.central

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.base.BluetoothBaseController
import kr.open.library.systemmanager.controller.bluetooth.base.BleResourceManager
import kr.open.library.systemmanager.controller.bluetooth.data.BleDevice
import kr.open.library.systemmanager.controller.bluetooth.debug.BleDebugLogger
import kr.open.library.systemmanager.controller.bluetooth.error.BleServiceError
import kr.open.library.systemmanager.controller.bluetooth.base.BlePermissionManager
import kr.open.library.systemmanager.controller.bluetooth.base.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * BLE 스캐닝 컨트롤러
 * BLE Scanning Controller
 * 
 * BLE 기기 검색 및 스캐닝 기능을 담당합니다.
 * Handles BLE device discovery and scanning functionality.
 * 
 * 주요 기능:
 * Key features:
 * - 고급 필터링 및 설정 옵션
 * - 실시간 스캔 결과 모니터링
 * - 자동 중복 제거 및 신호 강도 기반 업데이트
 * - 배터리 최적화된 스캐닝
 */
class BleScanController(context: Context) : BluetoothBaseController(context) {
    
    private val TAG = "BleScanController"
    
    /**
     * 스캔 상태 정의
     */
    enum class ScanState {
        IDLE,           // 대기 중
        SCANNING,       // 스캐닝 중
        STOPPING,       // 중지 중
        ERROR           // 오류 상태
    }
    
    /**
     * 스캔 설정 옵션
     */
    data class ScanConfig(
        val scanMode: Int = ScanSettings.SCAN_MODE_LOW_POWER,
        val callbackType: Int = ScanSettings.CALLBACK_TYPE_ALL_MATCHES,
        val matchMode: Int = ScanSettings.MATCH_MODE_AGGRESSIVE,
        val numOfMatches: Int = ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT,
        val reportDelay: Long = 0L,
        val legacyScanOnly: Boolean = false,
        val phy1MEnabled: Boolean = true,
        val phyCodedEnabled: Boolean = false,
        val filters: List<ScanFilterConfig> = emptyList(),
        val scanDuration: Long = 10000L,  // 10초 기본 스캔 시간
        val maxDevices: Int = 100,
        val rssiThreshold: Int = -80,     // RSSI 임계값 (dBm)
        val duplicateFilterTimeout: Long = 5000L // 중복 필터 타임아웃
    )
    
    /**
     * 스캔 필터 설정
     */
    data class ScanFilterConfig(
        val deviceName: String? = null,
        val deviceAddress: String? = null,
        val serviceUuid: String? = null,
        val serviceData: ByteArray? = null,
        val serviceDataMask: ByteArray? = null,
        val manufacturerData: ByteArray? = null,
        val manufacturerDataMask: ByteArray? = null,
        val manufacturerId: Int? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ScanFilterConfig
            return deviceName == other.deviceName &&
                    deviceAddress == other.deviceAddress &&
                    serviceUuid == other.serviceUuid &&
                    serviceData.contentEquals(other.serviceData) &&
                    serviceDataMask.contentEquals(other.serviceDataMask) &&
                    manufacturerData.contentEquals(other.manufacturerData) &&
                    manufacturerDataMask.contentEquals(other.manufacturerDataMask) &&
                    manufacturerId == other.manufacturerId
        }
        
        override fun hashCode(): Int {
            var result = deviceName?.hashCode() ?: 0
            result = 31 * result + (deviceAddress?.hashCode() ?: 0)
            result = 31 * result + (serviceUuid?.hashCode() ?: 0)
            result = 31 * result + (serviceData?.contentHashCode() ?: 0)
            result = 31 * result + (serviceDataMask?.contentHashCode() ?: 0)
            result = 31 * result + (manufacturerData?.contentHashCode() ?: 0)
            result = 31 * result + (manufacturerDataMask?.contentHashCode() ?: 0)
            result = 31 * result + (manufacturerId ?: 0)
            return result
        }
    }
    
    /**
     * 스캔 결과 리스너
     */
    interface ScanResultListener {
        fun onDeviceFound(device: BleDevice)
        fun onDeviceUpdated(device: BleDevice)
        fun onScanStateChanged(state: ScanState)
        fun onScanCompleted(devices: List<BleDevice>)
        fun onScanError(error: BleServiceError)
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    
    // 상태 관리
    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    
    private val _discoveredDevices = MutableStateFlow<Map<String, BleDevice>>(emptyMap())
    val discoveredDevices: StateFlow<Map<String, BleDevice>> = _discoveredDevices.asStateFlow()
    
    private val discoveredDevicesInternal = ConcurrentHashMap<String, BleDevice>()
    private val listeners = CopyOnWriteArrayList<ScanResultListener>()
    
    private var currentScanCallback: ScanCallback? = null
    private var currentScanConfig: ScanConfig? = null
    private val scanTimeoutHandler = Handler(Looper.getMainLooper())
    private var scanTimeoutRunnable: Runnable? = null
    
    // 리소스 ID
    private var scanCallbackResourceId: String? = null
    
    init {
        // 스캐너 초기화
        initializeScanner()
        
        BleDebugLogger.logSystemState("BleScanController initialized")
    }
    
    /**
     * 스캐너 초기화
     */
    private fun initializeScanner() {
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            BleDebugLogger.logError(
                BleServiceError.HardwareError.BluetoothNotAvailable("BLE scanner not available")
            )
        }
    }
    
    /**
     * 스캔 결과 리스너 등록
     */
    fun addScanResultListener(listener: ScanResultListener) {
        listeners.addIfAbsent(listener)
    }
    
    /**
     * 스캔 결과 리스너 해제
     */
    fun removeScanResultListener(listener: ScanResultListener) {
        listeners.remove(listener)
    }
    
    /**
     * 모든 리스너 해제
     */
    fun clearScanResultListeners() {
        listeners.clear()
    }
    
    /**
     * BLE 기기 스캔 시작
     */
    fun startScan(config: ScanConfig = ScanConfig()): Result<Unit> {
        return try {
            BleDebugLogger.logScanStart(config.filters.size, config.toString())
            
            // 전제 조건 검사
            val permissionResult = checkPermissions("SCAN")
            if (permissionResult is Result.Failure) {
                return permissionResult
            }
            
            val systemResult = checkSystemState()
            if (systemResult is Result.Failure) {
                return systemResult
            }
            
            // 이미 스캔 중이면 중지 후 새로 시작
            if (_scanState.value == ScanState.SCANNING) {
                val stopResult = stopScan()
                if (stopResult is Result.Failure) {
                    return stopResult
                }
            }
            
            // 스캐너 유효성 검사
            val scanner = bluetoothLeScanner
            if (scanner == null) {
                val error = BleServiceError.HardwareError.BluetoothNotAvailable("BLE scanner not available")
                BleDebugLogger.logError(error)
                return Result.Failure(error)
            }
            
            // 스캔 설정 및 필터 생성
            val scanSettings = buildScanSettings(config)
            val scanFilters = buildScanFilters(config.filters)
            
            // 스캔 콜백 생성
            val scanCallback = createScanCallback(config)
            
            // 스캔 시작
            scanner.startScan(scanFilters, scanSettings, scanCallback)
            
            // 상태 및 설정 저장
            currentScanCallback = scanCallback
            currentScanConfig = config
            _scanState.value = ScanState.SCANNING
            
            // 리소스 등록
            scanCallbackResourceId = resourceManager.registerResource(
                ScanCallbackResource(scanCallback),
                BleResourceManager.ResourceType.SCAN_CALLBACK
            )
            
            // 스캔 타임아웃 설정
            if (config.scanDuration > 0) {
                scheduleStopScan(config.scanDuration)
            }
            
            // 리스너들에게 상태 변경 알림
            notifyStateChanged(ScanState.SCANNING)
            
            Result.Success(Unit)
            
        } catch (e: Exception) {
            val error = BleServiceError.ScanError.ScanStartFailed("Failed to start scan", e)
            BleDebugLogger.logException(e, "Start scan failed")
            _scanState.value = ScanState.ERROR
            notifyError(error)
            Result.Failure(error)
        }
    }
    
    /**
     * BLE 기기 스캔 중지
     */
    fun stopScan(): Result<Unit> {
        return try {
            if (_scanState.value == ScanState.IDLE) {
                return Result.Success(Unit)
            }
            
            _scanState.value = ScanState.STOPPING
            
            // 스캔 타임아웃 취소
            cancelStopScan()
            
            // 스캔 중지
            val scanner = bluetoothLeScanner
            val callback = currentScanCallback
            
            if (scanner != null && callback != null) {
                scanner.stopScan(callback)
            }
            
            // 리소스 해제
            scanCallbackResourceId?.let { resourceId ->
                resourceManager.disposeResource(resourceId)
                scanCallbackResourceId = null
            }
            
            // 상태 정리
            currentScanCallback = null
            _scanState.value = ScanState.IDLE
            
            BleDebugLogger.logScanStop()
            
            // 리스너들에게 스캔 완료 알림
            notifyStateChanged(ScanState.IDLE)
            notifyScanCompleted()
            
            Result.Success(Unit)
            
        } catch (e: Exception) {
            val error = BleServiceError.ScanError.ScanStopFailed("Failed to stop scan", e)
            BleDebugLogger.logException(e, "Stop scan failed")
            notifyError(error)
            Result.Failure(error)
        }
    }
    
    /**
     * 발견된 기기 초기화
     */
    fun clearDiscoveredDevices() {
        discoveredDevicesInternal.clear()
        _discoveredDevices.value = emptyMap()
    }
    
    /**
     * 특정 기기 조회
     */
    fun getDevice(address: String): BleDevice? {
        return discoveredDevicesInternal[address]
    }
    
    /**
     * 발견된 기기 목록 반환
     */
    fun getDiscoveredDevices(): List<BleDevice> {
        return discoveredDevicesInternal.values.toList()
    }
    
    /**
     * 신호 강도별로 정렬된 기기 목록 반환
     */
    fun getDevicesSortedByRssi(): List<BleDevice> {
        return discoveredDevicesInternal.values.sortedByDescending { it.rssi }
    }
    
    /**
     * 연결 가능한 기기 목록 반환
     */
    fun getConnectableDevices(): List<BleDevice> {
        return discoveredDevicesInternal.values.filter { it.canConnect() }
    }
    
    /**
     * 스캔 설정 생성
     */
    private fun buildScanSettings(config: ScanConfig): ScanSettings {
        val builder = ScanSettings.Builder()
            .setScanMode(config.scanMode)
            .setCallbackType(config.callbackType)
            .setMatchMode(config.matchMode)
            .setNumOfMatches(config.numOfMatches)
            .setReportDelay(config.reportDelay)
            .setLegacy(config.legacyScanOnly)
        
        // PHY 설정 (MinSdk 28이므로 항상 사용 가능)
        var phyMask = 0
        if (config.phy1MEnabled) {
            phyMask = phyMask or ScanSettings.PHY_LE_1M
        }
        if (config.phyCodedEnabled) {
            phyMask = phyMask or ScanSettings.PHY_LE_CODED
        }
        if (phyMask != 0) {
            builder.setPhy(phyMask)
        }
        
        return builder.build()
    }
    
    /**
     * 스캔 필터 생성
     */
    private fun buildScanFilters(filterConfigs: List<ScanFilterConfig>): List<ScanFilter> {
        return filterConfigs.map { config ->
            val builder = ScanFilter.Builder()
            
            config.deviceName?.let { builder.setDeviceName(it) }
            config.deviceAddress?.let { builder.setDeviceAddress(it) }
            config.serviceUuid?.let { 
                builder.setServiceUuid(ParcelUuid.fromString(it))
            }
            config.serviceData?.let { data ->
                config.serviceUuid?.let { uuid ->
                    builder.setServiceData(
                        ParcelUuid.fromString(uuid),
                        data,
                        config.serviceDataMask
                    )
                }
            }
            config.manufacturerData?.let { data ->
                config.manufacturerId?.let { id ->
                    builder.setManufacturerData(id, data, config.manufacturerDataMask)
                }
            }
            
            builder.build()
        }
    }
    
    /**
     * 스캔 콜백 생성
     */
    private fun createScanCallback(config: ScanConfig): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result, config)
            }
            
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    handleScanResult(result, config)
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                val reason = getScanFailureReason(errorCode)
                val error = BleServiceError.ScanError.ScanFailed("Scan failed: $reason", errorCode)
                BleDebugLogger.logScanFailed(errorCode, reason)
                
                _scanState.value = ScanState.ERROR
                notifyError(error)
            }
        }
    }
    
    /**
     * 스캔 결과 처리
     */
    private fun handleScanResult(result: ScanResult, config: ScanConfig) {
        try {
            val device = result.device
            val rssi = result.rssi
            
            // RSSI 임계값 체크
            if (rssi < config.rssiThreshold) {
                return
            }
            
            // 최대 기기 수 체크
            if (discoveredDevicesInternal.size >= config.maxDevices && 
                !discoveredDevicesInternal.containsKey(device.address)) {
                return
            }
            
            val bleDevice = BleDevice.fromScanResult(result)
            val existingDevice = discoveredDevicesInternal[device.address]
            
            if (existingDevice == null) {
                // 새로운 기기 발견
                discoveredDevicesInternal[device.address] = bleDevice
                updateDevicesStateFlow()
                
                BleDebugLogger.logScanResult(bleDevice)
                notifyDeviceFound(bleDevice)
                
            } else {
                // 기존 기기 업데이트 (RSSI 등)
                val shouldUpdate = shouldUpdateDevice(existingDevice, bleDevice, config)
                if (shouldUpdate) {
                    discoveredDevicesInternal[device.address] = bleDevice
                    updateDevicesStateFlow()
                    notifyDeviceUpdated(bleDevice)
                }
            }
            
        } catch (e: Exception) {
            BleDebugLogger.logException(e, "Handle scan result failed", result.device?.address)
        }
    }
    
    /**
     * 기기 업데이트 여부 결정
     */
    private fun shouldUpdateDevice(existing: BleDevice, new: BleDevice, config: ScanConfig): Boolean {
        val timeDiff = new.lastSeenTime - existing.lastSeenTime
        
        return when {
            // 중복 필터 타임아웃 내에서는 RSSI가 크게 개선된 경우만 업데이트
            timeDiff < config.duplicateFilterTimeout -> {
                (new.rssi - existing.rssi) >= 5 // 5dBm 이상 개선
            }
            // 타임아웃 이후에는 항상 업데이트
            else -> true
        }
    }
    
    /**
     * 기기 상태 플로우 업데이트
     */
    private fun updateDevicesStateFlow() {
        _discoveredDevices.value = discoveredDevicesInternal.toMap()
    }
    
    /**
     * 스캔 타임아웃 예약
     */
    private fun scheduleStopScan(duration: Long) {
        scanTimeoutRunnable = Runnable {
            stopScan()
        }
        scanTimeoutHandler.postDelayed(scanTimeoutRunnable!!, duration)
    }
    
    /**
     * 스캔 타임아웃 취소
     */
    private fun cancelStopScan() {
        scanTimeoutRunnable?.let { runnable ->
            scanTimeoutHandler.removeCallbacks(runnable)
            scanTimeoutRunnable = null
        }
    }
    
    /**
     * 리스너들에게 상태 변경 알림
     */
    private fun notifyStateChanged(state: ScanState) {
        listeners.forEach { listener ->
            try {
                listener.onScanStateChanged(state)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify state changed failed")
            }
        }
    }
    
    /**
     * 리스너들에게 새 기기 발견 알림
     */
    private fun notifyDeviceFound(device: BleDevice) {
        listeners.forEach { listener ->
            try {
                listener.onDeviceFound(device)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify device found failed", device.address)
            }
        }
    }
    
    /**
     * 리스너들에게 기기 업데이트 알림
     */
    private fun notifyDeviceUpdated(device: BleDevice) {
        listeners.forEach { listener ->
            try {
                listener.onDeviceUpdated(device)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify device updated failed", device.address)
            }
        }
    }
    
    /**
     * 리스너들에게 스캔 완료 알림
     */
    private fun notifyScanCompleted() {
        val devices = getDiscoveredDevices()
        listeners.forEach { listener ->
            try {
                listener.onScanCompleted(devices)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify scan completed failed")
            }
        }
    }
    
    /**
     * 리스너들에게 오류 알림
     */
    private fun notifyError(error: BleServiceError) {
        listeners.forEach { listener ->
            try {
                listener.onScanError(error)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify error failed")
            }
        }
    }
    
    /**
     * 스캔 실패 이유 반환
     */
    private fun getScanFailureReason(errorCode: Int): String = when (errorCode) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Application registration failed"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
        ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Out of hardware resources"
        ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "Scanning too frequently"
        else -> "Unknown error ($errorCode)"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 스캔 중지
        stopScan()
        
        // 리스너 정리
        clearScanResultListeners()
        
        // 발견된 기기 정리
        clearDiscoveredDevices()
        
        BleDebugLogger.logSystemState("BleScanController destroyed")
    }
    
    /**
     * 스캔 콜백 리소스 래퍼
     */
    private class ScanCallbackResource(private val callback: ScanCallback) : BleResource {
        override fun cleanup() {
            // 스캔 콜백은 별도 정리 작업이 필요없음
        }
    }
}