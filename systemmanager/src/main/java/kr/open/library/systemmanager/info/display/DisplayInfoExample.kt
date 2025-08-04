package kr.open.library.systemmanager.info.display

import android.content.Context
import android.graphics.Point
import android.util.Log

/**
 * Example class demonstrating the usage of DisplayInfo with Result pattern.
 * DisplayInfo의 Result 패턴 사용법을 보여주는 예제 클래스입니다.
 */
class DisplayInfoExample(private val context: Context) {
    
    private val displayInfo = DisplayInfo(context)
    
    /**
     * Demonstrates traditional exception-based approach.
     * 전통적인 예외 기반 접근법을 보여줍니다.
     */
    fun demonstrateTraditionalApproach() {
        try {
            val statusBarHeight = displayInfo.getStatusBarHeight()
            val navigationBarHeight = displayInfo.getNavigationBarHeight()
            val screenSize = displayInfo.getScreen()
            
            Log.d("DisplayInfo", "Traditional approach successful:")
            Log.d("DisplayInfo", "Status bar height: ${statusBarHeight}px")
            Log.d("DisplayInfo", "Navigation bar height: ${navigationBarHeight}px")
            Log.d("DisplayInfo", "Screen size: ${screenSize.x} x ${screenSize.y}")
            
        } catch (e: Exception) {
            Log.e("DisplayInfo", "Traditional approach failed: ${e.message}")
        }
    }
    
    /**
     * Demonstrates Result pattern approach with proper error handling.
     * 적절한 오류 처리와 함께 Result 패턴 접근법을 보여줍니다.
     */
    fun demonstrateResultPatternApproach() {
        // Status bar height with Result pattern
        displayInfo.getStatusBarHeightSafe()
            .onSuccess { height ->
                Log.d("DisplayInfo", "Status bar height (safe): ${height}px")
            }
            .onFailure { error ->
                Log.w("DisplayInfo", "Could not get status bar height: ${error.message}")
            }
        
        // Navigation bar height with Result pattern
        displayInfo.getNavigationBarHeightSafe()
            .onSuccess { height ->
                Log.d("DisplayInfo", "Navigation bar height (safe): ${height}px")
            }
            .onFailure { error ->
                Log.w("DisplayInfo", "Could not get navigation bar height: ${error.message}")
            }
        
        // Screen size with Result pattern
        displayInfo.getScreenSafe()
            .onSuccess { size ->
                Log.d("DisplayInfo", "Screen size (safe): ${size.x} x ${size.y}")
            }
            .onFailure { error ->
                Log.w("DisplayInfo", "Could not get screen size: ${error.message}")
            }
    }
    
    /**
     * Demonstrates convenient approach with default values.
     * 기본값을 사용한 편리한 접근법을 보여줍니다.
     */
    fun demonstrateDefaultValueApproach() {
        // Always get a value, using defaults when actual values cannot be determined
        val statusBarHeight = displayInfo.getStatusBarHeightOrDefault(60)
        val navigationBarHeight = displayInfo.getNavigationBarHeightOrDefault(0)
        val screenSize = displayInfo.getScreenOrDefault(Point(1080, 1920))
        
        Log.d("DisplayInfo", "Default value approach:")
        Log.d("DisplayInfo", "Status bar height (with default): ${statusBarHeight}px")
        Log.d("DisplayInfo", "Navigation bar height (with default): ${navigationBarHeight}px")
        Log.d("DisplayInfo", "Screen size (with default): ${screenSize.x} x ${screenSize.y}")
    }
    
    /**
     * Demonstrates chaining multiple Result operations.
     * 여러 Result 작업을 연결하는 방법을 보여줍니다.
     */
    fun demonstrateResultChaining() {
        val statusBarResult = displayInfo.getStatusBarHeightSafe()
        val navigationBarResult = displayInfo.getNavigationBarHeightSafe()
        val screenResult = displayInfo.getScreenSafe()
        
        // Check if all operations succeeded
        if (statusBarResult.isSuccess && navigationBarResult.isSuccess && screenResult.isSuccess) {
            val statusBar = statusBarResult.getOrNull()!!
            val navigationBar = navigationBarResult.getOrNull()!!
            val screen = screenResult.getOrNull()!!
            
            val totalUsableHeight = screen.y
            val totalSystemBarsHeight = statusBar + navigationBar
            val usableContentHeight = totalUsableHeight - totalSystemBarsHeight
            
            Log.d("DisplayInfo", "All operations successful!")
            Log.d("DisplayInfo", "Total usable height: ${totalUsableHeight}px")
            Log.d("DisplayInfo", "System bars height: ${totalSystemBarsHeight}px")
            Log.d("DisplayInfo", "Usable content height: ${usableContentHeight}px")
            
        } else {
            Log.w("DisplayInfo", "Some operations failed, using fallback calculations")
            
            // Use default values when some operations fail
            val statusBar = statusBarResult.getOrDefault(60)
            val navigationBar = navigationBarResult.getOrDefault(0)
            val screen = screenResult.getOrDefault(Point(1080, 1920))
            
            Log.d("DisplayInfo", "Fallback calculations:")
            Log.d("DisplayInfo", "Status bar (fallback): ${statusBar}px")
            Log.d("DisplayInfo", "Navigation bar (fallback): ${navigationBar}px")
            Log.d("DisplayInfo", "Screen (fallback): ${screen.x} x ${screen.y}")
        }
    }
    
    /**
     * Demonstrates transforming Result values.
     * Result 값을 변환하는 방법을 보여줍니다.
     */
    fun demonstrateResultTransformation() {
        // Transform status bar height to DP units (assuming 160 DPI)
        val statusBarInDp = displayInfo.getStatusBarHeightSafe()
            .map { heightPx -> heightPx / (160f / 160f) } // Convert px to dp
            .getOrDefault(24f) // Default 24dp
        
        Log.d("DisplayInfo", "Status bar height in DP: ${statusBarInDp}dp")
        
        // Create a custom data class with all display info
        data class DisplaySummary(
            val screenSize: Point,
            val statusBarHeight: Int,
            val navigationBarHeight: Int,
            val usableArea: Point
        )
        
        val displaySummaryResult = displayInfo.getScreenSafe()
            .mapCatching { screenSize ->
                val statusBar = displayInfo.getStatusBarHeightSafe().getOrDefault(60)
                val navigationBar = displayInfo.getNavigationBarHeightSafe().getOrDefault(0)
                val usableArea = Point(
                    screenSize.x,
                    screenSize.y - statusBar - navigationBar
                )
                
                DisplaySummary(screenSize, statusBar, navigationBar, usableArea)
            }
        
        displaySummaryResult
            .onSuccess { summary ->
                Log.d("DisplayInfo", "Display Summary:")
                Log.d("DisplayInfo", "  Screen: ${summary.screenSize.x} x ${summary.screenSize.y}")
                Log.d("DisplayInfo", "  Status Bar: ${summary.statusBarHeight}px")
                Log.d("DisplayInfo", "  Navigation Bar: ${summary.navigationBarHeight}px")
                Log.d("DisplayInfo", "  Usable Area: ${summary.usableArea.x} x ${summary.usableArea.y}")
            }
            .onFailure { error ->
                Log.e("DisplayInfo", "Failed to create display summary: ${error.message}")
            }
    }
}