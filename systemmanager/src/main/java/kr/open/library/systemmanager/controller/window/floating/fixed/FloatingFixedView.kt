package kr.open.library.systemmanager.controller.window.floating.fixed

import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager.LayoutParams
import kr.open.library.systemmanager.extenstions.checkSdkVersion
import kr.open.library.systemmanager.extenstions.safeCatch

/**
 * FloatingFixedView - 고정 플로팅 뷰 클래스
 * Fixed Floating View Class
 * 
 * 고정된 위치의 플로팅 뷰를 관리하며, 드래그 가능한 뷰의 기본 클래스입니다.
 * Manages floating views at fixed positions and serves as the base class for draggable views.
 * 
 * 주요 기능 / Main Features:
 * - API 레벨별 레이아웃 파라미터 설정 / API level-specific layout parameter configuration
 * - 뷰 영역 계산 / View bounds calculation
 * - 플로팅 오버레이 권한 처리 / Floating overlay permission handling
 */
public open class FloatingFixedView(
    public val view: View,
    public val startX: Int,
    public val startY: Int
) {

    /**
     * 플로팅 뷰의 레이아웃 파라미터
     * Layout parameters for the floating view
     */
    public val params: LayoutParams = getFloatingLayoutParam().apply {
        gravity = Gravity.TOP or Gravity.LEFT
        this.x = startX
        this.y = startY
    }

    /**
     * API 레벨에 따른 플로팅 레이아웃 파라미터를 생성합니다.
     * Creates floating layout parameters based on API level.
     * 
     * @return 플로팅 레이아웃 파라미터 / Floating layout parameters
     */
    private fun getFloatingLayoutParam(): LayoutParams = safeCatch("getFloatingLayoutParam", 
        getDefaultLayoutParam()) {
        checkSdkVersion(Build.VERSION_CODES.O, {
            // Android O (API 26) 이상: TYPE_APPLICATION_OVERLAY 사용
            // Android O+ (API 26+): Use TYPE_APPLICATION_OVERLAY
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.TYPE_APPLICATION_OVERLAY,
                LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        }, {
            // Android O 미만: TYPE_SYSTEM_ALERT 사용
            // Below Android O: Use TYPE_SYSTEM_ALERT
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.TYPE_SYSTEM_ALERT + LayoutParams.TYPE_PHONE,
                LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        })
    }

    /**
     * 플로팅 뷰의 영역을 계산합니다.
     * Calculates the bounds of the floating view.
     * 
     * @return 뷰의 영역 정보 / View bounds information
     */
    public fun getRect(): Rect = safeCatch("getRect", Rect()) {
        val width = if (view.width > 0) view.width else view.measuredWidth
        val height = if (view.height > 0) view.height else view.measuredHeight
        Rect(params.x, params.y, params.x + width, params.y + height)
    }
    
    /**
     * 기본 레이아웃 파라미터를 생성합니다 (오류 시 폴백).
     * Creates default layout parameters (fallback for errors).
     * 
     * @return 기본 레이아웃 파라미터 / Default layout parameters
     */
    private fun getDefaultLayoutParam(): LayoutParams = LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT,
        LayoutParams.TYPE_APPLICATION_OVERLAY,
        LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )
}