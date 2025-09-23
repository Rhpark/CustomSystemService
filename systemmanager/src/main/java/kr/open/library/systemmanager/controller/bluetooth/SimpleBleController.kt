package kr.open.library.systemmanager.controller.bluetooth

import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kr.open.library.systemmanager.controller.bluetooth.base.BleComponent
import kr.open.library.systemmanager.controller.bluetooth.central.BleScanner
import kr.open.library.systemmanager.controller.bluetooth.connector.BleConnector
import kr.open.library.systemmanager.controller.bluetooth.data.BinaryProtocol
import kr.open.library.systemmanager.controller.bluetooth.data.BleDevice
import kr.open.library.systemmanager.controller.bluetooth.data.BleDeviceManager
import kr.open.library.systemmanager.controller.bluetooth.peripheral.BleAdvertiser

/**
 * 단순화된 BLE 컨트롤러 - 메인 컨트롤러
 * 일반 개발자 수준으로 단순화된 1:1 전용 BLE 컨트롤러
 */
class SimpleBleController(context: Context) : BleComponent(context) {
    
    enum class BleMode {
        IDLE,
        CENTRAL_MODE,
        PERIPHERAL_MODE,
        SCAN_ONLY_MODE
    }
    
    // 구성 요소
    private val scanner = BleScanner(context)
    private val advertiser = BleAdvertiser(context)
    private val connector = BleConnector(context)
    private val deviceManager = BleDeviceManager()

    // Flow 기반 반응형 상태 관리
    private val _currentMode = MutableStateFlow(BleMode.IDLE)
    val currentMode: StateFlow<BleMode> = _currentMode.asStateFlow()

    private val _connectedDevice = MutableStateFlow<String?>(null)
    val connectedDevice: StateFlow<String?> = _connectedDevice.asStateFlow()

    private val _scannedDevices = MutableSharedFlow<BleDevice>()
    val scannedDevices: SharedFlow<BleDevice> = _scannedDevices.asSharedFlow()

    private val _receivedMessages = MutableSharedFlow<Pair<Byte, ByteArray>>()
    val receivedMessages: SharedFlow<Pair<Byte, ByteArray>> = _receivedMessages.asSharedFlow()

    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    // SimpleBleController 전용 상태들 (BleComponent와 이름 충돌 방지)
    private val _bleReady = MutableStateFlow(false)
    val bleIsReady: StateFlow<Boolean> = _bleReady.asStateFlow()

    // SimpleBleController 전용 연결 상태 (BleComponent와 이름 충돌 방지)
    private val _bleConnectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val bleConnectionState: StateFlow<ConnectionState> = _bleConnectionState.asStateFlow()
    
    // 단순한 콜백 인터페이스
    interface BleControllerListener {
        fun onStateChanged(state: ConnectionState)
        fun onModeChanged(mode: BleMode)
        fun onDeviceConnected(deviceAddress: String)
        fun onDeviceDisconnected(deviceAddress: String)
        fun onMessageReceived(type: Byte, data: ByteArray)
        fun onError(error: String)
        fun onDeviceScanned(device: BleDevice)
    }
    
    private var listener: BleControllerListener? = null
    
    // Flow 기반 상태 업데이트 메서드들
    private fun updateCurrentMode(newMode: BleMode) {
        val currentModeValue = _currentMode.value
        if (currentModeValue != newMode) {
            _currentMode.value = newMode
            logd("Mode changed to: $newMode")
            listener?.onModeChanged(newMode)
        }
    }

    private fun updateConnectedDevice(deviceAddress: String?) {
        val previousDevice = _connectedDevice.value
        if (previousDevice != deviceAddress) {
            _connectedDevice.value = deviceAddress
            logd("Connected device changed: $previousDevice -> $deviceAddress")

            if (deviceAddress != null) {
                listener?.onDeviceConnected(deviceAddress)
            } else if (previousDevice != null) {
                listener?.onDeviceDisconnected(previousDevice)
            }
        }
    }

    private fun updateBleConnectionState(newState: ConnectionState) {
        val currentState = _bleConnectionState.value
        if (currentState != newState) {
            _bleConnectionState.value = newState
            logd("Connection state changed to: $newState")
            listener?.onStateChanged(newState)
        }
    }

    private fun updateBleReadyState(ready: Boolean) {
        val currentReady = _bleReady.value
        if (currentReady != ready) {
            _bleReady.value = ready
            logd("Ready state changed to: $ready")
        }
    }

    private fun notifyDeviceScanned(device: BleDevice) {
        _scannedDevices.tryEmit(device)
        listener?.onDeviceScanned(device)
    }

    private fun notifyMessageReceived(type: Byte, data: ByteArray) {
        _receivedMessages.tryEmit(Pair(type, data))
        listener?.onMessageReceived(type, data)
    }

    private fun notifyError(error: String) {
        loge(error)
        _errors.tryEmit(error)
        listener?.onError(error)
    }
    
    // 생명주기 - suspend 함수 유지 (컴포넌트 초기화는 비동기 작업)
    override suspend fun initialize(): Boolean {
        logd("Initializing SimpleBleController...")
        
        if (!checkAllRequiredPermissions()) {
            loge("Required permissions not granted")
            updateBleReadyState(false)
            return false
        }
        
        return try {
            // SDK 호환성 체크
            val isAndroid15Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
            logd("Android version: ${Build.VERSION.SDK_INT}, Android 15+: $isAndroid15Plus")
            
            // 사전에 모든 GATT 리소스 정리 (Too many register gatt interface 방지)
            logd("Pre-cleaning all GATT resources...")
            cleanupGattResources()
            
            // 컴포넌트들을 순차적으로 초기화 (비동기 작업)
            val scannerResult = scanner.initialize()
            val advertiserResult = advertiser.initialize()
            val connectorResult = connector.initialize()
            
            if (scannerResult && advertiserResult && connectorResult) {
                setupComponentListeners()
                updateBleReadyState(true)
                logi("SimpleBleController initialized successfully")
                true
            } else {
                val failedComponents = mutableListOf<String>()
                if (!scannerResult) failedComponents.add("scanner")
                if (!advertiserResult) failedComponents.add("advertiser") 
                if (!connectorResult) failedComponents.add("connector")
                loge("Failed to initialize components: $failedComponents")
                updateBleReadyState(false)
                false
            }
            
        } catch (e: Exception) {
            loge("Initialization failed", e)
            updateBleReadyState(false)
            false
        }
    }
    
    override suspend fun cleanupGattResources() {
        logd("Cleaning up GATT resources...")
        scanner.cleanupGattResources()
        advertiser.cleanupGattResources()
        connector.cleanupGattResources()
    }
    
    private fun setupComponentListeners() {
        // 컴포넌트 간 통신 설정을 여기서 구현
        logd("Setting up component listeners...")
        
        // 스캐너 리스너 설정
        setupScannerListener()
        
        // 광고자 리스너 설정
        setupAdvertiserListener()
        
        // 연결자 리스너 설정 
        setupConnectorListener()
        
        logd("Component listeners setup completed")
    }
    
    private fun setupScannerListener() {
        logd("Setting up scanner listener...")

        // Scanner는 startScan() 호출 시 리스너를 직접 전달하므로
        // 여기서는 공통 리스너 인스턴스만 생성
        logd("Scanner listener setup completed")
    }

    // Scanner용 공통 리스너 인스턴스
    private val scanListener = object : BleScanner.ScanListener {
        override fun onDeviceFound(device: BleDevice) {
            logd("Device found: ${device.displayName} (${device.address})")
            notifyDeviceScanned(device)

            // Central 모드에서 자동 연결 로직
            if (_currentMode.value == BleMode.CENTRAL_MODE) {
                logd("Central mode: Attempting to connect to ${device.displayName}")
                // 즉시 스캔 중지하고 연결 시도
                scanner.stopScan()
                connectToDevice(device.address)
            }
        }

        override fun onScanStarted() {
            logd("Scan started")
        }

        override fun onScanStopped() {
            logd("Scan stopped")
        }

        override fun onScanError(error: String) {
            loge("Scan error: $error")
            notifyError("Scan error: $error")
        }
    }
    
    private fun setupAdvertiserListener() {
        logd("Setting up advertiser listener...")

        // Advertiser는 startAdvertising() 호출 시 리스너를 직접 전달하므로
        // 여기서는 공통 리스너 인스턴스만 생성
        logd("Advertiser listener setup completed")
    }

    // Advertiser용 공통 리스너 인스턴스
    private val advertiseListener = object : BleAdvertiser.AdvertiseListener {
        override fun onAdvertiseStarted() {
            logd("Advertising started")
        }

        override fun onAdvertiseStopped() {
            logd("Advertising stopped")
        }

        override fun onConnectionReceived(deviceAddress: String) {
            logd("Connection received from: $deviceAddress")
            // 즉시 광고 중지 (BleAdvertiser에서 이미 처리되지만 상태 업데이트)
            updateConnectedDevice(deviceAddress)
            updateBleConnectionState(ConnectionState.CONNECTED)
        }

        override fun onAdvertiseError(error: String) {
            loge("Advertise error: $error")
            notifyError("Advertise error: $error")
        }
    }
    
    private fun setupConnectorListener() {
        logd("Setting up connector listener...")

        connector.setListener(object : BleConnector.ConnectionListener {
            override fun onConnected(deviceAddress: String) {
                logd("Connected to: $deviceAddress")
                updateConnectedDevice(deviceAddress)
                updateBleConnectionState(ConnectionState.CONNECTED)
            }

            override fun onDisconnected(deviceAddress: String) {
                logd("Disconnected from: $deviceAddress")
                updateConnectedDevice(null)
                updateBleConnectionState(ConnectionState.DISCONNECTED)
            }

            override fun onConnectionError(deviceAddress: String, error: String) {
                loge("Connection error with $deviceAddress: $error")
                updateBleConnectionState(ConnectionState.ERROR)
                notifyError("Connection error: $error")
            }

            override fun onMtuChanged(deviceAddress: String, mtu: Int) {
                logd("MTU changed for $deviceAddress: $mtu")
            }

            override fun onMessageReceived(deviceAddress: String, type: Byte, data: ByteArray) {
                logd("Message received from $deviceAddress, type: 0x${type.toString(16)}, size: ${data.size}")
                notifyMessageReceived(type, data)
            }
        })

        logd("Connector listener setup completed")
    }

    // 헬퍼 메서드: 디바이스 연결 시도
    private fun connectToDevice(deviceAddress: String) {
        logd("Attempting to connect to device: $deviceAddress")
        updateBleConnectionState(ConnectionState.CONNECTING)

        componentScope.launch {
            try {
                val success = connector.connect(deviceAddress)
                if (!success) {
                    loge("Failed to initiate connection to $deviceAddress")
                    updateBleConnectionState(ConnectionState.ERROR)
                    notifyError("Failed to start connection")
                }
                // 성공/실패는 connector의 콜백에서 처리됨
            } catch (e: Exception) {
                loge("Exception during connection attempt: ${e.message}")
                updateBleConnectionState(ConnectionState.ERROR)
                notifyError("Connection failed: ${e.message}")
            }
        }
    }
    
    override suspend fun cleanup() {
        logd("Cleaning up SimpleBleController...")
        
        stopAllOperations()
        
        // 모든 컴포넌트 정리 (순차 처리)
        scanner.cleanup()
        advertiser.cleanup()
        connector.cleanup()
        
        listener = null
        
        // 상태 초기화
        updateCurrentMode(BleMode.IDLE)
        updateConnectedDevice(null)
        
        super.cleanup()
    }
    
    // Central 모드 시작 (자동 연결) - 일반 함수로 단순화
    fun startAsCentral(targetDeviceName: String) {
        if (_currentMode.value != BleMode.IDLE) {
            val error = "Cannot start Central mode, current mode: ${_currentMode.value}"
            logw(error)
            notifyError(error)
            return
        }
        
        logd("Starting Central mode, scanning for: $targetDeviceName")
        updateCurrentMode(BleMode.CENTRAL_MODE)
        updateBleConnectionState(ConnectionState.CONNECTING)
        
        // 스캔 시작
        scanForDeviceAndConnect(targetDeviceName)
    }
    
    // 스캔만 시작 (자동 연결 안 함) - 일반 함수로 단순화
    fun startScanOnly() {
        if (_currentMode.value != BleMode.IDLE) {
            val error = "Cannot start scan, current mode: ${_currentMode.value}"
            logw(error)
            notifyError(error)
            return
        }
        
        logd("Starting scan only mode")
        updateCurrentMode(BleMode.SCAN_ONLY_MODE)
        
        // 스캔 시작 (모든 디바이스, 자동 연결 안 함)
        startScanForAllDevices()
    }
    
    // Peripheral 모드 시작 - 일반 함수로 단순화
    fun startAsPeripheral(deviceName: String) {
        logd("🔥🔥🔥 startAsPeripheral() CALLED with deviceName: '$deviceName'")

        if (_currentMode.value != BleMode.IDLE) {
            val error = "Cannot start Peripheral mode, current mode: ${_currentMode.value}"
            logw(error)
            notifyError(error)
            return
        }

        logd("🔥 Starting Peripheral mode with name: $deviceName")
        updateCurrentMode(BleMode.PERIPHERAL_MODE)

        // 광고 시작
        logd("🔥 About to call startAdvertising()")
        startAdvertising(deviceName)
        logd("🔥 startAdvertising() call completed")
    }
    
    // 모든 작업 중지 - 일반 함수로 단순화
    fun stopAllOperations() {
        logd("Stopping all BLE operations...")
        
        // 모든 작업을 순차적으로 중지
        scanner.stopScan()
        advertiser.stopAdvertising()
        _connectedDevice.value?.let { deviceAddress ->
            // connector.disconnect(deviceAddress)
        }

        // 상태 초기화
        updateCurrentMode(BleMode.IDLE)
        updateConnectedDevice(null)
        updateBleConnectionState(ConnectionState.DISCONNECTED)
    }
    
    // 메시지 전송 - 일반 함수로 단순화
    fun sendMessage(type: Byte, data: ByteArray): Boolean {
        val deviceAddress = _connectedDevice.value
        if (deviceAddress == null) {
            val error = "Cannot send message: not connected"
            logw(error)
            notifyError(error)
            return false
        }

        if (_bleConnectionState.value != ConnectionState.CONNECTED) {
            val error = "Cannot send message: connection state is ${_bleConnectionState.value}"
            logw(error)
            notifyError(error)
            return false
        }

        // BleConnector를 통한 실제 메시지 전송
        return try {
            // Connector.sendMessage()는 suspend 함수이므로 coroutine에서 호출
            componentScope.launch {
                val success = connector.sendMessage(deviceAddress, type, data)
                if (!success) {
                    notifyError("Message transmission failed")
                }
            }
            true // 전송 시작은 성공 (실제 결과는 콜백에서 확인)
        } catch (e: Exception) {
            loge("Exception during message send: ${e.message}")
            notifyError("Send failed: ${e.message}")
            false
        }
    }
    
    // 편의 메서드들 - 일반 함수로 단순화
    fun sendTextMessage(text: String): Boolean {
        val data = BinaryProtocol.createTextMessage(text)
        return sendMessage(BinaryProtocol.MessageType.TEXT_MESSAGE, data)
    }
    
    fun sendHeartbeat(): Boolean {
        val data = BinaryProtocol.createHeartbeat()
        return sendMessage(BinaryProtocol.MessageType.HEARTBEAT, data)
    }
    
    // 헬퍼 메서드들 - 실제 구현
    private fun scanForDeviceAndConnect(targetDeviceName: String) {
        logd("Scanning for device: $targetDeviceName")
        try {
            scanner.startScan(targetDeviceName, scanListener)
        } catch (e: Exception) {
            loge("Failed to start scan: ${e.message}")
            notifyError("Scan failed: ${e.message}")
        }
    }

    private fun startScanForAllDevices() {
        logd("Starting scan for all devices")
        try {
            scanner.startScanOnly(scanListener)
        } catch (e: Exception) {
            loge("Failed to start scan only: ${e.message}")
            notifyError("Scan failed: ${e.message}")
        }
    }

    private fun startAdvertising(deviceName: String) {
        logd("Starting advertising with name: $deviceName")
        try {
            advertiser.startAdvertising(deviceName, advertiseListener)
        } catch (e: Exception) {
            loge("Failed to start advertising: ${e.message}")
            notifyError("Advertising failed: ${e.message}")
        }
    }
    
    // 텍스트 파싱 (기존 호환성)
    fun parseTextMessage(data: ByteArray): String? = BinaryProtocol.parseTextMessage(data)
    
    // 상태 조회 메서드들
    fun getCurrentMode(): BleMode = _currentMode.value
    fun getConnectedDevice(): String? = _connectedDevice.value
    fun isConnected(): Boolean = _bleConnectionState.value == ConnectionState.CONNECTED

    fun getStatusSummary(): String {
        return buildString {
            appendLine("=== BLE Controller Status ===")
            appendLine("Mode: ${_currentMode.value}")
            appendLine("Connection State: ${_bleConnectionState.value}")
            appendLine("Connected Device: ${_connectedDevice.value ?: "None"}")
            appendLine("Ready State: ${_bleReady.value}")
            // TODO: 컴포넌트 준비 상태 확인 방법 단순화 후 추가
            appendLine("Android Version: ${Build.VERSION.SDK_INT}")
        }
    }
    
    // 리스너 설정 (기존 호환성)
    fun setListener(listener: BleControllerListener) {
        this.listener = listener
    }
}