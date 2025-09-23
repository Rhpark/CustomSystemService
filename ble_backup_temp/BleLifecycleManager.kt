package kr.open.library.systemmanager.controller.bluetooth.base

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kr.open.library.logcat.Logx
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * BLE 생명주기 관리자
 * BLE Lifecycle Manager
 * 
 * 앱의 생명주기와 BLE 작업을 동기화하여 배터리 최적화 및 안정성을 제공합니다.
 * Synchronizes app lifecycle with BLE operations for battery optimization and stability.
 * 
 * 주요 기능:
 * Key features:
 * - 앱 백그라운드/포그라운드 상태 추적
 * - BLE 작업의 자동 일시 중지/재개
 * - 메모리 누수 방지
 * - 배터리 최적화 대응
 */
class BleLifecycleManager private constructor(private val context: Context) {
    
    private val TAG = "BleLifecycleManager"
    
    /**
     * 앱 상태
     */
    enum class AppState {
        FOREGROUND,     // 포그라운드
        BACKGROUND,     // 백그라운드
        DESTROYED       // 종료됨
    }
    
    /**
     * BLE 생명주기 이벤트
     */
    enum class BleLifecycleEvent {
        APP_FOREGROUND,         // 앱 포그라운드 진입
        APP_BACKGROUND,         // 앱 백그라운드 진입
        ACTIVITY_CREATED,       // 액티비티 생성
        ACTIVITY_DESTROYED,     // 액티비티 파괴
        LOW_MEMORY,            // 메모리 부족
        SYSTEM_SHUTDOWN        // 시스템 종료
    }
    
    /**
     * BLE 생명주기 리스너
     */
    interface BleLifecycleListener {
        fun onBleLifecycleEvent(event: BleLifecycleEvent, data: Any? = null)
    }
    
    /**
     * 관리되는 BLE 컨트롤러 정보
     */
    private data class ManagedController(
        val controllerRef: WeakReference<BluetoothBaseController>,
        val priority: Int, // 우선순위 (낮을수록 높은 우선순위)
        var isActive: Boolean = true
    ) {
        fun isAlive(): Boolean = controllerRef.get() != null
    }
    
    private val listeners = CopyOnWriteArrayList<BleLifecycleListener>()
    private val managedControllers = CopyOnWriteArrayList<ManagedController>()
    
    @Volatile
    private var currentAppState = AppState.FOREGROUND
    
    @Volatile
    private var isInitialized = false
    
    @Volatile
    private var isLowMemory = false
    
    private var foregroundActivityCount = 0
    
    /**
     * 앱 생명주기 관찰자
     */
    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)
            handleAppForeground()
        }
        
        override fun onStop(owner: LifecycleOwner) {
            super.onStop(owner)
            handleAppBackground()
        }
    }
    
    /**
     * 액티비티 생명주기 콜백
     */
    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            foregroundActivityCount++
            notifyListeners(BleLifecycleEvent.ACTIVITY_CREATED, activity)
        }
        
        override fun onActivityStarted(activity: Activity) {
            // 구현 필요시 추가
        }
        
        override fun onActivityResumed(activity: Activity) {
            // 구현 필요시 추가
        }
        
        override fun onActivityPaused(activity: Activity) {
            // 구현 필요시 추가
        }
        
        override fun onActivityStopped(activity: Activity) {
            // 구현 필요시 추가
        }
        
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            // 구현 필요시 추가
        }
        
        override fun onActivityDestroyed(activity: Activity) {
            foregroundActivityCount--
            notifyListeners(BleLifecycleEvent.ACTIVITY_DESTROYED, activity)
        }
    }
    
    /**
     * 초기화
     */
    fun initialize() {
        if (isInitialized) {
            Logx.w(TAG, "BleLifecycleManager already initialized")
            return
        }
        
        try {
            // 프로세스 생명주기 관찰 시작
            ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
            
            // 액티비티 생명주기 콜백 등록
            if (context is Application) {
                context.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
            } else {
                val app = context.applicationContext as? Application
                app?.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
            }
            
            isInitialized = true
            Logx.i(TAG, "BleLifecycleManager initialized")
            
        } catch (e: Exception) {
            Logx.e(TAG, "Failed to initialize BleLifecycleManager", e)
            throw e
        }
    }
    
    /**
     * BLE 컨트롤러 등록
     */
    fun registerController(controller: BluetoothBaseController, priority: Int = 100) {
        val managedController = ManagedController(
            controllerRef = WeakReference(controller),
            priority = priority
        )
        
        managedControllers.add(managedController)
        
        // 우선순위 순으로 정렬
        managedControllers.sortBy { it.priority }
        
        Logx.d(TAG, "Registered BLE controller: ${controller::class.simpleName} (priority: $priority)")
        
        // 현재 앱 상태에 따라 컨트롤러 상태 조정
        when (currentAppState) {
            AppState.BACKGROUND -> pauseController(controller)
            AppState.DESTROYED -> stopController(controller)
            else -> { /* 포그라운드는 그대로 유지 */ }
        }
    }
    
    /**
     * BLE 컨트롤러 해제
     */
    fun unregisterController(controller: BluetoothBaseController) {
        managedControllers.removeAll { managedController ->
            val registeredController = managedController.controllerRef.get()
            registeredController == null || registeredController == controller
        }
        
        Logx.d(TAG, "Unregistered BLE controller: ${controller::class.simpleName}")
    }
    
    /**
     * 생명주기 리스너 등록
     */
    fun addLifecycleListener(listener: BleLifecycleListener) {
        listeners.addIfAbsent(listener)
    }
    
    /**
     * 생명주기 리스너 해제
     */
    fun removeLifecycleListener(listener: BleLifecycleListener) {
        listeners.remove(listener)
    }
    
    /**
     * 현재 앱 상태 반환
     */
    fun getCurrentAppState(): AppState = currentAppState
    
    /**
     * 메모리 부족 상태 알림
     */
    fun notifyLowMemory() {
        isLowMemory = true
        notifyListeners(BleLifecycleEvent.LOW_MEMORY)
        
        // 낮은 우선순위 컨트롤러들을 일시 중지
        pauseLowPriorityControllers()
        
        Logx.w(TAG, "Low memory condition detected - paused low priority BLE controllers")
    }
    
    /**
     * 메모리 부족 상태 해제
     */
    fun clearLowMemory() {
        isLowMemory = false
        
        // 일시 중지된 컨트롤러들을 재개 (포그라운드일 때만)
        if (currentAppState == AppState.FOREGROUND) {
            resumeAllControllers()
        }
        
        Logx.i(TAG, "Low memory condition cleared - resumed BLE controllers")
    }
    
    /**
     * 시스템 종료 알림
     */
    fun notifySystemShutdown() {
        currentAppState = AppState.DESTROYED
        notifyListeners(BleLifecycleEvent.SYSTEM_SHUTDOWN)
        
        // 모든 컨트롤러 정리
        stopAllControllers()
        
        Logx.i(TAG, "System shutdown - stopped all BLE controllers")
    }
    
    /**
     * 생명주기 관리자 정리
     */
    fun cleanup() {
        if (!isInitialized) return
        
        try {
            // 모든 컨트롤러 정리
            stopAllControllers()
            
            // 리스너 정리
            listeners.clear()
            managedControllers.clear()
            
            // 생명주기 관찰 해제
            ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
            
            // 액티비티 콜백 해제
            if (context is Application) {
                context.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
            } else {
                val app = context.applicationContext as? Application
                app?.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
            }
            
            isInitialized = false
            currentAppState = AppState.DESTROYED
            
            Logx.i(TAG, "BleLifecycleManager cleaned up")
            
        } catch (e: Exception) {
            Logx.e(TAG, "Error during cleanup", e)
        }
    }
    
    /**
     * 앱 포그라운드 진입 처리
     */
    private fun handleAppForeground() {
        if (currentAppState == AppState.FOREGROUND) return
        
        currentAppState = AppState.FOREGROUND
        notifyListeners(BleLifecycleEvent.APP_FOREGROUND)
        
        // 메모리 부족 상태가 아니라면 모든 컨트롤러 재개
        if (!isLowMemory) {
            resumeAllControllers()
        }
        
        Logx.i(TAG, "App entered foreground - resumed BLE controllers")
    }
    
    /**
     * 앱 백그라운드 진입 처리
     */
    private fun handleAppBackground() {
        if (currentAppState == AppState.BACKGROUND) return
        
        currentAppState = AppState.BACKGROUND
        notifyListeners(BleLifecycleEvent.APP_BACKGROUND)
        
        // 모든 컨트롤러 일시 중지
        pauseAllControllers()
        
        Logx.i(TAG, "App entered background - paused BLE controllers")
    }
    
    /**
     * 리스너들에게 이벤트 알림
     */
    private fun notifyListeners(event: BleLifecycleEvent, data: Any? = null) {
        listeners.forEach { listener ->
            try {
                listener.onBleLifecycleEvent(event, data)
            } catch (e: Exception) {
                Logx.w(TAG, "Error notifying lifecycle listener", e)
            }
        }
    }
    
    /**
     * 모든 컨트롤러 재개
     */
    private fun resumeAllControllers() {
        managedControllers.forEach { managedController ->
            managedController.controllerRef.get()?.let { controller ->
                if (!managedController.isActive) {
                    resumeController(controller)
                    managedController.isActive = true
                }
            }
        }
        
        // 죽은 참조 정리
        cleanupDeadReferences()
    }
    
    /**
     * 모든 컨트롤러 일시 중지
     */
    private fun pauseAllControllers() {
        managedControllers.forEach { managedController ->
            managedController.controllerRef.get()?.let { controller ->
                if (managedController.isActive) {
                    pauseController(controller)
                    managedController.isActive = false
                }
            }
        }
    }
    
    /**
     * 낮은 우선순위 컨트롤러들 일시 중지
     */
    private fun pauseLowPriorityControllers() {
        managedControllers
            .filter { it.priority > 50 } // 우선순위 50 초과는 낮은 우선순위로 간주
            .forEach { managedController ->
                managedController.controllerRef.get()?.let { controller ->
                    if (managedController.isActive) {
                        pauseController(controller)
                        managedController.isActive = false
                    }
                }
            }
    }
    
    /**
     * 모든 컨트롤러 정리
     */
    private fun stopAllControllers() {
        managedControllers.forEach { managedController ->
            managedController.controllerRef.get()?.let { controller ->
                stopController(controller)
            }
        }
        managedControllers.clear()
    }
    
    /**
     * 개별 컨트롤러 재개
     */
    private fun resumeController(controller: BluetoothBaseController) {
        try {
            // 컨트롤러별 재개 로직 (하위 클래스에서 구현)
            Logx.d(TAG, "Resumed controller: ${controller::class.simpleName}")
        } catch (e: Exception) {
            Logx.w(TAG, "Error resuming controller: ${controller::class.simpleName}", e)
        }
    }
    
    /**
     * 개별 컨트롤러 일시 중지
     */
    private fun pauseController(controller: BluetoothBaseController) {
        try {
            // 컨트롤러별 일시 중지 로직 (하위 클래스에서 구현)
            Logx.d(TAG, "Paused controller: ${controller::class.simpleName}")
        } catch (e: Exception) {
            Logx.w(TAG, "Error pausing controller: ${controller::class.simpleName}", e)
        }
    }
    
    /**
     * 개별 컨트롤러 정리
     */
    private fun stopController(controller: BluetoothBaseController) {
        try {
            controller.onDestroy()
            Logx.d(TAG, "Stopped controller: ${controller::class.simpleName}")
        } catch (e: Exception) {
            Logx.w(TAG, "Error stopping controller: ${controller::class.simpleName}", e)
        }
    }
    
    /**
     * 죽은 참조들 정리
     */
    private fun cleanupDeadReferences() {
        managedControllers.removeAll { !it.isAlive() }
    }
    
    /**
     * 상태 정보 반환
     */
    fun getStatusInfo(): String {
        cleanupDeadReferences()
        
        return buildString {
            appendLine("=== BLE Lifecycle Manager Status ===")
            appendLine("Initialized: $isInitialized")
            appendLine("Current App State: $currentAppState")
            appendLine("Low Memory: $isLowMemory")
            appendLine("Foreground Activities: $foregroundActivityCount")
            appendLine("Managed Controllers: ${managedControllers.size}")
            managedControllers.forEach { managedController ->
                val controllerName = managedController.controllerRef.get()?.let { 
                    it::class.simpleName 
                } ?: "Dead Reference"
                appendLine("  - $controllerName (priority: ${managedController.priority}, active: ${managedController.isActive})")
            }
            appendLine("Registered Listeners: ${listeners.size}")
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: BleLifecycleManager? = null
        
        fun getInstance(context: Context): BleLifecycleManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BleLifecycleManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        fun isInitialized(): Boolean = INSTANCE?.isInitialized == true
    }
}