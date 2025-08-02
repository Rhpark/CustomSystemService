package kr.open.library.system_service

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.open.library.systemmanager.info.battery.BatteryStateInfo
import kr.open.library.systemmanager.info.battery.BatteryStateEvent

/**
 * BatteryTestActivity - BatteryStateInfo 테스트 액티비티
 * BatteryStateInfo Test Activity
 * 
 * 배터리 상태 모니터링 기능을 종합적으로 테스트하는 액티비티입니다.
 * Activity for comprehensive testing of battery state monitoring functionality.
 * 
 * 주요 테스트 기능 / Main Test Features:
 * - 배터리 정보 실시간 모니터링 / Real-time battery information monitoring
 * - PowerProfile fallback 메커니즘 테스트 / PowerProfile fallback mechanism testing
 * - StateFlow 기반 반응형 업데이트 / StateFlow-based reactive updates
 * - 배터리 상태 이벤트 관찰 / Battery state event observation
 * - 권한 관리 및 확인 / Permission management and verification
 * - Hidden API 호환성 테스트 / Hidden API compatibility testing
 */
class BatteryTestActivity : AppCompatActivity() {

    private lateinit var batteryStateInfo: BatteryStateInfo
    private lateinit var logTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var capacityTextView: TextView
    private lateinit var temperatureTextView: TextView
    private lateinit var voltageTextView: TextView
    private lateinit var currentTextView: TextView
    private lateinit var fallbackTestTextView: TextView
    
    // UI 컨트롤 요소들 / UI Control Elements
    private lateinit var btnStartMonitoring: Button
    private lateinit var btnStopMonitoring: Button
    private lateinit var btnGetInstantInfo: Button
    private lateinit var btnTestFallback: Button
    private lateinit var btnCheckPermissions: Button
    private lateinit var btnClearLog: Button
    
    private var isMonitoring = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupUI()
        initializeBatteryController()
        
        logMessage("BatteryTestActivity initialized")
        logMessage("배터리 테스트 액티비티가 초기화되었습니다")
    }

    /**
     * UI 설정 및 초기화
     * UI setup and initialization
     */
    private fun setupUI() {
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 제목
        val titleTextView = TextView(this).apply {
            text = "🔋 Battery State Monitor Test\n배터리 상태 모니터 테스트"
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        // 권한 상태 표시
        statusTextView = TextView(this).apply {
            text = "권한 상태 확인 중... / Checking permissions..."
            textSize = 14f
            setTextColor(Color.BLUE)
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.LTGRAY)
        }

        // 실시간 배터리 정보 카드
        val batteryInfoCard = createBatteryInfoCard()

        // Fallback 테스트 카드
        val fallbackTestCard = createFallbackTestCard()

        // 컨트롤 버튼들
        val controlButtons = createControlButtons()

        // 실행 로그
        val logCard = createLogCard()

        // 모든 요소를 메인 레이아웃에 추가
        mainLayout.addView(titleTextView)
        mainLayout.addView(statusTextView)
        mainLayout.addView(batteryInfoCard)
        mainLayout.addView(fallbackTestCard)
        mainLayout.addView(controlButtons)
        mainLayout.addView(logCard)

        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }

    /**
     * 배터리 정보 카드 생성
     * Create battery information card
     */
    private fun createBatteryInfoCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#E3F2FD"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 16) }
        }

        val cardTitle = TextView(this).apply {
            text = "📊 실시간 배터리 정보 / Real-time Battery Info"
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 16)
        }

        capacityTextView = TextView(this).apply {
            text = "배터리 잔량 / Capacity: 대기 중... / Waiting..."
            textSize = 14f
            setTextColor(Color.BLACK)
            setPadding(0, 4, 0, 4)
        }

        temperatureTextView = TextView(this).apply {
            text = "온도 / Temperature: 대기 중... / Waiting..."
            textSize = 14f
            setTextColor(Color.BLACK)
            setPadding(0, 4, 0, 4)
        }

        voltageTextView = TextView(this).apply {
            text = "전압 / Voltage: 대기 중... / Waiting..."
            textSize = 14f
            setTextColor(Color.BLACK)
            setPadding(0, 4, 0, 4)
        }

        currentTextView = TextView(this).apply {
            text = "전류 / Current: 대기 중... / Waiting..."
            textSize = 14f
            setTextColor(Color.BLACK)
            setPadding(0, 4, 0, 4)
        }

        card.addView(cardTitle)
        card.addView(capacityTextView)
        card.addView(temperatureTextView)
        card.addView(voltageTextView)
        card.addView(currentTextView)

        return card
    }

    /**
     * Fallback 테스트 카드 생성
     * Create fallback test card
     */
    private fun createFallbackTestCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#FFF3E0"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }

        val cardTitle = TextView(this).apply {
            text = "🛡️ Fallback 메커니즘 테스트 / Fallback Mechanism Test"
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 16)
        }

        fallbackTestTextView = TextView(this).apply {
            text = "PowerProfile 상태: 확인 중... / PowerProfile Status: Checking..."
            textSize = 14f
            setTextColor(Color.BLACK)
            setPadding(0, 4, 0, 4)
        }

        card.addView(cardTitle)
        card.addView(fallbackTestTextView)

        return card
    }

    /**
     * 컨트롤 버튼들 생성
     * Create control buttons
     */
    private fun createControlButtons(): LinearLayout {
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }

        // 모니터링 관련 버튼들
        val monitoringRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
        }

        btnStartMonitoring = Button(this).apply {
            text = "모니터링 시작\nStart Monitoring"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(0, 0, 4, 0) }
            setOnClickListener { startBatteryMonitoring() }
        }

        btnStopMonitoring = Button(this).apply {
            text = "모니터링 중지\nStop Monitoring"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(4, 0, 0, 0) }
            setOnClickListener { stopBatteryMonitoring() }
            isEnabled = false
        }

        monitoringRow.addView(btnStartMonitoring)
        monitoringRow.addView(btnStopMonitoring)

        // 테스트 관련 버튼들
        val testRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
        }

        btnGetInstantInfo = Button(this).apply {
            text = "즉시 정보 조회\nGet Instant Info"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(0, 0, 4, 0) }
            setOnClickListener { getInstantBatteryInfo() }
        }

        btnTestFallback = Button(this).apply {
            text = "Fallback 테스트\nTest Fallback"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(4, 0, 0, 0) }
            setOnClickListener { testFallbackMechanism() }
        }

        testRow.addView(btnGetInstantInfo)
        testRow.addView(btnTestFallback)

        // 유틸리티 버튼들
        val utilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        btnCheckPermissions = Button(this).apply {
            text = "권한 상태 확인\nCheck Permission Status"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(0, 0, 4, 0) }
            setOnClickListener { checkAndRequestPermissions() }
        }

        btnClearLog = Button(this).apply {
            text = "로그 지우기\nClear Log"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(4, 0, 0, 0) }
            setOnClickListener { clearLog() }
        }

        utilityRow.addView(btnCheckPermissions)
        utilityRow.addView(btnClearLog)

        buttonLayout.addView(monitoringRow)
        buttonLayout.addView(testRow)
        buttonLayout.addView(utilityRow)

        return buttonLayout
    }

    /**
     * 로그 카드 생성
     * Create log card
     */
    private fun createLogCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400 // 고정 높이
            )
        }

        val cardTitle = TextView(this).apply {
            text = "📝 실행 로그 / Execution Log"
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 16)
        }

        val logScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        logTextView = TextView(this).apply {
            text = "로그가 여기에 표시됩니다...\nLogs will be displayed here..."
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.WHITE)
        }

        logScrollView.addView(logTextView)
        card.addView(cardTitle)
        card.addView(logScrollView)

        return card
    }

    /**
     * BatteryStateInfo 초기화
     * Initialize BatteryStateInfo
     */
    private fun initializeBatteryController() {
        try {
            batteryStateInfo = BatteryStateInfo(this)
            logMessage("✅ BatteryStateInfo initialized successfully")
            logMessage("✅ BatteryStateInfo가 성공적으로 초기화되었습니다")
            
            checkAndRequestPermissions()
        } catch (e: Exception) {
            logMessage("❌ Failed to initialize BatteryStateInfo: ${e.message}")
            logMessage("❌ BatteryStateInfo 초기화 실패: ${e.message}")
        }
    }

    /**
     * 권한 확인 (BATTERY_STATS는 시스템 권한으로 요청 불가)
     * Check permissions (BATTERY_STATS is system permission, cannot be requested)
     */
    private fun checkAndRequestPermissions() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BATTERY_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // API 23 이하에서는 자동으로 권한이 있다고 가정
        }

        if (hasPermission) {
            statusTextView.text = "✅ 권한 확인됨 / Permissions Granted"
            statusTextView.setTextColor(Color.parseColor("#4CAF50"))
            logMessage("✅ BATTERY_STATS permission granted")
        } else {
            statusTextView.text = "ℹ️ 시스템 권한 / System Permission Only"
            statusTextView.setTextColor(Color.parseColor("#FF9800"))
            
            logMessage("ℹ️ BATTERY_STATS is a system permission - not available for regular apps")
            logMessage("ℹ️ BATTERY_STATS는 시스템 권한입니다 - 일반 앱에서는 사용할 수 없음")
            logMessage("ℹ️ BatteryStateInfo will work without this permission using public APIs")
            logMessage("ℹ️ BatteryStateInfo는 이 권한 없이도 공개 API를 사용하여 작동합니다")
        }
    }

    /**
     * 배터리 모니터링 시작
     * Start battery monitoring
     */
    private fun startBatteryMonitoring() {
        if (isMonitoring) {
            logMessage("⚠️ Monitoring already started / 이미 모니터링이 시작되었습니다")
            return
        }

        try {
            // BroadcastReceiver 등록
            val registerResult = batteryStateInfo.registerBatteryReceiver()
            if (!registerResult) {
                logMessage("❌ Failed to register battery receiver")
                logMessage("❌ 배터리 리시버 등록 실패")
                return
            }

            // 모니터링 시작
            val startResult = batteryStateInfo.updateStart(lifecycleScope)
            if (!startResult) {
                logMessage("❌ Failed to start monitoring")
                logMessage("❌ 모니터링 시작 실패")
                return
            }

            // StateFlow 관찰 시작
            observeBatteryStateFlow()

            isMonitoring = true
            btnStartMonitoring.isEnabled = false
            btnStopMonitoring.isEnabled = true

            logMessage("🚀 Battery monitoring started successfully")
            logMessage("🚀 배터리 모니터링이 성공적으로 시작되었습니다")
            
        } catch (e: Exception) {
            logMessage("❌ Error starting monitoring: ${e.message}")
            logMessage("❌ 모니터링 시작 오류: ${e.message}")
        }
    }

    /**
     * 배터리 모니터링 중지
     * Stop battery monitoring
     */
    private fun stopBatteryMonitoring() {
        if (!isMonitoring) {
            logMessage("⚠️ Monitoring not started / 모니터링이 시작되지 않았습니다")
            return
        }

        try {
            val stopResult = batteryStateInfo.updateStop()
            val unregisterResult = batteryStateInfo.unRegisterReceiver()

            if (stopResult && unregisterResult) {
                isMonitoring = false
                btnStartMonitoring.isEnabled = true
                btnStopMonitoring.isEnabled = false

                logMessage("🛑 Battery monitoring stopped successfully")
                logMessage("🛑 배터리 모니터링이 성공적으로 중지되었습니다")
            } else {
                logMessage("⚠️ Some issues occurred while stopping monitoring")
                logMessage("⚠️ 모니터링 중지 중 일부 문제가 발생했습니다")
            }
        } catch (e: Exception) {
            logMessage("❌ Error stopping monitoring: ${e.message}")
            logMessage("❌ 모니터링 중지 오류: ${e.message}")
        }
    }

    /**
     * StateFlow 관찰
     * Observe StateFlow
     */
    private fun observeBatteryStateFlow() {
        lifecycleScope.launch {
            batteryStateInfo.sfUpdate.collect { event ->
                when (event) {
                    is BatteryStateEvent.OnCapacity -> {
                        capacityTextView.text = "배터리 잔량 / Capacity: ${event.percent}%"
                        logMessage("📊 Capacity updated: ${event.percent}%")
                    }
                    is BatteryStateEvent.OnTemperature -> {
                        val tempDisplay = if (event.temperature == -999.0) {
                            "사용할 수 없음 / Unavailable"
                        } else {
                            "${event.temperature}°C"
                        }
                        temperatureTextView.text = "온도 / Temperature: $tempDisplay"
                        
                        if (event.temperature == -999.0) {
                            logMessage("🌡️ Temperature unavailable (sensor error or not supported)")
                        } else if (event.temperature < -214000000.0) {
                            logMessage("🌡️ Temperature error detected: ${event.temperature}°C (ERROR_VALUE overflow)")
                        } else {
                            logMessage("🌡️ Temperature updated: ${event.temperature}°C (converted from ${(event.temperature * 10).toInt()}/10)")
                        }
                    }
                    is BatteryStateEvent.OnVoltage -> {
                        voltageTextView.text = "전압 / Voltage: ${event.voltage}V"
                        logMessage("⚡ Voltage updated: ${event.voltage}V")
                    }
                    is BatteryStateEvent.OnCurrentAmpere -> {
                        val status = if (event.current > 0) "충전중 / Charging" else "방전중 / Discharging"
                        currentTextView.text = "전류 / Current: ${event.current}µA ($status)"
                        logMessage("🔋 Current updated: ${event.current}µA")
                    }
                    is BatteryStateEvent.OnChargePlug -> {
                        val plugType = when (event.type) {
                            1 -> "AC"
                            2 -> "USB"
                            4 -> "Wireless"
                            else -> "None"
                        }
                        logMessage("🔌 Charge plug updated: $plugType")
                    }
                    is BatteryStateEvent.OnChargeStatus -> {
                        val statusText = when (event.status) {
                            2 -> "충전중 / Charging"
                            3 -> "방전중 / Discharging"
                            5 -> "완충 / Full"
                            else -> "알 수 없음 / Unknown"
                        }
                        logMessage("🔋 Charge status: $statusText")
                    }
                    else -> {
                        logMessage("📡 Battery event: ${event.javaClass.simpleName}")
                    }
                }
            }
        }
    }

    /**
     * 즉시 배터리 정보 조회
     * Get instant battery information
     */
    private fun getInstantBatteryInfo() {
        try {
            val capacity = batteryStateInfo.getCapacity()
            val temperature = batteryStateInfo.getTemperature()
            val voltage = batteryStateInfo.getVoltage()
            val current = batteryStateInfo.getCurrentAmpere()
            val chargeCounter = batteryStateInfo.getChargeCounter()
            val totalCapacity = batteryStateInfo.getTotalCapacity()
            val isCharging = batteryStateInfo.isCharging()
            val technology = batteryStateInfo.getTechnology()

            logMessage("📊 === 즉시 배터리 정보 / Instant Battery Info ===")
            logMessage("배터리 잔량 / Capacity: ${capacity}%")
            logMessage("현재 충전량 / Current Charge: ${chargeCounter}µAh (${chargeCounter/1000.0}mAh)")
            logMessage("총 배터리 용량 / Total Capacity: ${totalCapacity}mAh")
            if (temperature == -999.0) {
                logMessage("온도 / Temperature: 사용할 수 없음 / Unavailable (센서 오류 또는 미지원)")
            } else if (temperature < -214000000.0) {
                logMessage("온도 / Temperature: 오류 감지 ${temperature}°C (ERROR_VALUE 오버플로우)")
            } else {
                logMessage("온도 / Temperature: ${temperature}°C (원본값: ${(temperature * 10).toInt()}/10)")
            }
            logMessage("전압 / Voltage: ${voltage}V")
            logMessage("전류 / Current: ${current}µA")
            logMessage("충전 상태 / Charging: $isCharging")
            logMessage("배터리 기술 / Technology: $technology")
            logMessage("계산 검증 / Calculation Check: ${chargeCounter/1000.0}mAh (현재) / ${totalCapacity}mAh (총량) = ${(chargeCounter/1000.0/totalCapacity*100).toInt()}% (이론값)")
            logMessage("=".repeat(50))

            // UI도 즉시 업데이트
            capacityTextView.text = "배터리 잔량 / Capacity: ${capacity}%"
            
            val tempDisplay = if (temperature == -999.0) {
                "사용할 수 없음 / Unavailable"
            } else if (temperature < -214000000.0) {
                "오류 / Error"
            } else {
                "${temperature}°C"
            }
            temperatureTextView.text = "온도 / Temperature: $tempDisplay"
            voltageTextView.text = "전압 / Voltage: ${voltage}V"
            val chargeStatus = if (isCharging) "충전중 / Charging" else "방전중 / Discharging"
            currentTextView.text = "전류 / Current: ${current}µA ($chargeStatus)"

        } catch (e: Exception) {
            logMessage("❌ Error getting instant battery info: ${e.message}")
            logMessage("❌ 즉시 배터리 정보 조회 오류: ${e.message}")
        }
    }

    /**
     * Fallback 메커니즘 테스트
     * Test fallback mechanism
     */
    private fun testFallbackMechanism() {
        try {
            logMessage("🛡️ === Fallback 메커니즘 테스트 시작 / Starting Fallback Test ===")

            // PowerProfile 가용성 확인
            val powerProfile = kr.open.library.systemmanager.info.battery.power.PowerProfile(this)
            val isPowerProfileAvailable = powerProfile.isPowerProfileAvailable()
            
            logMessage("PowerProfile 사용 가능 / Available: $isPowerProfileAvailable")
            
            // 총 배터리 용량을 여러 방법으로 조회 (fallback 테스트)
            val totalCapacity = batteryStateInfo.getTotalCapacity()
            logMessage("총 배터리 용량 / Total Capacity: ${totalCapacity}mAh")
            
            // 기기 정보
            logMessage("기기 제조사 / Manufacturer: ${Build.MANUFACTURER}")
            logMessage("기기 모델 / Model: ${Build.MODEL}")
            logMessage("Android 버전 / Version: ${Build.VERSION.SDK_INT}")
            
            // Fallback 테스트 결과 UI 업데이트
            val fallbackStatus = if (isPowerProfileAvailable) {
                "✅ PowerProfile 사용 가능 / Available"
            } else {
                "⚠️ PowerProfile 불가, Fallback 사용 / Unavailable, using Fallback"
            }
            
            fallbackTestTextView.text = """
                PowerProfile: $fallbackStatus
                총 용량 / Total: ${totalCapacity}mAh
                Android API: ${Build.VERSION.SDK_INT}
                제조사 / Manufacturer: ${Build.MANUFACTURER}
            """.trimIndent()

            logMessage("🛡️ === Fallback 테스트 완료 / Fallback Test Completed ===")

        } catch (e: Exception) {
            logMessage("❌ Error testing fallback mechanism: ${e.message}")
            logMessage("❌ Fallback 메커니즘 테스트 오류: ${e.message}")
        }
    }

    /**
     * 로그 메시지 추가
     * Add log message
     */
    private fun logMessage(message: String) {
        runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val logEntry = "[$timestamp] $message\n"
            logTextView.text = "${logTextView.text}$logEntry"
            
            // 로그가 너무 길어지면 스크롤
            val scrollView = logTextView.parent as? ScrollView
            scrollView?.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    /**
     * 로그 지우기
     * Clear log
     */
    private fun clearLog() {
        logTextView.text = "로그가 지워졌습니다... / Log cleared...\n"
        logMessage("🗑️ Log cleared / 로그가 지워졌습니다")
    }


    override fun onDestroy() {
        super.onDestroy()
        try {
            if (isMonitoring) {
                batteryStateInfo.updateStop()
                batteryStateInfo.unRegisterReceiver()
            }
            batteryStateInfo.onDestroy()
            logMessage("🧹 BatteryTestActivity destroyed and resources cleaned")
        } catch (e: Exception) {
            logMessage("❌ Error during cleanup: ${e.message}")
        }
    }
}