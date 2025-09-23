package kr.open.library.systemmanager.controller.bluetooth.peripheral

import android.content.Context
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.base.BleLifecycleManager
import kr.open.library.systemmanager.controller.bluetooth.debug.BleDebugLogger
import kr.open.library.systemmanager.controller.bluetooth.error.BleServiceError
import kr.open.library.systemmanager.controller.bluetooth.base.Result
import kr.open.library.systemmanager.controller.bluetooth.peripheral.BleAdvertisingController.AdvertisingConfig
import kr.open.library.systemmanager.controller.bluetooth.peripheral.BleGattServerController.ConnectedClient
import kr.open.library.systemmanager.controller.bluetooth.peripheral.BleGattServerController.ServiceBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * BLE Peripheral 통합 관리자
 * BLE Peripheral Integration Manager
 * 
 * 광고와 GATT 서버 기능을 통합하여 관리하고, 상위 레벨 API를 제공합니다.
 * Integrates advertising and GATT server functionalities and provides high-level APIs.
 * 
 * 주요 기능:
 * Key features:
 * - 광고와 GATT 서버의 통합된 워크플로우
 * - 클라이언트 연결 및 서비스 관리
 * - 통합된 상태 관리 및 이벤트 처리
 * - 간편한 고수준 API 제공
 * - 자동 생명주기 관리
 */
class BlePeripheralManager private constructor(private val context: Context) {
    
    private val TAG = "BlePeripheralManager"
    
    /**
     * Peripheral 관리자 상태
     */
    enum class PeripheralState {
        IDLE,           // 대기
        ADVERTISING,    // 광고만 실행 중
        SERVING,        // 서버만 실행 중  
        ACTIVE,         // 광고 + 서버 모두 실행 중
        ERROR           // 오류 상태
    }
    
    /**
     * 통합 이벤트 리스너
     */
    interface PeripheralEventListener {
        fun onStateChanged(state: PeripheralState) {}
        fun onAdvertisingStarted() {}
        fun onAdvertisingFailed(errorCode: Int, reason: String) {}
        fun onAdvertisingStopped() {}
        fun onServerStarted() {}
        fun onServerStopped() {}
        fun onServerError(error: BleServiceError) {}
        fun onClientConnected(client: ConnectedClient) {}
        fun onClientDisconnected(client: ConnectedClient) {}
        fun onDataReceived(client: ConnectedClient, serviceUuid: UUID, characteristicUuid: UUID, data: ByteArray) {}
        fun onDataRequest(client: ConnectedClient, serviceUuid: UUID, characteristicUuid: UUID): ByteArray? { return null }
    }
    
    /**
     * 서비스 설정
     */
    data class ServiceConfig(
        val serviceBuilder: ServiceBuilder,
        val autoStart: Boolean = true
    )
    
    /**
     * Peripheral 설정
     */
    data class PeripheralConfig(
        val advertisingConfig: AdvertisingConfig = AdvertisingConfig(),
        val services: List<ServiceConfig> = emptyList(),
        val autoStartServer: Boolean = true,
        val autoRestartOnError: Boolean = true,
        val maxClients: Int = 5
    )
    
    // 컨트롤러들
    private val advertisingController = BleAdvertisingController(context)
    private val gattServerController = BleGattServerController(context)
    private val lifecycleManager = BleLifecycleManager.getInstance(context)
    
    // 상태 관리
    private val _peripheralState = MutableStateFlow(PeripheralState.IDLE)
    val peripheralState: StateFlow<PeripheralState> = _peripheralState.asStateFlow()
    
    private val _connectedClients = MutableStateFlow<Map<String, ConnectedClient>>(emptyMap())
    val connectedClients: StateFlow<Map<String, ConnectedClient>> = _connectedClients.asStateFlow()
    
    private val listeners = CopyOnWriteArrayList<PeripheralEventListener>()
    private var currentConfig: PeripheralConfig? = null
    private var isInitialized = false
    
    init {
        setupControllerListeners()
        setupLifecycleIntegration()
        
        BleDebugLogger.logSystemState("BlePeripheralManager created")
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
            lifecycleManager.registerController(advertisingController, 30) // 낮은 우선순위
            lifecycleManager.registerController(gattServerController, 25) // 높은 우선순위
            
            isInitialized = true
            BleDebugLogger.logSystemState("BlePeripheralManager initialized")
            
            Result.Success(Unit)
            
        } catch (e: Exception) {
            val error = BleServiceError.SystemState.InitializationFailed("Failed to initialize BlePeripheralManager", e)
            BleDebugLogger.logException(e, "BlePeripheralManager initialization failed")
            Result.Failure(error)
        }
    }
    
    /**
     * 이벤트 리스너 등록
     */
    fun addEventListener(listener: PeripheralEventListener) {
        listeners.addIfAbsent(listener)
    }
    
    /**
     * 이벤트 리스너 해제
     */
    fun removeEventListener(listener: PeripheralEventListener) {
        listeners.remove(listener)
    }
    
    /**
     * Peripheral 시작 (광고 + GATT 서버)
     */
    fun startPeripheral(config: PeripheralConfig): Result<Unit> {
        BleDebugLogger.logSystemState("Starting BLE Peripheral")
        
        currentConfig = config
        
        // GATT 서버 시작
        if (config.autoStartServer) {
            val serverResult = gattServerController.startServer()
            if (serverResult is Result.Failure) {
                return serverResult
            }
            
            // 서비스 추가
            config.services.forEach { serviceConfig ->
                if (serviceConfig.autoStart) {
                    val serviceResult = gattServerController.addService(serviceConfig.serviceBuilder)
                    if (serviceResult is Result.Failure) {
                        BleDebugLogger.logError(serviceResult.error)
                        // 서비스 추가 실패해도 계속 진행
                    }
                }
            }
        }
        
        // 광고 시작
        val advertisingResult = advertisingController.startAdvertising(config.advertisingConfig)
        if (advertisingResult is Result.Failure) {
            // 광고 실패시 서버 중지
            if (config.autoStartServer) {
                gattServerController.stopServer()
            }
            return advertisingResult
        }
        
        updatePeripheralState()
        return Result.Success(Unit)
    }
    
    /**
     * Peripheral 중지
     */
    fun stopPeripheral(): Result<Unit> {
        BleDebugLogger.logSystemState("Stopping BLE Peripheral")
        
        var lastError: BleServiceError? = null
        
        // 광고 중지
        val advertisingResult = advertisingController.stopAdvertising()
        if (advertisingResult is Result.Failure) {
            lastError = advertisingResult.error
        }
        
        // GATT 서버 중지
        val serverResult = gattServerController.stopServer()
        if (serverResult is Result.Failure) {
            lastError = serverResult.error
        }
        
        currentConfig = null
        updatePeripheralState()
        
        return if (lastError != null) {
            Result.Failure(lastError)
        } else {
            Result.Success(Unit)
        }
    }
    
    /**
     * 광고만 시작
     */
    fun startAdvertising(config: AdvertisingConfig = AdvertisingConfig()): Result<Unit> {
        val result = advertisingController.startAdvertising(config)
        updatePeripheralState()
        return result
    }
    
    /**
     * 광고 중지
     */
    fun stopAdvertising(): Result<Unit> {
        val result = advertisingController.stopAdvertising()
        updatePeripheralState()
        return result
    }
    
    /**
     * GATT 서버만 시작
     */
    fun startGattServer(): Result<Unit> {
        val result = gattServerController.startServer()
        updatePeripheralState()
        return result
    }
    
    /**
     * GATT 서버 중지
     */
    fun stopGattServer(): Result<Unit> {
        val result = gattServerController.stopServer()
        updatePeripheralState()
        return result
    }
    
    /**
     * 서비스 추가
     */
    fun addService(serviceBuilder: ServiceBuilder): Result<Unit> {
        return gattServerController.addService(serviceBuilder)
    }
    
    /**
     * 서비스 제거
     */
    fun removeService(serviceUuid: UUID): Result<Unit> {
        return gattServerController.removeService(serviceUuid)
    }
    
    /**
     * 클라이언트에게 알림 전송
     */
    fun notifyClient(
        clientAddress: String,
        serviceUuid: UUID,
        characteristicUuid: UUID,
        data: ByteArray,
        confirm: Boolean = false
    ): Result<Unit> {
        return gattServerController.notifyCharacteristicChanged(clientAddress, serviceUuid, characteristicUuid, data, confirm)
    }
    
    /**
     * 모든 클라이언트에게 알림 전송
     */
    fun notifyAllClients(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        data: ByteArray,
        confirm: Boolean = false
    ): Result<Int> {
        return gattServerController.notifyAllClients(serviceUuid, characteristicUuid, data, confirm)
    }
    
    /**
     * 클라이언트 연결 해제
     */
    fun disconnectClient(clientAddress: String): Result<Unit> {
        return gattServerController.disconnectClient(clientAddress)
    }
    
    /**
     * 모든 클라이언트 연결 해제
     */
    fun disconnectAllClients(): Result<Unit> {
        return gattServerController.disconnectAllClients()
    }
    
    /**
     * 연결된 클라이언트 목록 반환
     */
    fun getConnectedClientsList(): List<ConnectedClient> {
        return gattServerController.getConnectedClientsList()
    }
    
    /**
     * 특정 클라이언트 정보 반환
     */
    fun getClientInfo(clientAddress: String): ConnectedClient? {
        return gattServerController.getClientInfo(clientAddress)
    }
    
    /**
     * 광고 중인지 확인
     */
    fun isAdvertising(): Boolean {
        return advertisingController.isAdvertising()
    }
    
    /**
     * GATT 서버 실행 중인지 확인
     */
    fun isGattServerRunning(): Boolean {
        return gattServerController.isServerRunning()
    }
    
    /**
     * Peripheral 활성 상태인지 확인
     */
    fun isActive(): Boolean {
        return _peripheralState.value == PeripheralState.ACTIVE
    }
    
    /**
     * 현재 설정 반환
     */
    fun getCurrentConfig(): PeripheralConfig? {
        return currentConfig
    }
    
    /**
     * 상태 정보 반환
     */
    fun getStatusInfo(): String {
        return buildString {
            appendLine("=== BLE Peripheral Manager Status ===")
            appendLine("Initialized: $isInitialized")
            appendLine("Current State: ${_peripheralState.value}")
            appendLine("Advertising: ${isAdvertising()}")
            appendLine("GATT Server: ${isGattServerRunning()}")
            appendLine("Connected Clients: ${getConnectedClientsList().size}")
            appendLine("Event Listeners: ${listeners.size}")
            
            currentConfig?.let { config ->
                appendLine("Max Clients: ${config.maxClients}")
                appendLine("Auto Restart: ${config.autoRestartOnError}")
                appendLine("Services: ${config.services.size}")
            }
        }
    }
    
    /**
     * 컨트롤러 리스너 설정
     */
    private fun setupControllerListeners() {
        // 광고 리스너
        advertisingController.addAdvertisingListener(object : BleAdvertisingController.AdvertisingListener {
            override fun onAdvertisingStarted() {
                updatePeripheralState()
                notifyAdvertisingStarted()
            }
            
            override fun onAdvertisingStartFailure(errorCode: Int, reason: String) {
                _peripheralState.value = PeripheralState.ERROR
                notifyStateChanged(PeripheralState.ERROR)
                notifyAdvertisingFailed(errorCode, reason)
                
                // 자동 재시작 시도
                currentConfig?.let { config ->
                    if (config.autoRestartOnError) {
                        scheduleRestart()
                    }
                }
            }
            
            override fun onAdvertisingStopped() {
                updatePeripheralState()
                notifyAdvertisingStopped()
            }
            
            override fun onAdvertisingStateChanged(state: BleAdvertisingController.AdvertisingState) {
                updatePeripheralState()
            }
        })
        
        // GATT 서버 리스너
        gattServerController.addGattServerListener(object : BleGattServerController.GattServerListener {
            override fun onServerStarted() {
                updatePeripheralState()
                notifyServerStarted()
            }
            
            override fun onServerStopped() {
                updatePeripheralState()
                notifyServerStopped()
            }
            
            override fun onServerError(error: BleServiceError) {
                _peripheralState.value = PeripheralState.ERROR
                notifyStateChanged(PeripheralState.ERROR)
                notifyServerError(error)
                
                // 자동 재시작 시도
                currentConfig?.let { config ->
                    if (config.autoRestartOnError) {
                        scheduleRestart()
                    }
                }
            }
            
            override fun onClientConnected(client: ConnectedClient) {
                updateConnectedClientsStateFlow()
                notifyClientConnected(client)
                
                // 최대 클라이언트 수 확인
                currentConfig?.let { config ->
                    val currentClientCount = getConnectedClientsList().size
                    if (currentClientCount > config.maxClients) {
                        // 가장 오래된 클라이언트 연결 해제
                        val oldestClient = getConnectedClientsList().minByOrNull { it.connectedTime }
                        oldestClient?.let { disconnectClient(it.device.address) }
                    }
                }
            }
            
            override fun onClientDisconnected(client: ConnectedClient) {
                updateConnectedClientsStateFlow()
                notifyClientDisconnected(client)
            }
            
            override fun onCharacteristicReadRequest(
                client: ConnectedClient,
                characteristic: android.bluetooth.BluetoothGattCharacteristic,
                requestId: Int,
                offset: Int
            ): ByteArray? {
                return listeners.firstNotNullOfOrNull { listener ->
                    listener.onDataRequest(client, characteristic.service.uuid, characteristic.uuid)
                }
            }
            
            override fun onCharacteristicWriteRequest(
                client: ConnectedClient,
                characteristic: android.bluetooth.BluetoothGattCharacteristic,
                value: ByteArray,
                requestId: Int,
                offset: Int,
                responseNeeded: Boolean
            ): Boolean {
                notifyDataReceived(client, characteristic.service.uuid, characteristic.uuid, value)
                return true
            }
            
            override fun onDescriptorReadRequest(
                client: ConnectedClient,
                descriptor: android.bluetooth.BluetoothGattDescriptor,
                requestId: Int,
                offset: Int
            ): ByteArray? {
                return descriptor.value
            }
            
            override fun onDescriptorWriteRequest(
                client: ConnectedClient,
                descriptor: android.bluetooth.BluetoothGattDescriptor,
                value: ByteArray,
                requestId: Int,
                offset: Int,
                responseNeeded: Boolean
            ): Boolean {
                return true
            }
            
            override fun onNotificationSent(
                client: ConnectedClient,
                characteristic: android.bluetooth.BluetoothGattCharacteristic,
                status: Int
            ) {
                // 알림 전송 완료 처리
            }
            
            override fun onMtuChanged(client: ConnectedClient, mtu: Int) {
                // MTU 변경 처리
            }
            
            override fun onPhyChanged(client: ConnectedClient, txPhy: Int, rxPhy: Int) {
                // PHY 변경 처리
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
                        // 백그라운드 진입시 광고 유지 (서버는 계속 실행)
                        // Android의 광고 제한을 고려해서 필요시 광고 중지
                    }
                    BleLifecycleManager.BleLifecycleEvent.LOW_MEMORY -> {
                        // 메모리 부족시 일부 클라이언트 연결 해제
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
     * Peripheral 상태 업데이트
     */
    private fun updatePeripheralState() {
        val newState = when {
            isAdvertising() && isGattServerRunning() -> PeripheralState.ACTIVE
            isAdvertising() -> PeripheralState.ADVERTISING
            isGattServerRunning() -> PeripheralState.SERVING
            else -> PeripheralState.IDLE
        }
        
        if (_peripheralState.value != newState) {
            _peripheralState.value = newState
            notifyStateChanged(newState)
        }
    }
    
    /**
     * 연결된 클라이언트 상태 플로우 업데이트
     */
    private fun updateConnectedClientsStateFlow() {
        val clientsMap = getConnectedClientsList().associateBy { it.device.address }
        _connectedClients.value = clientsMap
    }
    
    /**
     * 메모리 부족 상황 처리
     */
    private fun handleLowMemory() {
        try {
            val clients = getConnectedClientsList()
            if (clients.size > 2) {
                // 가장 오래된 클라이언트 연결 해제
                val oldestClient = clients.minByOrNull { it.connectedTime }
                oldestClient?.let { client ->
                    disconnectClient(client.device.address)
                    BleDebugLogger.logDisconnection(client.device.address, 0, "Low memory - disconnected oldest client")
                }
            }
        } catch (e: Exception) {
            BleDebugLogger.logException(e, "Handle low memory failed")
        }
    }
    
    /**
     * 자동 재시작 예약
     */
    private fun scheduleRestart() {
        // 간단한 재시작 로직 (실제 구현에서는 더 정교한 재시작 전략 필요)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            currentConfig?.let { config ->
                BleDebugLogger.logSystemState("Attempting to restart peripheral")
                startPeripheral(config)
            }
        }, 5000) // 5초 후 재시작
    }
    
    /**
     * 정리 작업
     */
    fun cleanup() {
        if (!isInitialized) return
        
        try {
            // Peripheral 중지
            stopPeripheral()
            
            // 리스너 정리
            listeners.clear()
            advertisingController.clearAdvertisingListeners()
            gattServerController.clearGattServerListeners()
            
            // 생명주기 관리자에서 해제
            lifecycleManager.unregisterController(advertisingController)
            lifecycleManager.unregisterController(gattServerController)
            
            // 컨트롤러 정리
            advertisingController.onDestroy()
            gattServerController.onDestroy()
            
            isInitialized = false
            _peripheralState.value = PeripheralState.IDLE
            
            BleDebugLogger.logSystemState("BlePeripheralManager cleaned up")
            
        } catch (e: Exception) {
            BleDebugLogger.logException(e, "BlePeripheralManager cleanup failed")
        }
    }
    
    /**
     * 리스너 알림 메서드들
     */
    private fun notifyStateChanged(state: PeripheralState) {
        listeners.forEach { listener ->
            try {
                listener.onStateChanged(state)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify state changed failed")
            }
        }
    }
    
    private fun notifyAdvertisingStarted() {
        listeners.forEach { listener ->
            try {
                listener.onAdvertisingStarted()
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify advertising started failed")
            }
        }
    }
    
    private fun notifyAdvertisingFailed(errorCode: Int, reason: String) {
        listeners.forEach { listener ->
            try {
                listener.onAdvertisingFailed(errorCode, reason)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify advertising failed failed")
            }
        }
    }
    
    private fun notifyAdvertisingStopped() {
        listeners.forEach { listener ->
            try {
                listener.onAdvertisingStopped()
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify advertising stopped failed")
            }
        }
    }
    
    private fun notifyServerStarted() {
        listeners.forEach { listener ->
            try {
                listener.onServerStarted()
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify server started failed")
            }
        }
    }
    
    private fun notifyServerStopped() {
        listeners.forEach { listener ->
            try {
                listener.onServerStopped()
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify server stopped failed")
            }
        }
    }
    
    private fun notifyServerError(error: BleServiceError) {
        listeners.forEach { listener ->
            try {
                listener.onServerError(error)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify server error failed")
            }
        }
    }
    
    private fun notifyClientConnected(client: ConnectedClient) {
        listeners.forEach { listener ->
            try {
                listener.onClientConnected(client)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify client connected failed", client.device.address)
            }
        }
    }
    
    private fun notifyClientDisconnected(client: ConnectedClient) {
        listeners.forEach { listener ->
            try {
                listener.onClientDisconnected(client)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify client disconnected failed", client.device.address)
            }
        }
    }
    
    private fun notifyDataReceived(client: ConnectedClient, serviceUuid: UUID, characteristicUuid: UUID, data: ByteArray) {
        listeners.forEach { listener ->
            try {
                listener.onDataReceived(client, serviceUuid, characteristicUuid, data)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify data received failed", client.device.address)
            }
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: BlePeripheralManager? = null
        
        fun getInstance(context: Context): BlePeripheralManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BlePeripheralManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        fun isInitialized(): Boolean = INSTANCE?.isInitialized == true
    }
}