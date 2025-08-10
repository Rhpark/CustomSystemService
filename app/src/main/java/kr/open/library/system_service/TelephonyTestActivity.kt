package kr.open.library.system_service

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.open.library.logcat.Logx
import kr.open.library.permissions.PermissionManager
import kr.open.library.system_service.databinding.ActivityTelephonyTestBinding
import kr.open.library.systemmanager.base.SystemServiceException
import kr.open.library.systemmanager.base.getDeveloperMessage
import kr.open.library.systemmanager.base.getUserMessage
import kr.open.library.systemmanager.info.telephony.TelephonyInfo
import kr.open.library.systemmanager.info.network.telephony.data.state.TelephonyNetworkState
import java.text.SimpleDateFormat
import java.util.*

/**
 * TelephonyTestActivity - TelephonyInfo 테스트 액티비티
 * TelephonyInfo Test Activity
 * 
 * TelephonyInfo의 모든 기능을 종합적으로 테스트하는 액티비티입니다.
 * Activity for comprehensive testing of TelephonyInfo functionality.
 * 
 * 주요 테스트 기능 / Main Test Features:
 * - 통신사 정보 조회 / Carrier information retrieval
 * - SIM 카드 상태 확인 / SIM card status checking
 * - 네트워크 타입 및 신호 강도 모니터링 / Network type and signal strength monitoring
 * - 멀티 SIM 지원 테스트 / Multi-SIM support testing
 * - 실시간 콜백 등록/해제 / Real-time callback registration/unregistration
 * - Result 패턴 vs 전통적 방식 비교 / Result pattern vs traditional approach comparison
 * - API 호환성 테스트 / API compatibility testing
 */
class TelephonyTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTelephonyTestBinding
    private lateinit var telephonyInfo: TelephonyInfo
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    private var currentRequestId: String? = null
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionManager.result(this, permissions, currentRequestId)
    }
    
    private val permissionManager: PermissionManager by lazy {
        PermissionManager.getInstance()
    }
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_PHONE_NUMBERS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupBinding()
        setupUI()
        initializeTelephonyInfo()
        
        if (hasRequiredPermissions()) {
            updatePermissionStatus("권한 승인됨", Color.GREEN)
            enableTelephonyFeatures()
        } else {
            updatePermissionStatus("권한 필요", Color.RED)
            requestRequiredPermissions()
        }
    }

    private fun setupBinding() {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_telephony_test)
    }

    private fun setupUI() {
        // 기본 정보 버튼들
        binding.btnRequestPermissions.setOnClickListener { requestRequiredPermissions() }
        binding.btnGetBasicInfo.setOnClickListener { getBasicTelephonyInfo() }
        binding.btnGetCarrierInfo.setOnClickListener { getCarrierInformation() }
        binding.btnGetSimInfo.setOnClickListener { getSimInformation() }
        binding.btnGetNetworkInfo.setOnClickListener { getNetworkInformation() }
        binding.btnGetMultiSimInfo.setOnClickListener { getMultiSimInformation() }
        
        // 실시간 모니터링 버튼들
        binding.btnStartMonitoring.setOnClickListener { startRealTimeMonitoring() }
        binding.btnStopMonitoring.setOnClickListener { stopRealTimeMonitoring() }
        
        // 테스트 버튼들
        binding.btnTestResultPattern.setOnClickListener { testResultPatternApproach() }
        binding.btnTestTraditionalPattern.setOnClickListener { testTraditionalApproach() }
        binding.btnTestApiCompatibility.setOnClickListener { testApiCompatibility() }
        
        // 유틸리티 버튼들
        binding.btnClearLogs.setOnClickListener { clearLogs() }
        binding.btnRefreshAll.setOnClickListener { refreshAllInformation() }
        
        // 초기 UI 상태 설정
        binding.btnStartMonitoring.isEnabled = true
        binding.btnStopMonitoring.isEnabled = false
    }

    private fun initializeTelephonyInfo() {
        telephonyInfo = TelephonyInfo(this)
        appendLog("📱 TelephonyInfo 초기화 완료")
        Logx.d("TelephonyTestActivity initialized")
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestRequiredPermissions() {
        permissionManager.request(
            this, 
            requestPermissionLauncher = requestPermissionLauncher,
            specialPermissionLaunchers = null,
            permissions = requiredPermissions.toList()
        ) { deniedPermissions ->
            if (deniedPermissions.isEmpty()) {
                updatePermissionStatus("모든 권한 승인됨", Color.GREEN)
                enableTelephonyFeatures()
                appendLog("✅ 모든 권한이 승인되었습니다")
            } else {
                updatePermissionStatus("일부 권한 거부됨", Color.YELLOW)
                appendLog("⚠️ 일부 권한이 거부되었습니다: ${deniedPermissions.joinToString()}")
            }
        }
    }

    private fun updatePermissionStatus(text: String, color: Int) {
        binding.tvPermissionStatus.text = text
        binding.tvPermissionStatus.setTextColor(color)
    }

    private fun enableTelephonyFeatures() {
        // 권한이 승인되면 telephony 기능 활성화
        binding.btnGetBasicInfo.isEnabled = true
        binding.btnGetCarrierInfo.isEnabled = true
        binding.btnGetSimInfo.isEnabled = true
        binding.btnGetNetworkInfo.isEnabled = true
        binding.btnGetMultiSimInfo.isEnabled = true
        binding.btnStartMonitoring.isEnabled = true
        binding.btnTestResultPattern.isEnabled = true
        binding.btnTestTraditionalPattern.isEnabled = true
        binding.btnTestApiCompatibility.isEnabled = true
    }

    private fun getBasicTelephonyInfo() {
        appendLog("📱 === 기본 Telephony 정보 ===")
        
        try {
            val carrierName = telephonyInfo.getCarrierName()
            val simState = telephonyInfo.getSimState()
            val simStateString = telephonyInfo.getSimStateString()
            val networkType = telephonyInfo.getNetworkType()
            val networkTypeString = telephonyInfo.getNetworkTypeString()
            val callState = telephonyInfo.getCallState()
            val phoneNumber = telephonyInfo.getPhoneNumber()
            
            appendLog("통신사: ${carrierName ?: "알 수 없음"}")
            appendLog("SIM 상태: $simStateString ($simState)")
            appendLog("네트워크 타입: $networkTypeString ($networkType)")
            appendLog("통화 상태: $callState")
            appendLog("전화번호: ${phoneNumber ?: "알 수 없음"}")
            
        } catch (e: Exception) {
            appendLog("❌ 기본 정보 조회 실패: ${e.message}")
            Logx.e("Basic info retrieval failed", e)
        }
    }

    private fun getCarrierInformation() {
        appendLog("📡 === 통신사 정보 ===")
        
        try {
            val carrierName = telephonyInfo.getCarrierName()
            val mcc = telephonyInfo.getMobileCountryCode()
            val mnc = telephonyInfo.getMobileNetworkCode()
            val isRoaming = telephonyInfo.isNetworkRoaming()
            
            appendLog("통신사 이름: ${carrierName ?: "알 수 없음"}")
            appendLog("MCC (국가코드): ${mcc ?: "알 수 없음"}")
            appendLog("MNC (네트워크코드): ${mnc ?: "알 수 없음"}")
            appendLog("로밍 상태: ${if (isRoaming) "로밍 중" else "일반"}")
            
        } catch (e: Exception) {
            appendLog("❌ 통신사 정보 조회 실패: ${e.message}")
            Logx.e("Carrier info retrieval failed", e)
        }
    }

    private fun getSimInformation() {
        appendLog("💳 === SIM 정보 ===")
        
        try {
            val simState = telephonyInfo.getSimState()
            val simStateString = telephonyInfo.getSimStateString()
            val isSimReady = telephonyInfo.isSimReady()
            val simOperatorName = telephonyInfo.getSimOperatorName()
            val simCountryIso = telephonyInfo.getSimCountryIso()
            
            appendLog("SIM 상태: $simStateString")
            appendLog("SIM 준비 상태: ${if (isSimReady) "준비됨" else "준비되지 않음"}")
            appendLog("SIM 운영자: ${simOperatorName ?: "알 수 없음"}")
            appendLog("SIM 국가: ${simCountryIso ?: "알 수 없음"}")
            
        } catch (e: Exception) {
            appendLog("❌ SIM 정보 조회 실패: ${e.message}")
            Logx.e("SIM info retrieval failed", e)
        }
    }

    private fun getNetworkInformation() {
        appendLog("🌐 === 네트워크 정보 ===")
        
        try {
            val networkType = telephonyInfo.getNetworkType()
            val networkTypeString = telephonyInfo.getNetworkTypeString()
            val dataNetworkType = telephonyInfo.getDataNetworkType()
            val isRoaming = telephonyInfo.isNetworkRoaming()
            
            val currentSignalStrength = telephonyInfo.getCurrentSignalStrength()
            val currentServiceState = telephonyInfo.getCurrentServiceState()
            val currentNetworkState = telephonyInfo.currentNetworkState.value
            
            appendLog("네트워크 타입: $networkTypeString ($networkType)")
            appendLog("데이터 네트워크 타입: $dataNetworkType")
            appendLog("로밍 상태: ${if (isRoaming) "예" else "아니오"}")
            appendLog("신호 강도: ${currentSignalStrength?.let { "사용 가능" } ?: "콜백 등록 필요"}")
            appendLog("서비스 상태: ${currentServiceState?.let { "사용 가능" } ?: "콜백 등록 필요"}")
            appendLog("네트워크 상태: ${currentNetworkState?.let { "${it.networkTypeState}" } ?: "콜백 등록 필요"}")
            
        } catch (e: Exception) {
            appendLog("❌ 네트워크 정보 조회 실패: ${e.message}")
            Logx.e("Network info retrieval failed", e)
        }
    }

    private fun getMultiSimInformation() {
        appendLog("📱📱 === 멀티 SIM 정보 ===")
        
        try {
            val activeSimCount = telephonyInfo.getActiveSimCount()
            val subscriptionInfoList = telephonyInfo.getActiveSubscriptionInfoList()
            val defaultDataSubscriptionInfo = telephonyInfo.getDefaultDataSubscriptionInfo()
            
            appendLog("활성 SIM 수: $activeSimCount")
            
            if (subscriptionInfoList.isNotEmpty()) {
                appendLog("구독 정보 목록:")
                subscriptionInfoList.forEachIndexed { index, info ->
                    appendLog("  SIM ${index + 1}: ${info.displayName} (슬롯: ${info.simSlotIndex})")
                }
            } else {
                appendLog("구독 정보 없음")
            }
            
            defaultDataSubscriptionInfo?.let { info ->
                appendLog("기본 데이터 SIM: ${info.displayName} (슬롯: ${info.simSlotIndex})")
            } ?: appendLog("기본 데이터 SIM: 설정되지 않음")
            
        } catch (e: Exception) {
            appendLog("❌ 멀티 SIM 정보 조회 실패: ${e.message}")
            Logx.e("Multi-SIM info retrieval failed", e)
        }
    }

    private fun startRealTimeMonitoring() {
        appendLog("🔴 === 실시간 모니터링 시작 ===")
        
        telephonyInfo.registerCallback(
            onSignalStrengthChanged = { signalStrength ->
                lifecycleScope.launch {
                    appendLog("📶 신호 강도 변경: ${signalStrength.level}/4")
                }
            },
            onServiceStateChanged = { serviceState ->
                lifecycleScope.launch {
                    val stateString = when (serviceState.state) {
                        ServiceState.STATE_IN_SERVICE -> "서비스 중"
                        ServiceState.STATE_OUT_OF_SERVICE -> "서비스 불가"
                        ServiceState.STATE_EMERGENCY_ONLY -> "긴급 통화만"
                        ServiceState.STATE_POWER_OFF -> "전원 꺼짐"
                        else -> "알 수 없음"
                    }
                    appendLog("📡 서비스 상태 변경: $stateString")
                }
            },
            onNetworkStateChanged = { networkState ->
                lifecycleScope.launch {
                    appendLog("🌐 네트워크 상태 변경: ${networkState.networkTypeState}")
                }
            }
        ).fold(
            onSuccess = {
                appendLog("✅ 실시간 모니터링이 시작되었습니다")
                binding.btnStartMonitoring.isEnabled = false
                binding.btnStopMonitoring.isEnabled = true
            },
            onFailure = { error ->
                appendLog("❌ 모니터링 시작 실패: ${error.message}")
                when (error) {
                    is SystemServiceException -> {
                        appendLog("   상세: ${error.error.getDeveloperMessage()}")
                    }
                }
            }
        )
    }

    private fun stopRealTimeMonitoring() {
        telephonyInfo.unregisterCallback().fold(
            onSuccess = {
                appendLog("⏹️ 실시간 모니터링이 중지되었습니다")
                binding.btnStartMonitoring.isEnabled = true
                binding.btnStopMonitoring.isEnabled = false
            },
            onFailure = { error ->
                appendLog("❌ 모니터링 중지 실패: ${error.message}")
            }
        )
    }

    private fun testResultPatternApproach() {
        appendLog("🚀 === Result 패턴 테스트 ===")
        
        // 통신사 이름 조회 테스트
        telephonyInfo.getCarrierNameSafe().fold(
            onSuccess = { name ->
                appendLog("✅ 통신사 이름 (Result): ${name ?: "알 수 없음"}")
            },
            onFailure = { error ->
                when (error) {
                    is SystemServiceException -> {
                        appendLog("❌ 통신사 이름 조회 실패 (Result): ${error.error.getUserMessage()}")
                    }
                    else -> {
                        appendLog("❌ 통신사 이름 조회 실패 (Result): ${error.message}")
                    }
                }
            }
        )
        
        // SIM 상태 조회 테스트
        telephonyInfo.getSimStateSafe().fold(
            onSuccess = { state ->
                appendLog("✅ SIM 상태 (Result): ${telephonyInfo.getSimStateString()} ($state)")
            },
            onFailure = { error ->
                when (error) {
                    is SystemServiceException -> {
                        appendLog("❌ SIM 상태 조회 실패 (Result): ${error.error.getUserMessage()}")
                    }
                    else -> {
                        appendLog("❌ SIM 상태 조회 실패 (Result): ${error.message}")
                    }
                }
            }
        )
    }

    private fun testTraditionalApproach() {
        appendLog("📊 === 전통적 접근법 테스트 ===")
        
        try {
            val carrierName = telephonyInfo.getCarrierName()
            val simState = telephonyInfo.getSimState()
            val networkType = telephonyInfo.getNetworkType()
            
            appendLog("✅ 통신사 이름 (전통적): ${carrierName ?: "알 수 없음"}")
            appendLog("✅ SIM 상태 (전통적): ${telephonyInfo.getSimStateString()} ($simState)")
            appendLog("✅ 네트워크 타입 (전통적): ${telephonyInfo.getNetworkTypeString()} ($networkType)")
            
        } catch (e: Exception) {
            appendLog("❌ 전통적 접근법 테스트 실패: ${e.message}")
        }
    }

    private fun testApiCompatibility() {
        appendLog("🔧 === API 호환성 테스트 ===")
        
        appendLog("Android API 레벨: ${Build.VERSION.SDK_INT}")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appendLog("✅ 최신 TelephonyCallback 사용 가능")
        } else {
            appendLog("⚠️ 레거시 PhoneStateListener 사용")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            appendLog("✅ 최신 dataNetworkType 사용 가능")
        } else {
            appendLog("⚠️ 레거시 networkType 사용")
        }
        
        // 실제 API 호환성 테스트
        val networkType = telephonyInfo.getNetworkType()
        appendLog("네트워크 타입 (호환성): ${telephonyInfo.getNetworkTypeString()} ($networkType)")
    }

    private fun refreshAllInformation() {
        clearLogs()
        appendLog("🔄 === 전체 정보 새로고침 ===")
        
        getBasicTelephonyInfo()
        appendLog("")
        getCarrierInformation()
        appendLog("")
        getSimInformation()
        appendLog("")
        getNetworkInformation()
        appendLog("")
        getMultiSimInformation()
        
        appendLog("")
        appendLog("✅ 전체 정보 새로고침 완료!")
    }

    private fun clearLogs() {
        binding.tvLogs.text = ""
    }

    private fun appendLog(message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] $message"
        
        val currentText = binding.tvLogs.text.toString()
        binding.tvLogs.text = if (currentText.isEmpty()) {
            logMessage
        } else {
            "$currentText\n$logMessage"
        }
        
        // 스크롤을 맨 아래로 이동
        binding.scrollView.post {
            binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
        
        Logx.d("TelephonyTestActivity: $message")
    }

    override fun onDestroy() {
        try {
            telephonyInfo.onDestroy()
        } catch (e: Exception) {
            Logx.e("Error during cleanup", e)
        }
        super.onDestroy()
    }
}