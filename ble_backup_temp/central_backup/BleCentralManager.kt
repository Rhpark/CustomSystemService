package kr.open.library.systemmanager.controller.bluetooth.central

import android.content.Context
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.base.BleLifecycleManager
import kr.open.library.systemmanager.controller.bluetooth.data.BleConnectionState
import kr.open.library.systemmanager.controller.bluetooth.data.BleDevice
import kr.open.library.systemmanager.controller.bluetooth.debug.BleDebugLogger
import kr.open.library.systemmanager.controller.bluetooth.error.BleServiceError
import kr.open.library.systemmanager.controller.bluetooth.base.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.CopyOnWriteArrayList

/**
 * BLE Central 통합 관리자
 * BLE Central Integration Manager
 * 
 * 스캔과 연결 기능을 통합하여 관리하고, 상위 레벨 API를 제공합니다.
 * Integrates scanning and connection functionalities and provides high-level APIs.
 * 
 * 주요 기능:
 * Key features:
 * - 스캔과 연결의 통합된 워크플로우
 * - 자동 연결 및 재연결 관리
 * - 통합된 상태 관리 및 이벤트 처리
 * - 간편한 고수준 API 제공
 */
class BleCentralManager private constructor(private val context: Context) {
    
    private val TAG = "BleCentralManager"
    
    /**
     * Central 관리자 상태
     */
    enum class CentralState {
        IDLE,           // 대기
        SCANNING,       // 스캔 중
        CONNECTING,     // 연결 중
        CONNECTED,      // 연결됨
        ERROR           // 오류 상태
    }
    
    /**
     * 통합 이벤트 리스너
     */
    interface CentralEventListener {
        fun onStateChanged(state: CentralState)
        fun onDeviceFound(device: BleDevice) {}
        fun onDeviceConnected(device: BleDevice) {}
        fun onDeviceDisconnected(device: BleDevice) {}
        fun onDataReceived(device: BleDevice, serviceUuid: String, characteristicUuid: String, data: ByteArray) {}
        fun onError(error: BleServiceError) {}
    }
    
    /**
     * 자동 연결 설정
     */
    data class AutoConnectConfig(
        val enabled: Boolean = false,
        val deviceFilter: (BleDevice) -> Boolean = { true },
        val connectionConfig: BleConnectionController.ConnectionConfig = BleConnectionController.ConnectionConfig(),
        val maxConnections: Int = 1,
        val rssiThreshold: Int = -70
    )
    
    // 컨트롤러들
    private val scanController = BleScanController(context)
    private val connectionController = BleConnectionController(context)
    private val lifecycleManager = BleLifecycleManager.getInstance(context)
    
    // 상태 관리
    private val _centralState = MutableStateFlow(CentralState.IDLE)
    val centralState: StateFlow<CentralState> = _centralState.asStateFlow()
    
    private val _discoveredDevices = MutableStateFlow<Map<String, BleDevice>>(emptyMap())
    val discoveredDevices: StateFlow<Map<String, BleDevice>> = _discoveredDevices.asStateFlow()
    
    private val listeners = CopyOnWriteArrayList<CentralEventListener>()
    private var autoConnectConfig: AutoConnectConfig? = null
    private var isInitialized = false
    
    init {
        setupControllerListeners()
        setupLifecycleIntegration()
        
        BleDebugLogger.logSystemState("BleCentralManager created")
    }
    
    /**
     * 초기화
     */
    fun initialize(): Result<Unit> {
        return try {
            if (isInitialized) {
                return Result.Success(Unit)
            }
            
            // 생명주기 관리자 초기화
            lifecycleManager.initialize()
            
            // 컨트롤러들 등록
            lifecycleManager.registerController(scanController, 10) // 높은 우선순위
            lifecycleManager.registerController(connectionController, 20)
            
            isInitialized = true
            BleDebugLogger.logSystemState("BleCentralManager initialized")
            
            Result.Success(Unit)
            
        } catch (e: Exception) {
            val error = BleServiceError.SystemState.InitializationFailed("Failed to initialize BleCentralManager", e)
            BleDebugLogger.logException(e, "BleCentralManager initialization failed")
            Result.Failure(error)
        }
    }
    
    /**
     * 이벤트 리스너 등록
     */
    fun addEventListener(listener: CentralEventListener) {
        listeners.addIfAbsent(listener)
    }
    
    /**
     * 이벤트 리스너 해제
     */
    fun removeEventListener(listener: CentralEventListener) {
        listeners.remove(listener)
    }
    
    /**
     * 기기 스캔 시작
     */
    fun startScan(config: BleScanController.ScanConfig = BleScanController.ScanConfig()): Result<Unit> {
        BleDebugLogger.logScanStart(config.filters.size, "BleCentralManager scan")
        
        val result = scanController.startScan(config)
        if (result is Result.Success) {
            _centralState.value = CentralState.SCANNING
            notifyStateChanged(CentralState.SCANNING)
        }
        
        return result
    }
    
    /**
     * 스캔 중지
     */
    fun stopScan(): Result<Unit> {
        val result = scanController.stopScan()
        if (result is Result.Success) {
            updateCentralState()
        }
        return result
    }
    
    /**
     * 기기 연결
     */
    fun connectDevice(
        device: BleDevice, 
        config: BleConnectionController.ConnectionConfig = BleConnectionController.ConnectionConfig()
    ): Result<Unit> {
        val result = connectionController.connect(device, config)
        if (result is Result.Success) {
            _centralState.value = CentralState.CONNECTING
            notifyStateChanged(CentralState.CONNECTING)
        }
        return result
    }
    
    /**
     * 기기 연결 해제
     */
    fun disconnectDevice(deviceAddress: String): Result<Unit> {
        val result = connectionController.disconnect(deviceAddress)
        if (result is Result.Success) {
            updateCentralState()
        }
        return result
    }
    
    /**
     * 모든 기기 연결 해제
     */
    fun disconnectAllDevices(): Result<Unit> {
        val result = connectionController.disconnectAll()
        if (result is Result.Success) {
            updateCentralState()
        }
        return result
    }
    
    /**
     * 자동 연결 설정
     */
    fun enableAutoConnect(config: AutoConnectConfig) {
        autoConnectConfig = config
        BleDebugLogger.logSystemState("Auto-connect enabled")
    }
    
    /**
     * 자동 연결 해제
     */
    fun disableAutoConnect() {
        autoConnectConfig = null
        BleDebugLogger.logSystemState("Auto-connect disabled")
    }
    
    /**
     * 스캔 및 자동 연결 시작
     */
    fun startScanAndAutoConnect(
        scanConfig: BleScanController.ScanConfig = BleScanController.ScanConfig(),
        autoConnectConfig: AutoConnectConfig
    ): Result<Unit> {
        enableAutoConnect(autoConnectConfig)
        return startScan(scanConfig)
    }
    
    /**
     * 특성 읽기
     */
    fun readCharacteristic(
        deviceAddress: String,
        serviceUuid: String,
        characteristicUuid: String
    ): Result<Unit> {
        return connectionController.readCharacteristic(deviceAddress, serviceUuid, characteristicUuid)
    }
    
    /**
     * 특성 쓰기
     */
    fun writeCharacteristic(
        deviceAddress: String,
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray,
        writeType: Int = android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ): Result<Unit> {
        return connectionController.writeCharacteristic(deviceAddress, serviceUuid, characteristicUuid, data, writeType)
    }
    
    /**
     * 알림 설정
     */
    fun setCharacteristicNotification(
        deviceAddress: String,
        serviceUuid: String,
        characteristicUuid: String,
        enabled: Boolean
    ): Result<Unit> {
        return connectionController.setCharacteristicNotification(deviceAddress, serviceUuid, characteristicUuid, enabled)
    }
    
    /**
     * 연결된 기기 목록 반환
     */
    fun getConnectedDevices(): List<BleDevice> {
        return connectionController.getConnectedDevices()
    }
    
    /**
     * 발견된 기기 목록 반환
     */
    fun getDiscoveredDevices(): List<BleDevice> {
        return scanController.getDiscoveredDevices()
    }
    
    /**
     * 특정 기기의 연결 상태 반환
     */
    fun getConnectionState(deviceAddress: String): BleConnectionState {
        return connectionController.getConnectionState(deviceAddress)
    }
    
    /**
     * 기기가 연결되어 있는지 확인
     */
    fun isConnected(deviceAddress: String): Boolean {
        return connectionController.isConnected(deviceAddress)
    }
    
    /**
     * 현재 스캔 상태 확인
     */
    fun isScanning(): Boolean {
        return scanController.scanState.value == BleScanController.ScanState.SCANNING
    }
    
    /**
     * 통계 정보 반환
     */
    fun getStatusInfo(): String {
        return buildString {
            appendLine("=== BLE Central Manager Status ===")
            appendLine("Initialized: $isInitialized")
            appendLine("Current State: ${_centralState.value}")
            appendLine("Scanning: ${isScanning()}")
            appendLine("Auto Connect: ${autoConnectConfig != null}")
            appendLine("Discovered Devices: ${scanController.getDiscoveredDevices().size}")
            appendLine("Connected Devices: ${connectionController.getConnectedDevices().size}")
            appendLine("Event Listeners: ${listeners.size}")
        }
    }
    
    /**
     * 컨트롤러 리스너 설정
     */
    private fun setupControllerListeners() {
        // 스캔 리스너
        scanController.addScanResultListener(object : BleScanController.ScanResultListener {
            override fun onDeviceFound(device: BleDevice) {
                _discoveredDevices.value = scanController.discoveredDevices.value
                notifyDeviceFound(device)
                
                // 자동 연결 확인
                checkAutoConnect(device)
            }
            
            override fun onDeviceUpdated(device: BleDevice) {
                _discoveredDevices.value = scanController.discoveredDevices.value
                
                // 자동 연결 확인 (RSSI 개선된 경우)
                checkAutoConnect(device)
            }
            
            override fun onScanStateChanged(state: BleScanController.ScanState) {
                updateCentralState()
            }
            
            override fun onScanCompleted(devices: List<BleDevice>) {
                updateCentralState()
            }
            
            override fun onScanError(error: BleServiceError) {
                _centralState.value = CentralState.ERROR
                notifyStateChanged(CentralState.ERROR)
                notifyError(error)
            }
        })
        
        // 연결 리스너
        connectionController.addConnectionListener(object : BleConnectionController.ConnectionListener {
            override fun onConnectionStateChanged(device: BleDevice, state: BleConnectionState) {
                when (state) {
                    BleConnectionState.CONNECTED -> {
                        notifyDeviceConnected(device)
                    }
                    BleConnectionState.DISCONNECTED -> {
                        notifyDeviceDisconnected(device)
                    }
                    else -> {}
                }
                updateCentralState()
            }
            
            override fun onServicesDiscovered(device: BleDevice, services: List<android.bluetooth.BluetoothGattService>) {
                // 서비스 탐색 완료시 추가 작업 가능
            }
            
            override fun onCharacteristicRead(device: BleDevice, characteristic: android.bluetooth.BluetoothGattCharacteristic, data: ByteArray) {
                notifyDataReceived(device, characteristic.service.uuid.toString(), characteristic.uuid.toString(), data)
            }
            
            override fun onCharacteristicWrite(device: BleDevice, characteristic: android.bluetooth.BluetoothGattCharacteristic, success: Boolean) {
                // 쓰기 완료시 추가 작업 가능
            }
            
            override fun onCharacteristicChanged(device: BleDevice, characteristic: android.bluetooth.BluetoothGattCharacteristic, data: ByteArray) {
                notifyDataReceived(device, characteristic.service.uuid.toString(), characteristic.uuid.toString(), data)
            }
            
            override fun onMtuChanged(device: BleDevice, mtu: Int, status: Int) {
                // MTU 변경시 추가 작업 가능
            }
            
            override fun onConnectionError(device: BleDevice, error: BleServiceError) {
                _centralState.value = CentralState.ERROR
                notifyStateChanged(CentralState.ERROR)
                notifyError(error)
            }
        })
    }
    
    /**
     * 생명주기 통합 설정
     */
    private fun setupLifecycleIntegration() {
        lifecycleManager.addLifecycleListener(object : BleLifecycleManager.BleLifecycleListener {
            override fun onBleLifecycleEvent(event: BleLifecycleManager.BleLifecycleEvent, data: Any?) {
                when (event) {
                    BleLifecycleManager.BleLifecycleEvent.APP_BACKGROUND -> {
                        // 백그라운드 진입시 스캔 중지 (배터리 절약)
                        if (isScanning()) {
                            stopScan()
                        }
                    }
                    BleLifecycleManager.BleLifecycleEvent.LOW_MEMORY -> {
                        // 메모리 부족시 불필요한 연결 해제
                        handleLowMemory()
                    }
                    BleLifecycleManager.BleLifecycleEvent.SYSTEM_SHUTDOWN -> {
                        // 시스템 종료시 정리
                        cleanup()
                    }
                    else -> {}
                }
            }
        })
    }
    
    /**
     * Central 상태 업데이트
     */
    private fun updateCentralState() {
        val newState = when {
            isScanning() -> CentralState.SCANNING
            connectionController.getConnectedDevices().isNotEmpty() -> CentralState.CONNECTED
            connectionController.connectionStates.value.values.any { it == BleConnectionState.CONNECTING } -> CentralState.CONNECTING
            else -> CentralState.IDLE
        }
        
        if (_centralState.value != newState) {
            _centralState.value = newState
            notifyStateChanged(newState)
        }
    }
    
    /**
     * 자동 연결 확인
     */
    private fun checkAutoConnect(device: BleDevice) {
        val config = autoConnectConfig ?: return
        
        if (!config.enabled) return
        
        // 이미 최대 연결 수에 도달했으면 스킵
        if (connectionController.getConnectedDevices().size >= config.maxConnections) return
        
        // 이미 연결 중이거나 연결되어 있으면 스킵
        val connectionState = connectionController.getConnectionState(device.address)
        if (connectionState != BleConnectionState.DISCONNECTED) return
        
        // 필터 조건 확인
        if (!config.deviceFilter(device)) return
        
        // RSSI 임계값 확인
        if (device.rssi < config.rssiThreshold) return
        
        // 자동 연결 시도
        BleDebugLogger.logConnectionAttempt(device.address, device.name, true)
        connectDevice(device, config.connectionConfig)
    }
    
    /**
     * 메모리 부족 상황 처리
     */
    private fun handleLowMemory() {
        try {
            // 스캔 중지
            if (isScanning()) {
                stopScan()
            }
            
            // 낮은 우선순위 연결 해제 (신호가 약한 기기부터)
            val connectedDevices = connectionController.getConnectedDevices()
            if (connectedDevices.size > 1) {
                val weakestDevice = connectedDevices.minByOrNull { it.rssi }
                weakestDevice?.let { device ->
                    disconnectDevice(device.address)
                    BleDebugLogger.logDisconnection(device.address, 0, "Low memory - disconnected weak signal device")
                }
            }
            
        } catch (e: Exception) {
            BleDebugLogger.logException(e, "Handle low memory failed")
        }
    }
    
    /**
     * 정리 작업
     */
    fun cleanup() {
        if (!isInitialized) return
        
        try {
            // 스캔 중지
            stopScan()
            
            // 모든 연결 해제
            disconnectAllDevices()
            
            // 자동 연결 해제
            disableAutoConnect()
            
            // 리스너 정리
            listeners.clear()
            scanController.clearScanResultListeners()
            
            // 생명주기 관리자에서 해제
            lifecycleManager.unregisterController(scanController)
            lifecycleManager.unregisterController(connectionController)
            
            // 컨트롤러 정리
            scanController.onDestroy()
            connectionController.onDestroy()
            
            isInitialized = false
            _centralState.value = CentralState.IDLE
            
            BleDebugLogger.logSystemState("BleCentralManager cleaned up")
            
        } catch (e: Exception) {
            BleDebugLogger.logException(e, "BleCentralManager cleanup failed")
        }
    }
    
    /**
     * 리스너 알림 메서드들
     */
    private fun notifyStateChanged(state: CentralState) {
        listeners.forEach { listener ->
            try {
                listener.onStateChanged(state)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify state changed failed")
            }
        }
    }
    
    private fun notifyDeviceFound(device: BleDevice) {
        listeners.forEach { listener ->
            try {
                listener.onDeviceFound(device)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify device found failed", device.address)
            }
        }
    }
    
    private fun notifyDeviceConnected(device: BleDevice) {
        listeners.forEach { listener ->
            try {
                listener.onDeviceConnected(device)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify device connected failed", device.address)
            }
        }
    }
    
    private fun notifyDeviceDisconnected(device: BleDevice) {
        listeners.forEach { listener ->
            try {
                listener.onDeviceDisconnected(device)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify device disconnected failed", device.address)
            }
        }
    }
    
    private fun notifyDataReceived(device: BleDevice, serviceUuid: String, characteristicUuid: String, data: ByteArray) {
        listeners.forEach { listener ->
            try {
                listener.onDataReceived(device, serviceUuid, characteristicUuid, data)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify data received failed", device.address)
            }
        }
    }
    
    private fun notifyError(error: BleServiceError) {
        listeners.forEach { listener ->
            try {
                listener.onError(error)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify error failed")
            }
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: BleCentralManager? = null
        
        fun getInstance(context: Context): BleCentralManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BleCentralManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        fun isInitialized(): Boolean = INSTANCE?.isInitialized == true
    }
}