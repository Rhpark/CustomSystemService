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
    }
    
    private fun setupClickListeners() {
        btnAlarmTest.setOnClickListener {
            navigateToAlarmTest()
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
}