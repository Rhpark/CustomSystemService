package kr.open.library.systemmanager.controller.bluetooth.connector

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.base.BleComponent
import kr.open.library.systemmanager.controller.bluetooth.base.BleConstants
import kr.open.library.systemmanager.controller.bluetooth.data.BinaryProtocol
import java.util.*

/**
 * BLE 연결 관리자 - Central/Peripheral 공통 사용
 * 타임아웃/재시도는 Coroutine, 단순 상태는 Atomic 변수 사용
 * MTU 512 협상 및 TLV 메시지 송수신 담당
 */
class BleConnector(context: Context) : BleComponent(context) {
    
    // 콜백 인터페이스
    interface ConnectionListener {
        fun onConnected(deviceAddress: String)
        fun onDisconnected(deviceAddress: String)
        fun onConnectionError(deviceAddress: String, error: String)
        fun onMtuChanged(deviceAddress: String, mtu: Int)
        fun onMessageReceived(deviceAddress: String, type: Byte, data: ByteArray)
    }
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothManager: BluetoothManager? = null
    private var currentListener: ConnectionListener? = null
    
    // 단순한 상태 관리 (Flow 대신 Atomic 변수)
    @Volatile
    private var connectedDevice: String? = null
    
    private val isConnecting = AtomicBoolean(false)
    private val currentMtu = AtomicInteger(BleConstants.DEFAULT_MTU)
    
    // GATT 클라이언트 (Central 모드에서 사용)
    private var bluetoothGatt: BluetoothGatt? = null
    
    // GATT 서버 (Peripheral 모드에서 사용)
    private var gattServer: BluetoothGattServer? = null
    
    // 특성 캐시
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    
    // 메시지 큐 (MTU 협상 완료 대기)
    private val pendingMessages = mutableListOf<ByteArray>()
    private var mtuRequested = false
    
    // GATT 클라이언트 콜백 - 합리적 Coroutine 사용
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            Logx.d(TAG, "Connection state changed: ${BleConstants.getGattStatusString(status)}, newState: $newState")
            
            // Coroutine은 타임아웃이 필요한 작업에만 사용
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Logx.i(TAG, "Connected to $deviceAddress")
                        updateConnectionState(ConnectionState.CONNECTED)
                        connectedDevice = deviceAddress
                        
                        // 서비스 검색 시작 (타임아웃 필요)
                        Logx.d(TAG, "Starting service discovery...")
                        if (!gatt.discoverServices()) {
                            Logx.e(TAG, "Failed to start service discovery")
                            handleConnectionError(deviceAddress, "Service discovery failed to start")
                        }
                    } else {
                        val error = "Connection failed: ${BleConstants.getGattStatusString(status)}"
                        Logx.e(TAG, "Connection failed with status: ${BleConstants.getGattStatusString(status)}")
                        handleConnectionError(deviceAddress, error)
                    }
                }
                
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Logx.i(TAG, "Disconnected from $deviceAddress")
                    updateConnectionState(ConnectionState.DISCONNECTED)
                    connectedDevice = null
                    isConnecting.set(false)
                    mtuRequested = false
                    
                    // 리소스 정리
                    cleanupCharacteristics()
                    
                    currentListener?.onDisconnected(deviceAddress)
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val deviceAddress = gatt.device.address
            
            // 복잡한 설정 작업은 Coroutine 유지 (타임아웃 필요)
            componentScope.launch {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    val error = "Service discovery failed: ${BleConstants.getGattStatusString(status)}"
                    Logx.e(TAG, error)
                    handleConnectionError(deviceAddress, "Service discovery failed")
                    return@launch
                }
                
                Logx.d(TAG, "Services discovered for $deviceAddress")
                
                // Nordic UART Service 검색
                val service = gatt.getService(BleConstants.SERVICE_UUID)
                if (service == null) {
                    Logx.e(TAG, "Nordic UART Service not found")
                    handleConnectionError(deviceAddress, "Required service not found")
                    return@launch
                }
                
                // 특성 검색
                txCharacteristic = service.getCharacteristic(BleConstants.TX_CHAR_UUID)
                rxCharacteristic = service.getCharacteristic(BleConstants.RX_CHAR_UUID)
                
                if (txCharacteristic == null || rxCharacteristic == null) {
                    Logx.e(TAG, "Required characteristics not found")
                    handleConnectionError(deviceAddress, "Required characteristics not found")
                    return@launch
                }
                
                // TX 특성에 대한 알림 활성화
                val success = enableNotifications(gatt, txCharacteristic!!)
                if (!success) {
                    Logx.e(TAG, "Failed to enable notifications")
                    handleConnectionError(deviceAddress, "Failed to enable notifications")
                    return@launch
                }
                
                // MTU 협상 요청
                requestMtu(gatt)
                
                // 연결 완료 알림
                currentListener?.onConnected(deviceAddress)
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val deviceAddress = gatt.device.address
            val data = characteristic.value
            
            // 메시지 처리는 단순하므로 직접 처리
            if (data == null || data.isEmpty()) {
                Logx.w(TAG, "Received empty data from $deviceAddress")
                return
            }
            
            Logx.d(TAG, "Received ${data.size} bytes from $deviceAddress")
            
            // TLV 디코딩
            val tlvResult = BinaryProtocol.decode(data)
            if (tlvResult == null) {
                Logx.w(TAG, "Failed to decode TLV message from $deviceAddress")
                return
            }
            
            val (type, length, payload) = tlvResult
            Logx.d(TAG, "Decoded TLV: type=0x${type.toString(16)}, length=$length")
            
            // 직접 콜백 호출
            currentListener?.onMessageReceived(deviceAddress, type, payload)
        }
        
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val deviceAddress = gatt.device.address
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentMtu.set(mtu)
                Logx.i(TAG, "MTU changed to $mtu for $deviceAddress")
                
                currentListener?.onMtuChanged(deviceAddress, mtu)
                
                // 대기 중인 메시지 전송 (Coroutine 필요)
                componentScope.launch {
                    processPendingMessages()
                }
            } else {
                Logx.w(TAG, "MTU change failed: ${BleConstants.getGattStatusString(status)}")
            }
            
            mtuRequested = false
        }
        
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val deviceAddress = gatt.device.address
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Logx.d(TAG, "Message sent successfully to $deviceAddress")
            } else {
                Logx.e(TAG, "Message send failed: ${BleConstants.getGattStatusString(status)}")
            }
        }
    }
    
    override suspend fun initialize(): Boolean {
        Logx.d(TAG, "Initializing BleConnector...")
        
        if (!checkAllRequiredPermissions()) {
            Logx.e(TAG, "Required permissions not granted")
            return false
        }
        
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager == null) {
            Logx.e(TAG, "BluetoothManager not available")
            return false
        }
        
        bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null) {
            Logx.e(TAG, "BluetoothAdapter not available")
            return false
        }
        
        if (bluetoothAdapter?.isEnabled != true) {
            Logx.e(TAG, "Bluetooth is disabled")
            return false
        }
        
        Logx.i(TAG, "BleConnector initialized successfully")
        return true
    }
    
    /**
     * 컴포넌트 준비 상태 확인 (isReady() 충돌 해결)
     */
    private fun isComponentReady(): Boolean {
        return bluetoothAdapter?.isEnabled == true && checkAllRequiredPermissions()
    }

    // 🔧 호환성: getReadyState() 메서드 오버라이드
    override fun getReadyState(): Boolean = isComponentReady()
    
    internal override suspend fun cleanupGattResources() {
        Logx.d(TAG, "Cleaning up BleConnector GATT resources...")
        
        try {
            // GATT 클라이언트 정리
            bluetoothGatt?.let { gatt ->
                try {
                    gatt.disconnect()
                    gatt.close()
                    Logx.d(TAG, "GATT client closed successfully")
                } catch (e: Exception) {
                    Logx.w(TAG, "Exception closing GATT client: ${e.message}")
                }
            }
            bluetoothGatt = null
            
            // GATT 서버 정리 (Peripheral 모드에서 사용)
            gattServer?.let { server ->
                try {
                    server.clearServices()
                    server.close()
                    Logx.d(TAG, "GATT server closed successfully")
                } catch (e: Exception) {
                    Logx.w(TAG, "Exception closing GATT server: ${e.message}")
                }
            }
            gattServer = null
            
            cleanupCharacteristics()
            
        } catch (e: SecurityException) {
            Logx.w(TAG, "SecurityException during GATT cleanup: ${e.message}")
        } catch (e: Exception) {
            Logx.w(TAG, "Exception during GATT cleanup: ${e.message}")
        }
    }
    
    override suspend fun cleanup() {
        Logx.d(TAG, "Cleaning up BleConnector...")
        
        connectedDevice?.let { disconnect(it) }
        cleanupGattResources()
        currentListener = null
        pendingMessages.clear()
        
        super.cleanup()
    }
    
    /**
     * 디바이스에 연결 (Central 모드) - suspend 함수로 현대화
     */
    suspend fun connect(deviceAddress: String): Boolean {
        return try {
            synchronized(stateLock) {
                // 1:1 연결 확인
                val current = connectedDevice
                if (current != null && current != deviceAddress) {
                    Logx.w(TAG, "Already connected to $current. Cannot connect to $deviceAddress")
                    return false
                }
                
                if (current == deviceAddress && connectionState == ConnectionState.CONNECTED) {
                    Logx.d(TAG, "Already connected to $deviceAddress")
                    return true
                }
                
                if (isConnecting.get()) {
                    Logx.w(TAG, "Connection already in progress")
                    return false
                }
                
                if (!isComponentReady()) {
                    Logx.e(TAG, "Connector not ready")
                    return false
                }
                
                Logx.d(TAG, "Connecting to $deviceAddress...")
                
                // 기존 GATT 리소스 정리
                // cleanupGattResources() // synchronized 내에서 suspend 함수 호출 불가
                
                try {
                    val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                    if (device == null) {
                        Logx.e(TAG, "Failed to get remote device for $deviceAddress")
                        return false
                    }
                    
                    isConnecting.set(true)
                    updateConnectionState(ConnectionState.CONNECTING)
                    connectedDevice = deviceAddress
                    
                    bluetoothGatt = device.connectGatt(context, false, gattCallback)
                    if (bluetoothGatt == null) {
                        Logx.e(TAG, "Failed to create GATT connection")
                        isConnecting.set(false)
                        updateConnectionState(ConnectionState.DISCONNECTED)
                        connectedDevice = null
                        return false
                    }
                    
                    Logx.d(TAG, "GATT connection initiated")
                    return true
                    
                } catch (e: SecurityException) {
                    Logx.e(TAG, "Security exception during connection: ${e.message}")
                    handleConnectionError(deviceAddress, "Permission denied: ${e.message}")
                    return false
                } catch (e: Exception) {
                    Logx.e(TAG, "Unexpected error during connection: ${e.message}")
                    handleConnectionError(deviceAddress, "Connection failed: ${e.message}")
                    return false
                }
            }
        } catch (e: Exception) {
            Logx.e(TAG, "Error in connect: ${e.message}")
            false
        }
    }
    
    /**
     * 디바이스 연결 해제 - suspend 함수로 현대화
     */
    suspend fun disconnect(deviceAddress: String) {
        synchronized(stateLock) {
            if (connectedDevice != deviceAddress) {
                Logx.w(TAG, "No connection to $deviceAddress")
                return
            }
            
            Logx.d(TAG, "Disconnecting from $deviceAddress...")
            
            updateConnectionState(ConnectionState.DISCONNECTING)
            
            try {
                bluetoothGatt?.disconnect()
            } catch (e: Exception) {
                Logx.w(TAG, "Exception during disconnect: ${e.message}")
            } finally {
                // GATT 리소스는 onConnectionStateChange에서 정리됨
            }
        }
    }
    
    /**
     * 메시지 전송 - suspend 함수로 현대화
     */
    suspend fun sendMessage(deviceAddress: String, type: Byte, data: ByteArray): Boolean {
        return try {
            synchronized(stateLock) {
                if (connectedDevice != deviceAddress) {
                    Logx.w(TAG, "No connection to $deviceAddress")
                    return false
                }
                
                if (connectionState != ConnectionState.CONNECTED) {
                    Logx.w(TAG, "Not connected to $deviceAddress")
                    return false
                }
                
                try {
                    val tlvMessage = BinaryProtocol.encode(type, data)
                    
                    // 메시지 크기 확인
                    val maxSize = currentMtu.get() - 3 // ATT 헤더 3바이트 제외
                    if (tlvMessage.size > maxSize) {
                        Logx.e(TAG, "Message too large: ${tlvMessage.size} > $maxSize")
                        return false
                    }
                    
                    Logx.d(TAG, "Sending TLV message: type=0x${type.toString(16)}, size=${tlvMessage.size}")
                    
                    // MTU 협상 대기 중이면 메시지를 큐에 저장
                    if (mtuRequested) {
                        Logx.d(TAG, "MTU negotiation in progress, queuing message")
                        pendingMessages.add(tlvMessage)
                        return true
                    }
                    
                    return sendMessageInternal(tlvMessage)
                    
                } catch (e: Exception) {
                    Logx.e(TAG, "Error encoding/sending message: ${e.message}")
                    return false
                }
            }
        } catch (e: Exception) {
            Logx.e(TAG, "Error in sendMessage: ${e.message}")
            false
        }
    }
    
    /**
     * MTU 협상 요청 - 현대화
     */
    suspend fun requestMtu(deviceAddress: String, mtu: Int = BleConstants.TARGET_MTU) {
        if (connectedDevice != deviceAddress) {
            Logx.w(TAG, "No connection to $deviceAddress for MTU request")
            return
        }
        
        bluetoothGatt?.let { requestMtu(it) }
    }
    
    /**
     * 현재 연결된 디바이스 - Flow 기반
     */
    fun getConnectedDevice(): String? = connectedDevice
    
    /**
     * 연결 상태 조회 - 단순화
     */
    fun getCurrentConnectionState(): ConnectionState = connectionState
    
    /**
     * 연결 여부 - Flow 기반
     */
    fun isConnected(): Boolean = connectionState == ConnectionState.CONNECTED && connectedDevice != null
    
    /**
     * 리스너 설정
     */
    fun setListener(listener: ConnectionListener) {
        this.currentListener = listener
    }
    
    // ===== 내부 메서드들 =====
    
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        // 알림 활성화
        val enabled = gatt.setCharacteristicNotification(characteristic, true)
        if (!enabled) {
            Logx.e(TAG, "Failed to enable characteristic notification")
            return false
        }
        
        // CCCD 디스크립터 설정
        val descriptor = characteristic.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG)
        if (descriptor == null) {
            Logx.e(TAG, "CCCD descriptor not found")
            return false
        }
        
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val success = gatt.writeDescriptor(descriptor)
        if (!success) {
            Logx.e(TAG, "Failed to write CCCD descriptor")
            return false
        }
        
        Logx.d(TAG, "Notifications enabled for TX characteristic")
        return true
    }
    
    private fun requestMtu(gatt: BluetoothGatt, mtu: Int = BleConstants.TARGET_MTU) {
        if (mtuRequested) {
            Logx.d(TAG, "MTU request already in progress")
            return
        }
        
        mtuRequested = true
        Logx.d(TAG, "Requesting MTU: $mtu")
        
        val success = gatt.requestMtu(mtu)
        if (!success) {
            Logx.w(TAG, "Failed to request MTU")
            mtuRequested = false
        }
    }
    
    private fun sendMessageInternal(message: ByteArray): Boolean {
        val characteristic = rxCharacteristic
        if (characteristic == null) {
            Logx.e(TAG, "RX characteristic not available")
            return false
        }
        
        characteristic.value = message
        val success = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
        
        if (!success) {
            Logx.e(TAG, "Failed to write characteristic")
        }
        
        return success
    }
    
    private suspend fun processPendingMessages() {
        synchronized(stateLock) {
            if (pendingMessages.isNotEmpty()) {
                Logx.d(TAG, "Processing ${pendingMessages.size} pending messages")
                
                val messages = pendingMessages.toList()
                pendingMessages.clear()
                
                for (message in messages) {
                    if (!sendMessageInternal(message)) {
                        Logx.e(TAG, "Failed to send pending message")
                        break
                    }
                    
                    // 메시지 간 간격 (단순화)
                    Thread.sleep(50)
                }
            }
        }
    }
    
    private fun handleConnectionError(deviceAddress: String, error: String) {
        Logx.e(TAG, "Connection error with $deviceAddress: $error")
        
        isConnecting.set(false)
        updateConnectionState(ConnectionState.ERROR)
        connectedDevice = null
        
        // 직접 콜백으로 오류 처리
        // _connectionErrors.emit(Pair(deviceAddress, error)) // Flow 제거
        
        // GATT 리소스 정리
        // 전역 스코프에서 GATT 정리 실행
        componentScope.launch {
            try {
                cleanupGattResources()
            } catch (e: Exception) {
                Logx.w(TAG, "Error during GATT cleanup: ${e.message}")
            }
        }
        
        // Legacy 콜백 지원 (단순화)
        currentListener?.onConnectionError(deviceAddress, error)
    }
    
    private fun cleanupCharacteristics() {
        txCharacteristic = null
        rxCharacteristic = null
        currentMtu.set(BleConstants.DEFAULT_MTU)
        pendingMessages.clear()
    }
    
}