package kr.open.library.systemmanager.controller.bluetooth.base

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.*
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.base.BaseSystemService

/**
 * BLE 컴포넌트들의 공통 기반 클래스
 * BaseSystemService를 상속받아 권한 관리 자동화
 *
 * 🔧 2025-01-16 호환성 업데이트:
 * - 기존 ReentrantLock 기반 컴포넌트와 호환성 유지
 * - 새로운 Coroutine 기반 컴포넌트도 지원
 * - runOnUiThread/runBleTask 호환성 메서드 제공
 */
abstract class BleComponent(context: Context) : BaseSystemService(context, BLE_PERMISSIONS) {
    
    protected val TAG = this::class.simpleName ?: "BleComponent"
    
    companion object {
        // SDK별 권한 정의
        val BLE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    
    // 연결 상태 열거형
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        ERROR
    }
    
    // 단순한 상태 관리 (ReentrantLock 기반)
    protected val stateLock = ReentrantLock()
    
    // 최소한의 Coroutine Scope (cleanup용)
    protected val componentScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("BLE-${this::class.simpleName}")
    )
    
    // 단순한 상태 변수들
    @Volatile
    protected var connectionState: ConnectionState = ConnectionState.DISCONNECTED
        private set
    
    protected val isReady = AtomicBoolean(false)

    // 🔧 호환성: isReady() 메서드 충돌 방지
    open fun getReadyState(): Boolean = isReady.get()
    
    // 에러 처리는 단순한 콜백으로
    protected var errorListener: ((String) -> Unit)? = null

    // 🔧 호환성 브릿지 추가 - 기존 컴포넌트용
    @Deprecated("Use componentScope.launch for Coroutine-based components")
    protected fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }

    @Deprecated("Use componentScope.launch for Coroutine-based components")
    protected fun runBleTask(task: () -> Unit) {
        componentScope.launch { task() }
    }

    // 🔧 런타임 작업 실행 헬퍼 (Coroutine 래퍼)
    protected fun launchBleTask(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return componentScope.launch(dispatcher, block = block)
    }
    
    // 단순한 상태 업데이트 메서드들 (suspend 제거)
    protected fun updateConnectionState(newState: ConnectionState) {
        connectionState = newState
        Logx.d(TAG, "Connection state changed to: $newState")
    }
    
    protected fun updateReadyState(ready: Boolean) {
        isReady.set(ready)
        Logx.d(TAG, "Ready state changed to: $ready")
    }
    
    protected fun emitError(error: String) {
        Logx.e(TAG, error)
        errorListener?.invoke(error)
    }
    
    
    // 공통 생명주기 (서브클래스에서 구현) - suspend 함수로 변경
    abstract suspend fun initialize(): Boolean
    
    // GATT 리소스 정리 (이전 실패 경험 반영)
    internal abstract suspend fun cleanupGattResources()
    
    // 리소스 정리 - Coroutine Scope 취소
    open suspend fun cleanup() {
        Logx.d(TAG, "Cleaning up BLE component...")

        try {
            cleanupGattResources()
        } catch (e: Exception) {
            Logx.e(TAG, "Error during GATT cleanup: ${e.message}")
        }
        
        // 모든 Coroutine 취소
        componentScope.cancel("BLE Component cleanup")
        
        // 상태 초기화
        updateConnectionState(ConnectionState.DISCONNECTED)
        updateReadyState(false)
    }
    
    // 권한 체크 헬퍼
    protected fun checkAllRequiredPermissions(): Boolean {
        val deniedPermissions = getDeniedPermissionList()
        if (deniedPermissions.isNotEmpty()) {
            Logx.w(TAG, "Missing BLE permissions: $deniedPermissions")
            return false
        }
        return true
    }
    
    // 단순한 작업 실행 헬퍼 (에러 처리만)
    protected fun <T> safeExecute(
        operation: String,
        block: () -> T
    ): T? {
        return try {
            Logx.d(TAG, "Executing $operation")
            block()
        } catch (e: Exception) {
            val error = "$operation failed: ${e.message}"
            Logx.e(TAG, "$error: ${e.message}")
            emitError(error)
            null
        }
    }

    // 🔧 suspend 함수용 안전 실행 헬퍼
    protected suspend fun <T> safeExecuteSuspend(
        operation: String,
        block: suspend () -> T
    ): T? {
        return try {
            Logx.d(TAG, "Executing $operation (suspend)")
            block()
        } catch (e: Exception) {
            val error = "$operation failed: ${e.message}"
            Logx.e(TAG, "$error: ${e.message}")
            emitError(error)
            null
        }
    }
}