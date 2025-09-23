package kr.open.library.system_service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kr.open.library.logcat.Logx
import kr.open.library.system_service.databinding.ActivityBleTestBinding
import kr.open.library.systemmanager.controller.bluetooth.SimpleBleController
import kr.open.library.systemmanager.controller.bluetooth.base.BleComponent
import kr.open.library.systemmanager.controller.bluetooth.data.BinaryProtocol
import kr.open.library.systemmanager.controller.bluetooth.data.BleDevice

/**
 * BLE 통신 테스트를 위한 Activity
 * Central/Peripheral 모드를 전환하여 실제 BLE 통신을 검증
 */
class BleTestActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityBleTestBinding
    private var bleController: SimpleBleController? = null
    private var bleForegroundService: BleForegroundService? = null
    private var isServiceBound = false
    
    // 직접 스캐너 사용 (SimpleBleController 우회)
    private var directScanner: kr.open.library.systemmanager.controller.bluetooth.central.BleScanner? = null
    
    private var isInitialized = false
    private var currentMode = SimpleBleController.BleMode.IDLE
    
    // 디바이스 목록 관리
    private lateinit var deviceAdapter: BleDeviceAdapter
    private var isScanning = false
    
    companion object {
        private const val TAG = "BleTestActivity"
        private const val TEST_DEVICE_NAME = "TestBLE"
        private const val TARGET_DEVICE_NAME = "TestBLE"
    }
    
    // BLE 권한 요청
    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Logx.i(TAG, "All BLE permissions granted")
            checkBluetoothAndInitialize()
        } else {
            Logx.w(TAG, "Some BLE permissions denied: ${permissions.filter { !it.value }}")
            Toast.makeText(this, "BLE 권한이 필요합니다", Toast.LENGTH_LONG).show()
        }
    }
    
    // 블루투스 활성화 요청
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            addLog("✅ 블루투스 활성화됨")
            initializeBle()
        } else {
            addLog("❌ 블루투스 활성화 거부됨")
            Toast.makeText(this, "블루투스가 필요합니다", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupDeviceList() {
        // 디바이스 어댑터 초기화
        deviceAdapter = BleDeviceAdapter { device ->
            // 디바이스 선택 시 연결 시도
            connectToDevice(device)
        }
        
        // RecyclerView 설정
        binding.recyclerViewDevices.apply {
            layoutManager = LinearLayoutManager(this@BleTestActivity)
            adapter = deviceAdapter
        }
    }
    
    private fun connectToDevice(device: BleDevice) {
        addLog("🔗 디바이스 연결 시도: ${device.displayName} (${device.address})")
        
        // 스캔 중지
        if (isScanning) {
            stopScanning()
        }
        
        // 연결 시도 - startAsCentral으로 대체
        lifecycleScope.launch {
            try {
                addLog("🔗 ${device.name ?: "Unknown"}에 연결 시도 중...")
                bleController?.startAsCentral(device.name ?: device.address)
                addLog("✅ 연결 요청 전송됨")
            } catch (e: Exception) {
                addLog("❌ 연결 요청 실패: ${e.message}")
            }
        }
    }
    
    // Service Connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Logx.d(TAG, "BLE Foreground Service connected")
            
            val binder = service as BleForegroundService.BleBinder
            bleForegroundService = binder.getService()
            isServiceBound = true
            
            // 서비스에서 BLE 컨트롤러 가져오기
            bleController = bleForegroundService?.getBleController()
            
            if (bleController != null) {
                setupBleControllerListener()
                
                // 직접 스캐너 초기화 (SimpleBleController 우회용)
                initializeDirectScanner()
                
                isInitialized = true
                addLog("✅ Foreground Service에서 BLE 초기화 완료")
                updateStatusDisplay()
                updateButtonStates()
            } else {
                addLog("❌ Foreground Service에서 BLE 초기화 실패")
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Logx.d(TAG, "BLE Foreground Service disconnected")
            bleForegroundService = null
            bleController = null
            isServiceBound = false
            isInitialized = false
            updateStatusDisplay()
            updateButtonStates()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = DataBindingUtil.setContentView(this, R.layout.activity_ble_test)
        
        setupViews()
        checkPermissionsAndInitialize()
    }
    
    private fun setupViews() {
        // 디바이스 목록 설정
        setupDeviceList()
        
        binding.apply {
            // Central 모드 버튼
            btnStartCentral.setOnClickListener {
                if (currentMode == SimpleBleController.BleMode.IDLE) {
                    startCentralMode()
                } else {
                    stopAllOperations()
                }
            }
            
            // Peripheral 모드 버튼
            btnStartPeripheral.setOnClickListener {
                if (currentMode == SimpleBleController.BleMode.IDLE) {
                    startPeripheralMode()
                } else {
                    stopAllOperations()
                }
            }
            
            // 메시지 전송 버튼
            btnSendMessage.setOnClickListener {
                sendTestMessage()
            }
            
            // 하트비트 전송
            btnSendHeartbeat.setOnClickListener {
                sendHeartbeat()
            }
            
            // 센서 데이터 전송
            btnSendSensorData.setOnClickListener {
                sendSensorData()
            }
            
            // 상태 새로고침
            btnRefreshStatus.setOnClickListener {
                if (!isInitialized) {
                    checkPermissionsAndInitialize()
                } else {
                    updateStatusDisplay()
                }
            }
            
            // 로그 지우기
            btnClearLog.setOnClickListener {
                tvLogOutput.text = ""
            }
        }
    }
    
    private fun checkPermissionsAndInitialize() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
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
        
        val deniedPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (deniedPermissions.isNotEmpty()) {
            Logx.d(TAG, "Requesting permissions: $deniedPermissions")
            blePermissionLauncher.launch(deniedPermissions.toTypedArray())
        } else {
            checkBluetoothAndInitialize()
        }
    }
    
    private fun checkBluetoothAndInitialize() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        
        if (bluetoothAdapter == null) {
            addLog("❌ 이 디바이스는 블루투스를 지원하지 않습니다")
            Toast.makeText(this, "블루투스를 지원하지 않는 디바이스입니다", Toast.LENGTH_LONG).show()
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            addLog("⚠️ 블루투스가 비활성화됨 - 활성화 요청 중...")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
        } else {
            addLog("✅ 블루투스 이미 활성화됨")
            initializeBle()
        }
    }
    
    private fun initializeBle() {
        if (isInitialized) return
        
        addLog("Foreground Service로 BLE 초기화 시작...")
        
        // Foreground Service 시작 및 연결
        val serviceIntent = Intent(this, BleForegroundService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun setupBleControllerListener() {
        // BLE 컨트롤러 리스너 설정  
        // SimpleBleController.BleControllerListener의 모든 콜백 메서드 구현
        
        bleController?.setListener(object : SimpleBleController.BleControllerListener {
            override fun onStateChanged(state: BleComponent.ConnectionState) {
                addLog("연결 상태 변경: $state")
                updateStatusDisplay()
                updateButtonStates()
            }
            
            override fun onModeChanged(mode: SimpleBleController.BleMode) {
                currentMode = mode
                addLog("모드 변경: $mode")
                updateStatusDisplay()
                updateButtonStates()
            }
            
            override fun onDeviceConnected(deviceAddress: String) {
                addLog("✅ 디바이스 연결됨: $deviceAddress")
                isScanning = false
                updateStatusDisplay()
                updateButtonStates()
            }
            
            override fun onDeviceDisconnected(deviceAddress: String) {
                addLog("❌ 디바이스 연결 해제됨: $deviceAddress")
                updateStatusDisplay()
                updateButtonStates()
            }
            
            override fun onMessageReceived(type: Byte, data: ByteArray) {
                handleReceivedMessage(type, data)
            }
            
            override fun onError(error: String) {
                addLog("⚠️ 에러: $error")
                updateStatusDisplay()
            }
            
            override fun onDeviceScanned(device: BleDevice) {
                Logx.d(TAG, "🎯 [BleTestActivity] onDeviceScanned called: ${device.displayName} (${device.address})")
                // 스캔된 디바이스를 목록에 추가
                deviceAdapter.updateDevice(device)
                addLog("📡 디바이스 발견: ${device.displayName} (${device.rssi}dBm)")
                Logx.d(TAG, "🎯 [BleTestActivity] deviceAdapter.updateDevice() called")
            }
        })
    }
    
    // 직접 스캐너 초기화 - suspend 함수를 lifecycleScope로 호출
    private fun initializeDirectScanner() {
        try {
            directScanner = kr.open.library.systemmanager.controller.bluetooth.central.BleScanner(this)
            
            // suspend 함수를 coroutine으로 호출
            lifecycleScope.launch {
                try {
                    val initialized = directScanner?.initialize() ?: false
                    
                    if (initialized) {
                        addLog("✅ 직접 스캐너 초기화 완료")
                    } else {
                        addLog("❌ 직접 스캐너 초기화 실패")
                    }
                } catch (e: Exception) {
                    addLog("❌ 직접 스캐너 초기화 오류: ${e.message}")
                    Logx.e(TAG, "Direct scanner initialization error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            addLog("❌ 직접 스캐너 생성 오류: ${e.message}")
            Logx.e(TAG, "Direct scanner creation error: ${e.message}")
        }
    }
    
    // 직접 스캔 리스너 (SimpleBleController 우회)
    private val directScanListener = object : kr.open.library.systemmanager.controller.bluetooth.central.BleScanner.ScanListener {
        override fun onDeviceFound(device: kr.open.library.systemmanager.controller.bluetooth.data.BleDevice) {
            Logx.d(TAG, "🎯 [DirectScan] Device found: ${device.displayName} (${device.address})")
            // 직접 UI 업데이트
            deviceAdapter.updateDevice(device)
            addLog("📡 디바이스 발견: ${device.displayName} (${device.rssi}dBm)")
        }
        
        override fun onScanStarted() {
            addLog("🔍 직접 스캔 시작됨")
        }
        
        override fun onScanStopped() {
            addLog("🛑 직접 스캔 중지됨")
            isScanning = false
            updateButtonStates()
        }
        
        override fun onScanError(error: String) {
            addLog("❌직접 스캔 오류: $error")
            isScanning = false
            updateButtonStates()
        }
    }
    
    private fun startCentralMode() {
        if (!isInitialized) {
            Toast.makeText(this, "BLE가 초기화되지 않았습니다", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isScanning) {
            // 스캔 중이면 중지
            stopScanning()
        } else {
            // 스캔 시작
            startScanning()
        }
    }
    
    private fun startScanning() {
        addLog("🔍 BLE 디바이스 스캔 시작...")
        
        // 이전 디바이스 목록 정리
        deviceAdapter.clearDevices()
        
        // 스캔 전용 모드 시작 (자동 연결 안 함)
        isScanning = true
        updateButtonStates()
        
        // SimpleBleController 우회하고 직접 스캐너 사용 - suspend 함수를 coroutine으로 호출
        lifecycleScope.launch {
            try {
                directScanner?.startScanOnly(directScanListener)
            } catch (e: Exception) {
                addLog("❌ 스캔 시작 오류: ${e.message}")
                isScanning = false
                updateButtonStates()
            }
        }
    }
    
    private fun stopScanning() {
        addLog("🛑 BLE 스캔 중지")
        isScanning = false
        updateButtonStates()
        
        // 직접 스캐너 중지 - suspend 함수를 coroutine으로 호출
        lifecycleScope.launch {
            try {
                directScanner?.stopScan()
            } catch (e: Exception) {
                addLog("❌ 스캔 중지 오류: ${e.message}")
            }
        }
    }
    
    private fun startPeripheralMode() {
        if (!isInitialized) {
            Toast.makeText(this, "BLE가 초기화되지 않았습니다", Toast.LENGTH_SHORT).show()
            return
        }
        
        addLog("📡 Peripheral 모드 시작 - '$TEST_DEVICE_NAME'로 광고 중...")
        
        // suspend 함수를 coroutine으로 호출
        lifecycleScope.launch {
            try {
                bleController?.startAsPeripheral(TEST_DEVICE_NAME)
            } catch (e: Exception) {
                addLog("❌ Peripheral 모드 시작 오류: ${e.message}")
            }
        }
        updateButtonStates()
    }
    
    private fun stopAllOperations() {
        if (!isInitialized) return
        
        addLog("🛑 모든 BLE 작업 중지...")
        
        // suspend 함수를 coroutine으로 호출
        lifecycleScope.launch {
            try {
                bleController?.stopAllOperations()
            } catch (e: Exception) {
                addLog("❌ 작업 중지 오류: ${e.message}")
            }
        }
        updateButtonStates()
    }
    
    private fun sendTestMessage() {
        if (bleController?.isConnected() != true) {
            Toast.makeText(this, "연결된 디바이스가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        
        val message = "Hello from ${if (currentMode == SimpleBleController.BleMode.CENTRAL_MODE) "Central" else "Peripheral"}! Time: ${System.currentTimeMillis()}"
        
        // suspend 함수를 coroutine으로 호출
        lifecycleScope.launch {
            try {
                val success = bleController?.sendTextMessage(message) ?: false
                if (success) {
                    addLog("📤 텍스트 메시지 전송: $message")
                } else {
                    addLog("❌ 텍스트 메시지 전송 실패")
                }
            } catch (e: Exception) {
                addLog("❌ 메시지 전송 오류: ${e.message}")
            }
        }
    }
    
    private fun sendHeartbeat() {
        if (bleController?.isConnected() != true) {
            Toast.makeText(this, "연결된 디바이스가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        
        // suspend 함수를 coroutine으로 호출
        lifecycleScope.launch {
            try {
                val success = bleController?.sendHeartbeat() ?: false
                if (success) {
                    addLog("💓 하트비트 전송 완료")
                } else {
                    addLog("❌ 하트비트 전송 실패")
                }
            } catch (e: Exception) {
                addLog("❌ 하트비트 전송 오류: ${e.message}")
            }
        }
    }
    
    private fun sendSensorData() {
        if (bleController?.isConnected() != true) {
            Toast.makeText(this, "연결된 디바이스가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        
        val temperature = (20..30).random().toFloat() + (0..99).random() / 100f
        val humidity = (40..80).random().toFloat() + (0..99).random() / 100f
        
        // suspend 함수를 coroutine으로 호출하고 BinaryProtocol 사용
        lifecycleScope.launch {
            try {
                val sensorData = BinaryProtocol.createSensorData(temperature, humidity)
                val success = bleController?.sendMessage(BinaryProtocol.MessageType.SENSOR_DATA, sensorData) ?: false
                
                if (success) {
                    addLog("🌡️ 센서 데이터 전송: 온도=${String.format("%.2f", temperature)}°C, 습도=${String.format("%.2f", humidity)}%")
                } else {
                    addLog("❌ 센서 데이터 전송 실패")
                }
            } catch (e: Exception) {
                addLog("❌ 센서 데이터 전송 오류: ${e.message}")
            }
        }
    }
    
    private fun handleReceivedMessage(type: Byte, data: ByteArray) {
        when (type) {
            BinaryProtocol.MessageType.HEARTBEAT -> {
                addLog("💓 하트비트 수신")
            }
            
            BinaryProtocol.MessageType.TEXT_MESSAGE -> {
                val text = BinaryProtocol.parseTextMessage(data)
                addLog("📥 텍스트 메시지 수신: $text")
            }
            
            BinaryProtocol.MessageType.SENSOR_DATA -> {
                val sensorData = BinaryProtocol.parseSensorData(data)
                if (sensorData != null) {
                    val (temp, humidity, timestamp) = sensorData
                    addLog("🌡️ 센서 데이터 수신: 온도=${String.format("%.2f", temp)}°C, 습도=${String.format("%.2f", humidity)}%, 시간=$timestamp")
                } else {
                    addLog("❌ 센서 데이터 파싱 실패")
                }
            }
            
            BinaryProtocol.MessageType.CONTROL_CMD -> {
                val command = BinaryProtocol.parseControlCommand(data)
                if (command != null) {
                    val (cmdId, param) = command
                    addLog("🎮 제어 명령 수신: ID=0x${cmdId.toString(16)}, 파라미터=$param")
                } else {
                    addLog("❌ 제어 명령 파싱 실패")
                }
            }
            
            BinaryProtocol.MessageType.ACK -> {
                addLog("✅ ACK 수신")
            }
            
            BinaryProtocol.MessageType.ERROR -> {
                addLog("⚠️ 에러 메시지 수신: ${String(data)}")
            }
            
            else -> {
                addLog("❓ 알 수 없는 메시지 타입: 0x${type.toString(16)}, 크기: ${data.size}bytes")
            }
        }
    }
    
    private fun updateStatusDisplay() {
        binding.apply {
            val statusText = buildString {
                appendLine("=== BLE 상태 ===")
                
                // 블루투스 어댑터 상태
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter == null) {
                    appendLine("블루투스: 지원 안됨")
                } else {
                    appendLine("블루투스: ${if (bluetoothAdapter.isEnabled) "활성화됨" else "비활성화됨"}")
                    // 내 BLE 디바이스 이름 표시
                    try {
                        val myDeviceName = bluetoothAdapter.name ?: "알 수 없음"
                        appendLine("내 디바이스 이름: $myDeviceName")
                    } catch (e: SecurityException) {
                        appendLine("내 디바이스 이름: 권한 필요")
                    }
                }

                appendLine("모드: $currentMode")
                appendLine("초기화: ${if (isInitialized) "완료" else "미완료"}")

                // 모드별 설정 정보
                when (currentMode) {
                    SimpleBleController.BleMode.PERIPHERAL_MODE -> {
                        appendLine("광고 이름: $TEST_DEVICE_NAME")
                    }
                    SimpleBleController.BleMode.CENTRAL_MODE -> {
                        appendLine("타겟 디바이스: $TARGET_DEVICE_NAME")
                    }
                    else -> {
                        appendLine("Peripheral용 이름: $TEST_DEVICE_NAME")
                        appendLine("Central용 타겟: $TARGET_DEVICE_NAME")
                    }
                }
                
                if (isInitialized) {
                    appendLine("연결 상태: ${bleController?.bleConnectionState?.value}")
                    appendLine("연결된 디바이스: ${bleController?.getConnectedDevice() ?: "없음"}")
                    appendLine("준비 상태: ${bleController?.bleIsReady?.value}")
                    appendLine("서비스 연결: ${if (isServiceBound) "연결됨" else "연결 안됨"}")
                }
                
                appendLine("")
                appendLine("=== 상세 정보 ===")
                if (isInitialized) {
                    append(bleController?.getStatusSummary() ?: "정보 없음")
                }
            }
            
            tvStatus.text = statusText
        }
    }
    
    private fun updateButtonStates() {
        binding.apply {
            val isIdle = currentMode == SimpleBleController.BleMode.IDLE
            val isConnected = isInitialized && (bleController?.isConnected() == true)
            // Central 스캔 버튼
            btnStartCentral.text = if (isScanning) "스캔 중지" else "스캔 시작"
            btnStartCentral.isEnabled = isInitialized && (isIdle || isScanning)

            // Peripheral 모드 버튼 (모든 Android 버전에서 지원)
            btnStartPeripheral.text = if (currentMode == SimpleBleController.BleMode.PERIPHERAL_MODE) "Peripheral 중지" else "Peripheral 시작"
            btnStartPeripheral.isEnabled = isInitialized && (isIdle || currentMode == SimpleBleController.BleMode.PERIPHERAL_MODE)
            btnStartPeripheral.alpha = 1.0f
            
            // 메시지 전송 버튼들
            btnSendMessage.isEnabled = isConnected
            btnSendHeartbeat.isEnabled = isConnected
            btnSendSensorData.isEnabled = isConnected
            
            // 기타 버튼들
            btnRefreshStatus.isEnabled = true
            btnClearLog.isEnabled = true
        }
    }
    
    private fun addLog(message: String) {
        val timestamp = android.text.format.DateFormat.format("HH:mm:ss.SSS", System.currentTimeMillis())
        val logMessage = "[$timestamp] $message"
        
        binding.tvLogOutput.append("$logMessage\n")
        
        // 스크롤을 맨 아래로
        binding.scrollLog.post {
            binding.scrollLog.fullScroll(android.view.View.FOCUS_DOWN)
        }
        
        // Logcat에도 출력
        Logx.i(TAG, message)
    }
    
    override fun onDestroy() {
        // 직접 스캐너 정리 - suspend 함수를 runBlocking으로 호출
        directScanner?.let { scanner ->
            runBlocking {
                try {
                    scanner.cleanup()
                } catch (e: Exception) {
                    Logx.e(TAG, "Error during scanner cleanup: ${e.message}")
                }
            }
        }
        directScanner = null
        
        // Service 연결 해제
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        
        // BLE 컨트롤러는 서비스에서 관리되므로 여기서는 cleanup 안 함
        bleController = null
        bleForegroundService = null
        
        super.onDestroy()
    }
}