package kr.open.library.systemmanager.info.display

import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.RequiresApi
import kr.open.library.systemmanager.base.BaseSystemService
import kr.open.library.systemmanager.extenstions.checkSdkVersion
import kr.open.library.systemmanager.extenstions.getWindowManager

/**
 * This class provides information about the display of an Android device.
 * DisplayInfo 클래스는 Android 기기의 디스플레이 정보를 제공.
 *
 * @param context The application context.
 * @param context 애플리케이션 컨텍스트.
 */
public open class DisplayInfo(context: Context)
    : BaseSystemService(context, null) {


    public val windowManager: WindowManager by lazy { context.getWindowManager() }

    /**
     * Returns the full screen size.
     * 전체 화면 크기를 반환.
     *
     * @return  The full screen size (width, height).
     * @return 전체 화면 크기 (너비, 높이)
     */
    public fun getFullScreenSize(): Point = checkSdkVersion(Build.VERSION_CODES.R,
        positiveWork = { with(getCurrentWindowMetrics().bounds) { Point(width(), height()) } },
        negativeWork = {
            val metrics = DisplayMetrics().apply {
                windowManager.defaultDisplay.getRealMetrics(this)
            }
            Point(metrics.widthPixels, metrics.heightPixels)
        }
    )

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getCurrentWindowMetrics() = windowManager.currentWindowMetrics

    /**
     * Returns the screen size excluding the status bar and navigation bar.
     * 상태 표시줄과 네비게이션 바를 제외한 화면 크기를 반환.
     *
     * If the desired result is not obtained,
     * be used getScreenWithStatusBar() - getNavigationBarHeight(activity: Activity)
     *
     * @return The screen size (width, height).
     * @return 화면 크기 (너비, 높이).
     */
    public fun getScreen(): Point = checkSdkVersion(Build.VERSION_CODES.R,
        positiveWork = {
            val windowMetrics = getCurrentWindowMetrics()
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())

            val width = windowMetrics.bounds.width() - (insets.left + insets.right)
            val height = windowMetrics.bounds.height() - (insets.bottom + insets.top)
            Point(width, height)
        }, negativeWork = {
            getScreenWithStatusBar().let { Point(it.x, it.y - getStatusBarHeight()) }
        }
    )

    /**
     * Returns the screen size excluding the navigation bar.
     * 탐색 표시줄을 제외한 화면 크기를 반환.
     *
     * @return The screen size (width, height).
     * @return 화면 크기 (너비, 높이)
     */
    public fun getScreenWithStatusBar(): Point = checkSdkVersion(Build.VERSION_CODES.R,
        positiveWork = {
            val windowMetrics = getCurrentWindowMetrics()
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())

            val width = windowMetrics.bounds.width()
            val height = windowMetrics.bounds.height() - (insets.bottom)
            Point(width, height)
        },
        negativeWork = {
            with(context.resources.displayMetrics) { Point(widthPixels, heightPixels) }
        }
    )

    /**
     * Returns the status bar height.
     * 상태 표시줄 높이를 반환.
     *
     * @return The status bar height.
     * @return 상태 표시줄 높이.
     */
    public fun getStatusBarHeight(): Int = checkSdkVersion(Build.VERSION_CODES.R,
        positiveWork = {
            getCurrentWindowMetrics().windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).top
        },
        negativeWork = {
            context.resources.getIdentifier("status_bar_height", "dimen", "android")
                .takeIf { it > 0 }?.let { context.resources.getDimensionPixelSize(it) }
                ?: throw Resources.NotFoundException("Cannot find status bar height. Try getStatusBarHeight(activity: Activity).")
        }
    )

    /**
     * Returns the navigation bar height.
     * 탐색 표시줄 높이를 반환.
     *
     * @return The navigation bar height.
     * @return 탐색 표시줄 높이.
     */
    public fun getNavigationBarHeight(): Int = checkSdkVersion(Build.VERSION_CODES.R,
        positiveWork = {
            getCurrentWindowMetrics().windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).bottom
        },
        negativeWork = {
            context.resources.getIdentifier("navigation_bar_height", "dimen", "android").
            takeIf { it > 0 }?.let { context.resources.getDimensionPixelSize(it) }
                ?: throw Resources.NotFoundException("Cannot find navigation bar height. Try getNavigationBarHeight(activity: Activity).")
        }
    )
}