package kr.open.library.systemmanager.controller.proxy

import android.content.Context
import android.os.Build
import kr.open.library.systemmanager.extensions.safeCatch

/**
 * API 호환성 프록시의 기본 인터페이스
 * Base interface for API compatibility proxies
 */
public interface ApiCompatibilityProxy {
    val context: Context
    val supportedApiLevel: Int
    
    /**
     * 현재 API 레벨에서 지원되는지 확인
     * Check if supported on current API level
     */
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= supportedApiLevel
}

/**
 * 호환성 프록시의 공통 구현
 * Common implementation for compatibility proxies
 */
public abstract class BaseCompatibilityProxy(
    override val context: Context,
    override val supportedApiLevel: Int = Build.VERSION_CODES.BASE
) : ApiCompatibilityProxy {
    
    /**
     * API 레벨별 안전한 실행
     * Safe execution by API level
     */
    protected inline fun <T> executeSafely(
        operation: String,
        defaultValue: T,
        crossinline modernApi: () -> T,
        crossinline legacyApi: () -> T = { defaultValue }
    ): T = safeCatch(operation, defaultValue) {
        if (isSupported()) {
            modernApi()
        } else {
            legacyApi()
        }
    }
}