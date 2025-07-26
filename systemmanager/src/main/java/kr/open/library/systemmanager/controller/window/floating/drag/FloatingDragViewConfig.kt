package kr.open.library.systemmanager.controller.window.floating.drag

import android.graphics.Point
import android.graphics.PointF
import android.view.View
import kotlin.math.abs

internal class FloatingDragViewConfig(
    public val floatingView: FloatingDragView,

) {
    /**
     * Value for Drag
     */
    private var initialTouchDownPosition :Point = Point(0,0)
    private var initialTouchDragPosition:PointF = PointF(0f,0f)

    /**
     * Value for Click
     */
    private val clickThreshold = 15
    private var isDragging = false
    private var initialClickDownPosition:PointF = PointF(0f,0f)

    public fun onTouchDown(rawX: Float, rawY: Float) {
        setLocation(rawX,rawY)
    }

    public fun onTouchMove(rawX: Float, rawY: Float) {
        changeLocation(rawX,rawY)
    }

    public fun onTouchUp() {
        isDragging = false
    }

    public fun getIsDragging() = isDragging
//
//    public fun setOnTouchListener(view: View, event: MotionEvent): Boolean {
//        return when (event.action) {
//            MotionEvent.ACTION_DOWN -> {
//                setLocation(event.rawX, event.rawY)
////                onTouchDown?.invoke(view, floatingView.params)
//                floatingView.updateCollisionState(FloatingViewTouchType.TOUCH_DOWN,FloatingViewCollisionsType.UNCOLLISIONS)
//                true
//            }
//
//            MotionEvent.ACTION_MOVE -> {
//                changeLocation(event.rawX, event.rawY)
////                onTouchMove?.invoke(floatingView.view, floatingView.params)
////                floatingView.updateCollisionState(FloatingViewTouchType.TOUCH_DRAG, FloatingViewCollisionsType.UNCOLLISIONS)
//                true
//            }
//
//            MotionEvent.ACTION_UP -> {
//                if (!isDragging) {
//                    floatingView.view.performClick()
//                }
////                onTouchUp?.invoke(view, floatingView.params)
////                floatingView.updateCollisionState(FloatingViewTouchType.TOUCH_UP,FloatingViewCollisionsType.UNCOLLISIONS)
//                isDragging = false
//                true
//            }
//            else -> false
//        }
//    }

    private fun setLocation(x:Float, y:Float) {
        initDrag(x, y)
        initClick(x, y)
    }

    private fun initDrag(x:Float, y:Float) {
        initialTouchDownPosition.set(floatingView.params.x, floatingView.params.y)
        initialTouchDragPosition.set(x,y)
    }

    private fun initClick(x:Float, y:Float) {
        isDragging = false
        initialClickDownPosition.set(x,y)
    }

    private fun changeLocation(x:Float, y:Float) {
        touchDragChangeLocation(x,y)
        clickDragChanged(x,y)
    }

    private fun touchDragChangeLocation(x:Float, y:Float) {
        floatingView.params.x = initialTouchDownPosition.x + (x - initialTouchDragPosition.x).toInt()
        floatingView.params.y = initialTouchDownPosition.y + (y - initialTouchDragPosition.y).toInt()
    }

    private fun clickDragChanged(x: Float, y: Float) {
        if (abs(x - initialClickDownPosition.x) > clickThreshold ||
            abs(y - initialClickDownPosition.y) > clickThreshold) {
            isDragging = true
        }
    }

    public fun getView():View = floatingView.view
}