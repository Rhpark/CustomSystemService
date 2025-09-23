package kr.open.library.system_service

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import kr.open.library.logcat.Logx
import kr.open.library.system_service.databinding.ActivityBleTestBinding
import kr.open.library.systemmanager.controller.bluetooth.BleMasterController
import kr.open.library.systemmanager.controller.bluetooth.data.BleDevice
import kr.open.library.systemmanager.controller.bluetooth.data.BleMessage
import kr.open.library.systemmanager.controller.bluetooth.data.SensorDataMessage
import kr.open.library.systemmanager.controller.bluetooth.data.ControlCommandMessage
import kr.open.library.systemmanager.controller.bluetooth.data.TextMessage
import java.util.UUID

/**
 * BLE 컨트롤러 테스트 액티비티 (간소화된 버전)
 * BLE Controller Test Activity (Simplified version)
 */
class BleTestActivity : AppCompatActivity() {

    private val TAG = "BleTestActivity"
    private lateinit var binding: ActivityBleTestBinding
    private lateinit var bleMasterController: BleMasterController
    private lateinit var deviceAdapter: BleDeviceAdapter
    private var connectedDeviceAddress: String? = null
    private val receivedMessages = mutableListOf<String>()
    
    // API 레벨별 권한 정의
    private val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    // 권한 요청 launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            updateStatus("✅ 모든 BLE 권한이 허용되었습니다")
            // 권한 허용 후 자동으로 권한 체크
            checkPermissions()
        } else {
            val deniedPermissions = permissions.filterValues { !it }.keys
            val deniedList = deniedPermissions.joinToString(", ") { it.substringAfterLast(".") }
            updateStatus("❌ 일부 권한이 거부되었습니다: $deniedList")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = DataBindingUtil.setContentView(this, R.layout.activity_ble_test)
        
        // BLE Master Controller 초기화
        bleMasterController = BleMasterController.getInstance(this)
        
        setupClickListeners()
        setupEventListener()
        setupRecyclerView()
        setupDataCommunication()
        
        updateStatus("BLE Test Activity initialized")
    }
    
    private fun setupClickListeners() {
        binding.btnInit.setOnClickListener {
            initializeBle()
        }
        
        binding.btnCheckPermissions.setOnClickListener {
            requestPermissionsIfNeeded()
        }
        
        binding.btnStartScan.setOnClickListener {
            startAsCentral()
        }
        
        binding.btnStopScan.setOnClickListener {
            stopAll()
        }
        
        binding.btnStartAdvertising.setOnClickListener {
            startAsPeripheral()
        }
        
        binding.btnStopAdvertising.setOnClickListener {
            stopAll()
        }
        
        binding.btnCheckStatus.setOnClickListener {
            checkStatus()
        }
        
        binding.btnClear.setOnClickListener {
            clearResults()
        }
        
        binding.btnSendData.setOnClickListener {
            sendBleMessage()
        }
        
        binding.btnClearMessages.setOnClickListener {
            clearReceivedMessages()
        }
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = BleDeviceAdapter()
        binding.recyclerViewDevices.apply {
            layoutManager = LinearLayoutManager(this@BleTestActivity)
            adapter = deviceAdapter
        }
        
        // 디바이스 선택 리스너 설정
        deviceAdapter.setOnDeviceClickListener { device ->
            onDeviceSelected(device)
        }
        
        // 연결 버튼 리스너 설정
        deviceAdapter.setOnConnectClickListener { device ->
            connectToDevice(device)
        }
        
        // 연결 해제 버튼 리스너 설정
        deviceAdapter.setOnDisconnectClickListener { device ->
            disconnectFromDevice(device)
        }
        
        updateDeviceCount(0)
    }
    
    private fun setupEventListener() {
        bleMasterController.addEventListener(object : BleMasterController.MasterEventListener {
            override fun onMasterStateChanged(state: BleMasterController.MasterState) {
                runOnUiThread {
                    updateStatus("State changed to: $state")
                    updateButtonStates(state)
                }
            }
            
            override fun onRoleChanged(newRole: BleMasterController.BleRole) {
                runOnUiThread {
                    updateStatus("Role changed to: $newRole")
                }
            }
            
            override fun onError(message: String) {
                runOnUiThread {
                    updateStatus("Error: $message")
                }
            }
            
            override fun onDeviceDiscovered(device: BleDevice) {
                runOnUiThread {
                    deviceAdapter.addOrUpdateDevice(device)
                    updateDeviceCount(bleMasterController.getDiscoveredDevices().size)
                    Logx.d(TAG, "Device discovered: ${device.displayName}")
                }
            }
            
            override fun onScanStarted() {
                runOnUiThread {
                    updateStatus("📡 BLE Scan started - discovering devices...")
                    deviceAdapter.clearDevices()
                    updateDeviceCount(0)
                }
            }
            
            override fun onScanStopped() {
                runOnUiThread {
                    val deviceCount = bleMasterController.getDiscoveredDevices().size
                    updateStatus("🔍 BLE Scan stopped - found $deviceCount devices")
                }
            }
            
            override fun onDeviceConnected(device: BleDevice) {
                runOnUiThread {
                    updateStatus("✅ Connected to ${device.displayName}")
                    deviceAdapter.updateConnectionState(device.address, BleMasterController.ConnectionState.CONNECTED)
                    
                    // 연결된 디바이스 주소 저장 및 Send 버튼 상태 업데이트
                    connectedDeviceAddress = device.address
                    updateSendButtonState()
                    
                    addReceivedMessage("📶 [${System.currentTimeMillis()}] Connected to ${device.displayName} (${device.address})\n")
                }
            }
            
            override fun onDeviceDisconnected(device: BleDevice) {
                runOnUiThread {
                    updateStatus("🔌 Disconnected from ${device.displayName}")
                    deviceAdapter.updateConnectionState(device.address, BleMasterController.ConnectionState.DISCONNECTED)
                    
                    // 연결 해제 시 상태 초기화
                    if (connectedDeviceAddress == device.address) {
                        connectedDeviceAddress = null
                    }
                    updateSendButtonState()
                    
                    addReceivedMessage("📵 [${System.currentTimeMillis()}] Disconnected from ${device.displayName} (${device.address})\n")
                }
            }
            
            override fun onConnectionStateChanged(device: BleDevice, state: BleMasterController.ConnectionState) {
                runOnUiThread {
                    deviceAdapter.updateConnectionState(device.address, state)
                    val stateText = when (state) {
                        BleMasterController.ConnectionState.CONNECTING -> "연결 중"
                        BleMasterController.ConnectionState.CONNECTED -> "연결됨"
                        BleMasterController.ConnectionState.DISCONNECTING -> "연결 해제 중"
                        BleMasterController.ConnectionState.DISCONNECTED -> "연결 해제됨"
                        BleMasterController.ConnectionState.ERROR -> "연결 오류"
                    }
                    Logx.d(TAG, "Connection state changed: ${device.displayName} -> $stateText")
                }
            }
            
            override fun onServicesDiscovered(device: BleDevice, services: List<android.bluetooth.BluetoothGattService>) {
                runOnUiThread {
                    val serviceCount = services.size
                    updateStatus("🔍 Services discovered: ${device.displayName} has $serviceCount services")
                    
                    // 서비스 정보를 결과 영역에 표시
                    val servicesInfo = buildString {
                        appendLine("📡 Connected Device Services:")
                        appendLine("Device: ${device.displayName} (${device.address})")
                        appendLine("Services: $serviceCount")
                        appendLine()
                        
                        services.forEach { service ->
                            appendLine("Service: ${service.uuid}")
                            service.characteristics.forEach { characteristic ->
                                val properties = mutableListOf<String>()
                                if (characteristic.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                                    properties.add("READ")
                                }
                                if (characteristic.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                                    properties.add("WRITE")
                                }
                                if (characteristic.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                                    properties.add("NOTIFY")
                                }
                                appendLine("  - ${characteristic.uuid} [${properties.joinToString(", ")}]")
                            }
                            appendLine()
                        }
                    }
                    updateResults(servicesInfo)
                }
            }
            
            override fun onDataReceived(device: BleDevice, characteristic: android.bluetooth.BluetoothGattCharacteristic, data: ByteArray) {
                runOnUiThread {
                    try {
                        val dataString = String(data, Charsets.UTF_8)
                        val receivedMessageText = "📥 [${System.currentTimeMillis()}] Received from ${device.displayName}:\n${dataString}\n"
                        addReceivedMessage(receivedMessageText)
                        updateStatus("📥 Data received from ${device.displayName}")
                    } catch (e: Exception) {
                        val errorMessage = "❌ Failed to decode received data: ${e.message}"
                        addReceivedMessage(errorMessage)
                    }
                }
            }
            
            override fun onDataSent(device: BleDevice, characteristic: android.bluetooth.BluetoothGattCharacteristic, success: Boolean) {
                runOnUiThread {
                    val statusText = if (success) "✅ Data sent successfully" else "❌ Failed to send data"
                    updateStatus("$statusText to ${device.displayName}")
                }
            }
            
            override fun onMessageReceived(device: BleDevice, message: BleMessage) {
                runOnUiThread {
                    val receivedMessageText = "📥 [${message.getFormattedTime()}] Received ${message.messageType} from ${device.displayName}:\n${formatMessageForDisplay(message)}\n"
                    addReceivedMessage(receivedMessageText)
                    updateStatus("📥 Message received from ${device.displayName}")
                }
            }
        })
    }
    
    private fun initializeBle() {
        try {
            val config = BleMasterController.MasterConfig(
                defaultRole = BleMasterController.BleRole.DUAL,
                enableDebugLogging = true
            )
            
            val result = bleMasterController.initialize(config)
            if (result) {
                updateStatus("BLE Master Controller initialized successfully")
                binding.btnCheckPermissions.isEnabled = true
                binding.btnCheckStatus.isEnabled = true
            } else {
                updateStatus("Failed to initialize BLE Master Controller")
            }
        } catch (e: Exception) {
            Logx.e(TAG, "Initialize failed: ${e.message}")
            updateStatus("Initialize error: ${e.message}")
        }
    }
    
    /**
     * 권한이 필요하면 요청, 있으면 체크
     */
    private fun requestPermissionsIfNeeded() {
        val missingPermissions = blePermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            val missingList = missingPermissions.joinToString(", ") { it.substringAfterLast(".") }
            updateStatus("🔐 권한 요청 중: $missingList")
            Logx.d(TAG, "Requesting missing permissions: $missingList")
            permissionLauncher.launch(blePermissions)
        } else {
            // 모든 권한이 있으면 BleMasterController로 체크
            checkPermissions()
        }
    }
    
    /**
     * BleMasterController를 통한 권한 체크
     */
    private fun checkPermissions() {
        try {
            val result = bleMasterController.checkAllPermissions()
            if (result) {
                updateStatus("✅ 모든 권한이 허용되었습니다")
                binding.btnStartScan.isEnabled = true
                binding.btnStartAdvertising.isEnabled = true
            } else {
                updateStatus("❌ 일부 권한이 부족합니다")
            }
        } catch (e: Exception) {
            Logx.e(TAG, "Permission check failed: ${e.message}")
            updateStatus("Permission check error: ${e.message}")
        }
    }
    
    private fun startAsCentral() {
        try {
            val result = bleMasterController.startAsCentral()
            if (result) {
                updateStatus("Started as Central (scanning for devices)")
                binding.btnStopScan.isEnabled = true
                binding.btnStartScan.isEnabled = false
                binding.btnStartAdvertising.isEnabled = false  // Disable advertising while scanning
            } else {
                updateStatus("Failed to start as Central")
            }
        } catch (e: Exception) {
            Logx.e(TAG, "Start as Central failed: ${e.message}")
            updateStatus("Start as Central error: ${e.message}")
        }
    }
    
    private fun startAsPeripheral() {
        try {
            val result = bleMasterController.startAsPeripheral()
            if (result) {
                updateStatus("Started as Peripheral (advertising)")
                binding.btnStopAdvertising.isEnabled = true
                binding.btnStartAdvertising.isEnabled = false
                binding.btnStartScan.isEnabled = false  // Disable scanning while advertising
            } else {
                updateStatus("Failed to start as Peripheral")
            }
        } catch (e: Exception) {
            Logx.e(TAG, "Start as Peripheral failed: ${e.message}")
            updateStatus("Start as Peripheral error: ${e.message}")
        }
    }
    
    private fun stopAll() {
        try {
            val result = bleMasterController.stopAll()
            if (result) {
                updateStatus("Stopped all BLE operations")
                // Reset all buttons to initial state
                binding.btnStopScan.isEnabled = false
                binding.btnStopAdvertising.isEnabled = false
                binding.btnStartScan.isEnabled = true
                binding.btnStartAdvertising.isEnabled = true
            } else {
                updateStatus("Failed to stop BLE operations")
            }
        } catch (e: Exception) {
            Logx.e(TAG, "Stop all failed: ${e.message}")
            updateStatus("Stop all error: ${e.message}")
        }
    }
    
    private fun checkStatus() {
        try {
            val statusInfo = bleMasterController.getStatusInfo()
            updateResults(statusInfo)
        } catch (e: Exception) {
            Logx.e(TAG, "Status check failed: ${e.message}")
            updateStatus("Status check error: ${e.message}")
        }
    }
    
    private fun clearResults() {
        binding.tvResults.text = "System info will be displayed here..."
        binding.tvStatus.text = "Ready for BLE operations"
        deviceAdapter.clearDevices()
        updateDeviceCount(0)
    }
    
    private fun updateDeviceCount(count: Int) {
        binding.tvDeviceCount.text = when (count) {
            0 -> "No devices found"
            1 -> "1 device found"
            else -> "$count devices found"
        }
    }
    
    private fun onDeviceSelected(device: BleDevice) {
        try {
            val connectionState = bleMasterController.getConnectionState(device.address)
            val deviceInfo = buildString {
                appendLine("📱 Selected BLE Device:")
                appendLine("Name: ${device.displayName}")
                appendLine("Address: ${device.address}")
                appendLine("RSSI: ${device.signalStrengthText}")
                appendLine("Connectable: ${device.connectableText}")
                appendLine("Connection State: $connectionState")
                appendLine("Services: ${device.serviceUuidsText}")
                if (device.txPowerLevel != null) {
                    appendLine("TX Power: ${device.txPowerLevel}dBm")
                }
                appendLine("Last seen: ${device.lastSeenMinutesAgo} minutes ago")
                appendLine()
                when (connectionState) {
                    BleMasterController.ConnectionState.CONNECTED -> {
                        appendLine("✅ Device is connected! Services are being discovered.")
                    }
                    BleMasterController.ConnectionState.DISCONNECTED -> {
                        appendLine("💡 Device selected! Tap the 'Connect' button to establish connection.")
                    }
                    else -> {
                        appendLine("🔄 Connection in progress...")
                    }
                }
            }
            
            updateStatus("Selected device: ${device.displayName} (${device.address})")
            updateResults(deviceInfo)
            
            Logx.i(TAG, "User selected device: ${device.displayName} at ${device.address}")
        } catch (e: Exception) {
            Logx.e(TAG, "Device selection failed: ${e.message}")
            updateStatus("Device selection error: ${e.message}")
        }
    }
    
    private fun connectToDevice(device: BleDevice) {
        try {
            updateStatus("Connecting to ${device.displayName}...")
            val result = bleMasterController.connectToDevice(device)
            if (!result) {
                updateStatus("Failed to initiate connection to ${device.displayName}")
            }
            Logx.i(TAG, "Connection attempt for ${device.displayName}: $result")
        } catch (e: Exception) {
            Logx.e(TAG, "Connect to device failed: ${e.message}")
            updateStatus("Connect error: ${e.message}")
        }
    }
    
    private fun disconnectFromDevice(device: BleDevice) {
        try {
            updateStatus("Disconnecting from ${device.displayName}...")
            val result = bleMasterController.disconnectDevice(device.address)
            if (!result) {
                updateStatus("Failed to disconnect from ${device.displayName}")
            }
            Logx.i(TAG, "Disconnect attempt for ${device.displayName}: $result")
        } catch (e: Exception) {
            Logx.e(TAG, "Disconnect from device failed: ${e.message}")
            updateStatus("Disconnect error: ${e.message}")
        }
    }
    
    private fun updateStatus(message: String) {
        binding.tvStatus.text = message
        Logx.d(TAG, message)
    }
    
    private fun updateResults(results: String) {
        binding.tvResults.text = results
    }
    
    private fun updateButtonStates(state: BleMasterController.MasterState) {
        when (state) {
            BleMasterController.MasterState.IDLE -> {
                // Both modes available when idle
                binding.btnStartScan.isEnabled = BleMasterController.isInitialized()
                binding.btnStopScan.isEnabled = false
                binding.btnStartAdvertising.isEnabled = BleMasterController.isInitialized()
                binding.btnStopAdvertising.isEnabled = false
            }
            BleMasterController.MasterState.CENTRAL_ONLY -> {
                // Central mode active - only scanning stop available
                binding.btnStartScan.isEnabled = false
                binding.btnStopScan.isEnabled = true
                binding.btnStartAdvertising.isEnabled = false
                binding.btnStopAdvertising.isEnabled = false
            }
            BleMasterController.MasterState.PERIPHERAL_ONLY -> {
                // Peripheral mode active - only advertising stop available
                binding.btnStartScan.isEnabled = false
                binding.btnStopScan.isEnabled = false
                binding.btnStartAdvertising.isEnabled = false
                binding.btnStopAdvertising.isEnabled = true
            }
            BleMasterController.MasterState.DUAL_MODE -> {
                // Both modes active - both stops available
                binding.btnStartScan.isEnabled = false
                binding.btnStopScan.isEnabled = true
                binding.btnStartAdvertising.isEnabled = false
                binding.btnStopAdvertising.isEnabled = true
            }
            BleMasterController.MasterState.ERROR -> {
                // Error state - allow stopping anything that might be running
                binding.btnStartScan.isEnabled = false
                binding.btnStopScan.isEnabled = true
                binding.btnStartAdvertising.isEnabled = false
                binding.btnStopAdvertising.isEnabled = true
            }
        }
    }
    
    private fun setupDataCommunication() {
        // Message Type Spinner 설정
        val messageTypes = arrayOf(
            "Text Message",
            "Sensor Data", 
            "Control Command",
            "Status Info"
        )
        
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, messageTypes)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMessageType.adapter = spinnerAdapter
        
        // Spinner 선택 리스너
        binding.spinnerMessageType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updateInputVisibility(position)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        
        // 초기 설정: Text Message가 기본 선택
        updateInputVisibility(0)
        
        // Send 버튼은 연결되기 전까지 비활성화
        binding.btnSendData.isEnabled = false
        updateSendButtonState()
    }
    
    private fun updateInputVisibility(messageTypePosition: Int) {
        // 모든 입력 폼 숨기기
        binding.etTextMessage.visibility = View.GONE
        binding.layoutSensorInputs.visibility = View.GONE
        binding.layoutControlInputs.visibility = View.GONE
        
        // 선택된 메시지 타입에 따라 해당 입력 폼 표시
        when (messageTypePosition) {
            0 -> binding.etTextMessage.visibility = View.VISIBLE // Text Message
            1 -> binding.layoutSensorInputs.visibility = View.VISIBLE // Sensor Data
            2 -> binding.layoutControlInputs.visibility = View.VISIBLE // Control Command
            3 -> binding.etTextMessage.visibility = View.VISIBLE // Status Info (텍스트로 입력)
        }
    }
    
    private fun sendBleMessage() {
        try {
            val message = createMessageFromInput()
            if (message == null) {
                updateStatus("❌ Invalid input data")
                return
            }
            
            // 스마트 전송: 현재 역할에 따라 자동으로 적절한 방법 선택
            val result = bleMasterController.sendMessageSmart(connectedDeviceAddress, message)
            
            if (result) {
                updateStatus("📤 Sending message: ${message.messageType}")
                val sentMessageText = "📤 [${message.getFormattedTime()}] Sent ${message.messageType}:\n${formatMessageForDisplay(message)}\n"
                addReceivedMessage(sentMessageText)
            } else {
                updateStatus("❌ Failed to send message")
            }
            
        } catch (e: Exception) {
            Logx.e(TAG, "Send message failed: ${e.message}")
            updateStatus("❌ Send error: ${e.message}")
        }
    }
    
    private fun createMessageFromInput(): BleMessage? {
        return try {
            when (binding.spinnerMessageType.selectedItemPosition) {
                0 -> { // Text Message
                    val text = binding.etTextMessage.text.toString().trim()
                    if (text.isEmpty()) return null
                    TextMessage(text = text, deviceId = connectedDeviceAddress)
                }
                1 -> { // Sensor Data
                    SensorDataMessage(
                        temperature = binding.etTemperature.text.toString().toFloatOrNull(),
                        humidity = binding.etHumidity.text.toString().toFloatOrNull(),
                        battery = binding.etBattery.text.toString().toIntOrNull(),
                        light = binding.etLight.text.toString().toFloatOrNull(),
                        deviceId = connectedDeviceAddress
                    )
                }
                2 -> { // Control Command
                    val command = binding.etCommand.text.toString().trim()
                    if (command.isEmpty()) return null
                    ControlCommandMessage(
                        command = command,
                        parameter = binding.etParameter.text.toString().takeIf { it.isNotBlank() },
                        value = binding.etValue.text.toString().toFloatOrNull(),
                        deviceId = connectedDeviceAddress
                    )
                }
                3 -> { // Status Info (텍스트로 처리)
                    val text = binding.etTextMessage.text.toString().trim()
                    if (text.isEmpty()) return null
                    TextMessage(text = "STATUS: $text", deviceId = connectedDeviceAddress)
                }
                else -> null
            }
        } catch (e: Exception) {
            Logx.e(TAG, "Create message failed: ${e.message}")
            null
        }
    }
    
    private fun formatMessageForDisplay(message: BleMessage): String {
        return when (message) {
            is TextMessage -> "Text: ${message.text}"
            is SensorDataMessage -> buildString {
                message.temperature?.let { appendLine("Temperature: ${it}°C") }
                message.humidity?.let { appendLine("Humidity: ${it}%") }
                message.battery?.let { appendLine("Battery: ${it}%") }
                message.light?.let { appendLine("Light: ${it} lux") }
            }.trim()
            is ControlCommandMessage -> buildString {
                appendLine("Command: ${message.command}")
                message.parameter?.let { appendLine("Parameter: $it") }
                message.value?.let { appendLine("Value: $it") }
            }.trim()
            else -> "Unknown message type"
        }
    }
    
    private fun addReceivedMessage(message: String) {
        receivedMessages.add(message)
        
        // 최대 100개 메시지만 유지
        if (receivedMessages.size > 100) {
            receivedMessages.removeFirst()
        }
        
        updateReceivedMessagesDisplay()
    }
    
    private fun updateReceivedMessagesDisplay() {
        val displayText = if (receivedMessages.isEmpty()) {
            "No messages received yet..."
        } else {
            receivedMessages.takeLast(20).joinToString("\n")
        }
        binding.tvReceivedMessages.text = displayText
        
        // 스크롤을 맨 아래로
        binding.tvReceivedMessages.post {
            val scrollView = binding.tvReceivedMessages.parent as? android.widget.ScrollView
            scrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }
    
    private fun clearReceivedMessages() {
        receivedMessages.clear()
        updateReceivedMessagesDisplay()
        updateStatus("Received messages cleared")
    }
    
    /**
     * Send 버튼 활성화 상태 업데이트
     * Central 연결 또는 Peripheral 클라이언트 연결 확인
     */
    private fun updateSendButtonState() {
        try {
            // Central 모드: 연결된 디바이스가 있는지 확인
            val hasConnectedDevices = bleMasterController.getConnectedDevices().isNotEmpty()
            
            // Peripheral 모드: 연결된 클라이언트가 있는지 확인
            val hasConnectedClients = bleMasterController.getPeripheralConnectedDevices().isNotEmpty()
            
            // 둘 중 하나라도 연결되어 있으면 Send 버튼 활성화
            val shouldEnable = hasConnectedDevices || hasConnectedClients
            binding.btnSendData.isEnabled = shouldEnable
            
            // 상태 로깅
            Logx.d(TAG, "Send button state: enabled=$shouldEnable (Central devices: ${bleMasterController.getConnectedDevices().size}, Peripheral clients: ${bleMasterController.getPeripheralConnectedDevices().size})")
            
        } catch (e: Exception) {
            Logx.e(TAG, "Failed to update send button state: ${e.message}")
            binding.btnSendData.isEnabled = false
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            bleMasterController.cleanup()
        } catch (e: Exception) {
            Logx.e(TAG, "Cleanup failed: ${e.message}")
        }
    }
}