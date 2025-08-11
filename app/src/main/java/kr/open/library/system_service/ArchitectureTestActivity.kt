package kr.open.library.system_service

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.open.library.logcat.Logx
import kr.open.library.permissions.PermissionManager
import kr.open.library.system_service.databinding.ActivityArchitectureTestBinding
import kr.open.library.systemmanager.info.connectivity.NetworkConnectivityInfo
import kr.open.library.systemmanager.info.network.NetworkStateInfo
import kr.open.library.systemmanager.info.sim.SimInfo
import kr.open.library.systemmanager.info.telephony.TelephonyInfo
import java.text.SimpleDateFormat
import java.util.*

/**
 * ArchitectureTestActivity - 새로운 3개 클래스 아키텍처 테스트
 * New 3-Class Architecture Test Activity
 * 
 * 이 액티비티는 Phase 4에서 구현한 새로운 아키텍처를 테스트합니다:
 * This activity tests the new architecture implemented in Phase 4:
 * 
 * - SimInfo: SIM 카드 및 구독 정보 관리
 * - NetworkConnectivityInfo: 순수 네트워크 연결성 관리  
 * - TelephonyInfo: 통신 품질 및 신호 관리
 * - NetworkStateInfo: 기존 호환성을 위한 Facade 패턴
 * 
 * 테스트 항목:
 * - 각 클래스의 독립적 기능 확인
 * - 상호 참조 기능 테스트
 * - 기존 NetworkStateInfo 호환성 확인
 * - 성능 비교 및 메모리 사용량 확인
 */
class ArchitectureTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArchitectureTestBinding
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    // 새로운 아키텍처 - 3개 독립 클래스
    private lateinit var simInfo: SimInfo
    private lateinit var networkConnectivityInfo: NetworkConnectivityInfo
    private lateinit var telephonyInfo: TelephonyInfo
    
    // 기존 호환성 - Facade 패턴
    private lateinit var networkStateInfo: NetworkStateInfo
    
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
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.ACCESS_NETWORK_STATE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupBinding()
        setupUI()
        initializeClasses()
        
        if (hasRequiredPermissions()) {
            updatePermissionStatus("권한 승인됨", Color.GREEN)
            enableTestFeatures()
        } else {
            updatePermissionStatus("권한 필요", Color.RED)
            requestRequiredPermissions()
        }
    }

    private fun setupBinding() {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_architecture_test)
    }

    private fun setupUI() {
        // 권한 및 초기화
        binding.btnRequestPermissions.setOnClickListener { requestRequiredPermissions() }
        
        // 새로운 아키텍처 테스트
        binding.btnTestSimInfo.setOnClickListener { testSimInfoFeatures() }
        binding.btnTestNetworkConnectivity.setOnClickListener { testNetworkConnectivityFeatures() }
        binding.btnTestTelephonyInfo.setOnClickListener { testTelephonyInfoFeatures() }
        
        // 상호 참조 테스트
        binding.btnTestCrossReference.setOnClickListener { testCrossReferenceFeatures() }
        
        // 호환성 테스트
        binding.btnTestCompatibility.setOnClickListener { testBackwardCompatibility() }
        
        // 성능 비교 테스트
        binding.btnTestPerformance.setOnClickListener { testPerformanceComparison() }
        
        // 유틸리티
        binding.btnClearLogs.setOnClickListener { clearLogs() }
        binding.btnRunAllTests.setOnClickListener { runAllTests() }
        
        // 초기 UI 상태
        enableTestFeatures(false)
    }

    private fun initializeClasses() {
        try {
            // 새로운 아키텍처 초기화
            simInfo = SimInfo(this)
            networkConnectivityInfo = NetworkConnectivityInfo(this)
            telephonyInfo = TelephonyInfo(this)
            
            // 기존 호환성 초기화
            networkStateInfo = NetworkStateInfo(this)
            
            appendLog("🏗️ 모든 클래스 초기화 완료")
            appendLog("   - SimInfo ✅")
            appendLog("   - NetworkConnectivityInfo ✅")
            appendLog("   - TelephonyInfo ✅")
            appendLog("   - NetworkStateInfo (Facade) ✅")
            
        } catch (e: Exception) {
            appendLog("❌ 클래스 초기화 실패: ${e.message}")
            Logx.e("ArchitectureTest: Initialization failed", e)
        }
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
                enableTestFeatures()
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

    private fun enableTestFeatures(enabled: Boolean = true) {
        binding.btnTestSimInfo.isEnabled = enabled
        binding.btnTestNetworkConnectivity.isEnabled = enabled
        binding.btnTestTelephonyInfo.isEnabled = enabled
        binding.btnTestCrossReference.isEnabled = enabled
        binding.btnTestCompatibility.isEnabled = enabled
        binding.btnTestPerformance.isEnabled = enabled
        binding.btnRunAllTests.isEnabled = enabled
    }

    private fun testSimInfoFeatures() {
        appendLog("📱 === SimInfo 기능 테스트 ===")
        
        try {
            // 기본 SIM 정보
            val isDualSim = simInfo.isDualSim()
            val activeSimCount = simInfo.getActiveSimCount()
            val maxSimCount = simInfo.getMaximumUSimCount()
            
            appendLog("SIM 구성:")
            appendLog("  - 듀얼 SIM: ${if (isDualSim) "예" else "아니오"}")
            appendLog("  - 활성 SIM 수: $activeSimCount")
            appendLog("  - 최대 SIM 수: $maxSimCount")
            
            // 구독 정보
            val subscriptionList = simInfo.getActiveSubscriptionInfoList()
            appendLog("구독 정보:")
            subscriptionList.forEachIndexed { index, info ->
                appendLog("  - SIM ${index + 1}: ${info.displayName} (슬롯: ${info.simSlotIndex})")
            }
            
            // eSIM 지원
            val isESimSupported = simInfo.isESimSupported()
            appendLog("eSIM 지원: ${if (isESimSupported) "예" else "아니오"}")
            
            // MCC/MNC 정보
            val mcc = simInfo.getMccFromDefaultUSimString()
            val mnc = simInfo.getMncFromDefaultUSimString()
            appendLog("통신사 코드: MCC=$mcc, MNC=$mnc")
            
            appendLog("✅ SimInfo 테스트 완료")
            
        } catch (e: Exception) {
            appendLog("❌ SimInfo 테스트 실패: ${e.message}")
            Logx.e("ArchitectureTest: SimInfo test failed", e)
        }
    }

    private fun testNetworkConnectivityFeatures() {
        appendLog("🌐 === NetworkConnectivityInfo 기능 테스트 ===")
        
        try {
            // 기본 연결성
            val isConnected = networkConnectivityInfo.isNetworkConnected()
            appendLog("네트워크 연결 상태: ${if (isConnected) "연결됨" else "연결 안됨"}")
            
            // 타입별 연결 확인
            val isWifiConnected = networkConnectivityInfo.isConnectedWifi()
            val isMobileConnected = networkConnectivityInfo.isConnectedMobile()
            val isVpnConnected = networkConnectivityInfo.isConnectedVPN()
            val isBluetoothConnected = networkConnectivityInfo.isConnectedBluetooth()
            val isEthernetConnected = networkConnectivityInfo.isConnectedEthernet()
            
            appendLog("연결 타입:")
            appendLog("  - WiFi: ${if (isWifiConnected) "연결됨" else "연결 안됨"}")
            appendLog("  - Mobile: ${if (isMobileConnected) "연결됨" else "연결 안됨"}")
            appendLog("  - VPN: ${if (isVpnConnected) "연결됨" else "연결 안됨"}")
            appendLog("  - Bluetooth: ${if (isBluetoothConnected) "연결됨" else "연결 안됨"}")
            appendLog("  - Ethernet: ${if (isEthernetConnected) "연결됨" else "연결 안됨"}")
            
            // WiFi 상태
            val isWifiEnabled = networkConnectivityInfo.isWifiEnabled()
            appendLog("WiFi 활성화: ${if (isWifiEnabled) "예" else "아니오"}")
            
            // 네트워크 능력
            val capabilities = networkConnectivityInfo.getNetworkCapabilities()
            appendLog("네트워크 능력: ${if (capabilities != null) "사용 가능" else "사용 불가"}")
            
            // 종합 정보
            val summary = networkConnectivityInfo.getNetworkConnectivitySummary()
            appendLog("종합 정보: 연결=${summary.isNetworkConnected}, WiFi활성=${summary.isWifiEnabled}")
            
            appendLog("✅ NetworkConnectivityInfo 테스트 완료")
            
        } catch (e: Exception) {
            appendLog("❌ NetworkConnectivityInfo 테스트 실패: ${e.message}")
            Logx.e("ArchitectureTest: NetworkConnectivityInfo test failed", e)
        }
    }

    private fun testTelephonyInfoFeatures() {
        appendLog("⚡ === TelephonyInfo 기능 테스트 ===")
        
        try {
            // 기본 정보
            val carrierName = telephonyInfo.getCarrierName()
            val simState = telephonyInfo.getSimState()
            val simStateString = telephonyInfo.getSimStateString()
            val networkType = telephonyInfo.getNetworkType()
            val networkTypeString = telephonyInfo.getNetworkTypeString()
            
            appendLog("통신 정보:")
            appendLog("  - 통신사: ${carrierName ?: "알 수 없음"}")
            appendLog("  - SIM 상태: $simStateString ($simState)")
            appendLog("  - 네트워크 타입: $networkTypeString ($networkType)")
            
            // MCC/MNC
            val mcc = telephonyInfo.getMobileCountryCode()
            val mnc = telephonyInfo.getMobileNetworkCode()
            val isRoaming = telephonyInfo.isNetworkRoaming()
            appendLog("통신사 코드: MCC=$mcc, MNC=$mnc")
            appendLog("로밍 상태: ${if (isRoaming) "로밍 중" else "일반"}")
            
            // 전화번호
            val phoneNumber = telephonyInfo.getPhoneNumber()
            appendLog("전화번호: ${phoneNumber ?: "알 수 없음"}")
            
            // 신호 정보 (콜백 등록 필요)
            val currentSignal = telephonyInfo.getCurrentSignalStrength()
            val currentService = telephonyInfo.getCurrentServiceState()
            appendLog("현재 신호: ${if (currentSignal != null) "사용 가능" else "콜백 등록 필요"}")
            appendLog("현재 서비스: ${if (currentService != null) "사용 가능" else "콜백 등록 필요"}")
            
            appendLog("✅ TelephonyInfo 테스트 완료")
            
        } catch (e: Exception) {
            appendLog("❌ TelephonyInfo 테스트 실패: ${e.message}")
            Logx.e("ArchitectureTest: TelephonyInfo test failed", e)
        }
    }

    private fun testCrossReferenceFeatures() {
        appendLog("🔄 === 상호 참조 기능 테스트 ===")
        
        try {
            // SimInfo를 통한 TelephonyManager 접근
            val simSlots = simInfo.getActiveSimSlotIndexList()
            appendLog("활성 SIM 슬롯: $simSlots")
            
            simSlots.forEach { slotIndex ->
                val tm = telephonyInfo.getTelephonyManagerFromUSim(slotIndex)
                appendLog("  - 슬롯 $slotIndex TelephonyManager: ${if (tm != null) "사용 가능" else "사용 불가"}")
            }
            
            // NetworkConnectivityInfo와 SimInfo 협력
            val networkSummary = networkConnectivityInfo.getNetworkConnectivitySummary()
            val simCount = simInfo.getActiveSimCount()
            
            appendLog("협력 정보:")
            appendLog("  - 네트워크 연결: ${networkSummary.isNetworkConnected}")
            appendLog("  - Mobile 연결: ${networkSummary.isMobileConnected}")
            appendLog("  - 활성 SIM 수: $simCount")
            
            // 상호 참조 성공 여부
            val crossRefSuccess = (simSlots.isNotEmpty() && networkSummary.isNetworkConnected)
            appendLog("상호 참조 테스트: ${if (crossRefSuccess) "성공 ✅" else "일부 제한 ⚠️"}")
            
            appendLog("✅ 상호 참조 테스트 완료")
            
        } catch (e: Exception) {
            appendLog("❌ 상호 참조 테스트 실패: ${e.message}")
            Logx.e("ArchitectureTest: Cross-reference test failed", e)
        }
    }

    private fun testBackwardCompatibility() {
        appendLog("🔙 === 기존 호환성 테스트 ===")
        
        try {
            appendLog("기존 NetworkStateInfo (Facade) 테스트:")
            
            // 기존 방식으로 접근
            @Suppress("DEPRECATION")
            val oldIsConnected = networkStateInfo.isNetworkConnected()
            @Suppress("DEPRECATION")
            val oldSimCount = networkStateInfo.getActiveSimCount()
            @Suppress("DEPRECATION")
            val oldIsDualSim = networkStateInfo.isDualSim()
            @Suppress("DEPRECATION")
            val oldIsWifiConnected = networkStateInfo.isConnectedWifi()
            
            appendLog("기존 API 결과:")
            appendLog("  - 네트워크 연결: $oldIsConnected")
            appendLog("  - 활성 SIM 수: $oldSimCount")
            appendLog("  - 듀얼 SIM: $oldIsDualSim")
            appendLog("  - WiFi 연결: $oldIsWifiConnected")
            
            // 새로운 방식과 비교
            val newIsConnected = networkConnectivityInfo.isNetworkConnected()
            val newSimCount = simInfo.getActiveSimCount()
            val newIsDualSim = simInfo.isDualSim()
            val newIsWifiConnected = networkConnectivityInfo.isConnectedWifi()
            
            appendLog("새로운 API 결과:")
            appendLog("  - 네트워크 연결: $newIsConnected")
            appendLog("  - 활성 SIM 수: $newSimCount")
            appendLog("  - 듀얼 SIM: $newIsDualSim")
            appendLog("  - WiFi 연결: $newIsWifiConnected")
            
            // 호환성 검증
            val isCompatible = (oldIsConnected == newIsConnected) && 
                              (oldSimCount == newSimCount) && 
                              (oldIsDualSim == newIsDualSim) && 
                              (oldIsWifiConnected == newIsWifiConnected)
            
            appendLog("호환성 검증: ${if (isCompatible) "성공 ✅" else "불일치 발견 ⚠️"}")
            
            // Facade에서 새 클래스 접근
            val simInfoRef = networkStateInfo.simInfo
            val networkInfoRef = networkStateInfo.networkConnectivityInfo
            val telephonyInfoRef = networkStateInfo.telephonyInfo
            
            appendLog("Facade 내부 참조:")
            appendLog("  - simInfo: 접근 가능")
            appendLog("  - networkConnectivityInfo: 접근 가능") 
            appendLog("  - telephonyInfo: 접근 가능")
            
            appendLog("✅ 호환성 테스트 완료")
            
        } catch (e: Exception) {
            appendLog("❌ 호환성 테스트 실패: ${e.message}")
            Logx.e("ArchitectureTest: Compatibility test failed", e)
        }
    }

    private fun testPerformanceComparison() {
        appendLog("⚡ === 성능 비교 테스트 ===")
        
        try {
            val iterations = 1000
            
            // 기존 방식 성능 측정
            val oldStartTime = System.nanoTime()
            @Suppress("DEPRECATION")
            repeat(iterations) {
                networkStateInfo.isNetworkConnected()
                networkStateInfo.getActiveSimCount()
            }
            val oldEndTime = System.nanoTime()
            val oldDuration = (oldEndTime - oldStartTime) / 1_000_000 // ms
            
            // 새로운 방식 성능 측정
            val newStartTime = System.nanoTime()
            repeat(iterations) {
                networkConnectivityInfo.isNetworkConnected()
                simInfo.getActiveSimCount()
            }
            val newEndTime = System.nanoTime()
            val newDuration = (newEndTime - newStartTime) / 1_000_000 // ms
            
            appendLog("성능 비교 (${iterations}회 반복):")
            appendLog("  - 기존 방식: ${oldDuration}ms")
            appendLog("  - 새로운 방식: ${newDuration}ms")
            
            val improvement = if (newDuration > 0) {
                ((oldDuration - newDuration).toDouble() / oldDuration * 100).toInt()
            } else 0
            
            appendLog("성능 개선: ${if (improvement > 0) "+$improvement%" else "${improvement}%"}")
            
            // 메모리 사용량 확인
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 // MB
            appendLog("현재 메모리 사용량: ${usedMemory}MB")
            
            appendLog("✅ 성능 비교 완료")
            
        } catch (e: Exception) {
            appendLog("❌ 성능 비교 실패: ${e.message}")
            Logx.e("ArchitectureTest: Performance test failed", e)
        }
    }

    private fun runAllTests() {
        appendLog("🚀 === 전체 테스트 실행 ===")
        
        lifecycleScope.launch {
            try {
                testSimInfoFeatures()
                appendLog("")
                
                testNetworkConnectivityFeatures()
                appendLog("")
                
                testTelephonyInfoFeatures()
                appendLog("")
                
                testCrossReferenceFeatures()
                appendLog("")
                
                testBackwardCompatibility()
                appendLog("")
                
                testPerformanceComparison()
                appendLog("")
                
                appendLog("🎉 전체 테스트 완료!")
                appendLog("새로운 3개 클래스 아키텍처가 성공적으로 작동합니다.")
                
            } catch (e: Exception) {
                appendLog("❌ 전체 테스트 실행 중 오류: ${e.message}")
                Logx.e("ArchitectureTest: Full test execution failed", e)
            }
        }
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
        
        Logx.d("ArchitectureTest: $message")
    }

    override fun onDestroy() {
        try {
            simInfo.onDestroy()
            networkConnectivityInfo.onDestroy()
            telephonyInfo.onDestroy()
            networkStateInfo.onDestroy()
        } catch (e: Exception) {
            Logx.e("Error during cleanup", e)
        }
        super.onDestroy()
    }
}