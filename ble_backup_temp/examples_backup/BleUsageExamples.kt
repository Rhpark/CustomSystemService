package kr.open.library.systemmanager.controller.bluetooth.examples

import android.content.Context
import android.os.ParcelUuid
import kr.open.library.systemmanager.controller.bluetooth.BleMasterController
import kr.open.library.systemmanager.controller.bluetooth.central.BleCentralManager
import kr.open.library.systemmanager.controller.bluetooth.central.BleConnectionController
import kr.open.library.systemmanager.controller.bluetooth.central.BleScanController
import kr.open.library.systemmanager.controller.bluetooth.data.BleDevice
import kr.open.library.systemmanager.controller.bluetooth.error.BleServiceError
import kr.open.library.systemmanager.controller.bluetooth.base.Result
import kr.open.library.systemmanager.controller.bluetooth.peripheral.BleAdvertisingController
import kr.open.library.systemmanager.controller.bluetooth.peripheral.BleGattServerController
import kr.open.library.systemmanager.controller.bluetooth.peripheral.BlePeripheralManager
import java.util.UUID

/**
 * BLE 사용법 예제 모음
 * BLE Usage Examples Collection
 * 
 * 이 클래스는 BLE 시스템의 다양한 사용 시나리오를 보여주는 예제들을 포함합니다.
 * This class contains examples showing various usage scenarios of the BLE system.
 */
object BleUsageExamples {
    
    // 표준 BLE UUID들
    private val DEVICE_INFORMATION_SERVICE_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    private val DEVICE_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    
    // 사용자 정의 UUID들 (예제용)
    private val CUSTOM_SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    private val CUSTOM_DATA_CHARACTERISTIC_UUID = UUID.fromString("87654321-4321-4321-4321-cba987654321")
    private val CUSTOM_CONTROL_CHARACTERISTIC_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")
    
    /**
     * 예제 1: 기본적인 Central (클라이언트) 사용법
     * Example 1: Basic Central (Client) Usage
     */
    fun basicCentralExample(context: Context) {
        val centralManager = BleCentralManager.getInstance(context)
        
        // 1. 초기화
        centralManager.initialize()
        
        // 2. 이벤트 리스너 등록
        centralManager.addEventListener(object : BleCentralManager.CentralEventListener {
            override fun onDeviceFound(device: BleDevice) {
                println("Found device: ${device.name} (${device.address})")
                
                // 특정 조건의 기기에 연결 시도
                if (device.name?.contains("MyDevice") == true) {
                    connectToDevice(centralManager, device)
                }
            }
            
            override fun onDeviceConnected(device: BleDevice) {
                println("Connected to: ${device.name}")
                
                // 연결 후 데이터 읽기
                readDeviceInformation(centralManager, device)
            }
            
            override fun onDataReceived(device: BleDevice, serviceUuid: String, characteristicUuid: String, data: ByteArray) {
                println("Received data from ${device.address}: ${String(data)}")
            }
        })
        
        // 3. 스캔 설정 및 시작
        val scanConfig = BleScanController.ScanConfig(
            scanMode = android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY,
            scanDuration = 10000L, // 10초
            filters = listOf(
                BleScanController.ScanFilterConfig(
                    serviceUuid = DEVICE_INFORMATION_SERVICE_UUID.toString()
                )
            )
        )
        
        val result = centralManager.startScan(scanConfig)
        when (result) {
            is Result.Success -> println("Scan started successfully")
            is Result.Failure -> println("Scan failed: ${result.error}")
        }
    }
    
    /**
     * 기기 연결 예제
     */
    private fun connectToDevice(centralManager: BleCentralManager, device: BleDevice) {
        val connectionConfig = BleConnectionController.ConnectionConfig(
            autoConnect = false,
            connectionTimeout = 10000L,
            enableNotifications = true,
            autoDiscoverServices = true
        )
        
        val result = centralManager.connectDevice(device, connectionConfig)
        when (result) {
            is Result.Success -> println("Connection initiated for ${device.address}")
            is Result.Failure -> println("Connection failed: ${result.error}")
        }
    }
    
    /**
     * 기기 정보 읽기 예제
     */
    private fun readDeviceInformation(centralManager: BleCentralManager, device: BleDevice) {
        // 기기 이름 읽기
        centralManager.readCharacteristic(
            device.address,
            DEVICE_INFORMATION_SERVICE_UUID.toString(),
            DEVICE_NAME_CHARACTERISTIC_UUID.toString()
        )
        
        // 배터리 레벨 알림 설정
        centralManager.setCharacteristicNotification(
            device.address,
            BATTERY_SERVICE_UUID.toString(),
            BATTERY_LEVEL_CHARACTERISTIC_UUID.toString(),
            true
        )
    }
    
    /**
     * 예제 2: 기본적인 Peripheral (서버) 사용법
     * Example 2: Basic Peripheral (Server) Usage
     */
    fun basicPeripheralExample(context: Context) {
        val peripheralManager = BlePeripheralManager.getInstance(context)
        
        // 1. 초기화
        peripheralManager.initialize()
        
        // 2. 이벤트 리스너 등록
        peripheralManager.addEventListener(object : BlePeripheralManager.PeripheralEventListener {
            override fun onClientConnected(client: BleGattServerController.ConnectedClient) {
                println("Client connected: ${client.device.address}")
            }
            
            override fun onDataReceived(
                client: BleGattServerController.ConnectedClient,
                serviceUuid: UUID,
                characteristicUuid: UUID,
                data: ByteArray
            ) {
                println("Received data from ${client.device.address}: ${String(data)}")
                
                // 에코 응답 전송
                val responseData = "Echo: ${String(data)}".toByteArray()
                peripheralManager.notifyClient(
                    client.device.address,
                    serviceUuid,
                    characteristicUuid,
                    responseData
                )
            }
            
            override fun onDataRequest(
                client: BleGattServerController.ConnectedClient,
                serviceUuid: UUID,
                characteristicUuid: UUID
            ): ByteArray? {
                return when (characteristicUuid) {
                    DEVICE_NAME_CHARACTERISTIC_UUID -> "MyPeripheralDevice".toByteArray()
                    BATTERY_LEVEL_CHARACTERISTIC_UUID -> byteArrayOf(85) // 85% 배터리
                    CUSTOM_DATA_CHARACTERISTIC_UUID -> "Hello from Peripheral".toByteArray()
                    else -> null
                }
            }
        })
        
        // 3. GATT 서비스 구성
        val customService = createCustomService()
        val batteryService = createBatteryService()
        
        // 4. Peripheral 설정 및 시작
        val peripheralConfig = BlePeripheralManager.PeripheralConfig(
            advertisingConfig = BleAdvertisingController.AdvertisingConfig(
                advertiseMode = android.bluetooth.le.AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY,
                connectable = true,
                includeDeviceName = true,
                localName = "MyBLEDevice",
                serviceUuids = listOf(
                    CUSTOM_SERVICE_UUID.toString(),
                    BATTERY_SERVICE_UUID.toString()
                )
            ),
            services = listOf(
                BlePeripheralManager.ServiceConfig(customService, true),
                BlePeripheralManager.ServiceConfig(batteryService, true)
            ),
            maxClients = 3
        )
        
        val result = peripheralManager.startPeripheral(peripheralConfig)
        when (result) {
            is Result.Success -> println("Peripheral started successfully")
            is Result.Failure -> println("Peripheral failed: ${result.error}")
        }
    }
    
    /**
     * 커스텀 GATT 서비스 생성
     */
    private fun createCustomService(): BleGattServerController.ServiceBuilder {
        return BleGattServerController.ServiceBuilder(
            uuid = CUSTOM_SERVICE_UUID,
            type = android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
        ).apply {
            // 데이터 특성 (읽기/쓰기/알림)
            addCharacteristic(
                BleGattServerController.CharacteristicBuilder(
                    uuid = CUSTOM_DATA_CHARACTERISTIC_UUID,
                    properties = android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ or
                            android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE or
                            android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    permissions = android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ or
                            android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE,
                    initialValue = "Initial Data".toByteArray()
                ).apply {
                    // 클라이언트 특성 설정 디스크립터 추가 (알림용)
                    addDescriptor(
                        BleGattServerController.DescriptorBuilder(
                            uuid = BleGattServerController.CLIENT_CHARACTERISTIC_CONFIG_UUID,
                            permissions = android.bluetooth.BluetoothGattDescriptor.PERMISSION_READ or
                                    android.bluetooth.BluetoothGattDescriptor.PERMISSION_WRITE,
                            initialValue = BleGattServerController.DISABLE_NOTIFICATION_VALUE
                        )
                    )
                }
            )
            
            // 제어 특성 (쓰기 전용)
            addCharacteristic(
                BleGattServerController.CharacteristicBuilder(
                    uuid = CUSTOM_CONTROL_CHARACTERISTIC_UUID,
                    properties = android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE,
                    permissions = android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE
                )
            )
        }
    }
    
    /**
     * 배터리 서비스 생성
     */
    private fun createBatteryService(): BleGattServerController.ServiceBuilder {
        return BleGattServerController.ServiceBuilder(
            uuid = BATTERY_SERVICE_UUID,
            type = android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
        ).apply {
            addCharacteristic(
                BleGattServerController.CharacteristicBuilder(
                    uuid = BATTERY_LEVEL_CHARACTERISTIC_UUID,
                    properties = android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ or
                            android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    permissions = android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ,
                    initialValue = byteArrayOf(100) // 100% 배터리
                ).apply {
                    addDescriptor(
                        BleGattServerController.DescriptorBuilder(
                            uuid = BleGattServerController.CLIENT_CHARACTERISTIC_CONFIG_UUID,
                            permissions = android.bluetooth.BluetoothGattDescriptor.PERMISSION_READ or
                                    android.bluetooth.BluetoothGattDescriptor.PERMISSION_WRITE,
                            initialValue = BleGattServerController.DISABLE_NOTIFICATION_VALUE
                        )
                    )
                }
            )
        }
    }
    
    /**
     * 예제 3: 듀얼 모드 (Central + Peripheral) 사용법
     * Example 3: Dual Mode (Central + Peripheral) Usage
     */
    fun dualModeExample(context: Context) {
        val masterController = BleMasterController.getInstance(context)
        
        // 1. 초기화
        val config = BleMasterController.MasterConfig(
            defaultRole = BleMasterController.BleRole.DUAL,
            enableDualMode = true,
            maxConnections = 5,
            enableDebugLogging = true
        )
        
        masterController.initialize(config)
        
        // 2. 통합 이벤트 리스너 등록
        masterController.addEventListener(object : BleMasterController.MasterEventListener {
            override fun onMasterStateChanged(state: BleMasterController.MasterState) {
                println("Master state changed: $state")
            }
            
            override fun onRoleChanged(newRole: BleMasterController.BleRole) {
                println("Role changed to: $newRole")
            }
            
            // Central 이벤트들
            override fun onDeviceFound(device: BleDevice) {
                println("Found device: ${device.name}")
            }
            
            override fun onDeviceConnected(device: BleDevice) {
                println("Device connected: ${device.name}")
            }
            
            override fun onCentralDataReceived(device: BleDevice, serviceUuid: String, characteristicUuid: String, data: ByteArray) {
                println("Central received data: ${String(data)}")
            }
            
            // Peripheral 이벤트들
            override fun onClientConnected(clientAddress: String, clientName: String?) {
                println("Client connected: $clientName ($clientAddress)")
            }
            
            override fun onPeripheralDataReceived(clientAddress: String, serviceUuid: UUID, characteristicUuid: UUID, data: ByteArray) {
                println("Peripheral received data: ${String(data)}")
            }
            
            override fun onPeripheralDataRequested(clientAddress: String, serviceUuid: UUID, characteristicUuid: UUID): ByteArray? {
                return when (characteristicUuid) {
                    CUSTOM_DATA_CHARACTERISTIC_UUID -> "Dual mode data".toByteArray()
                    else -> null
                }
            }
            
            override fun onError(error: BleServiceError, role: BleMasterController.BleRole?) {
                println("Error in $role: $error")
            }
        })
        
        // 3. 듀얼 모드 시작
        val scanConfig = BleScanController.ScanConfig(scanDuration = 30000L) // 30초 스캔
        val peripheralConfig = BlePeripheralManager.PeripheralConfig(
            advertisingConfig = BleAdvertisingController.AdvertisingConfig(
                localName = "DualModeDevice",
                serviceUuids = listOf(CUSTOM_SERVICE_UUID.toString())
            ),
            services = listOf(
                BlePeripheralManager.ServiceConfig(createCustomService(), true)
            )
        )
        
        val result = masterController.startAsDualMode(scanConfig, peripheralConfig)
        when (result) {
            is Result.Success -> println("Dual mode started successfully")
            is Result.Failure -> println("Dual mode failed: ${result.error}")
        }
        
        // 4. 5초 후 발견된 첫 번째 기기에 연결 시도
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val discoveredDevices = masterController.centralDevices.value.values.toList()
            if (discoveredDevices.isNotEmpty()) {
                val firstDevice = discoveredDevices.first()
                masterController.connectDevice(firstDevice)
                println("Attempting to connect to: ${firstDevice.name}")
            }
        }, 5000)
        
        // 5. 10초 후 연결된 모든 클라이언트에게 브로드캐스트 메시지 전송
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val message = "Broadcast from dual mode device".toByteArray()
            val result = masterController.notifyAllClients(
                CUSTOM_SERVICE_UUID,
                CUSTOM_DATA_CHARACTERISTIC_UUID,
                message
            )
            
            when (result) {
                is Result.Success -> println("Broadcast sent to ${result.data} clients")
                is Result.Failure -> println("Broadcast failed: ${result.error}")
            }
        }, 10000)
    }
    
    /**
     * 예제 4: 고급 스캔 필터링
     * Example 4: Advanced Scan Filtering
     */
    fun advancedScanFilteringExample(context: Context) {
        val centralManager = BleCentralManager.getInstance(context)
        centralManager.initialize()
        
        // 복합 필터 설정
        val advancedScanConfig = BleScanController.ScanConfig(
            scanMode = android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_POWER,
            scanDuration = 20000L, // 20초
            rssiThreshold = -70, // 신호 강도 -70dBm 이상만
            maxDevices = 50,
            duplicateFilterTimeout = 2000L, // 2초 중복 필터
            filters = listOf(
                // 특정 서비스 UUID를 가진 기기
                BleScanController.ScanFilterConfig(
                    serviceUuid = DEVICE_INFORMATION_SERVICE_UUID.toString()
                ),
                // 특정 제조사 데이터를 가진 기기 (Apple 예제)
                BleScanController.ScanFilterConfig(
                    manufacturerId = 0x004C, // Apple
                    manufacturerData = byteArrayOf(0x02, 0x15), // iBeacon 프리픽스
                    manufacturerDataMask = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
                ),
                // 특정 이름 패턴을 가진 기기
                BleScanController.ScanFilterConfig(
                    deviceName = "Sensor"
                )
            )
        )
        
        centralManager.addEventListener(object : BleCentralManager.CentralEventListener {
            override fun onDeviceFound(device: BleDevice) {
                println("Filtered device found: ${device.name} (RSSI: ${device.rssi}, Quality: ${device.getSignalQuality()})")
                
                // 신호 품질에 따른 연결 우선순위 결정
                when (device.getSignalQuality()) {
                    BleDevice.SignalQuality.EXCELLENT, BleDevice.SignalQuality.GOOD -> {
                        println("High quality signal - connecting immediately")
                        centralManager.connectDevice(device)
                    }
                    BleDevice.SignalQuality.FAIR -> {
                        println("Fair signal - adding to connection queue")
                        // 연결 대기열에 추가 로직
                    }
                    else -> {
                        println("Poor signal - skipping connection")
                    }
                }
            }
        })
        
        centralManager.startScan(advancedScanConfig)
    }
    
    /**
     * 예제 5: 오류 처리 및 복구
     * Example 5: Error Handling and Recovery
     */
    fun errorHandlingExample(context: Context) {
        val masterController = BleMasterController.getInstance(context)
        
        // 오류 복구 전략을 포함한 설정
        val config = BleMasterController.MasterConfig(
            enableAutoRestart = true,
            restartDelay = 5000L // 5초 후 재시작
        )
        
        masterController.initialize(config)
        
        masterController.addEventListener(object : BleMasterController.MasterEventListener {
            override fun onError(error: BleServiceError, role: BleMasterController.BleRole?) {
                println("Error occurred in $role: $error")
                
                when (error) {
                    is BleServiceError.PermissionError -> {
                        println("Permission error - need to request permissions")
                        handlePermissionError(error)
                    }
                    
                    is BleServiceError.HardwareError -> {
                        println("Hardware error - check Bluetooth status")
                        handleHardwareError(error)
                    }
                    
                    is BleServiceError.ConnectionError -> {
                        println("Connection error - retry connection")
                        handleConnectionError(error)
                    }
                    
                    is BleServiceError.SystemState -> {
                        println("System state error - adjust behavior")
                        handleSystemStateError(error)
                    }
                    
                    else -> {
                        println("Other error - generic handling")
                    }
                }
            }
        })
        
        // 권한 확인
        val permissionResult = masterController.checkAllPermissions()
        when (permissionResult) {
            is Result.Success -> println("All permissions granted")
            is Result.Failure -> {
                println("Permission check failed: ${permissionResult.error}")
                // 권한 요청 로직 구현 필요
            }
        }
    }
    
    /**
     * 권한 오류 처리
     */
    private fun handlePermissionError(error: BleServiceError.PermissionError) {
        when (error) {
            is BleServiceError.PermissionError.MissingPermissions -> {
                println("Missing permissions: ${error.missingPermissions.joinToString(", ")}")
                // Activity에서 권한 요청 필요
            }
            is BleServiceError.PermissionError.LocationServiceDisabled -> {
                println("Location service disabled - need to enable")
                // 위치 서비스 활성화 요청
            }
        }
    }
    
    /**
     * 하드웨어 오류 처리
     */
    private fun handleHardwareError(error: BleServiceError.HardwareError) {
        when (error) {
            is BleServiceError.HardwareError.BluetoothNotSupported -> {
                println("BLE not supported on this device")
                // 사용자에게 알림 및 대안 제시
            }
            is BleServiceError.HardwareError.BluetoothDisabled -> {
                println("Bluetooth is disabled - requesting enable")
                // 블루투스 활성화 요청
            }
            is BleServiceError.HardwareError.BluetoothNotAvailable -> {
                println("Bluetooth not available - retry later")
                // 재시도 로직
            }
        }
    }
    
    /**
     * 연결 오류 처리
     */
    private fun handleConnectionError(error: BleServiceError.ConnectionError) {
        when (error) {
            is BleServiceError.ConnectionError.ConnectionTimeout -> {
                println("Connection timeout - retry with different settings")
                // 타임아웃 설정 조정 후 재연결
            }
            is BleServiceError.ConnectionError.ConnectionFailed -> {
                println("Connection failed - try different device")
                // 다른 기기로 연결 시도
            }
            is BleServiceError.ConnectionError.DeviceNotFound -> {
                println("Device not found - start new scan")
                // 새로운 스캔 시작
            }
        }
    }
    
    /**
     * 시스템 상태 오류 처리
     */
    private fun handleSystemStateError(error: BleServiceError.SystemState) {
        when (error) {
            BleServiceError.SystemState.LowMemory -> {
                println("Low memory - reducing connections")
                // 불필요한 연결 해제
            }
            BleServiceError.SystemState.DozeMode -> {
                println("Doze mode active - reducing activity")
                // 백그라운드 활동 축소
            }
            BleServiceError.SystemState.BatteryOptimizationEnabled -> {
                println("Battery optimization enabled - may affect BLE")
                // 사용자에게 배터리 최적화 해제 요청
            }
        }
    }
    
    /**
     * 예제 6: 리소스 정리 및 생명주기 관리
     * Example 6: Resource Cleanup and Lifecycle Management
     */
    fun lifecycleManagementExample(context: Context) {
        val masterController = BleMasterController.getInstance(context)
        masterController.initialize()
        
        // Activity/Fragment의 생명주기에 따른 정리
        Runtime.getRuntime().addShutdownHook(Thread {
            println("Application shutting down - cleaning up BLE resources")
            masterController.cleanup()
        })
        
        // 메모리 부족 시 자동 정리
        val memoryListener = object : BleMasterController.MasterEventListener {
            override fun onError(error: BleServiceError, role: BleMasterController.BleRole?) {
                if (error == BleServiceError.SystemState.LowMemory) {
                    println("Low memory detected - performing cleanup")
                    
                    // 불필요한 연결 정리
                    val connections = masterController.getAllConnections()
                    if (connections.size > 2) {
                        // 절반의 연결을 정리
                        connections.keys.take(connections.size / 2).forEach { address ->
                            masterController.disconnectDevice(address)
                        }
                    }
                    
                    // 스캔 중지
                    masterController.stopAll()
                }
            }
        }
        
        masterController.addEventListener(memoryListener)
        
        println("Lifecycle management example setup complete")
        println("Current status: ${masterController.getStatusInfo()}")
    }
}