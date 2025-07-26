package kr.open.library.systemmanager.controller.window.floating.drag

import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.window.floating.fixed.FloatingFixedView
import kr.open.library.systemmanager.controller.window.floating.vo.FloatingViewCollisionsType
import kr.open.library.systemmanager.controller.window.floating.vo.FloatingViewTouchType


/**
 * FloatingDragView는 드래그 가능한 플로팅 뷰로, 드래그 시 충돌 상태를 갱신.
 * 코루틴 StateFlow를 통해 터치 단계별 충돌 상태를 항상 최신으로 보유.
 */
public open class FloatingDragView(
    view: View,
    startX: Int,
    startY: Int,
    public var collisionsWhileTouchDown: ((FloatingDragView, FloatingViewCollisionsType) -> Unit)? = null,
    public var collisionsWhileDrag: ((FloatingDragView, FloatingViewCollisionsType) -> Unit)? = null,
    public var collisionsWhileTouchUp: ((FloatingDragView, FloatingViewCollisionsType) -> Unit)? = null
) : FloatingFixedView(view, startX, startY) {

    private val msfCollisionStateFlow = MutableStateFlow<Pair<FloatingViewTouchType, FloatingViewCollisionsType>>(
        FloatingViewTouchType.TOUCH_UP to FloatingViewCollisionsType.UNCOLLISIONS
    )
    public val sfCollisionStateFlow: StateFlow<Pair<FloatingViewTouchType, FloatingViewCollisionsType>>
        get() = msfCollisionStateFlow

    public fun updateCollisionState(phase: FloatingViewTouchType, type: FloatingViewCollisionsType) {
        Logx.d(phase to type)
        msfCollisionStateFlow.value = phase to type
    }
}