package kr.open.library.systemmanager.controller.window

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.base.BaseSystemService
import kr.open.library.systemmanager.controller.window.floating.drag.FloatingDragView
import kr.open.library.systemmanager.controller.window.floating.drag.FloatingDragViewConfig
import kr.open.library.systemmanager.controller.window.floating.fixed.FloatingFixedView
import kr.open.library.systemmanager.controller.window.floating.vo.FloatingViewCollisionsType
import kr.open.library.systemmanager.controller.window.floating.vo.FloatingViewTouchType
import kr.open.library.systemmanager.extensions.getWindowManager
import kr.open.library.systemmanager.extensions.safeCatch


/**
 * FloatingViewController - 플로팅 뷰 관리 컨트롤러
 * Floating View Management Controller
 * 
 * 드래그 가능한 뷰와 고정 뷰를 관리하며, 충돌 감지 및 뷰 위치 업데이트 기능을 제공합니다.
 * Manages draggable and fixed floating views, providing collision detection and view position update functionality.
 * 
 * 주요 기능 / Main Features:
 * - 플로팅 뷰 추가/제거 / Add/Remove floating views
 * - 드래그 뷰와 고정 뷰 간 충돌 감지 / Collision detection between drag and fixed views
 * - 윈도우 매니저를 통한 뷰 관리 / View management through WindowManager
 * 
 * 필수 권한 / Required Permission:
 * - Android.Manifest.permission.SYSTEM_ALERT_WINDOW
 */
public open class FloatingViewController(context: Context) :
    BaseSystemService(context, listOf(android.Manifest.permission.SYSTEM_ALERT_WINDOW)) {

    public val windowManager: WindowManager by lazy { context.getWindowManager() }

    private var floatingDragViewInfoList: MutableList<FloatingDragViewConfig> = mutableListOf()
    private var floatingFixedView: FloatingFixedView? = null


    /**
     * 고정 플로팅 뷰를 설정합니다.
     * Sets the fixed floating view.
     * 
     * @param floatingView 설정할 고정 플로팅 뷰 (null이면 제거) / Fixed floating view to set (remove if null)
     * @return 성공 여부 / Success status
     */
    public fun setFloatingFixedView(floatingView: FloatingFixedView?): Boolean = safeCatch("setFloatingFixedView", false) {
        if (!isPermissionAllGranted()) {
            val guidanceMessage = getPermissionGuidanceMessage()
            Logx.e("Cannot display floating view. $guidanceMessage")
            return@safeCatch false
        }

        if(floatingView == null) {
            removeFloatingFixedView()
        } else {
            addView(floatingView.view, floatingView.params)
        }
        this.floatingFixedView = floatingView
        true
    }

    /**
     * Enhanced method for setting floating view with permission guidance callback.
     * 권한 안내 콜백이 포함된 향상된 플로팅 뷰 설정 메서드입니다.
     * 
     * @param floatingView 설정할 고정 플로팅 뷰 / Fixed floating view to set
     * @param onPermissionRequired 권한이 필요할 때 호출되는 콜백 / Callback when permission is required
     * @return Result containing success or error information
     */
    public fun setFloatingFixedViewSafe(
        floatingView: FloatingFixedView?,
        onPermissionRequired: ((String, String?) -> Unit)? = null
    ): Result<Unit> {
        return safeExecuteWithPermissionGuidance(
            "setFloatingFixedViewSafe",
            onPermissionRequired = { error ->
                onPermissionRequired?.invoke(error.permission, error.settingsAction)
            }
        ) {
            if(floatingView == null) {
                removeFloatingFixedView()
            } else {
                addView(floatingView.view, floatingView.params)
            }
            this.floatingFixedView = floatingView
        }
    }

    /**
     * 현재 설정된 고정 플로팅 뷰를 반환합니다.
     * Returns the currently set fixed floating view.
     * 
     * @return 고정 플로팅 뷰 (없으면 null) / Fixed floating view (null if none)
     */
    public fun getFloatingFixedView(): FloatingFixedView? = floatingFixedView

    /**
     * 드래그 가능한 플로팅 뷰를 추가합니다.
     * Adds a draggable floating view.
     * 
     * @param floatingView 추가할 드래그 플로팅 뷰 / Draggable floating view to add
     * @return 추가 성공 여부 / Addition success status
     */
    public fun addFloatingDragView(floatingView: FloatingDragView): Boolean = safeCatch("addFloatingDragView", false) {
        if (!isPermissionAllGranted()) {
            val guidanceMessage = getPermissionGuidanceMessage()
            Logx.e("Cannot add floating drag view. $guidanceMessage")
            return@safeCatch false
        }

        val config = FloatingDragViewConfig(floatingView)

        floatingView.view.setOnTouchListener{ view, event->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    config.onTouchDown(event.rawX, event.rawY)
                    floatingView.updateCollisionState(
                        FloatingViewTouchType.TOUCH_DOWN,
                        getCollisionTypeWithFixedView(floatingView)
                    )
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    config.onTouchMove(event.rawX, event.rawY)
                    updateView(view, floatingView.params)
                    floatingView.updateCollisionState(
                        FloatingViewTouchType.TOUCH_MOVE,
                        getCollisionTypeWithFixedView(floatingView)
                    )
                    // 중복 호출 제거됨 (기존 버그 수정)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    floatingView.updateCollisionState(
                        FloatingViewTouchType.TOUCH_UP,
                        getCollisionTypeWithFixedView(floatingView)
                    )
                    if (!config.getIsDragging()) {
                        floatingView.view.performClick()
                    }
                    config.onTouchUp()
                    true
                }

                else -> false
            }
        }

        floatingDragViewInfoList.add(config)
        addView(config.getView(), floatingView.params)
        true
    }

    /**
     * Enhanced method for adding draggable floating view with permission guidance.
     * 권한 안내가 포함된 향상된 드래그 플로팅 뷰 추가 메서드입니다.
     * 
     * @param floatingView 추가할 드래그 플로팅 뷰 / Draggable floating view to add
     * @param onPermissionRequired 권한이 필요할 때 호출되는 콜백 / Callback when permission is required
     * @return Result containing success or error information
     */
    public fun addFloatingDragViewSafe(
        floatingView: FloatingDragView,
        onPermissionRequired: ((String, String?) -> Unit)? = null
    ): Result<Unit> {
        return safeExecuteWithPermissionGuidance(
            "addFloatingDragViewSafe",
            onPermissionRequired = { error ->
                onPermissionRequired?.invoke(error.permission, error.settingsAction)
            }
        ) {
            val config = FloatingDragViewConfig(floatingView)

            floatingView.view.setOnTouchListener{ view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        config.onTouchDown(event.rawX, event.rawY)
                        floatingView.updateCollisionState(
                            FloatingViewTouchType.TOUCH_DOWN,
                            getCollisionTypeWithFixedView(floatingView)
                        )
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        config.onTouchMove(event.rawX, event.rawY)
                        updateView(view, floatingView.params)
                        floatingView.updateCollisionState(
                            FloatingViewTouchType.TOUCH_MOVE,
                            getCollisionTypeWithFixedView(floatingView)
                        )
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        floatingView.updateCollisionState(
                            FloatingViewTouchType.TOUCH_UP,
                            getCollisionTypeWithFixedView(floatingView)
                        )
                        if (!config.getIsDragging()) {
                            floatingView.view.performClick()
                        }
                        config.onTouchUp()
                        true
                    }

                    else -> false
                }
            }

            floatingDragViewInfoList.add(config)
            addView(config.getView(), floatingView.params)
        }
    }

    private fun getCollisionTypeWithFixedView(floatingDragView: FloatingDragView): FloatingViewCollisionsType =
        if(isCollisionFixedView(floatingDragView)) FloatingViewCollisionsType.OCCURING
        else FloatingViewCollisionsType.UNCOLLISIONS

    private fun isCollisionFixedView(floatingDragView: FloatingDragView): Boolean =
        floatingFixedView?.let { Rect.intersects(floatingDragView.getRect(), it.getRect()) } ?: false

    /**
     * 뷰의 레이아웃을 업데이트합니다.
     * Updates the view's layout.
     * 
     * @param view 업데이트할 뷰 / View to update
     * @param params 새로운 레이아웃 파라미터 / New layout parameters
     * @return 업데이트 성공 여부 / Update success status
     */
    public fun updateView(view: View, params: LayoutParams): Boolean = safeCatch("updateView", false) {
        params.x = params.x.coerceAtLeast(0)
        params.y = params.y.coerceAtLeast(0)
        windowManager.updateViewLayout(view, params)
        true
    }

    /**
     * Enhanced method for updating view layout with validation and Result pattern.
     * 검증과 Result 패턴이 포함된 향상된 뷰 레이아웃 업데이트 메서드입니다.
     * 
     * @param view 업데이트할 뷰 / View to update
     * @param params 새로운 레이아웃 파라미터 / New layout parameters
     * @return Result containing success or error information
     */
    public fun updateViewSafe(view: View, params: LayoutParams): Result<Unit> {
        return safeExecute("updateViewSafe", requiresPermission = false) {
            validateViewAndParams(view, params)
            
            // Enhanced boundary checking with screen dimensions
            val displayMetrics = context.resources.displayMetrics
            params.x = params.x.coerceIn(0, displayMetrics.widthPixels - (params.width.takeIf { it > 0 } ?: 100))
            params.y = params.y.coerceIn(0, displayMetrics.heightPixels - (params.height.takeIf { it > 0 } ?: 100))
            
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("View is not attached to window manager", e)
            }
        }
    }

    /**
     * 윈도우 매니저에 뷰를 추가합니다.
     * Adds a view to the window manager.
     * 
     * @param view 추가할 뷰 / View to add
     * @param params 레이아웃 파라미터 / Layout parameters
     * @return 추가 성공 여부 / Addition success status
     */
    public fun addView(view: View, params: LayoutParams): Boolean = safeCatch("addView", false) {
        windowManager.addView(view, params)
        true
    }

    /**
     * Enhanced method for adding any view with detailed error handling and validation.
     * 상세한 오류 처리와 검증이 포함된 향상된 뷰 추가 메서드입니다.
     * 
     * @param view 추가할 뷰 / View to add
     * @param params 레이아웃 파라미터 / Layout parameters
     * @return Result containing success or error information
     */
    public fun addViewSafe(view: View, params: LayoutParams): Result<Unit> {
        return safeExecute("addViewSafe") {
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
     * 드래그 플로팅 뷰를 제거합니다.
     * Removes a draggable floating view.
     * 
     * @param floatingView 제거할 드래그 플로팅 뷰 / Draggable floating view to remove
     * @return 제거 성공 여부 / Removal success status
     */
    public fun removeFloatingDragView(floatingView: FloatingDragView): Boolean = safeCatch("removeFloatingDragView", false) {
        floatingDragViewInfoList.find { it.floatingView == floatingView }?.let {
            it.getView().setOnTouchListener(null)
            removeView(it.getView())
            floatingDragViewInfoList.remove(it)
            true
        } ?: false
    }

    /**
     * 윈도우 매니저에서 뷰를 제거합니다.
     * Removes a view from the window manager.
     * 
     * @param view 제거할 뷰 / View to remove
     * @return 제거 성공 여부 / Removal success status
     */
    public fun removeView(view: View): Boolean = safeCatch("removeView", false) {
        windowManager.removeView(view)
        true
    }

    /**
     * Enhanced method for removing view with proper error handling.
     * 적절한 오류 처리가 포함된 향상된 뷰 제거 메서드입니다.
     * 
     * @param view 제거할 뷰 / View to remove
     * @return Result containing success or error information
     */
    public fun removeViewSafe(view: View): Result<Unit> {
        return safeExecute("removeViewSafe", requiresPermission = false) {
            try {
                windowManager.removeView(view)
            } catch (e: IllegalArgumentException) {
                // View was not added to window manager
                throw IllegalStateException("View is not attached to window manager", e)
            }
        }
    }

    /**
     * 고정 플로팅 뷰를 제거합니다.
     * Removes the fixed floating view.
     * 
     * @return 제거 성공 여부 / Removal success status
     */
    public fun removeFloatingFixedView(): Boolean = safeCatch("removeFloatingFixedView", false) {
        floatingFixedView?.let { removeView(it.view) }
        floatingFixedView = null
        true
    }

    /**
     * 모든 플로팅 뷰를 제거합니다.
     * Removes all floating views.
     * 
     * @return 제거 성공 여부 / Removal success status
     */
    public fun removeAllFloatingView(): Boolean = safeCatch("removeAllFloatingView", false) {
        val configs = floatingDragViewInfoList.toList()
        configs.forEach { removeFloatingDragView(it.floatingView) }
        floatingDragViewInfoList.clear()
        removeFloatingFixedView()
        true
    }

    /**
     * Validates view and layout parameters before window operations.
     * 윈도우 작업 전에 뷰와 레이아웃 매개변수의 유효성을 검사합니다.
     */
    private fun validateViewAndParams(view: View, params: LayoutParams) {
        require(view.parent == null) { "View already has a parent" }
        require(params.width > 0 || params.width == LayoutParams.MATCH_PARENT || params.width == LayoutParams.WRAP_CONTENT) {
            "Invalid width: ${params.width}"
        }
        require(params.height > 0 || params.height == LayoutParams.MATCH_PARENT || params.height == LayoutParams.WRAP_CONTENT) {
            "Invalid height: ${params.height}"
        }
    }

    /**
     * Batch operation to add multiple views with transaction-like behavior.
     * 트랜잭션과 같은 동작으로 여러 뷰를 추가하는 일괄 작업입니다.
     * 
     * @param viewsAndParams 뷰와 파라미터 쌍의 목록 / List of view and parameter pairs
     * @return Result containing success or error information
     */
    public fun addViewsBatch(viewsAndParams: List<Pair<View, LayoutParams>>): Result<Unit> {
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
                    }
                }
                throw e
            }
        }
    }

    /**
     * Enhanced destroy method with proper cleanup and error handling.
     * 적절한 정리 작업과 오류 처리가 포함된 향상된 소멸 메서드입니다.
     */
    override fun onDestroy() {
        try {
            // Clear all touch listeners to prevent memory leaks
            floatingDragViewInfoList.forEach { config ->
                try {
                    config.getView().setOnTouchListener(null)
                } catch (ignored: Exception) {
                    // Ignore errors during cleanup
                }
            }
            
            removeAllFloatingView()
            floatingDragViewInfoList.clear()
        } catch (e: Exception) {
            Logx.e("Error during FloatingViewController cleanup: ${e.message}")
        } finally {
            super.onDestroy()
        }
    }
}