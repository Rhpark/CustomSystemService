package kr.open.library.system_service

import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kr.open.library.logcat.Logx
import kr.open.library.system_service.databinding.ActivityDisplayTestBinding
import kr.open.library.systemmanager.info.display.DisplayInfo
import kr.open.library.systemmanager.base.SystemServiceError
import kr.open.library.systemmanager.base.onSystemServiceFailure
import kr.open.library.systemmanager.base.getDeveloperMessage
import kr.open.library.systemmanager.base.getUserMessage

/**
 * DisplayTestActivity - DisplayInfo 테스트 액티비티
 * DisplayInfo Test Activity
 * 
 * 디스플레이 정보 조회 기능을 종합적으로 테스트하는 액티비티입니다.
 * Activity for comprehensive testing of display information retrieval functionality.
 * 
 * 주요 테스트 기능 / Main Test Features:
 * - 상태바 높이 조회 / Status bar height retrieval
 * - 네비게이션바 높이 조회 / Navigation bar height retrieval  
 * - 화면 크기 조회 / Screen size retrieval
 * - Result 패턴 vs 전통적 방식 비교 / Result pattern vs traditional approach comparison
 * - 다양한 API 레벨 호환성 테스트 / Various API level compatibility testing
 */
class DisplayTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDisplayTestBinding
    private lateinit var displayInfo: DisplayInfo
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupBinding()
        setupDisplayInfo()
        setupUI()
        runAllTests()
    }

    private fun setupBinding() {
        binding = ActivityDisplayTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    private fun setupDisplayInfo() {
        displayInfo = DisplayInfo(this)
        Logx.d("DisplayTestActivity initialized")
    }

    private fun setupUI() {
        // 버튼 클릭 리스너 설정
        binding.btnRunTests.setOnClickListener {
            runAllTests()
        }
        
        binding.btnClearLog.setOnClickListener {
            clearLog()
        }
        
        binding.btnTraditionalTest.setOnClickListener {
            runTraditionalTest()
        }
        
        binding.btnResultPatternTest.setOnClickListener {
            runResultPatternTest()
        }
        
        binding.btnConvenientTest.setOnClickListener {
            runConvenientTest()
        }
        
        binding.btnChainingTest.setOnClickListener {
            runChainingTest()
        }
    }

    private fun runAllTests() {
        clearLog()
        appendLog("🔍 DisplayInfo 종합 테스트")
        appendLog("=" * 50)
        appendLog("")
        
        runTraditionalTest()
        runResultPatternTest() 
        runConvenientTest()
        runChainingTest()
        
        appendLog("")
        appendLog("✅ 모든 테스트 완료!")
        Logx.d("All DisplayInfo tests completed")
    }
    
    private fun runTraditionalTest() {
        appendLog("📊 1. 전통적 접근법 (Traditional Approach)")
        appendLog("-" * 40)
        val results = StringBuilder()
        testTraditionalApproach(results)
        appendLog(results.toString())
        appendLog("")
    }
    
    private fun runResultPatternTest() {
        appendLog("🚀 2. Result 패턴 (Result Pattern)")
        appendLog("-" * 40)
        val results = StringBuilder()
        testResultPatternApproach(results)
        appendLog(results.toString())
        appendLog("")
    }
    
    private fun runConvenientTest() {
        appendLog("⚡ 3. 편리한 접근법 (Convenient Approach)")
        appendLog("-" * 40)
        val results = StringBuilder()
        testConvenientApproach(results)
        appendLog(results.toString())
        appendLog("")
    }
    
    private fun runChainingTest() {
        appendLog("🔗 4. 메서드 체이닝 (Method Chaining)")
        appendLog("-" * 40)
        val results = StringBuilder()
        testMethodChaining(results)
        appendLog(results.toString())
    }
    
    private fun clearLog() {
        binding.tvResult.text = ""
    }
    
    private fun appendLog(message: String) {
        val currentText = binding.tvResult.text.toString()
        binding.tvResult.text = if (currentText.isEmpty()) {
            message
        } else {
            "$currentText\n$message"
        }
    }

    private fun testTraditionalApproach(results: StringBuilder) {
        try {
            val statusBarHeight = displayInfo.getStatusBarHeight()
            val navigationBarHeight = displayInfo.getNavigationBarHeight()
            val screenSize = displayInfo.getScreen()
            
            results.append("✅ 성공적으로 정보 조회:\n")
            results.append("   • 상태바 높이: ${statusBarHeight}px\n")
            results.append("   • 네비게이션바 높이: ${navigationBarHeight}px\n")
            results.append("   • 화면 크기: ${screenSize.x} x ${screenSize.y}\n")
            
            Logx.d("Traditional approach successful")
            
        } catch (e: Exception) {
            results.append("❌ 오류 발생: ${e.message}\n")
            Logx.e("Traditional approach failed: ${e.message}")
        }
    }

    private fun testResultPatternApproach(results: StringBuilder) {
        // 상태바 높이 테스트
        displayInfo.getStatusBarHeightSafe()
            .onSuccess { height ->
                results.append("✅ 상태바 높이: ${height}px\n")
                Logx.d("Status bar height: ${height}px")
            }
            .onSystemServiceFailure { error ->
                results.append("❌ 상태바 높이 조회 실패: ${error.getUserMessage()}\n")
                Logx.w("Status bar height failed: ${error.getDeveloperMessage()}")
            }

        // 네비게이션바 높이 테스트
        displayInfo.getNavigationBarHeightSafe()
            .onSuccess { height ->
                results.append("✅ 네비게이션바 높이: ${height}px\n")
                Logx.d("Navigation bar height: ${height}px")
            }
            .onSystemServiceFailure { error ->
                results.append("❌ 네비게이션바 높이 조회 실패: ${error.getUserMessage()}\n")
                Logx.w("Navigation bar height failed: ${error.getDeveloperMessage()}")
            }

        // 화면 크기 테스트
        displayInfo.getScreenSafe()
            .onSuccess { screen ->
                results.append("✅ 화면 크기: ${screen.x} x ${screen.y}\n")
                Logx.d("Screen size: ${screen.x} x ${screen.y}")
            }
            .onSystemServiceFailure { error ->
                results.append("❌ 화면 크기 조회 실패: ${error.getUserMessage()}\n")
                Logx.w("Screen size failed: ${error.getDeveloperMessage()}")
            }
    }

    private fun testConvenientApproach(results: StringBuilder) {
        val statusBarHeight = displayInfo.getStatusBarHeightOrDefault(0)
        val navigationBarHeight = displayInfo.getNavigationBarHeightOrDefault(0)
        val screenSize = displayInfo.getScreenOrDefault(Point(0, 0))
        
        results.append("📱 기본값 포함 결과:\n")
        results.append("   • 상태바 높이: ${statusBarHeight}px ${if (statusBarHeight == 0) "(기본값)" else ""}\n")
        results.append("   • 네비게이션바 높이: ${navigationBarHeight}px ${if (navigationBarHeight == 0) "(기본값)" else ""}\n")
        results.append("   • 화면 크기: ${screenSize.x} x ${screenSize.y} ${if (screenSize.x == 0) "(기본값)" else ""}\n")
        
        Logx.d("Convenient approach completed")
    }

    private fun testMethodChaining(results: StringBuilder) {
        val statusBarResult = displayInfo.getStatusBarHeightSafe()
        val navigationBarResult = displayInfo.getNavigationBarHeightSafe()
        val screenResult = displayInfo.getScreenSafe()
        
        if (statusBarResult.isSuccess && navigationBarResult.isSuccess && screenResult.isSuccess) {
            val statusBar = statusBarResult.getOrDefault(0)
            val navigationBar = navigationBarResult.getOrDefault(0)
            val screen = screenResult.getOrDefault(Point(0, 0))
            
            val totalSystemUI = statusBar + navigationBar
            val contentHeight = screen.y - totalSystemUI
            
            results.append("🔄 체이닝 계산 결과:\n")
            results.append("   • 전체 시스템 UI 높이: ${totalSystemUI}px\n")
            results.append("   • 실제 컨텐츠 영역 높이: ${contentHeight}px\n")
            results.append("   • 시스템 UI 비율: ${String.format("%.1f", (totalSystemUI.toFloat() / screen.y) * 100)}%\n")
            
            Logx.d("Method chaining successful")
        } else {
            results.append("❌ 체이닝 중 일부 값 조회 실패\n")
            Logx.w("Method chaining partially failed")
        }
    }

    private operator fun String.times(count: Int): String = repeat(count)
}