package kr.open.library.systemmanager.controller.window

import android.content.Context
import android.view.View
import android.view.WindowManager
import kr.open.library.systemmanager.base.BaseSystemService
import kr.open.library.systemmanager.base.SystemServiceError
import kr.open.library.systemmanager.controller.window.floating.fixed.FloatingFixedView
import kr.open.library.systemmanager.extenstions.getWindowManager

/**
 * Enhanced FloatingViewController demonstrating integration with the new BaseSystemService
 * Result pattern support. This shows how existing controllers can be upgraded.
 * 새로운 BaseSystemService Result 패턴 지원과의 통합을 보여주는 향상된 FloatingViewController입니다.
 * 기존 컨트롤러를 어떻게 업그레이드할 수 있는지 보여줍니다.
 */
class FloatingViewControllerEnhanced(context: Context) :
    BaseSystemService(context, listOf(android.Manifest.permission.SYSTEM_ALERT_WINDOW)) {

    public val windowManager: WindowManager by lazy { context.getWindowManager() }

    private var floatingFixedView: FloatingFixedView? = null

    // =================================================
    // LEGACY METHODS (for backward compatibility)
    // 레거시 메서드 (하위 호환성을 위함)
    // =================================================

    /**
     * Legacy method maintaining the same API as the original FloatingViewController.
     * 원래 FloatingViewController와 동일한 API를 유지하는 레거시 메서드입니다.
     */
    public fun setFloatingFixedView(floatingView: FloatingFixedView?): Boolean {
        return safeExecuteOrDefault("setFloatingFixedView", false) {
            if (floatingView == null) {
                removeFloatingFixedView()
            } else {
                windowManager.addView(floatingView.view, floatingView.params)
            }
            this.floatingFixedView = floatingView
            true
        }
    }

    // =================================================
    // ENHANCED METHODS (with Result pattern)
    // 향상된 메서드 (Result 패턴 사용)
    // =================================================

    /**
     * Enhanced method providing detailed error information through Result pattern.
     * Result 패턴을 통해 상세한 오류 정보를 제공하는 향상된 메서드입니다.
     */
    public fun setFloatingFixedViewSafe(floatingView: FloatingFixedView?): Result<Unit> {
        return safeExecute("setFloatingFixedView") {
            if (floatingView == null) {
                removeFloatingFixedViewInternal()
            } else {
                try {
                    windowManager.addView(floatingView.view, floatingView.params)
                } catch (e: WindowManager.BadTokenException) {
                    throw IllegalStateException("Invalid window token - activity may be destroyed", e)
                } catch (e: WindowManager.InvalidDisplayException) {
                    throw IllegalStateException("Invalid display - display may have been removed", e)
                }
            }
            this.floatingFixedView = floatingView
        }
    }

    /**
     * Convenient method that handles common errors automatically and provides user feedback.
     * 일반적인 오류를 자동으로 처리하고 사용자 피드백을 제공하는 편리한 메서드입니다.
     */
    public fun setFloatingFixedViewWithHandling(
        floatingView: FloatingFixedView?,
        onPermissionRequired: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean {
        return setFloatingFixedViewSafe(floatingView)
            .onSystemServiceFailure { error ->
                when (error) {
                    is SystemServiceError.Permission.SpecialPermissionRequired -> {
                        onPermissionRequired?.invoke() ?: run {
                            // Default handling - could show permission dialog
                            // 기본 처리 - 권한 대화상자를 표시할 수 있음
                        }
                    }
                    is SystemServiceError.State.InvalidState -> {
                        onError?.invoke("Cannot add floating view: ${error.operation}")
                    }
                    is SystemServiceError.Unknown.Exception -> {
                        when (error.cause) {
                            is WindowManager.BadTokenException -> {
                                onError?.invoke("Window token is invalid. Try again after activity is ready.")
                            }
                            is WindowManager.InvalidDisplayException -> {
                                onError?.invoke("Display is not available.")
                            }
                            else -> {
                                onError?.invoke("Unexpected error: ${error.cause.message}")
                            }
                        }
                    }
                    else -> {
                        onError?.invoke(error.getUserMessage())
                    }
                }
            }
            .isSuccess
    }

    /**
     * Enhanced method for adding any view with detailed error handling.
     * 상세한 오류 처리를 통해 모든 뷰를 추가하는 향상된 메서드입니다.
     */
    public fun addViewSafe(view: View, params: WindowManager.LayoutParams): Result<Unit> {
        return safeExecute("addView") {
            validateViewAndParams(view, params)
            
            try {
                windowManager.addView(view, params)
            } catch (e: WindowManager.BadTokenException) {
                throw IllegalStateException("Invalid window token - ensure activity is active", e)
            } catch (e: IllegalStateException) {
                if (e.message?.contains("has already been added") == true) {
                    throw IllegalStateException("View has already been added to window manager", e)
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * Enhanced method for updating view layout with validation.
     * 유효성 검사와 함께 뷰 레이아웃을 업데이트하는 향상된 메서드입니다.
     */
    public fun updateViewSafe(view: View, params: WindowManager.LayoutParams): Result<Unit> {
        return safeExecute("updateView", requiresPermission = false) {
            validateViewAndParams(view, params)
            
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("View is not attached to window manager", e)
            }
        }
    }

    /**
     * Enhanced method for removing view with proper error handling.
     * 적절한 오류 처리와 함께 뷰를 제거하는 향상된 메서드입니다.
     */
    public fun removeViewSafe(view: View): Result<Unit> {
        return safeExecute("removeView", requiresPermission = false) {
            try {
                windowManager.removeView(view)
            } catch (e: IllegalArgumentException) {
                // View was not added to window manager
                // 뷰가 윈도우 매니저에 추가되지 않았음
                throw IllegalStateException("View is not attached to window manager", e)
            }
        }
    }

    /**
     * Batch operation to add multiple views with transaction-like behavior.
     * 트랜잭션과 같은 동작으로 여러 뷰를 추가하는 일괄 작업입니다.
     */
    public fun addViewsBatch(viewsAndParams: List<Pair<View, WindowManager.LayoutParams>>): Result<Unit> {
        return safeExecute("addViewsBatch") {
            val addedViews = mutableListOf<View>()
            
            try {
                viewsAndParams.forEach { (view, params) ->
                    validateViewAndParams(view, params)
                    windowManager.addView(view, params)
                    addedViews.add(view)
                }
            } catch (e: Exception) {
                // Rollback: remove any views that were successfully added
                // 롤백: 성공적으로 추가된 뷰들을 제거
                addedViews.forEach { view ->
                    try {
                        windowManager.removeView(view)
                    } catch (ignored: Exception) {
                        // Ignore errors during rollback
                        // 롤백 중 오류 무시
                    }
                }
                throw e
            }
        }
    }

    // =================================================
    // UTILITY METHODS
    // 유틸리티 메서드
    // =================================================

    /**
     * Validates view and layout parameters before window operations.
     * 윈도우 작업 전에 뷰와 레이아웃 매개변수의 유효성을 검사합니다.
     */
    private fun validateViewAndParams(view: View, params: WindowManager.LayoutParams) {
        require(view.parent == null) { "View already has a parent" }
        require(params.width > 0 || params.width == WindowManager.LayoutParams.MATCH_PARENT || params.width == WindowManager.LayoutParams.WRAP_CONTENT) {
            "Invalid width: ${params.width}"
        }
        require(params.height > 0 || params.height == WindowManager.LayoutParams.MATCH_PARENT || params.height == WindowManager.LayoutParams.WRAP_CONTENT) {
            "Invalid height: ${params.height}"
        }
    }

    /**
     * Internal method for removing floating fixed view.
     * 플로팅 고정 뷰를 제거하는 내부 메서드입니다.
     */
    private fun removeFloatingFixedViewInternal() {
        floatingFixedView?.let { fixedView ->
            try {
                windowManager.removeView(fixedView.view)
            } catch (e: IllegalArgumentException) {
                // View was already removed or never added
                // 뷰가 이미 제거되었거나 추가된 적이 없음
            }
        }
    }

    /**
     * Legacy method for compatibility.
     * 호환성을 위한 레거시 메서드입니다.
     */
    private fun removeFloatingFixedView() {
        removeFloatingFixedViewInternal()
    }

    /**
     * Gets current floating fixed view.
     * 현재 플로팅 고정 뷰를 가져옵니다.
     */
    public fun getFloatingFixedView(): FloatingFixedView? = floatingFixedView

    /**
     * Enhanced destroy method with proper cleanup.
     * 적절한 정리 작업이 포함된 향상된 소멸 메서드입니다.
     */
    override fun onDestroy() {
        try {
            removeFloatingFixedViewInternal()
            floatingFixedView = null
        } catch (e: Exception) {
            // Log but don't throw during cleanup
            // 정리 중에는 로그만 남기고 예외를 던지지 않음
        }
        super.onDestroy()
    }
}

/**
 * Usage example demonstrating the enhanced FloatingViewController.
 * 향상된 FloatingViewController를 보여주는 사용 예제입니다.
 */
class FloatingViewControllerUsageExample(private val context: Context) {

    private val controller = FloatingViewControllerEnhanced(context)

    fun demonstrateEnhancedErrorHandling() {
        val view = View(context)
        val params = WindowManager.LayoutParams()

        // Using the enhanced method with detailed error handling
        // 상세한 오류 처리가 포함된 향상된 메서드 사용
        controller.addViewSafe(view, params)
            .onSuccess {
                println("View added successfully")
                
                // Chain another operation
                // 다른 작업 연결
                val newParams = WindowManager.LayoutParams().apply {
                    width = 200
                    height = 200
                }
                
                controller.updateViewSafe(view, newParams)
                    .onSystemServiceFailure { updateError ->
                        println("Update failed: ${updateError.getDeveloperMessage()}")
                    }
            }
            .onSystemServiceFailure { error ->
                when (error) {
                    is SystemServiceError.Permission.SpecialPermissionRequired -> {
                        println("Special permission required: ${error.permission}")
                        println("Settings action: ${error.settingsAction}")
                        // Trigger permission request
                        // 권한 요청 트리거
                    }
                    is SystemServiceError.State.InvalidState -> {
                        println("Invalid state: ${error.currentState} -> ${error.requiredState}")
                        // Handle state issue
                        // 상태 문제 처리
                    }
                    is SystemServiceError.Unknown.Exception -> {
                        if (error.cause is WindowManager.BadTokenException) {
                            println("Bad window token - activity may be destroyed")
                            // Handle activity lifecycle issue
                            // 액티비티 생명주기 문제 처리
                        }
                    }
                    else -> {
                        println("Unexpected error: ${error.getUserMessage()}")
                    }
                }
            }
    }

    fun demonstrateConvenientUsage() {
        val view = View(context)
        val params = WindowManager.LayoutParams()

        // Using the convenient method with automatic error handling
        // 자동 오류 처리가 포함된 편리한 메서드 사용
        val success = controller.setFloatingFixedViewWithHandling(
            FloatingFixedView(view, params.x, params.y),
            onPermissionRequired = {
                println("Please grant overlay permission")
                // Show permission request UI
                // 권한 요청 UI 표시
            },
            onError = { message ->
                println("Error: $message")
                // Show error to user
                // 사용자에게 오류 표시
            }
        )

        if (success) {
            println("Floating view set successfully")
        }
    }
}