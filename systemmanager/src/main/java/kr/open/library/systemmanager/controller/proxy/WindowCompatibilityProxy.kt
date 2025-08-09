package kr.open.library.systemmanager.controller.proxy

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi

/**
 * 윈도우 관련 deprecated API들의 호환성 프록시
 * Compatibility proxy for window-related deprecated APIs
 */
public class WindowCompatibilityProxy(context: Context) : BaseCompatibilityProxy(
    context,
    Build.VERSION_CODES.O // API 26+에서 TYPE_APPLICATION_OVERLAY 권장
) {
    
    /**
     * 플로팅 오버레이용 윈도우 타입 프록시
     * Window type proxy for floating overlays
     */
    public fun getFloatingWindowType(): Int = executeSafely(
        operation = "WindowProxy.getFloatingWindowType",
        defaultValue = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        modernApi = {
            // API 26+ (Android O): TYPE_APPLICATION_OVERLAY 사용 (권장)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        },
        legacyApi = {
            // API 26 미만: 보안을 고려하여 가능한 한 TYPE_APPLICATION_OVERLAY 사용
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                // API 19 미만에서만 TYPE_SYSTEM_ALERT 사용 (레거시 지원)
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
        }
    )
    
    /**
     * 보안 강화된 플로팅 레이아웃 파라미터 생성
     * Create security-enhanced floating layout parameters
     */
    public fun createSecureFloatingLayoutParams(
        width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        flags: Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    ): WindowManager.LayoutParams = executeSafely(
        operation = "WindowProxy.createSecureFloatingLayoutParams",
        defaultValue = WindowManager.LayoutParams(
            width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 
            flags, PixelFormat.TRANSLUCENT
        ),
        modernApi = {
            WindowManager.LayoutParams(
                width, height, 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
            )
        },
        legacyApi = {
            WindowManager.LayoutParams(
                width, height,
                getFloatingWindowType(),
                flags,
                PixelFormat.TRANSLUCENT
            )
        }
    )
    
    /**
     * 대화형 플로팅 레이아웃 파라미터 생성 (터치 가능)
     * Create interactive floating layout parameters (touchable)
     */
    public fun createInteractiveFloatingLayoutParams(
        width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        height: Int = WindowManager.LayoutParams.WRAP_CONTENT
    ): WindowManager.LayoutParams = executeSafely(
        operation = "WindowProxy.createInteractiveFloatingLayoutParams", 
        defaultValue = WindowManager.LayoutParams(
            width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ),
        modernApi = {
            WindowManager.LayoutParams(
                width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // 터치는 가능하지만 포커스는 받지 않음
                PixelFormat.TRANSLUCENT
            )
        },
        legacyApi = {
            WindowManager.LayoutParams(
                width, height,
                getFloatingWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        }
    )
    
    /**
     * 시스템 오버레이 권한이 필요한지 확인
     * Check if system overlay permission is required
     */
    public fun requiresOverlayPermission(): Boolean = executeSafely(
        operation = "WindowProxy.requiresOverlayPermission",
        defaultValue = true,
        modernApi = {
            // API 23+ (Android M): SYSTEM_ALERT_WINDOW 권한 필요
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        },
        legacyApi = {
            // API 23 미만: 권한 불필요하지만 보안상 권한 확인 권장
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        }
    )
    
    /**
     * 윈도우 타입별 보안 레벨 확인
     * Check security level by window type
     */
    public enum class SecurityLevel {
        HIGH,    // TYPE_APPLICATION_OVERLAY
        MEDIUM,  // 기타 제한된 타입
        LOW      // TYPE_SYSTEM_ALERT (deprecated)
    }
    
    public fun getWindowTypeSecurityLevel(windowType: Int): SecurityLevel = when (windowType) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY -> SecurityLevel.HIGH
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT -> SecurityLevel.LOW
        else -> SecurityLevel.MEDIUM
    }
}