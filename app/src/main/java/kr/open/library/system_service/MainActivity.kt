package kr.open.library.system_service

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Main activity that serves as the entry point for testing SystemManager library components.
 * Provides navigation to various test activities for different system service controllers.
 * 
 * SystemManager 라이브러리 구성 요소를 테스트하기 위한 진입점 역할을 하는 메인 액티비티입니다.
 * 다양한 시스템 서비스 컨트롤러를 위한 테스트 액티비티로의 탐색을 제공합니다.
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var btnAlarmTest: Button
    private lateinit var btnNotificationTest: Button
    private lateinit var btnSoftKeyboardTest: Button
    private lateinit var btnVibratorTest: Button
    private lateinit var btnFloatingViewTest: Button
    private lateinit var btnBatteryTest: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Setup window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Initialize views
        initializeViews()
        setupClickListeners()
    }
    
    private fun initializeViews() {
        btnAlarmTest = findViewById(R.id.btnAlarmTest)
        btnNotificationTest = findViewById(R.id.btnNotificationTest)
        btnSoftKeyboardTest = findViewById(R.id.btnSoftKeyboardTest)
        btnVibratorTest = findViewById(R.id.btnVibratorTest)
        btnFloatingViewTest = findViewById(R.id.btnFloatingViewTest)
        btnBatteryTest = findViewById(R.id.btnBatteryTest)
    }
    
    private fun setupClickListeners() {
        btnAlarmTest.setOnClickListener {
            navigateToAlarmTest()
        }
        
        btnNotificationTest.setOnClickListener {
            navigateToNotificationTest()
        }
        
        btnSoftKeyboardTest.setOnClickListener {
            navigateToSoftKeyboardTest()
        }
        
        btnVibratorTest.setOnClickListener {
            navigateToVibratorTest()
        }
        
        btnFloatingViewTest.setOnClickListener {
            navigateToFloatingViewTest()
        }
        
        btnBatteryTest.setOnClickListener {
            navigateToBatteryTest()
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
}