package kr.open.library.system_service

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kr.open.library.logcat.Logx
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

    private lateinit var displayInfo: DisplayInfo
    private lateinit var resultTextView: TextView
    private lateinit var scrollView: ScrollView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupDisplayInfo()
        setupUI()
    }

    private fun setupDisplayInfo() {
        displayInfo = DisplayInfo(this)
        Logx.d("DisplayTestActivity initialized")
    }

    private fun setupUI() {
        // 스크롤 가능한 텍스트뷰를 포함한 간단한 레이아웃 생성
        scrollView = ScrollView(this).apply {
            layoutParams = ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.MATCH_PARENT
            )
        }
        
        resultTextView = TextView(this).apply {
            layoutParams = ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 32, 32, 32)
            textSize = 14f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
        }
        
        scrollView.addView(resultTextView)
        setContentView(scrollView)
        
        // 테스트 시작
        runAllTests()
    }

    private fun runAllTests() {
        val results = StringBuilder()
        results.append("🔍 DisplayInfo 종합 테스트\n")
        results.append("=" * 50 + "\n\n")
        
        // 1. 전통적 방식 테스트
        results.append("📊 1. 전통적 접근법 (Traditional Approach)\n")
        results.append("-" * 40 + "\n")
        testTraditionalApproach(results)
        
        results.append("\n")
        
        // 2. Result 패턴 테스트
        results.append("🚀 2. Result 패턴 (Result Pattern)\n")
        results.append("-" * 40 + "\n")
        testResultPatternApproach(results)
        
        results.append("\n")
        
        // 3. 편리한 방식 테스트
        results.append("⚡ 3. 편리한 접근법 (Convenient Approach)\n")
        results.append("-" * 40 + "\n")
        testConvenientApproach(results)
        
        results.append("\n")
        
        // 4. 체이닝 테스트
        results.append("🔗 4. 메서드 체이닝 (Method Chaining)\n")
        results.append("-" * 40 + "\n")
        testMethodChaining(results)
        
        results.append("\n")
        results.append("✅ 모든 테스트 완료!\n")
        
        resultTextView.text = results.toString()
        Logx.d("All DisplayInfo tests completed")
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
        val screenSize = displayInfo.getScreenOrDefault(android.graphics.Point(0, 0))
        
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
            val screen = screenResult.getOrDefault(android.graphics.Point(0, 0))
            
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