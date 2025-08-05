package kr.open.library.system_service

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.open.library.system_service.databinding.ActivityBatteryTestBinding
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

    private lateinit var binding: ActivityBatteryTestBinding
    private lateinit var batteryStateInfo: BatteryStateInfo
    private var isMonitoring = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupBinding()
        setupUI()
        initializeBatteryController()
        
        logMessage("BatteryTestActivity initialized")
        logMessage("배터리 테스트 액티비티가 초기화되었습니다")
    }
    
    private fun setupBinding() {
        binding = ActivityBatteryTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    /**
     * UI 설정 및 초기화
     * UI setup and initialization
     */
    private fun setupUI() {
        // 버튼 클릭 리스너 설정
        setupButtonListeners()
    }
    
    /**
     * 버튼 클릭 리스너 설정
     * Setup button click listeners
     */
    private fun setupButtonListeners() {
        binding.btnStartMonitoring.setOnClickListener { startBatteryMonitoring() }
        binding.btnStopMonitoring.setOnClickListener { stopBatteryMonitoring() }
        binding.btnGetInstantInfo.setOnClickListener { getInstantBatteryInfo() }
        binding.btnTestFallback.setOnClickListener { testFallbackMechanism() }
        binding.btnCheckPermissions.setOnClickListener { checkAndRequestPermissions() }
        binding.btnClearLog.setOnClickListener { clearLog() }
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
            binding.tvPermissionStatus.text = "✅ 권한 확인됨 / Permissions Granted"
            binding.tvPermissionStatus.setTextColor(Color.parseColor("#4CAF50"))
            logMessage("✅ BATTERY_STATS permission granted")
        } else {
            binding.tvPermissionStatus.text = "ℹ️ 시스템 권한 / System Permission Only"
            binding.tvPermissionStatus.setTextColor(Color.parseColor("#FF9800"))
            
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
            binding.btnStartMonitoring.isEnabled = false
            binding.btnStopMonitoring.isEnabled = true

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
                binding.btnStartMonitoring.isEnabled = true
                binding.btnStopMonitoring.isEnabled = false

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
                        binding.tvBatteryCapacity.text = "배터리 잔량 / Capacity: ${event.percent}%"
                        logMessage("📊 Capacity updated: ${event.percent}%")
                    }
                    is BatteryStateEvent.OnTemperature -> {
                        val tempDisplay = if (event.temperature == -999.0) {
                            "사용할 수 없음 / Unavailable"
                        } else {
                            "${event.temperature}°C"
                        }
                        binding.tvBatteryTemperature.text = "온도 / Temperature: $tempDisplay"
                        
                        if (event.temperature == -999.0) {
                            logMessage("🌡️ Temperature unavailable (sensor error or not supported)")
                        } else if (event.temperature < -214000000.0) {
                            logMessage("🌡️ Temperature error detected: ${event.temperature}°C (ERROR_VALUE overflow)")
                        } else {
                            logMessage("🌡️ Temperature updated: ${event.temperature}°C (converted from ${(event.temperature * 10).toInt()}/10)")
                        }
                    }
                    is BatteryStateEvent.OnVoltage -> {
                        binding.tvBatteryVoltage.text = "전압 / Voltage: ${event.voltage}V"
                        logMessage("⚡ Voltage updated: ${event.voltage}V")
                    }
                    is BatteryStateEvent.OnCurrentAmpere -> {
                        // 전류와 충전기 연결 상태를 함께 고려하여 충전 상태 판단
                        // Consider both current and charger connection for accurate charging status
                        val chargePlug = batteryStateInfo.getChargePlug()
                        val isCharging = batteryStateInfo.isCharging()
                        val status = when {
                            event.current > 0 && chargePlug != batteryStateInfo.ERROR_VALUE -> "충전중 / Charging"
                            isCharging && chargePlug != batteryStateInfo.ERROR_VALUE -> "충전중 / Charging"
                            else -> "방전중 / Discharging"
                        }
                        binding.tvBatteryCurrent.text = "전류 / Current: ${event.current}µA ($status)"
                        logMessage("🔋 Current updated: ${event.current}µA ($status)")
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
            // 충전 상태를 전류 값과 충전 상태 모두 고려하여 정확하게 판단
            // Determine charging status by considering both current value and charge status
            val chargePlug = batteryStateInfo.getChargePlug()
            val chargeStatus = batteryStateInfo.getChargeStatus()
            val actualChargingStatus = when {
                // 전류가 양수이고 충전기가 연결되어 있으면 충전중
                current > 0 && chargePlug != batteryStateInfo.ERROR_VALUE -> "충전중 / Charging"
                // 충전 상태가 충전중이면서 충전기가 연결되어 있으면 충전중
                isCharging && chargePlug != batteryStateInfo.ERROR_VALUE -> "충전중 / Charging"
                // 그 외의 경우는 방전중
                else -> "방전중 / Discharging"
            }
            
            logMessage("전류 / Current: ${current}µA")
            logMessage("충전 상태 / Charging Status: $chargeStatus")
            logMessage("충전기 연결 / Charger Plugged: ${if (chargePlug != batteryStateInfo.ERROR_VALUE) "연결됨 / Connected" else "미연결 / Disconnected"}")
            logMessage("실제 충전 상태 / Actual Charging: $actualChargingStatus")
            logMessage("배터리 기술 / Technology: $technology")
            logMessage("계산 검증 / Calculation Check: ${chargeCounter/1000.0}mAh (현재) / ${totalCapacity}mAh (총량) = ${(chargeCounter/1000.0/totalCapacity*100).toInt()}% (이론값)")
            logMessage("=".repeat(50))

            // UI도 즉시 업데이트
            binding.tvBatteryCapacity.text = "배터리 잔량 / Capacity: ${capacity}%"
            
            val tempDisplay = if (temperature == -999.0) {
                "사용할 수 없음 / Unavailable"
            } else if (temperature < -214000000.0) {
                "오류 / Error"
            } else {
                "${temperature}°C"
            }
            binding.tvBatteryTemperature.text = "온도 / Temperature: $tempDisplay"
            binding.tvBatteryVoltage.text = "전압 / Voltage: ${voltage}V"
            binding.tvBatteryCurrent.text = "전류 / Current: ${current}µA ($actualChargingStatus)"

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
            
            binding.tvFallbackTest.text = """
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
            binding.tvExecutionLog.text = "${binding.tvExecutionLog.text}$logEntry"
            
            // XML 레이아웃에서 ScrollView를 찾아서 스크롤
            val scrollView = binding.tvExecutionLog.parent.parent as? ScrollView
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
        binding.tvExecutionLog.text = "로그가 지워졌습니다... / Log cleared...\n"
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