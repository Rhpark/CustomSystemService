package kr.open.library.systemmanager.controller.bluetooth.central

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.base.BluetoothBaseController
import kr.open.library.systemmanager.controller.bluetooth.base.BleResourceManager
import kr.open.library.systemmanager.controller.bluetooth.data.BleConnectionState
import kr.open.library.systemmanager.controller.bluetooth.data.BleDevice
import kr.open.library.systemmanager.controller.bluetooth.debug.BleDebugLogger
import kr.open.library.systemmanager.controller.bluetooth.error.BleServiceError
import kr.open.library.systemmanager.controller.bluetooth.base.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * BLE 연결 관리 컨트롤러
 * BLE Connection Management Controller
 * 
 * BLE 기기와의 연결, GATT 서비스 탐색, 특성 읽기/쓰기 등을 담당합니다.
 * Handles BLE device connection, GATT service discovery, characteristic read/write operations.
 * 
 * 주요 기능:
 * Key features:
 * - 다중 기기 동시 연결 관리
 * - 자동 재연결 및 연결 복구
 * - GATT 서비스 및 특성 자동 탐색
 * - 안전한 데이터 송수신
 * - 연결 상태 실시간 모니터링
 */
class BleConnectionController(context: Context) : BluetoothBaseController(context) {
    
    private val TAG = "BleConnectionController"
    
    /**
     * 연결 설정 옵션
     */
    data class ConnectionConfig(
        val autoConnect: Boolean = false,
        val transport: Int = BluetoothDevice.TRANSPORT_LE,
        val connectionPriority: Int = BluetoothGatt.CONNECTION_PRIORITY_BALANCED,
        val mtuSize: Int = 517, // 기본 MTU 크기
        val connectionTimeout: Long = 10000L, // 10초
        val operationTimeout: Long = 5000L,   // 5초
        val maxRetryCount: Int = 3,
        val retryDelay: Long = 1000L,         // 1초
        val enableNotifications: Boolean = true,
        val autoDiscoverServices: Boolean = true
    )
    
    /**
     * GATT 작업 타입
     */
    sealed class GattOperation {
        data class Read(val characteristic: BluetoothGattCharacteristic) : GattOperation()
        data class Write(val characteristic: BluetoothGattCharacteristic, val data: ByteArray, val writeType: Int) : GattOperation()
        data class ReadDescriptor(val descriptor: BluetoothGattDescriptor) : GattOperation()
        data class WriteDescriptor(val descriptor: BluetoothGattDescriptor, val data: ByteArray) : GattOperation()
        data class SetNotification(val characteristic: BluetoothGattCharacteristic, val enabled: Boolean) : GattOperation()
        data class RequestMtu(val mtuSize: Int) : GattOperation()
    }
    
    /**
     * 연결 이벤트 리스너
     */
    interface ConnectionListener {
        fun onConnectionStateChanged(device: BleDevice, state: BleConnectionState)
        fun onServicesDiscovered(device: BleDevice, services: List<BluetoothGattService>)
        fun onCharacteristicRead(device: BleDevice, characteristic: BluetoothGattCharacteristic, data: ByteArray)
        fun onCharacteristicWrite(device: BleDevice, characteristic: BluetoothGattCharacteristic, success: Boolean)
        fun onCharacteristicChanged(device: BleDevice, characteristic: BluetoothGattCharacteristic, data: ByteArray)
        fun onMtuChanged(device: BleDevice, mtu: Int, status: Int)
        fun onConnectionError(device: BleDevice, error: BleServiceError)
    }
    
    /**
     * 연결된 기기 정보
     */
    private data class ConnectedDeviceInfo(
        val device: BleDevice,
        val bluetoothGatt: BluetoothGatt,
        val config: ConnectionConfig,
        var connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
        val services: MutableList<BluetoothGattService> = mutableListOf(),
        val operationQueue: MutableList<GattOperation> = mutableListOf(),
        var isProcessingOperation: Boolean = false,
        var retryCount: Int = 0,
        val resourceId: String
    )
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    // 연결 관리
    private val connectedDevices = ConcurrentHashMap<String, ConnectedDeviceInfo>()
    private val listeners = CopyOnWriteArrayList<ConnectionListener>()
    private val connectionTimeouts = ConcurrentHashMap<String, Runnable>()
    private val operationTimeouts = ConcurrentHashMap<String, Runnable>()
    private val timeoutHandler = Handler(Looper.getMainLooper())
    
    // 상태 플로우
    private val _connectionStates = MutableStateFlow<Map<String, BleConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, BleConnectionState>> = _connectionStates.asStateFlow()
    
    private val operationIdCounter = AtomicInteger(0)
    
    init {
        BleDebugLogger.logSystemState("BleConnectionController initialized")
    }
    
    /**
     * 연결 리스너 등록
     */
    fun addConnectionListener(listener: ConnectionListener) {
        listeners.addIfAbsent(listener)
    }
    
    /**
     * 연결 리스너 해제
     */
    fun removeConnectionListener(listener: ConnectionListener) {
        listeners.remove(listener)
    }
    
    /**
     * BLE 기기 연결
     */
    fun connect(device: BleDevice, config: ConnectionConfig = ConnectionConfig()): Result<Unit> {
        return try {
            BleDebugLogger.logConnectionAttempt(device.address, device.name, config.autoConnect)
            
            // 전제 조건 검사
            val permissionResult = checkPermissions("CONNECT")
            if (permissionResult is Result.Failure) {
                return permissionResult
            }
            
            val systemResult = checkSystemState()
            if (systemResult is Result.Failure) {
                return systemResult
            }
            
            val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                ?: return Result.Failure(BleServiceError.HardwareError.BluetoothNotAvailable("Bluetooth adapter not available"))
            
            // 이미 연결되어 있거나 연결 중인지 확인
            val existingConnection = connectedDevices[device.address]
            if (existingConnection != null) {
                when (existingConnection.connectionState) {
                    BleConnectionState.CONNECTED -> {
                        BleDebugLogger.logConnectionSuccess(device.address, device.name)
                        return Result.Success(Unit)
                    }
                    BleConnectionState.CONNECTING -> {
                        return Result.Failure(BleServiceError.ConnectionError.ConnectionInProgress("Connection already in progress"))
                    }
                    else -> {
                        // 기존 연결 정리 후 새로 연결
                        disconnect(device.address)
                    }
                }
            }
            
            // GATT 콜백 생성
            val gattCallback = createGattCallback(device, config)
            
            // GATT 연결 시작 (MinSdk 28이므로 transport 파라미터 항상 사용 가능)
            val bluetoothGatt = bluetoothDevice.connectGatt(context, config.autoConnect, gattCallback, config.transport)
            
            if (bluetoothGatt == null) {
                return Result.Failure(BleServiceError.ConnectionError.ConnectionFailed("Failed to create GATT connection"))
            }
            
            // 리소스 등록
            val resourceId = resourceManager.registerResource(
                GattConnectionResource(bluetoothGatt),
                BleResourceManager.ResourceType.GATT_CONNECTION,
                "gatt_${device.address}"
            )
            
            // 연결 정보 저장
            val deviceInfo = ConnectedDeviceInfo(
                device = device,
                bluetoothGatt = bluetoothGatt,
                config = config,
                connectionState = BleConnectionState.CONNECTING,
                resourceId = resourceId
            )
            
            connectedDevices[device.address] = deviceInfo
            updateConnectionState(device.address, BleConnectionState.CONNECTING)
            
            // 연결 타임아웃 설정
            scheduleConnectionTimeout(device.address, config.connectionTimeout)
            
            Result.Success(Unit)
            
        } catch (e: Exception) {
            val error = BleServiceError.ConnectionError.ConnectionFailed("Failed to connect", e)
            BleDebugLogger.logException(e, "Connect failed", device.address)
            notifyConnectionError(device, error)
            Result.Failure(error)
        }
    }
    
    /**
     * BLE 기기 연결 해제
     */
    fun disconnect(deviceAddress: String): Result<Unit> {
        return try {
            val deviceInfo = connectedDevices[deviceAddress]
            if (deviceInfo == null) {
                return Result.Success(Unit) // 이미 연결되어 있지 않음
            }
            
            BleDebugLogger.logDisconnection(deviceAddress, 0, "Requested disconnect")
            
            // 타임아웃 취소
            cancelConnectionTimeout(deviceAddress)
            cancelOperationTimeout(deviceAddress)
            
            // GATT 연결 해제
            try {
                deviceInfo.bluetoothGatt.disconnect()
                deviceInfo.bluetoothGatt.close()
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "GATT disconnect failed", deviceAddress)
            }
            
            // 리소스 해제
            resourceManager.disposeResource(deviceInfo.resourceId)
            
            // 연결 정보 제거
            connectedDevices.remove(deviceAddress)
            updateConnectionState(deviceAddress, BleConnectionState.DISCONNECTED)
            
            Result.Success(Unit)
            
        } catch (e: Exception) {
            val error = BleServiceError.ConnectionError.DisconnectionFailed("Failed to disconnect", e)
            BleDebugLogger.logException(e, "Disconnect failed", deviceAddress)
            Result.Failure(error)
        }
    }
    
    /**
     * 모든 기기 연결 해제
     */
    fun disconnectAll(): Result<Unit> {
        val devices = connectedDevices.keys.toList()
        var lastError: BleServiceError? = null
        
        devices.forEach { deviceAddress ->
            val result = disconnect(deviceAddress)
            if (result is Result.Failure) {
                lastError = result.error
            }
        }
        
        return if (lastError != null) {
            Result.Failure(lastError!!)
        } else {
            Result.Success(Unit)
        }
    }
    
    /**
     * 연결된 기기 목록 반환
     */
    fun getConnectedDevices(): List<BleDevice> {
        return connectedDevices.values.filter { 
            it.connectionState == BleConnectionState.CONNECTED 
        }.map { it.device }
    }
    
    /**
     * 특정 기기의 연결 상태 반환
     */
    fun getConnectionState(deviceAddress: String): BleConnectionState {
        return connectedDevices[deviceAddress]?.connectionState ?: BleConnectionState.DISCONNECTED
    }
    
    /**
     * 기기가 연결되어 있는지 확인
     */
    fun isConnected(deviceAddress: String): Boolean {
        return getConnectionState(deviceAddress) == BleConnectionState.CONNECTED
    }
    
    /**
     * 특성 읽기
     */
    fun readCharacteristic(
        deviceAddress: String,
        serviceUuid: String,
        characteristicUuid: String
    ): Result<Unit> {
        val deviceInfo = connectedDevices[deviceAddress]
            ?: return Result.Failure(BleServiceError.ConnectionError.DeviceNotConnected("Device not connected"))
        
        val characteristic = findCharacteristic(deviceInfo, serviceUuid, characteristicUuid)
            ?: return Result.Failure(BleServiceError.GattError.CharacteristicNotFound("Characteristic not found"))
        
        return queueGattOperation(deviceAddress, GattOperation.Read(characteristic))
    }
    
    /**
     * 특성 쓰기
     */
    fun writeCharacteristic(
        deviceAddress: String,
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ): Result<Unit> {
        val deviceInfo = connectedDevices[deviceAddress]
            ?: return Result.Failure(BleServiceError.ConnectionError.DeviceNotConnected("Device not connected"))
        
        val characteristic = findCharacteristic(deviceInfo, serviceUuid, characteristicUuid)
            ?: return Result.Failure(BleServiceError.GattError.CharacteristicNotFound("Characteristic not found"))
        
        return queueGattOperation(deviceAddress, GattOperation.Write(characteristic, data, writeType))
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
        val deviceInfo = connectedDevices[deviceAddress]
            ?: return Result.Failure(BleServiceError.ConnectionError.DeviceNotConnected("Device not connected"))
        
        val characteristic = findCharacteristic(deviceInfo, serviceUuid, characteristicUuid)
            ?: return Result.Failure(BleServiceError.GattError.CharacteristicNotFound("Characteristic not found"))
        
        return queueGattOperation(deviceAddress, GattOperation.SetNotification(characteristic, enabled))
    }
    
    /**
     * MTU 크기 변경 요청
     */
    fun requestMtu(deviceAddress: String, mtuSize: Int): Result<Unit> {
        val deviceInfo = connectedDevices[deviceAddress]
            ?: return Result.Failure(BleServiceError.ConnectionError.DeviceNotConnected("Device not connected"))
        
        return queueGattOperation(deviceAddress, GattOperation.RequestMtu(mtuSize))
    }
    
    /**
     * GATT 콜백 생성
     */
    private fun createGattCallback(device: BleDevice, config: ConnectionConfig): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                handleConnectionStateChange(device, gatt, status, newState, config)
            }
            
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                handleServicesDiscovered(device, gatt, status, config)
            }
            
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    handleCharacteristicRead(device, gatt, characteristic, characteristic.value, status)
                } else {
                    @Suppress("DEPRECATION")
                    handleCharacteristicRead(device, gatt, characteristic, characteristic.value, status)
                }
            }
            
            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                handleCharacteristicWrite(device, gatt, characteristic, status)
            }
            
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    handleCharacteristicChanged(device, gatt, characteristic, characteristic.value)
                } else {
                    @Suppress("DEPRECATION")
                    handleCharacteristicChanged(device, gatt, characteristic, characteristic.value)
                }
            }
            
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                handleMtuChanged(device, gatt, mtu, status)
            }
        }
    }
    
    /**
     * 연결 상태 변경 처리
     */
    private fun handleConnectionStateChange(
        device: BleDevice, 
        gatt: BluetoothGatt, 
        status: Int, 
        newState: Int,
        config: ConnectionConfig
    ) {
        val deviceInfo = connectedDevices[device.address] ?: return
        
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    BleDebugLogger.logConnectionSuccess(device.address, device.name)
                    
                    cancelConnectionTimeout(device.address)
                    deviceInfo.connectionState = BleConnectionState.CONNECTED
                    deviceInfo.retryCount = 0
                    
                    updateConnectionState(device.address, BleConnectionState.CONNECTED)
                    notifyConnectionStateChanged(device, BleConnectionState.CONNECTED)
                    
                    // 서비스 탐색 시작
                    if (config.autoDiscoverServices) {
                        gatt.discoverServices()
                    }
                    
                    // 연결 우선순위 설정 (MinSdk 28이므로 항상 사용 가능)
                    gatt.requestConnectionPriority(config.connectionPriority)
                    
                } else {
                    handleConnectionFailure(device, status, config)
                }
            }
            
            BluetoothProfile.STATE_DISCONNECTED -> {
                BleDebugLogger.logDisconnection(device.address, status, "Connection lost")
                
                cancelConnectionTimeout(device.address)
                cancelOperationTimeout(device.address)
                
                val wasConnected = deviceInfo.connectionState == BleConnectionState.CONNECTED
                deviceInfo.connectionState = BleConnectionState.DISCONNECTED
                
                updateConnectionState(device.address, BleConnectionState.DISCONNECTED)
                notifyConnectionStateChanged(device, BleConnectionState.DISCONNECTED)
                
                // 자동 재연결 처리
                if (wasConnected && config.autoConnect && deviceInfo.retryCount < config.maxRetryCount) {
                    scheduleReconnect(device, config, deviceInfo)
                } else {
                    // 연결 정보 정리
                    cleanupConnection(device.address)
                }
            }
            
            BluetoothProfile.STATE_CONNECTING -> {
                deviceInfo.connectionState = BleConnectionState.CONNECTING
                updateConnectionState(device.address, BleConnectionState.CONNECTING)
                notifyConnectionStateChanged(device, BleConnectionState.CONNECTING)
            }
            
            BluetoothProfile.STATE_DISCONNECTING -> {
                deviceInfo.connectionState = BleConnectionState.DISCONNECTING
                updateConnectionState(device.address, BleConnectionState.DISCONNECTING)
                notifyConnectionStateChanged(device, BleConnectionState.DISCONNECTING)
            }
        }
    }
    
    /**
     * 서비스 탐색 완료 처리
     */
    private fun handleServicesDiscovered(device: BleDevice, gatt: BluetoothGatt, status: Int, config: ConnectionConfig) {
        val deviceInfo = connectedDevices[device.address] ?: return
        
        if (status == BluetoothGatt.GATT_SUCCESS) {
            val services = gatt.services ?: emptyList()
            deviceInfo.services.clear()
            deviceInfo.services.addAll(services)
            
            BleDebugLogger.logServiceDiscovery(device.address, services.size)
            notifyServicesDiscovered(device, services)
            
            // MTU 크기 요청 (MinSdk 28이므로 항상 사용 가능)
            if (config.mtuSize > 23) {
                queueGattOperation(device.address, GattOperation.RequestMtu(config.mtuSize))
            }
            
            // 알림 설정
            if (config.enableNotifications) {
                enableNotificationsForDevice(deviceInfo)
            }
            
        } else {
            BleDebugLogger.logServiceDiscoveryFailed(device.address, status)
            val error = BleServiceError.GattError.ServiceDiscoveryFailed("Service discovery failed", status)
            notifyConnectionError(device, error)
        }
    }
    
    /**
     * 특성 읽기 완료 처리
     */
    private fun handleCharacteristicRead(
        device: BleDevice, 
        gatt: BluetoothGatt, 
        characteristic: BluetoothGattCharacteristic, 
        data: ByteArray?, 
        status: Int
    ) {
        val success = status == BluetoothGatt.GATT_SUCCESS
        val readData = data ?: byteArrayOf()
        
        BleDebugLogger.logCharacteristicRead(
            device.address,
            characteristic.service.uuid.toString(),
            characteristic.uuid.toString(),
            success,
            readData.size
        )
        
        if (success && readData.isNotEmpty()) {
            BleDebugLogger.logDataReceived(device.address, characteristic.uuid.toString(), readData)
            notifyCharacteristicRead(device, characteristic, readData)
        } else {
            val error = BleServiceError.GattError.CharacteristicReadFailed("Read failed", status)
            notifyConnectionError(device, error)
        }
        
        processNextGattOperation(device.address)
    }
    
    /**
     * 특성 쓰기 완료 처리
     */
    private fun handleCharacteristicWrite(
        device: BleDevice, 
        gatt: BluetoothGatt, 
        characteristic: BluetoothGattCharacteristic, 
        status: Int
    ) {
        val success = status == BluetoothGatt.GATT_SUCCESS
        
        BleDebugLogger.logCharacteristicWrite(
            device.address,
            characteristic.service.uuid.toString(),
            characteristic.uuid.toString(),
            success
        )
        
        notifyCharacteristicWrite(device, characteristic, success)
        
        if (!success) {
            val error = BleServiceError.GattError.CharacteristicWriteFailed("Write failed", status)
            notifyConnectionError(device, error)
        }
        
        processNextGattOperation(device.address)
    }
    
    /**
     * 특성 변경 알림 처리
     */
    private fun handleCharacteristicChanged(
        device: BleDevice, 
        gatt: BluetoothGatt, 
        characteristic: BluetoothGattCharacteristic, 
        data: ByteArray?
    ) {
        val changeData = data ?: byteArrayOf()
        
        if (changeData.isNotEmpty()) {
            BleDebugLogger.logDataReceived(device.address, characteristic.uuid.toString(), changeData)
            notifyCharacteristicChanged(device, characteristic, changeData)
        }
    }
    
    /**
     * MTU 변경 완료 처리
     */
    private fun handleMtuChanged(device: BleDevice, gatt: BluetoothGatt, mtu: Int, status: Int) {
        BleDebugLogger.logMtuChange(device.address, mtu, status)
        notifyMtuChanged(device, mtu, status)
        processNextGattOperation(device.address)
    }
    
    /**
     * GATT 작업 큐에 추가
     */
    private fun queueGattOperation(deviceAddress: String, operation: GattOperation): Result<Unit> {
        val deviceInfo = connectedDevices[deviceAddress]
            ?: return Result.Failure(BleServiceError.ConnectionError.DeviceNotConnected("Device not connected"))
        
        synchronized(deviceInfo.operationQueue) {
            deviceInfo.operationQueue.add(operation)
        }
        
        processNextGattOperation(deviceAddress)
        return Result.Success(Unit)
    }
    
    /**
     * 다음 GATT 작업 처리
     */
    private fun processNextGattOperation(deviceAddress: String) {
        val deviceInfo = connectedDevices[deviceAddress] ?: return
        
        synchronized(deviceInfo.operationQueue) {
            if (deviceInfo.isProcessingOperation || deviceInfo.operationQueue.isEmpty()) {
                return
            }
            
            val operation = deviceInfo.operationQueue.removeAt(0)
            deviceInfo.isProcessingOperation = true
            
            scheduleOperationTimeout(deviceAddress, deviceInfo.config.operationTimeout)
            
            try {
                val success = executeGattOperation(deviceInfo, operation)
                if (!success) {
                    deviceInfo.isProcessingOperation = false
                    cancelOperationTimeout(deviceAddress)
                    processNextGattOperation(deviceAddress) // 다음 작업 시도
                }
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Execute GATT operation failed", deviceAddress)
                deviceInfo.isProcessingOperation = false
                cancelOperationTimeout(deviceAddress)
                processNextGattOperation(deviceAddress)
            }
        }
    }
    
    /**
     * GATT 작업 실행
     */
    private fun executeGattOperation(deviceInfo: ConnectedDeviceInfo, operation: GattOperation): Boolean {
        return try {
            when (operation) {
                is GattOperation.Read -> {
                    deviceInfo.bluetoothGatt.readCharacteristic(operation.characteristic)
                }
                is GattOperation.Write -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        deviceInfo.bluetoothGatt.writeCharacteristic(operation.characteristic, operation.data, operation.writeType)
                        true
                    } else {
                        @Suppress("DEPRECATION")
                        operation.characteristic.value = operation.data
                        @Suppress("DEPRECATION")
                        operation.characteristic.writeType = operation.writeType
                        @Suppress("DEPRECATION")
                        deviceInfo.bluetoothGatt.writeCharacteristic(operation.characteristic)
                    }
                }
                is GattOperation.SetNotification -> {
                    val success = deviceInfo.bluetoothGatt.setCharacteristicNotification(operation.characteristic, operation.enabled)
                    if (success && operation.enabled) {
                        // CCCD 설정
                        val cccd = operation.characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        if (cccd != null) {
                            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                deviceInfo.bluetoothGatt.writeDescriptor(cccd, value)
                            } else {
                                @Suppress("DEPRECATION")
                                cccd.value = value
                                @Suppress("DEPRECATION")
                                deviceInfo.bluetoothGatt.writeDescriptor(cccd)
                            }
                        }
                    }
                    success
                }
                is GattOperation.RequestMtu -> {
                    deviceInfo.bluetoothGatt.requestMtu(operation.mtuSize)
                }
                else -> false
            }
        } catch (e: Exception) {
            BleDebugLogger.logException(e, "GATT operation execution failed", deviceInfo.device.address)
            false
        }
    }
    
    // 유틸리티 메서드들
    private fun findCharacteristic(deviceInfo: ConnectedDeviceInfo, serviceUuid: String, characteristicUuid: String): BluetoothGattCharacteristic? {
        return deviceInfo.services
            .find { it.uuid.toString().equals(serviceUuid, ignoreCase = true) }
            ?.getCharacteristic(UUID.fromString(characteristicUuid))
    }
    
    private fun updateConnectionState(deviceAddress: String, state: BleConnectionState) {
        val currentStates = _connectionStates.value.toMutableMap()
        if (state == BleConnectionState.DISCONNECTED) {
            currentStates.remove(deviceAddress)
        } else {
            currentStates[deviceAddress] = state
        }
        _connectionStates.value = currentStates
    }
    
    private fun enableNotificationsForDevice(deviceInfo: ConnectedDeviceInfo) {
        deviceInfo.services.forEach { service ->
            service.characteristics.forEach { characteristic ->
                val properties = characteristic.properties
                if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                    queueGattOperation(deviceInfo.device.address, GattOperation.SetNotification(characteristic, true))
                }
            }
        }
    }
    
    private fun handleConnectionFailure(device: BleDevice, status: Int, config: ConnectionConfig) {
        val deviceInfo = connectedDevices[device.address] ?: return
        
        BleDebugLogger.logConnectionFailed(device.address, status, "Connection failed with status $status")
        
        if (deviceInfo.retryCount < config.maxRetryCount) {
            scheduleReconnect(device, config, deviceInfo)
        } else {
            val error = BleServiceError.ConnectionError.ConnectionFailed("Connection failed after ${config.maxRetryCount} retries", status)
            notifyConnectionError(device, error)
            cleanupConnection(device.address)
        }
    }
    
    private fun scheduleReconnect(device: BleDevice, config: ConnectionConfig, deviceInfo: ConnectedDeviceInfo) {
        deviceInfo.retryCount++
        timeoutHandler.postDelayed({
            if (connectedDevices.containsKey(device.address)) {
                connect(device, config)
            }
        }, config.retryDelay)
    }
    
    private fun scheduleConnectionTimeout(deviceAddress: String, timeout: Long) {
        val timeoutRunnable = Runnable {
            val deviceInfo = connectedDevices[deviceAddress]
            if (deviceInfo != null && deviceInfo.connectionState == BleConnectionState.CONNECTING) {
                val error = BleServiceError.ConnectionError.ConnectionTimeout("Connection timeout")
                BleDebugLogger.logConnectionFailed(deviceAddress, -1, "Connection timeout")
                notifyConnectionError(deviceInfo.device, error)
                disconnect(deviceAddress)
            }
        }
        
        connectionTimeouts[deviceAddress] = timeoutRunnable
        timeoutHandler.postDelayed(timeoutRunnable, timeout)
    }
    
    private fun cancelConnectionTimeout(deviceAddress: String) {
        connectionTimeouts.remove(deviceAddress)?.let { runnable ->
            timeoutHandler.removeCallbacks(runnable)
        }
    }
    
    private fun scheduleOperationTimeout(deviceAddress: String, timeout: Long) {
        val timeoutRunnable = Runnable {
            val deviceInfo = connectedDevices[deviceAddress]
            if (deviceInfo != null) {
                deviceInfo.isProcessingOperation = false
                processNextGattOperation(deviceAddress)
            }
        }
        
        operationTimeouts[deviceAddress] = timeoutRunnable
        timeoutHandler.postDelayed(timeoutRunnable, timeout)
    }
    
    private fun cancelOperationTimeout(deviceAddress: String) {
        operationTimeouts.remove(deviceAddress)?.let { runnable ->
            timeoutHandler.removeCallbacks(runnable)
        }
    }
    
    private fun cleanupConnection(deviceAddress: String) {
        val deviceInfo = connectedDevices.remove(deviceAddress)
        if (deviceInfo != null) {
            resourceManager.disposeResource(deviceInfo.resourceId)
        }
        cancelConnectionTimeout(deviceAddress)
        cancelOperationTimeout(deviceAddress)
        updateConnectionState(deviceAddress, BleConnectionState.DISCONNECTED)
    }
    
    // 리스너 알림 메서드들
    private fun notifyConnectionStateChanged(device: BleDevice, state: BleConnectionState) {
        listeners.forEach { listener ->
            try {
                listener.onConnectionStateChanged(device, state)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify connection state changed failed", device.address)
            }
        }
    }
    
    private fun notifyServicesDiscovered(device: BleDevice, services: List<BluetoothGattService>) {
        listeners.forEach { listener ->
            try {
                listener.onServicesDiscovered(device, services)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify services discovered failed", device.address)
            }
        }
    }
    
    private fun notifyCharacteristicRead(device: BleDevice, characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        listeners.forEach { listener ->
            try {
                listener.onCharacteristicRead(device, characteristic, data)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify characteristic read failed", device.address)
            }
        }
    }
    
    private fun notifyCharacteristicWrite(device: BleDevice, characteristic: BluetoothGattCharacteristic, success: Boolean) {
        listeners.forEach { listener ->
            try {
                listener.onCharacteristicWrite(device, characteristic, success)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify characteristic write failed", device.address)
            }
        }
    }
    
    private fun notifyCharacteristicChanged(device: BleDevice, characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        listeners.forEach { listener ->
            try {
                listener.onCharacteristicChanged(device, characteristic, data)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify characteristic changed failed", device.address)
            }
        }
    }
    
    private fun notifyMtuChanged(device: BleDevice, mtu: Int, status: Int) {
        listeners.forEach { listener ->
            try {
                listener.onMtuChanged(device, mtu, status)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify MTU changed failed", device.address)
            }
        }
    }
    
    private fun notifyConnectionError(device: BleDevice, error: BleServiceError) {
        listeners.forEach { listener ->
            try {
                listener.onConnectionError(device, error)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify connection error failed", device.address)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 모든 연결 해제
        disconnectAll()
        
        // 타임아웃 정리
        timeoutHandler.removeCallbacksAndMessages(null)
        
        // 리스너 정리
        listeners.clear()
        
        BleDebugLogger.logSystemState("BleConnectionController destroyed")
    }
    
    /**
     * GATT 연결 리소스 래퍼
     */
    private class GattConnectionResource(private val gatt: BluetoothGatt) : BleResource {
        override fun cleanup() {
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                // 정리 중 오류 무시
            }
        }
    }
}