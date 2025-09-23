package kr.open.library.systemmanager.controller.bluetooth.peripheral

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.base.BluetoothBaseController
import kr.open.library.systemmanager.controller.bluetooth.base.BlePermissionManager
import kr.open.library.systemmanager.controller.bluetooth.base.BleResourceManager
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

/**
 * BLE GATT 서버 컨트롤러
 * BLE GATT Server Controller
 * 
 * BLE Peripheral로서 GATT 서버 역할을 수행하여 클라이언트들에게 서비스와 특성을 제공합니다.
 * Performs as a GATT server in BLE Peripheral role, providing services and characteristics to clients.
 * 
 * 주요 기능:
 * Key features:
 * - GATT 서비스 및 특성 관리
 * - 클라이언트 연결 관리
 * - 데이터 읽기/쓰기 처리
 * - 알림 및 인디케이션 관리
 * - MTU 및 PHY 설정
 */
class BleGattServerController(context: Context) : BluetoothBaseController(
    context, 
    BlePermissionManager.BleRole.PERIPHERAL
) {
    
    private val TAG = "BleGattServerController"
    
    /**
     * GATT 서버 상태
     */
    enum class GattServerState {
        IDLE,           // 대기 중
        STARTING,       // 시작 중
        RUNNING,        // 실행 중
        STOPPING,       // 중지 중
        ERROR           // 오류 상태
    }
    
    /**
     * 클라이언트 연결 정보
     */
    data class ConnectedClient(
        val device: BluetoothDevice,
        val bleDevice: BleDevice,
        val connectionId: Int,
        var mtuSize: Int = 23,
        var phyTx: Int = BluetoothDevice.PHY_LE_1M,
        var phyRx: Int = BluetoothDevice.PHY_LE_1M,
        val connectedTime: Long = System.currentTimeMillis(),
        val notifiedCharacteristics: MutableSet<UUID> = mutableSetOf()
    )
    
    /**
     * 서비스 빌더
     */
    data class ServiceBuilder(
        val uuid: UUID,
        val type: Int = BluetoothGattService.SERVICE_TYPE_PRIMARY,
        val characteristics: MutableList<CharacteristicBuilder> = mutableListOf()
    ) {
        fun addCharacteristic(characteristic: CharacteristicBuilder): ServiceBuilder {
            characteristics.add(characteristic)
            return this
        }
    }
    
    /**
     * 특성 빌더
     */
    data class CharacteristicBuilder(
        val uuid: UUID,
        val properties: Int,
        val permissions: Int,
        val initialValue: ByteArray? = null,
        val descriptors: MutableList<DescriptorBuilder> = mutableListOf()
    ) {
        fun addDescriptor(descriptor: DescriptorBuilder): CharacteristicBuilder {
            descriptors.add(descriptor)
            return this
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CharacteristicBuilder
            return uuid == other.uuid &&
                    properties == other.properties &&
                    permissions == other.permissions &&
                    initialValue?.contentEquals(other.initialValue) == true
        }
        
        override fun hashCode(): Int {
            var result = uuid.hashCode()
            result = 31 * result + properties
            result = 31 * result + permissions
            result = 31 * result + (initialValue?.contentHashCode() ?: 0)
            return result
        }
    }
    
    /**
     * 디스크립터 빌더
     */
    data class DescriptorBuilder(
        val uuid: UUID,
        val permissions: Int,
        val initialValue: ByteArray? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as DescriptorBuilder
            return uuid == other.uuid &&
                    permissions == other.permissions &&
                    initialValue?.contentEquals(other.initialValue) == true
        }
        
        override fun hashCode(): Int {
            var result = uuid.hashCode()
            result = 31 * result + permissions
            result = 31 * result + (initialValue?.contentHashCode() ?: 0)
            return result
        }
    }
    
    /**
     * GATT 서버 이벤트 리스너
     */
    interface GattServerListener {
        fun onServerStarted()
        fun onServerStopped()
        fun onServerError(error: BleServiceError)
        fun onClientConnected(client: ConnectedClient)
        fun onClientDisconnected(client: ConnectedClient)
        fun onCharacteristicReadRequest(client: ConnectedClient, characteristic: BluetoothGattCharacteristic, requestId: Int, offset: Int): ByteArray?
        fun onCharacteristicWriteRequest(client: ConnectedClient, characteristic: BluetoothGattCharacteristic, value: ByteArray, requestId: Int, offset: Int, responseNeeded: Boolean): Boolean
        fun onDescriptorReadRequest(client: ConnectedClient, descriptor: BluetoothGattDescriptor, requestId: Int, offset: Int): ByteArray?
        fun onDescriptorWriteRequest(client: ConnectedClient, descriptor: BluetoothGattDescriptor, value: ByteArray, requestId: Int, offset: Int, responseNeeded: Boolean): Boolean
        fun onNotificationSent(client: ConnectedClient, characteristic: BluetoothGattCharacteristic, status: Int)
        fun onMtuChanged(client: ConnectedClient, mtu: Int)
        fun onPhyChanged(client: ConnectedClient, txPhy: Int, rxPhy: Int)
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGattServer: BluetoothGattServer? = null
    
    // 상태 관리
    private val _gattServerState = MutableStateFlow(GattServerState.IDLE)
    val gattServerState: StateFlow<GattServerState> = _gattServerState.asStateFlow()
    
    private val _connectedClients = MutableStateFlow<Map<String, ConnectedClient>>(emptyMap())
    val connectedClients: StateFlow<Map<String, ConnectedClient>> = _connectedClients.asStateFlow()
    
    private val connectedClientsInternal = ConcurrentHashMap<String, ConnectedClient>()
    private val listeners = CopyOnWriteArrayList<GattServerListener>()
    private val registeredServices = mutableListOf<BluetoothGattService>()
    
    // 리소스 ID
    private var gattServerResourceId: String? = null
    
    // 상수 정의
    companion object {
        // 잘 알려진 디스크립터 UUID들
        val CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC_USER_DESCRIPTION_UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb")
        
        // 디스크립터 값들
        val DISABLE_NOTIFICATION_VALUE = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        val ENABLE_NOTIFICATION_VALUE = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val ENABLE_INDICATION_VALUE = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
    }
    
    init {
        BleDebugLogger.logSystemState("BleGattServerController initialized")
    }
    
    /**
     * 서버 이벤트 리스너 등록
     */
    fun addGattServerListener(listener: GattServerListener) {
        listeners.addIfAbsent(listener)
    }
    
    /**
     * 서버 이벤트 리스너 해제
     */
    fun removeGattServerListener(listener: GattServerListener) {
        listeners.remove(listener)
    }
    
    /**
     * 모든 리스너 해제
     */
    fun clearGattServerListeners() {
        listeners.clear()
    }
    
    /**
     * GATT 서버 시작
     */
    fun startServer(): Result<Unit> {
        return try {
            BleDebugLogger.logSystemState("Starting GATT server")
            
            // 전제 조건 검사
            val permissionResult = checkPermissions("GATT_SERVER")
            if (permissionResult is Result.Failure) {
                return permissionResult
            }
            
            val systemResult = checkSystemState()
            if (systemResult is Result.Failure) {
                return systemResult
            }
            
            // 이미 실행 중이면 중지 후 새로 시작
            if (_gattServerState.value == GattServerState.RUNNING) {
                val stopResult = stopServer()
                if (stopResult is Result.Failure) {
                    return stopResult
                }
            }
            
            _gattServerState.value = GattServerState.STARTING
            
            // GATT 서버 생성
            val gattServerCallback = createGattServerCallback()
            bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            
            if (bluetoothGattServer == null) {
                val error = BleServiceError.GattServerError.ServerCreationFailed("Failed to create GATT server")
                BleDebugLogger.logError(error)
                _gattServerState.value = GattServerState.ERROR
                return Result.Failure(error)
            }
            
            // 리소스 등록
            gattServerResourceId = resourceManager.registerResource(
                GattServerResource(bluetoothGattServer!!),
                BleResourceManager.ResourceType.GATT_SERVER
            )
            
            _gattServerState.value = GattServerState.RUNNING
            notifyServerStarted()
            
            Result.Success(Unit)
            
        } catch (e: Exception) {
            val error = BleServiceError.GattServerError.ServerCreationFailed("Failed to start GATT server", e)
            BleDebugLogger.logException(e, "Start GATT server failed")
            _gattServerState.value = GattServerState.ERROR
            notifyServerError(error)
            Result.Failure(error)
        }
    }
    
    /**
     * GATT 서버 중지
     */
    fun stopServer(): Result<Unit> {
        return try {
            if (_gattServerState.value == GattServerState.IDLE) {
                return Result.Success(Unit)
            }
            
            _gattServerState.value = GattServerState.STOPPING
            
            // 모든 클라이언트 연결 해제
            disconnectAllClients()
            
            // GATT 서버 정리
            bluetoothGattServer?.let { server ->
                server.clearServices()
                server.close()
            }
            bluetoothGattServer = null
            
            // 리소스 해제
            gattServerResourceId?.let { resourceId ->
                resourceManager.disposeResource(resourceId)
                gattServerResourceId = null
            }
            
            // 등록된 서비스 정리
            registeredServices.clear()
            
            _gattServerState.value = GattServerState.IDLE
            notifyServerStopped()
            
            BleDebugLogger.logSystemState("GATT server stopped")
            
            Result.Success(Unit)
            
        } catch (e: Exception) {
            val error = BleServiceError.GattServerError.ServerStopFailed("Failed to stop GATT server", e)
            BleDebugLogger.logException(e, "Stop GATT server failed")
            Result.Failure(error)
        }
    }
    
    /**
     * 서비스 추가
     */
    fun addService(serviceBuilder: ServiceBuilder): Result<Unit> {
        return try {
            val server = bluetoothGattServer
                ?: return Result.Failure(BleServiceError.GattServerError.ServerNotRunning("GATT server not running"))
            
            val service = buildGattService(serviceBuilder)
            val success = server.addService(service)
            
            if (success) {
                registeredServices.add(service)
                BleDebugLogger.logSystemState("Added GATT service: ${serviceBuilder.uuid}")
                Result.Success(Unit)
            } else {
                val error = BleServiceError.GattServerError.ServiceAddFailed("Failed to add service: ${serviceBuilder.uuid}")
                BleDebugLogger.logError(error)
                Result.Failure(error)
            }
            
        } catch (e: Exception) {
            val error = BleServiceError.GattServerError.ServiceAddFailed("Failed to add service", e)
            BleDebugLogger.logException(e, "Add service failed")
            Result.Failure(error)
        }
    }
    
    /**
     * 서비스 제거
     */
    fun removeService(serviceUuid: UUID): Result<Unit> {
        return try {
            val server = bluetoothGattServer
                ?: return Result.Failure(BleServiceError.GattServerError.ServerNotRunning("GATT server not running"))
            
            val service = registeredServices.find { it.uuid == serviceUuid }
            if (service != null) {
                val success = server.removeService(service)
                if (success) {
                    registeredServices.remove(service)
                    BleDebugLogger.logSystemState("Removed GATT service: $serviceUuid")
                    Result.Success(Unit)
                } else {
                    val error = BleServiceError.GattServerError.ServiceRemoveFailed("Failed to remove service: $serviceUuid")
                    Result.Failure(error)
                }
            } else {
                Result.Success(Unit) // 서비스가 존재하지 않음
            }
            
        } catch (e: Exception) {
            val error = BleServiceError.GattServerError.ServiceRemoveFailed("Failed to remove service", e)
            BleDebugLogger.logException(e, "Remove service failed")
            Result.Failure(error)
        }
    }
    
    /**
     * 특성 알림 전송
     */
    fun notifyCharacteristicChanged(
        clientAddress: String,
        serviceUuid: UUID,
        characteristicUuid: UUID,
        data: ByteArray,
        confirm: Boolean = false
    ): Result<Unit> {
        return try {
            val server = bluetoothGattServer
                ?: return Result.Failure(BleServiceError.GattServerError.ServerNotRunning("GATT server not running"))
            
            val client = connectedClientsInternal[clientAddress]
                ?: return Result.Failure(BleServiceError.GattServerError.ClientNotConnected("Client not connected"))
            
            val characteristic = findCharacteristic(serviceUuid, characteristicUuid)
                ?: return Result.Failure(BleServiceError.GattError.CharacteristicNotFound("Characteristic not found"))
            
            characteristic.value = data
            val success = server.notifyCharacteristicChanged(client.device, characteristic, confirm)
            
            if (success) {
                BleDebugLogger.logDataSent(clientAddress, characteristicUuid.toString(), data)
                Result.Success(Unit)
            } else {
                val error = BleServiceError.GattServerError.NotificationFailed("Failed to send notification")
                BleDebugLogger.logError(error, clientAddress)
                Result.Failure(error)
            }
            
        } catch (e: Exception) {
            val error = BleServiceError.GattServerError.NotificationFailed("Failed to send notification", e)
            BleDebugLogger.logException(e, "Send notification failed", clientAddress)
            Result.Failure(error)
        }
    }
    
    /**
     * 모든 연결된 클라이언트에게 알림 전송
     */
    fun notifyAllClients(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        data: ByteArray,
        confirm: Boolean = false
    ): Result<Int> {
        var successCount = 0
        var lastError: BleServiceError? = null
        
        connectedClientsInternal.values.forEach { client ->
            val result = notifyCharacteristicChanged(client.device.address, serviceUuid, characteristicUuid, data, confirm)
            if (result is Result.Success) {
                successCount++
            } else if (result is Result.Failure) {
                lastError = result.error
            }
        }
        
        return if (successCount > 0) {
            Result.Success(successCount)
        } else {
            Result.Failure(lastError ?: BleServiceError.GattServerError.NotificationFailed("No clients to notify"))
        }
    }
    
    /**
     * 클라이언트 연결 해제
     */
    fun disconnectClient(clientAddress: String): Result<Unit> {
        return try {
            val server = bluetoothGattServer
                ?: return Result.Failure(BleServiceError.GattServerError.ServerNotRunning("GATT server not running"))
            
            val client = connectedClientsInternal[clientAddress]
                ?: return Result.Success(Unit) // 이미 연결되어 있지 않음
            
            server.cancelConnection(client.device)
            
            Result.Success(Unit)
            
        } catch (e: Exception) {
            val error = BleServiceError.GattServerError.ClientDisconnectFailed("Failed to disconnect client", e)
            BleDebugLogger.logException(e, "Disconnect client failed", clientAddress)
            Result.Failure(error)
        }
    }
    
    /**
     * 모든 클라이언트 연결 해제
     */
    fun disconnectAllClients(): Result<Unit> {
        val clients = connectedClientsInternal.keys.toList()
        var lastError: BleServiceError? = null
        
        clients.forEach { clientAddress ->
            val result = disconnectClient(clientAddress)
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
     * 연결된 클라이언트 목록 반환
     */
    fun getConnectedClientsList(): List<ConnectedClient> {
        return connectedClientsInternal.values.toList()
    }
    
    /**
     * 특정 클라이언트 정보 반환
     */
    fun getClientInfo(clientAddress: String): ConnectedClient? {
        return connectedClientsInternal[clientAddress]
    }
    
    /**
     * 서버 실행 중인지 확인
     */
    fun isServerRunning(): Boolean {
        return _gattServerState.value == GattServerState.RUNNING
    }
    
    /**
     * 등록된 서비스 목록 반환
     */
    fun getRegisteredServices(): List<BluetoothGattService> {
        return registeredServices.toList()
    }
    
    /**
     * GATT 서비스 빌드
     */
    private fun buildGattService(serviceBuilder: ServiceBuilder): BluetoothGattService {
        val service = BluetoothGattService(serviceBuilder.uuid, serviceBuilder.type)
        
        serviceBuilder.characteristics.forEach { charBuilder ->
            val characteristic = BluetoothGattCharacteristic(
                charBuilder.uuid,
                charBuilder.properties,
                charBuilder.permissions
            )
            
            charBuilder.initialValue?.let { value ->
                characteristic.value = value
            }
            
            charBuilder.descriptors.forEach { descBuilder ->
                val descriptor = BluetoothGattDescriptor(
                    descBuilder.uuid,
                    descBuilder.permissions
                )
                
                descBuilder.initialValue?.let { value ->
                    descriptor.value = value
                }
                
                characteristic.addDescriptor(descriptor)
            }
            
            service.addCharacteristic(characteristic)
        }
        
        return service
    }
    
    /**
     * 특성 찾기
     */
    private fun findCharacteristic(serviceUuid: UUID, characteristicUuid: UUID): BluetoothGattCharacteristic? {
        return registeredServices
            .find { it.uuid == serviceUuid }
            ?.getCharacteristic(characteristicUuid)
    }
    
    /**
     * GATT 서버 콜백 생성
     */
    private fun createGattServerCallback(): BluetoothGattServerCallback {
        return object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                handleConnectionStateChange(device, status, newState)
            }
            
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                handleCharacteristicReadRequest(device, requestId, offset, characteristic)
            }
            
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                handleCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            }
            
            override fun onDescriptorReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                descriptor: BluetoothGattDescriptor
            ) {
                handleDescriptorReadRequest(device, requestId, offset, descriptor)
            }
            
            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                handleDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            }
            
            override fun onNotificationSent(device: BluetoothDevice, status: Int) {
                handleNotificationSent(device, status)
            }
            
            override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                handleMtuChanged(device, mtu)
            }
            
            override fun onPhyUpdate(device: BluetoothDevice, txPhy: Int, rxPhy: Int, status: Int) {
                handlePhyUpdate(device, txPhy, rxPhy, status)
            }
        }
    }
    
    // 콜백 처리 메서드들
    private fun handleConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val bleDevice = BleDevice.fromBluetoothDevice(device)
                    val client = ConnectedClient(device, bleDevice, device.hashCode())
                    
                    connectedClientsInternal[device.address] = client
                    updateConnectedClientsStateFlow()
                    
                    BleDebugLogger.logConnectionSuccess(device.address, device.name)
                    notifyClientConnected(client)
                }
            }
            
            BluetoothProfile.STATE_DISCONNECTED -> {
                val client = connectedClientsInternal.remove(device.address)
                if (client != null) {
                    updateConnectedClientsStateFlow()
                    BleDebugLogger.logDisconnection(device.address, status, "Client disconnected")
                    notifyClientDisconnected(client)
                }
            }
        }
    }
    
    private fun handleCharacteristicReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        characteristic: BluetoothGattCharacteristic
    ) {
        val client = connectedClientsInternal[device.address] ?: return
        val server = bluetoothGattServer ?: return
        
        try {
            val data = listeners.firstNotNullOfOrNull { listener ->
                listener.onCharacteristicReadRequest(client, characteristic, requestId, offset)
            }
            
            if (data != null) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data)
                BleDebugLogger.logCharacteristicRead(
                    device.address,
                    characteristic.service.uuid.toString(),
                    characteristic.uuid.toString(),
                    true,
                    data.size
                )
            } else {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
            }
        } catch (e: Exception) {
            BleDebugLogger.logException(e, "Handle characteristic read request failed", device.address)
            server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
        }
    }
    
    private fun handleCharacteristicWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray?
    ) {
        val client = connectedClientsInternal[device.address] ?: return
        val server = bluetoothGattServer ?: return
        val writeData = value ?: byteArrayOf()
        
        try {
            val success = listeners.any { listener ->
                listener.onCharacteristicWriteRequest(client, characteristic, writeData, requestId, offset, responseNeeded)
            }
            
            if (responseNeeded) {
                val status = if (success) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_WRITE_NOT_PERMITTED
                server.sendResponse(device, requestId, status, offset, null)
            }
            
            if (success) {
                BleDebugLogger.logCharacteristicWrite(
                    device.address,
                    characteristic.service.uuid.toString(),
                    characteristic.uuid.toString(),
                    true,
                    writeData.size
                )
                BleDebugLogger.logDataReceived(device.address, characteristic.uuid.toString(), writeData)
            }
        } catch (e: Exception) {
            BleDebugLogger.logException(e, "Handle characteristic write request failed", device.address)
            if (responseNeeded) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            }
        }
    }
    
    private fun handleDescriptorReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int,
        descriptor: BluetoothGattDescriptor
    ) {
        val client = connectedClientsInternal[device.address] ?: return
        val server = bluetoothGattServer ?: return
        
        try {
            val data = listeners.firstNotNullOfOrNull { listener ->
                listener.onDescriptorReadRequest(client, descriptor, requestId, offset)
            } ?: descriptor.value
            
            server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data)
        } catch (e: Exception) {
            BleDebugLogger.logException(e, "Handle descriptor read request failed", device.address)
            server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
        }
    }
    
    private fun handleDescriptorWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        descriptor: BluetoothGattDescriptor,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray?
    ) {
        val client = connectedClientsInternal[device.address] ?: return
        val server = bluetoothGattServer ?: return
        val writeData = value ?: byteArrayOf()
        
        try {
            val success = listeners.any { listener ->
                listener.onDescriptorWriteRequest(client, descriptor, writeData, requestId, offset, responseNeeded)
            }
            
            if (responseNeeded) {
                val status = if (success) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_WRITE_NOT_PERMITTED
                server.sendResponse(device, requestId, status, offset, null)
            }
            
            // CCCD 처리
            if (descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                val characteristic = descriptor.characteristic
                if (writeData.contentEquals(ENABLE_NOTIFICATION_VALUE) || 
                    writeData.contentEquals(ENABLE_INDICATION_VALUE)) {
                    client.notifiedCharacteristics.add(characteristic.uuid)
                } else {
                    client.notifiedCharacteristics.remove(characteristic.uuid)
                }
            }
            
        } catch (e: Exception) {
            BleDebugLogger.logException(e, "Handle descriptor write request failed", device.address)
            if (responseNeeded) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            }
        }
    }
    
    private fun handleNotificationSent(device: BluetoothDevice, status: Int) {
        val client = connectedClientsInternal[device.address] ?: return
        // 알림 전송 완료 처리 (필요시 리스너에게 알림)
    }
    
    private fun handleMtuChanged(device: BluetoothDevice, mtu: Int) {
        val client = connectedClientsInternal[device.address] ?: return
        client.mtuSize = mtu
        
        BleDebugLogger.logMtuChange(device.address, mtu, BluetoothGatt.GATT_SUCCESS)
        
        listeners.forEach { listener ->
            try {
                listener.onMtuChanged(client, mtu)
            } catch (e: Exception) {
                BleDebugLogger.logException(e, "Notify MTU changed failed", device.address)
            }
        }
    }
    
    private fun handlePhyUpdate(device: BluetoothDevice, txPhy: Int, rxPhy: Int, status: Int) {
        val client = connectedClientsInternal[device.address] ?: return
        
        if (status == BluetoothGatt.GATT_SUCCESS) {
            client.phyTx = txPhy
            client.phyRx = rxPhy
            
            listeners.forEach { listener ->
                try {
                    listener.onPhyChanged(client, txPhy, rxPhy)
                } catch (e: Exception) {
                    BleDebugLogger.logException(e, "Notify PHY changed failed", device.address)
                }
            }
        }
    }
    
    /**
     * 연결된 클라이언트 상태 플로우 업데이트
     */
    private fun updateConnectedClientsStateFlow() {
        _connectedClients.value = connectedClientsInternal.toMap()
    }
    
    // 리스너 알림 메서드들
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
    
    /**
     * 상태 정보 반환
     */
    fun getStatusInfo(): String {
        return buildString {
            appendLine("=== BLE GATT Server Controller Status ===")
            appendLine("Server State: ${_gattServerState.value}")
            appendLine("Server Running: ${isServerRunning()}")
            appendLine("Connected Clients: ${connectedClientsInternal.size}")
            appendLine("Registered Services: ${registeredServices.size}")
            appendLine("Event Listeners: ${listeners.size}")
            
            if (connectedClientsInternal.isNotEmpty()) {
                appendLine("Clients:")
                connectedClientsInternal.values.forEach { client ->
                    appendLine("  - ${client.device.address} (MTU: ${client.mtuSize}, Connected: ${System.currentTimeMillis() - client.connectedTime}ms ago)")
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // GATT 서버 중지
        stopServer()
        
        // 리스너 정리
        clearGattServerListeners()
        
        BleDebugLogger.logSystemState("BleGattServerController destroyed")
    }
    
    /**
     * GATT 서버 리소스 래퍼
     */
    private class GattServerResource(private val server: BluetoothGattServer) : BleResource {
        override fun cleanup() {
            try {
                server.clearServices()
                server.close()
            } catch (e: Exception) {
                // 정리 중 오류 무시
            }
        }
    }
}