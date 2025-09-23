package kr.open.library.system_service

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Dispatchers
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.SimpleBleController
import kr.open.library.systemmanager.controller.bluetooth.base.BleComponent
import kr.open.library.systemmanager.controller.bluetooth.data.BinaryProtocol
import kr.open.library.systemmanager.controller.bluetooth.data.BleDevice

/**
 * 실제 두 스마트폰 간 BLE 통신 테스트 액티비티
 * 
 * 사용법:
 * 1. 두 폰 모두에서 이 액티비티 실행
 * 2. 한 폰은 "Peripheral 모드", 다른 폰은 "Central 모드" 선택
 * 3. Peripheral 먼저 시작, 그 다음 Central 시작
 */
class BleTwoPhoneTestActivity : AppCompatActivity() {
    
    // View references
    private lateinit var tvDeviceName: TextView
    private lateinit var tvBleStatus: TextView
    private lateinit var tvBleMode: TextView
    private lateinit var tvConnectedDevice: TextView
    private lateinit var tvLastMessage: TextView
    private lateinit var tvScanResult: TextView
    private lateinit var btnPeripheralMode: Button
    private lateinit var btnCentralMode: Button
    private lateinit var btnScanOnly: Button
    private lateinit var btnSendMessage: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnCheckStatus: Button
    
    private lateinit var bleController: SimpleBleController
    private lateinit var permissionHelper: BlePermissionHelper
    
    private var isConnected = false
    private val deviceName = "TD_${System.currentTimeMillis() % 10000}" // 🔧 광고 데이터 크기 문제 해결 (31바이트 제한)

    // 메시지 히스토리 관리
    private val messageHistory = mutableListOf<String>()
    private val maxHistorySize = 10
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_two_phone_test)
        
        // Initialize views
        initViews()
        
        // 권한 헬퍼 초기화
        permissionHelper = BlePermissionHelper(this)
        
        // BLE 컨트롤러 초기화
        bleController = SimpleBleController(this)
        
        setupUI()
        setupBleController()
    }
    
    private fun initViews() {
        tvDeviceName = findViewById(R.id.tvDeviceName)
        tvBleStatus = findViewById(R.id.tvBleStatus)
        tvBleMode = findViewById(R.id.tvBleMode)
        tvConnectedDevice = findViewById(R.id.tvConnectedDevice)
        tvLastMessage = findViewById(R.id.tvLastMessage)
        tvScanResult = findViewById(R.id.tvScanResult)
        btnPeripheralMode = findViewById(R.id.btnPeripheralMode)
        btnCentralMode = findViewById(R.id.btnCentralMode)
        btnScanOnly = findViewById(R.id.btnScanOnly)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        btnCheckStatus = findViewById(R.id.btnCheckStatus)
    }
    
    private fun setupUI() {
        // 디바이스 정보 표시
        tvDeviceName.text = "디바이스 이름: $deviceName"
        tvBleStatus.text = "BLE 상태: 초기화 중..."
        
        // Peripheral 모드 버튼
        btnPeripheralMode.setOnClickListener {
            startPeripheralMode()
        }
        
        // Central 모드 버튼  
        btnCentralMode.setOnClickListener {
            startCentralMode()
        }
        
        // 스캔만 하기 버튼
        btnScanOnly.setOnClickListener {
            startScanOnly()
        }
        
        // 메시지 전송 버튼
        btnSendMessage.setOnClickListener {
            sendTestMessage()
        }
        
        // 연결 해제 버튼
        btnDisconnect.setOnClickListener {
            disconnectAll()
        }
        
        // 상태 확인 버튼
        btnCheckStatus.setOnClickListener {
            showStatusInfo()
        }
        
        // 초기 버튼 상태
        updateButtonStates(false)
    }
    
    private fun setupBleController() {
        // Flow 기반 상태 관찰 설정
        setupFlowObservers()
        
        // Legacy 콜백 호환성도 유지 (디버깅용)
        setupLegacyListener()
        
        // BLE 컨트롤러 초기화
        checkPermissionsAndInitialize()
    }
    
    private fun setupFlowObservers() {
        // 연결 상태 관찰
        lifecycleScope.launch {
            bleController.bleConnectionState.collect { state ->
                tvBleStatus.text = "연결 상태: $state"
                isConnected = (state == BleComponent.ConnectionState.CONNECTED)
                updateButtonStates(isConnected)
            }
        }
        
        // 모드 상태 관찰
        lifecycleScope.launch {
            bleController.currentMode.collect { mode ->
                tvBleMode.text = "모드: $mode"
            }
        }
        
        // 연결된 디바이스 관찰
        lifecycleScope.launch {
            bleController.connectedDevice.collect { deviceAddress ->
                if (deviceAddress != null) {
                    tvConnectedDevice.text = "연결된 디바이스: $deviceAddress"
                    showToast("✅ 디바이스 연결됨: $deviceAddress")
                } else {
                    tvConnectedDevice.text = "연결된 디바이스: 없음"
                }
            }
        }
        
        // 스캔된 디바이스 관찰
        lifecycleScope.launch {
            bleController.scannedDevices.collect { device ->
                tvScanResult.text = "발견: ${device.displayName} (${device.address}) ${device.rssi}dBm"
            }
        }
        
        // 메시지 수신 관찰 - 강화된 로깅 및 UI 업데이트
        lifecycleScope.launch(Dispatchers.Main) {
            bleController.receivedMessages.collect { (type, data) ->
                Logx.d("BleTwoPhoneTest", "📨 메시지 수신됨: 타입=0x${type.toString(16)}, 크기=${data.size}")

                val messageText = when (type) {
                    BinaryProtocol.MessageType.TEXT_MESSAGE -> {
                        val text = bleController.parseTextMessage(data)
                        Logx.d("BleTwoPhoneTest", "📝 텍스트 메시지: '$text'")
                        "📝 텍스트: $text"
                    }
                    BinaryProtocol.MessageType.HEARTBEAT -> {
                        Logx.d("BleTwoPhoneTest", "💗 하트비트 수신")
                        "💗 Heartbeat"
                    }
                    BinaryProtocol.MessageType.SENSOR_DATA -> {
                        val sensorData = BinaryProtocol.parseSensorData(data)
                        if (sensorData != null) {
                            val (temp, humidity, timestamp) = sensorData
                            Logx.d("BleTwoPhoneTest", "🌡️ 센서 데이터: 온도=$temp, 습도=$humidity")
                            "🌡️ 센서: 온도=$temp°C, 습도=$humidity%"
                        } else {
                            "🌡️ 센서 데이터 (파싱 실패)"
                        }
                    }
                    BinaryProtocol.MessageType.ACK -> {
                        "✅ ACK"
                    }
                    BinaryProtocol.MessageType.ERROR -> {
                        "❌ ERROR"
                    }
                    else -> {
                        "❓ 알 수 없는 타입: 0x${type.toString(16)} (${data.size}bytes)"
                    }
                }

                // 메시지 히스토리에 추가
                addToMessageHistory("받음: $messageText")

                // UI 업데이트 (메인 스레드에서 실행 보장)
                tvLastMessage.text = "마지막 메시지: $messageText"
                showToast("📩 $messageText")

                Logx.d("BleTwoPhoneTest", "✅ UI 업데이트 완료")
            }
        }
        
        // 에러 관찰
        lifecycleScope.launch {
            bleController.errors.collect { error ->
                tvBleStatus.text = "오류: $error"
                showToast("❌ $error")
            }
        }
        
        // Ready 상태 관찰
        lifecycleScope.launch {
            bleController.bleIsReady.collect { ready ->
                Logx.d("BleTwoPhoneTest", "BLE Controller ready state: $ready")
                if (ready) {
                    btnPeripheralMode.isEnabled = true
                    btnCentralMode.isEnabled = true
                    btnScanOnly.isEnabled = true
                }
            }
        }
    }
    
    private fun setupLegacyListener() {
        // Legacy 콜백도 유지 (기존 호환성)
        bleController.setListener(object : SimpleBleController.BleControllerListener {
            override fun onStateChanged(state: BleComponent.ConnectionState) {
                // Flow에서 이미 처리됨
            }
            
            override fun onModeChanged(mode: SimpleBleController.BleMode) {
                // Flow에서 이미 처리됨
            }
            
            override fun onDeviceConnected(deviceAddress: String) {
                // Flow에서 이미 처리됨
            }
            
            override fun onDeviceDisconnected(deviceAddress: String) {
                showToast("❌ 디바이스 연결 해제: $deviceAddress")
            }
            
            override fun onMessageReceived(type: Byte, data: ByteArray) {
                // Flow에서 이미 처리됨
            }
            
            override fun onDeviceScanned(device: BleDevice) {
                // Flow에서 이미 처리됨
            }
            
            override fun onError(error: String) {
                // Flow에서 이미 처리됨
            }
        })
    }
    
    private fun checkPermissionsAndInitialize() {
        val requirements = permissionHelper.checkAllBleRequirements()
        tvBleStatus.text = requirements
        
        if (requirements.startsWith("✅")) {
            // 모든 요구사항 충족 - BLE 컨트롤러 초기화 (suspend 함수)
            lifecycleScope.launch {
                try {
                    val success = bleController.initialize()
                    if (success) {
                        tvBleStatus.text = "BLE 초기화 성공"
                    } else {
                        tvBleStatus.text = "BLE 초기화 실패"
                    }
                } catch (e: Exception) {
                    tvBleStatus.text = "BLE 초기화 오류: ${e.message}"
                    Logx.e("BleTwoPhoneTest", "Initialize error: ${e.message}")
                }
            }
        } else {
            // 요구사항 부족 - 권한 요청
            requestPermissions()
        }
    }
    
    private fun requestPermissions() {
        tvBleStatus.text = "권한 요청 중..."
        
        permissionHelper.requestBlePermissions { success ->
            if (success) {
                showToast("✅ 권한 획득 완료")
                checkPermissionsAndInitialize()
            } else {
                tvBleStatus.text = "❌ 권한 부족 - BLE 사용 불가"
                showToast("❌ BLE 권한이 필요합니다")
            }
        }
    }
    
    private fun startPeripheralMode() {
        tvBleStatus.text = "Peripheral 모드 시작..."
        lifecycleScope.launch {
            try {
                bleController.startAsPeripheral(deviceName)
                showToast("📡 Peripheral 모드 시작 - 다른 폰에서 Central 모드로 연결하세요")
            } catch (e: Exception) {
                tvBleStatus.text = "Peripheral 시작 실패: ${e.message}"
                showToast("❌ Peripheral 모드 시작 실패")
                Logx.e("BleTwoPhoneTest", "Peripheral start error: ${e.message}")
            }
        }
    }
    
    private fun startCentralMode() {
        // 연결할 디바이스 이름 입력 받기
        val builder = AlertDialog.Builder(this)
        builder.setTitle("연결할 디바이스")
        builder.setMessage("연결할 디바이스 이름을 입력하세요:")
        
        val input = EditText(this)
        input.setText("TD_") // 🔧 기본값 수정 (짧은 이름으로 변경)
        builder.setView(input)
        
        builder.setPositiveButton("연결") { _, _ ->
            val targetDeviceName = input.text.toString()
            if (targetDeviceName.isNotEmpty()) {
                tvBleStatus.text = "Central 모드 - '$targetDeviceName' 스캔 중..."
                lifecycleScope.launch {
                    try {
                        bleController.startAsCentral(targetDeviceName)
                        showToast("🔍 '$targetDeviceName' 검색 중...")
                    } catch (e: Exception) {
                        tvBleStatus.text = "Central 시작 실패: ${e.message}"
                        showToast("❌ Central 모드 시작 실패")
                        Logx.e("BleTwoPhoneTest", "Central start error: ${e.message}")
                    }
                }
            }
        }
        
        builder.setNegativeButton("취소", null)
        builder.show()
    }
    
    private fun startScanOnly() {
        tvBleStatus.text = "스캔 전용 모드..."
        lifecycleScope.launch {
            try {
                bleController.startScanOnly()
                showToast("🔍 모든 BLE 디바이스 스캔 중...")
            } catch (e: Exception) {
                tvBleStatus.text = "스캔 시작 실패: ${e.message}"
                showToast("❌ 스캔 시작 실패")
                Logx.e("BleTwoPhoneTest", "Scan start error: ${e.message}")
            }
        }
    }
    
    private fun sendTestMessage() {
        if (!isConnected) {
            showToast("❌ 연결된 디바이스가 없습니다")
            return
        }

        val timestamp = System.currentTimeMillis()
        val message = "Hello from ${android.os.Build.MODEL} at $timestamp"

        Logx.d("BleTwoPhoneTest", "📤 메시지 전송 시도: '$message'")

        lifecycleScope.launch {
            try {
                val success = bleController.sendTextMessage(message)
                Logx.d("BleTwoPhoneTest", "📤 메시지 전송 결과: $success")

                if (success) {
                    // 보낸 메시지를 히스토리에 추가
                    addToMessageHistory("보냄: 📝 텍스트: $message")

                    // UI 업데이트
                    runOnUiThread {
                        tvLastMessage.text = "보낸 메시지: $message"
                        showToast("📤 메시지 전송 성공")
                    }
                } else {
                    runOnUiThread {
                        showToast("❌ 메시지 전송 실패")
                    }
                }
            } catch (e: Exception) {
                Logx.e("BleTwoPhoneTest", "메시지 전송 오류: ${e.message}")
                runOnUiThread {
                    showToast("❌ 메시지 전송 오류: ${e.message}")
                }
            }
        }
    }
    
    private fun disconnectAll() {
        lifecycleScope.launch {
            try {
                bleController.stopAllOperations()
                showToast("🔌 모든 연결 해제")
            } catch (e: Exception) {
                showToast("❌ 연결 해제 오류: ${e.message}")
                Logx.e("BleTwoPhoneTest", "Disconnect error: ${e.message}")
            }
        }
    }
    
    // 메시지 히스토리 관리
    private fun addToMessageHistory(message: String) {
        synchronized(messageHistory) {
            messageHistory.add(0, "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}] $message")

            // 최대 크기 제한
            while (messageHistory.size > maxHistorySize) {
                messageHistory.removeAt(messageHistory.size - 1)
            }
        }

        Logx.d("BleTwoPhoneTest", "메시지 히스토리 추가: $message (총 ${messageHistory.size}개)")
    }

    private fun showStatusInfo() {
        val baseStatus = bleController.getStatusSummary()

        val enhancedStatus = buildString {
            appendLine("=== 디바이스 정보 ===")

            // 내 BLE 디바이스 이름 표시
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter != null) {
                try {
                    val myDeviceName = bluetoothAdapter.name ?: "알 수 없음"
                    appendLine("내 BLE 이름: $myDeviceName")
                } catch (e: SecurityException) {
                    appendLine("내 BLE 이름: 권한 필요")
                }
            }

            appendLine("설정된 이름: $deviceName")

            // 현재 모드 정보
            val currentMode = bleController.getCurrentMode()
            when (currentMode) {
                SimpleBleController.BleMode.PERIPHERAL_MODE -> {
                    appendLine("모드: Peripheral (광고 중)")
                }
                SimpleBleController.BleMode.CENTRAL_MODE -> {
                    appendLine("모드: Central (스캔/연결)")
                }
                else -> {
                    appendLine("모드: IDLE")
                }
            }

            appendLine("")
            appendLine("=== 시스템 상태 ===")
            append(baseStatus)

            // 메시지 히스토리 추가
            if (messageHistory.isNotEmpty()) {
                appendLine("")
                appendLine("=== 메시지 히스토리 (최근 ${messageHistory.size}개) ===")
                messageHistory.forEach { message ->
                    appendLine(message)
                }
            } else {
                appendLine("")
                appendLine("=== 메시지 히스토리 ===")
                appendLine("아직 송수신된 메시지가 없습니다.")
            }
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("BLE 상태 정보")
        builder.setMessage(enhancedStatus)
        builder.setPositiveButton("확인", null)

        // 메시지 히스토리 초기화 옵션 추가
        if (messageHistory.isNotEmpty()) {
            builder.setNeutralButton("히스토리 초기화") { _, _ ->
                synchronized(messageHistory) {
                    messageHistory.clear()
                }
                showToast("메시지 히스토리가 초기화되었습니다")
            }
        }

        builder.show()
    }
    
    private fun updateButtonStates(connected: Boolean) {
        btnSendMessage.isEnabled = connected
        btnDisconnect.isEnabled = connected || 
            bleController.getCurrentMode() != SimpleBleController.BleMode.IDLE
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        Logx.d("BleTwoPhoneTestActivity", message)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // cleanup은 suspend 함수이므로 coroutine에서 실행
        lifecycleScope.launch {
            try {
                bleController.cleanup()
            } catch (e: Exception) {
                Logx.e("BleTwoPhoneTest", "Cleanup error: ${e.message}")
            }
        }
    }
}