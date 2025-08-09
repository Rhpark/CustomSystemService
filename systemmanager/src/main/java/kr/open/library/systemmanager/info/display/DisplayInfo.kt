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
 * This class offers both traditional exception-based methods and safer Result-based alternatives.
 * 이 클래스는 전통적인 예외 기반 메서드와 더 안전한 Result 기반 대안을 모두 제공합니다.
 *
 * Example usage / 사용 예제:
 * ```kotlin
 * val displayInfo = DisplayInfo(context)
 * 
 * // Traditional approach (may throw exceptions)
 * // 전통적인 방식 (예외 발생 가능)
 * try {
 *     val height = displayInfo.getStatusBarHeight()
 *     // Use height...
 * } catch (e: Resources.NotFoundException) {
 *     // Handle error...
 * }
 * 
 * // Safe approach with Result pattern
 * // Result 패턴을 사용한 안전한 방식
 * displayInfo.getStatusBarHeightSafe().fold(
 *     onSuccess = { height -> 
 *         // Use height safely
 *         // 높이를 안전하게 사용
 *     },
 *     onFailure = { error -> 
 *         // Handle error gracefully
 *         // 오류를 우아하게 처리
 *     }
 * )
 * 
 * // Convenient approach with default values
 * // 기본값을 사용한 편리한 방식
 * val height = displayInfo.getStatusBarHeightOrDefault(60) // Uses 60px if unable to determine
 * ```
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
     * Safely returns the screen size excluding the status bar and navigation bar using Result pattern.
     * Result 패턴을 사용하여 안전하게 상태바와 네비게이션바를 제외한 화면 크기를 반환합니다.
     *
     * @return Result containing the screen size or failure information.
     * @return 화면 크기 또는 실패 정보를 포함한 Result.
     */
    public fun getScreenSafe(): Result<Point> = runCatching {
        checkSdkVersion(Build.VERSION_CODES.R,
            positiveWork = {
                val windowMetrics = getCurrentWindowMetrics()
                val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())

                val width = windowMetrics.bounds.width() - (insets.left + insets.right)
                val height = windowMetrics.bounds.height() - (insets.bottom + insets.top)
                Point(width, height)
            }, negativeWork = {
                val screenWithStatusBar = getScreenWithStatusBar()
                val statusBarHeight = getStatusBarHeightSafe().getOrThrow()
                Point(screenWithStatusBar.x, screenWithStatusBar.y - statusBarHeight)
            }
        )
    }

    /**
     * Returns the screen size with a default fallback value.
     * 기본 대체 값과 함께 화면 크기를 반환합니다.
     *
     * @param defaultSize The default size to use if unable to determine actual size
     * @param defaultSize 실제 크기를 확인할 수 없을 때 사용할 기본 크기
     * @return The screen size or default value
     * @return 화면 크기 또는 기본값
     */
    public fun getScreenOrDefault(defaultSize: Point = Point(1080, 1920)): Point {
        return getScreenSafe().getOrDefault(defaultSize)
    }

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
     * Safely returns the status bar height using Result pattern.
     * Result 패턴을 사용하여 안전하게 상태 표시줄 높이를 반환합니다.
     *
     * @return Result containing the status bar height or failure information.
     * @return 상태 표시줄 높이 또는 실패 정보를 포함한 Result.
     */
    public fun getStatusBarHeightSafe(): Result<Int> = runCatching {
        checkSdkVersion(Build.VERSION_CODES.R,
            positiveWork = {
                getCurrentWindowMetrics().windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).top
            },
            negativeWork = {
                context.resources.getIdentifier("status_bar_height", "dimen", "android")
                    .takeIf { it > 0 }?.let { context.resources.getDimensionPixelSize(it) }
                    ?: throw Resources.NotFoundException("Cannot find status bar height from system resources")
            }
        )
    }

    /**
     * Returns the status bar height with a default fallback value.
     * 기본 대체 값과 함께 상태 표시줄 높이를 반환합니다.
     *
     * @param defaultHeight The default height to use if unable to determine actual height (default: 60px)
     * @param defaultHeight 실제 높이를 확인할 수 없을 때 사용할 기본 높이 (기본값: 60px)
     * @return The status bar height or default value
     * @return 상태 표시줄 높이 또는 기본값
     */
    public fun getStatusBarHeightOrDefault(defaultHeight: Int = 60): Int {
        return getStatusBarHeightSafe().getOrDefault(defaultHeight)
    }

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

    /**
     * Safely returns the navigation bar height using Result pattern.
     * Result 패턴을 사용하여 안전하게 네비게이션바 높이를 반환합니다.
     *
     * @return Result containing the navigation bar height or failure information.
     * @return 네비게이션바 높이 또는 실패 정보를 포함한 Result.
     */
    public fun getNavigationBarHeightSafe(): Result<Int> = runCatching {
        checkSdkVersion(Build.VERSION_CODES.R,
            positiveWork = {
                getCurrentWindowMetrics().windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).bottom
            },
            negativeWork = {
                context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
                    .takeIf { it > 0 }?.let { context.resources.getDimensionPixelSize(it) }
                    ?: throw Resources.NotFoundException("Cannot find navigation bar height from system resources")
            }
        )
    }

    /**
     * Returns the navigation bar height with a default fallback value.
     * 기본 대체 값과 함께 네비게이션바 높이를 반환합니다.
     *
     * @param defaultHeight The default height to use if unable to determine actual height (default: 0px for devices without navigation bar)
     * @param defaultHeight 실제 높이를 확인할 수 없을 때 사용할 기본 높이 (기본값: 0px, 네비게이션바가 없는 기기용)
     * @return The navigation bar height or default value
     * @return 네비게이션바 높이 또는 기본값
     */
    public fun getNavigationBarHeightOrDefault(defaultHeight: Int = 0): Int {
        return getNavigationBarHeightSafe().getOrDefault(defaultHeight)
    }
}