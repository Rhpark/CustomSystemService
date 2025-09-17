package kr.open.library.system_service

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import kr.open.library.system_service.R
import kr.open.library.system_service.databinding.ActivityMainBinding

/**
 * Main activity that serves as the entry point for testing SystemManager library components.
 * Provides navigation to various test activities for different system service controllers.
 * 
 * SystemManager 라이브러리 구성 요소를 테스트하기 위한 진입점 역할을 하는 메인 액티비티입니다.
 * 다양한 시스템 서비스 컨트롤러를 위한 테스트 액티비티로의 탐색을 제공합니다.
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupBinding()
        
        // Setup window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Setup click listeners
        setupClickListeners()
    }
    
    private fun setupBinding() {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
    }
    
    private fun setupClickListeners() {
        binding.btnAlarmTest.setOnClickListener {
            navigateToAlarmTest()
        }
        
        binding.btnNotificationTest.setOnClickListener {
            navigateToNotificationTest()
        }
        
        binding.btnSoftKeyboardTest.setOnClickListener {
            navigateToSoftKeyboardTest()
        }
        
        binding.btnVibratorTest.setOnClickListener {
            navigateToVibratorTest()
        }
        
        binding.btnFloatingViewTest.setOnClickListener {
            navigateToFloatingViewTest()
        }
        
        binding.btnBatteryTest.setOnClickListener {
            navigateToBatteryTest()
        }
        
        binding.btnDisplayTest.setOnClickListener {
            navigateToDisplayTest()
        }
        
        binding.btnLocationTest.setOnClickListener {
            navigateToLocationTest()
        }
        
        binding.btnWifiTest.setOnClickListener {
            navigateToWifiTest()
        }
        
        binding.btnTelephonyTest.setOnClickListener {
            navigateToTelephonyTest()
        }
        
        binding.btnBleTest.setOnClickListener {
            navigateToBleTest()
        }
        
        binding.btnBleTwoPhoneTest.setOnClickListener {
            navigateToBleTwoPhoneTest()
        }
        
        binding.btnArchitectureTest.setOnClickListener {
            navigateToArchitectureTest()
        }
    }
    
    /**
     * Navigates to the AlarmTestActivity for testing alarm functionality.
     * 알람 기능을 테스트하기 위해 AlarmTestActivity로 이동합니다.
     */
    private fun navigateToAlarmTest() {
        val intent = Intent(this, AlarmTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * Navigates to the NotificationTestActivity for testing notification functionality.
     * 알림 기능을 테스트하기 위해 NotificationTestActivity로 이동합니다.
     */
    private fun navigateToNotificationTest() {
        val intent = Intent(this, NotificationTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * Navigates to the SoftKeyboardTestActivity for testing soft keyboard functionality.
     * 소프트 키보드 기능을 테스트하기 위해 SoftKeyboardTestActivity로 이동합니다.
     */
    private fun navigateToSoftKeyboardTest() {
        val intent = Intent(this, SoftKeyboardTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * Navigates to the VibratorTestActivity for testing vibrator functionality.
     * 진동 기능을 테스트하기 위해 VibratorTestActivity로 이동합니다.
     */
    private fun navigateToVibratorTest() {
        val intent = Intent(this, VibratorTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * Navigates to the FloatingViewTestActivity for testing floating view functionality.
     * 플로팅 뷰 기능을 테스트하기 위해 FloatingViewTestActivity로 이동합니다.
     */
    private fun navigateToFloatingViewTest() {
        val intent = Intent(this, FloatingViewTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * Navigates to the BatteryTestActivity for testing battery state monitoring functionality.
     * 배터리 상태 모니터링 기능을 테스트하기 위해 BatteryTestActivity로 이동합니다.
     */
    private fun navigateToBatteryTest() {
        val intent = Intent(this, BatteryTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * Navigates to the DisplayTestActivity for testing display information functionality.
     * 디스플레이 정보 기능을 테스트하기 위해 DisplayTestActivity로 이동합니다.
     */
    private fun navigateToDisplayTest() {
        val intent = Intent(this, DisplayTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * Navigates to the LocationTestActivity for testing location service functionality.
     * 위치 서비스 기능을 테스트하기 위해 LocationTestActivity로 이동합니다.
     */
    private fun navigateToLocationTest() {
        val intent = Intent(this, LocationTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * Navigates to the WifiTestActivity for testing WiFi controller functionality.
     * WiFi 컨트롤러 기능을 테스트하기 위해 WifiTestActivity로 이동합니다.
     */
    private fun navigateToWifiTest() {
        val intent = Intent(this, WifiTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * Navigates to the TelephonyTestActivity for testing telephony information functionality.
     * 전화통신 정보 기능을 테스트하기 위해 TelephonyTestActivity로 이동합니다.
     */
    private fun navigateToTelephonyTest() {
        val intent = Intent(this, TelephonyTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * Navigates to the BleTestActivity for testing BLE functionality.
     * BLE 기능을 테스트하기 위해 BleTestActivity로 이동합니다.
     */
    private fun navigateToBleTest() {
        val intent = Intent(this, BleTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * Navigates to the BleTwoPhoneTestActivity for testing BLE communication between two phones.
     * 두 스마트폰 간 BLE 통신을 테스트하기 위해 BleTwoPhoneTestActivity로 이동합니다.
     */
    private fun navigateToBleTwoPhoneTest() {
        val intent = Intent(this, BleTwoPhoneTestActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * Navigates to the ArchitectureTestActivity for testing new 3-class architecture.
     * 새로운 3개 클래스 아키텍처를 테스트하기 위해 ArchitectureTestActivity로 이동합니다.
     */
    private fun navigateToArchitectureTest() {
        val intent = Intent(this, ArchitectureTestActivity::class.java)
        startActivity(intent)
    }
}