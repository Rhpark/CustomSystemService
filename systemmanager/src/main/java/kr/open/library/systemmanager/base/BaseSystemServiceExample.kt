package kr.open.library.systemmanager.base

import android.content.Context
import android.view.View
import android.view.WindowManager
import kr.open.library.systemmanager.extenstions.getWindowManager

/**
 * Example implementation showing how to use the enhanced BaseSystemService
 * with integrated Result pattern support.
 * 통합된 Result 패턴 지원을 통해 향상된 BaseSystemService를 사용하는 방법을 보여주는 예제 구현입니다.
 */
class ExampleFloatingController(context: Context) : 
    BaseSystemService(context, listOf(android.Manifest.permission.SYSTEM_ALERT_WINDOW)) {

    private val windowManager: WindowManager by lazy { context.getWindowManager() }

    /**
     * Traditional approach with boolean return (maintained for compatibility).
     * 호환성을 위해 유지되는 boolean 반환을 사용한 전통적인 접근법입니다.
     */
    fun addViewLegacy(view: View, params: WindowManager.LayoutParams): Boolean {
        return safeExecuteOrDefault("addViewLegacy", false) {
            windowManager.addView(view, params)
            true
        }
    }

    /**
     * Modern approach with Result pattern providing detailed error information.
     * 상세한 오류 정보를 제공하는 Result 패턴을 사용한 현대적인 접근법입니다.
     */
    fun addViewSafe(view: View, params: WindowManager.LayoutParams): Result<Unit> {
        return safeExecute("addView") {
            windowManager.addView(view, params)
        }
    }

    /**
     * Convenient approach that handles common errors automatically.
     * 일반적인 오류를 자동으로 처리하는 편리한 접근법입니다.
     */
    fun addViewWithAutoHandling(view: View, params: WindowManager.LayoutParams): Boolean {
        return addViewSafe(view, params)
            .onFailure { error ->
                when (error) {
                    is SystemServiceError.Permission.SpecialPermissionRequired -> {
                        // Could trigger permission request UI
                        // 권한 요청 UI를 트리거할 수 있음
                        handleSpecialPermissionRequired(error)
                    }
                    is SystemServiceError.Unknown.Exception -> {
                        if (error.cause is WindowManager.BadTokenException) {
                            // Specific handling for bad token
                            // 잘못된 토큰에 대한 특정 처리
                            handleBadToken(view)
                        }
                    }
                    else -> {
                        // Generic error handling
                        // 일반적인 오류 처리
                        logError("Failed to add view", error)
                    }
                }
            }
            .isSuccess
    }

    /**
     * Example of operation that doesn't require permissions.
     * 권한이 필요하지 않은 작업의 예제입니다.
     */
    fun getScreenSize(): Result<android.graphics.Point> {
        return safeExecute("getScreenSize", requiresPermission = false) {
            val metrics = android.util.DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            android.graphics.Point(metrics.widthPixels, metrics.heightPixels)
        }
    }

    /**
     * Example of operation with specific error handling.
     * 특정 오류 처리를 사용한 작업의 예제입니다.
     */
    fun updateViewSafe(view: View, params: WindowManager.LayoutParams): Result<Unit> {
        return safeExecute("updateView") {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: IllegalArgumentException) {
                // Convert to more specific error
                throw IllegalArgumentException("View not attached to window manager", e)
            }
        }
    }

    // Helper methods for error handling
    // 오류 처리를 위한 도우미 메서드들

    private fun handleSpecialPermissionRequired(error: SystemServiceError.Permission.SpecialPermissionRequired) {
        // Implementation would trigger permission request
        // 구현 시 권한 요청을 트리거함
        println("Special permission required: ${error.permission}")
        println("Settings action: ${error.settingsAction}")
    }

    private fun handleBadToken(view: View) {
        // Implementation would handle bad token scenario
        // 구현 시 잘못된 토큰 시나리오를 처리함
        println("Bad token detected for view: $view")
    }

    private fun logError(operation: String, error: SystemServiceError) {
        println("$operation failed: ${error.getDeveloperMessage()}")
    }
}

/**
 * Usage examples demonstrating different approaches.
 * 다양한 접근법을 보여주는 사용 예제입니다.
 */
class BaseSystemServiceUsageExample(private val context: Context) {

    private val controller = ExampleFloatingController(context)

    fun demonstrateTraditionalApproach() {
        val view = View(context)
        val params = WindowManager.LayoutParams()

        // Traditional boolean approach - compatible with existing code
        // 전통적인 boolean 접근법 - 기존 코드와 호환 가능
        val success = controller.addViewLegacy(view, params)
        if (success) {
            println("View added successfully")
        } else {
            println("Failed to add view - check logs for details")
        }
    }

    fun demonstrateResultPatternApproach() {
        val view = View(context)
        val params = WindowManager.LayoutParams()

        // Modern Result pattern approach - detailed error handling
        // 현대적인 Result 패턴 접근법 - 상세한 오류 처리
        controller.addViewSafe(view, params)
            .onSuccess {
                println("View added successfully")
                // Can chain additional operations
                // 추가 작업을 연결할 수 있음
                controller.updateViewSafe(view, params)
                    .onFailure { updateError ->
                        println("Update failed: ${updateError.getUserMessage()}")
                    }
            }
            .onFailure { error ->
                when (error) {
                    is SystemServiceError.Permission.SpecialPermissionRequired -> {
                        println("Need special permission: ${error.permission}")
                        // Trigger permission request UI
                        // 권한 요청 UI 트리거
                    }
                    is SystemServiceError.Security.AccessDenied -> {
                        println("Access denied: ${error.reason}")
                        // Show user explanation
                        // 사용자에게 설명 표시
                    }
                    is SystemServiceError.Unknown.Exception -> {
                        println("Unexpected error: ${error.cause.message}")
                        // Log for debugging
                        // 디버깅을 위한 로그
                    }
                    else -> {
                        println("Operation failed: ${error.getUserMessage()}")
                    }
                }
            }
    }

    fun demonstrateConvenientApproach() {
        val view = View(context)
        val params = WindowManager.LayoutParams()

        // Convenient approach - automatic error handling
        // 편리한 접근법 - 자동 오류 처리
        val success = controller.addViewWithAutoHandling(view, params)
        
        // Just check success, errors are handled automatically
        // 성공 여부만 확인, 오류는 자동으로 처리됨
        if (success) {
            println("View added and errors handled automatically")
        }
    }

    fun demonstrateChainedOperations() {
        // Chaining multiple operations with Result pattern
        // Result 패턴으로 여러 작업 연결
        controller.getScreenSize()
            .mapCatching { screenSize ->
                println("Screen size: ${screenSize.x} x ${screenSize.y}")
                
                // Create view based on screen size
                // 화면 크기를 기반으로 뷰 생성
                val view = View(context)
                val params = WindowManager.LayoutParams().apply {
                    width = screenSize.x / 2
                    height = screenSize.y / 2
                }
                
                // Add the view
                // 뷰 추가
                controller.addViewSafe(view, params).getOrThrow()
                
                "Operation completed successfully"
            }
            .onSuccess { message ->
                println(message)
            }
            .onFailure { error ->
                println("Chained operation failed: ${error.message}")
            }
    }

    fun demonstratePermissionHandling() {
        // Check permission status
        // 권한 상태 확인
        val permissionInfo = controller.getPermissionInfo()
        println("Permission status: $permissionInfo")

        // Refresh permissions after user grants them
        // 사용자가 권한을 부여한 후 권한 새로고침
        controller.refreshPermissions()

        // Check specific permission
        // 특정 권한 확인
        val hasOverlayPermission = controller.isPermissionGranted(android.Manifest.permission.SYSTEM_ALERT_WINDOW)
        println("Has overlay permission: $hasOverlayPermission")
    }
}