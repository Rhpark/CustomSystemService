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
import kr.open.library.systemmanager.extenstions.getWindowManager


/**
 * Several functions require Android.Manifest.permission.SYSTEM_ALERT_WINDOW permission.
 *
 * FloatingViewController는 플로팅 뷰(드래그 가능한 뷰와 고정 뷰)를 관리하는 클래스.
 * 창을 추가/업데이트/제거하고, 충돌 감지 및 뷰 위치 업데이트 기능을 제공.
 */
public open class FloatingViewController(context: Context, ) :
    BaseSystemService(context, listOf(android.Manifest.permission.SYSTEM_ALERT_WINDOW)) {

    public val windowManager: WindowManager by lazy { context.getWindowManager() }

    private var floatingDragViewInfoList: MutableList<FloatingDragViewConfig> = mutableListOf()
    private var floatingFixedView: FloatingFixedView? = null


    public fun setFloatingFixedView(floatingView: FloatingFixedView?) {

        if (!isPermissionAllGranted()) {
            Logx.e("Can not draw window Floating Icon, Permission not granted")
            return
        }

        if(floatingView == null) {
            removeFloatingFixedView()
        } else {
            addView(floatingView.view, floatingView.params)
        }
        this.floatingFixedView = floatingView
    }

    public fun getFloatingFixedView(): FloatingFixedView? = floatingFixedView

    public fun addFloatingDragView(floatingView: FloatingDragView) {

        if (!isPermissionAllGranted()) {
            Logx.e("Can not draw window Floating Icon, Permission not granted")
            return
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
                    getCollisionTypeWithFixedView(floatingView)
//                onTouchMove?.invoke(floatingView.view, floatingView.params)
//                floatingView.updateCollisionState(FloatingViewTouchType.TOUCH_DRAG, FloatingViewCollisionsType.UNCOLLISIONS)
                    true
                }

                MotionEvent.ACTION_UP -> {
//                    floatingView.collisionsWhileTouchUp?.invoke(floatingView, getCollisionTypeWithFixedView(floatingView))
                    floatingView.updateCollisionState(
                        FloatingViewTouchType.TOUCH_UP,
                        getCollisionTypeWithFixedView(floatingView)
                    )
                    if (!config.getIsDragging()) {
                        floatingView.view.performClick()
                    }
                    config.onTouchUp()
//                onTouchUp?.invoke(view, floatingView.params)
//                floatingView.updateCollisionState(FloatingViewTouchType.TOUCH_UP,FloatingViewCollisionsType.UNCOLLISIONS)
                    true
                }

                else -> false
            }
        }

        floatingDragViewInfoList.add(config)
        addView(config.getView(), floatingView.params)
    }

    private fun getCollisionTypeWithFixedView(floatingDragView: FloatingDragView): FloatingViewCollisionsType =
        if(isCollisionFixedView(floatingDragView)) FloatingViewCollisionsType.OCCURING
        else FloatingViewCollisionsType.UNCOLLISIONS

    private fun isCollisionFixedView(floatingDragView: FloatingDragView): Boolean =
        floatingFixedView?.let { Rect.intersects(floatingDragView.getRect(), it.getRect()) } ?: false

    public fun updateView(view: View, params: LayoutParams) {
        params.x = params.x.coerceAtLeast(0)
        params.y = params.y.coerceAtLeast(0)
        windowManager.updateViewLayout(view, params)
    }

    public fun addView(view: View, params: LayoutParams) {
        windowManager.addView(view, params)
    }

    public fun removeFloatingDragView(floatingView: FloatingDragView) {
        floatingDragViewInfoList.find { it.floatingView == floatingView }?.let {
            it.getView().setOnTouchListener(null)
            removeView(it.getView())
            floatingDragViewInfoList.remove(it)
        }
    }

    public fun removeView(view: View) {
        windowManager.removeView(view)
    }

    public fun removeFloatingFixedView() {
        floatingFixedView?.let { removeView(it.view) }
        floatingFixedView = null
    }

    public fun removeAllFloatingView() {
        val configs = floatingDragViewInfoList.toList()
        configs.forEach { removeFloatingDragView(it.floatingView) }
        floatingDragViewInfoList.clear()
        removeFloatingFixedView()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeAllFloatingView()
    }
}