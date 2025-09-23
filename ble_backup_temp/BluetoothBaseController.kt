package kr.open.library.systemmanager.controller.bluetooth.base

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.base.BaseSystemService
import kr.open.library.systemmanager.base.SystemServiceException
import kr.open.library.systemmanager.controller.bluetooth.error.BleServiceError
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BLE 컨트롤러의 기본 클래스
 * Base class for BLE controllers
 * 
 * BaseSystemService를 확장하여 BLE 특화 기능을 제공합니다:
 * Extends BaseSystemService to provide BLE-specific features:
 * - BLE 시스템 상태 관리
 * - 권한 자동 처리
 * - 리소스 생명주기 관리
 * - 메모리 누수 방지
 */
abstract class BluetoothBaseController(
    context: Context,
    private val role: BlePermissionManager.BleRole = BlePermissionManager.BleRole.CENTRAL,
    requiredPermissions: List<String>? = null
) : BaseSystemService(
    context, 
    requiredPermissions ?: BlePermissionManager.getRequiredPermissions(role)
) {
    
    /**
     * BLE 시스템 컴포넌트들
     * BLE system components
     */
    protected val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    protected val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    
    private val systemStateMonitor: BleSystemStateMonitor by lazy {
        BleSystemStateMonitor(context)
    }
    
    protected val resourceManager: BleResourceManager by lazy {
        BleResourceManager()
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isInitialized = AtomicBoolean(false)
    private val activeResources = ConcurrentHashMap<String, BleResource>()
    
    /**
     * BLE 리소스 인터페이스
     */
    interface BleResource {
        fun cleanup()
    }
    
    /**
     * 시스템 상태 변화 리스너
     */
    private val stateChangeListener = object : BleSystemStateMonitor.StateChangeListener {
        override fun onBluetoothStateChanged(oldState: Int, newState: Int) {
            handleBluetoothStateChange(oldState, newState)
        }
        
        override fun onLocationServiceChanged(enabled: Boolean) {
            handleLocationServiceChange(enabled)
        }
        
        override fun onBatteryOptimizationChanged(enabled: Boolean) {
            handleBatteryOptimizationChange(enabled)
        }
        
        override fun onSystemStateChanged(state: BleSystemStateMonitor.BleSystemState) {
            handleSystemStateChange(state)
        }
    }
    
    /**
     * BLE 컨트롤러 초기화
     * Initialize BLE controller
     */
    fun initialize(): Result<Unit> {
        return safeExecute("initializeBleController", requiresPermission = false) {
            if (isInitialized.compareAndSet(false, true)) {
                // 시스템 상태 모니터링 시작
                systemStateMonitor.startMonitoring().getOrThrow()
                systemStateMonitor.addStateChangeListener(stateChangeListener)
                
                // BLE 준비 상태 확인
                systemStateMonitor.checkBleReadiness(role).getOrThrow()
                
                // 하위 클래스 초기화
                onInitialize()
                
                Logx.i(this::class.simpleName, "BLE controller initialized successfully")
            } else {
                Logx.w(this::class.simpleName, "BLE controller already initialized")
            }
        }
    }
    
    /**
     * 하위 클래스에서 구현할 초기화 로직
     * Initialization logic to be implemented by subclasses
     */
    protected open fun onInitialize() {
        // Override in subclasses if needed
    }
    
    /**
     * BLE 지원 여부 확인
     * Check BLE support
     */
    protected fun checkBleSupport(): Result<Unit> {
        return safeExecute("checkBleSupport", requiresPermission = false) {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                throw SystemServiceException(BleServiceError.Hardware.NotSupported)
            }
            
            if (bluetoothAdapter == null) {
                throw SystemServiceException(BleServiceError.Hardware.AdapterNotFound)
            }
        }
    }
    
    /**
     * Bluetooth 활성화 확인
     * Ensure Bluetooth is enabled
     */
    protected fun ensureBluetoothEnabled(): Result<Unit> {
        return safeExecute("ensureBluetoothEnabled", requiresPermission = false) {
            val adapter = bluetoothAdapter 
                ?: throw SystemServiceException(BleServiceError.Hardware.AdapterNotFound)
            
            when (adapter.state) {
                BluetoothAdapter.STATE_ON -> {
                    // 정상 상태
                }
                BluetoothAdapter.STATE_TURNING_ON -> {
                    throw SystemServiceException(BleServiceError.Hardware.TurningOn)
                }
                BluetoothAdapter.STATE_TURNING_OFF -> {
                    throw SystemServiceException(BleServiceError.Hardware.TurningOff)
                }
                BluetoothAdapter.STATE_OFF -> {
                    throw SystemServiceException(BleServiceError.Hardware.BluetoothOff)
                }
                else -> {
                    throw SystemServiceException(BleServiceError.Hardware.NotSupported)
                }
            }
        }
    }
    
    /**
     * BLE 준비 상태 확인 (권한 + 시스템 상태)
     * Check BLE readiness (permissions + system state)
     */
    protected fun ensureBleReady(): Result<Unit> {
        return safeExecute("ensureBleReady") {
            checkBleSupport().getOrThrow()
            systemStateMonitor.checkBleReadiness(role).getOrThrow()
        }
    }
    
    /**
     * 메인 스레드에서 실행 보장
     * Ensure execution on main thread
     */
    protected fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }
    
    /**
     * BLE 리소스 등록 및 관리
     * Register and manage BLE resource
     */
    protected fun <T : BleResource> registerResource(resource: T): T {
        activeResources[resource.getResourceId()] = resource
        Logx.d(this::class.simpleName, "Registered BLE resource: ${resource.getResourceId()}")
        return resource
    }
    
    /**
     * BLE 리소스 해제
     * Unregister BLE resource
     */
    protected fun unregisterResource(resourceId: String) {
        activeResources.remove(resourceId)?.let { resource ->
            try {
                resource.cleanup()
                Logx.d(this::class.simpleName, "Cleaned up BLE resource: $resourceId")
            } catch (e: Exception) {
                Logx.w(this::class.simpleName, "Failed to cleanup BLE resource: $resourceId", e)
            }
        }
    }
    
    /**
     * 모든 리소스 해제
     * Clean up all resources
     */
    private fun cleanupAllResources() {
        val resourceIds = activeResources.keys.toList()
        resourceIds.forEach { resourceId ->
            unregisterResource(resourceId)
        }
        Logx.i(this::class.simpleName, "Cleaned up ${resourceIds.size} BLE resources")
    }
    
    /**
     * 현재 시스템 상태 반환
     * Get current system state
     */
    protected fun getCurrentSystemState(): BleSystemStateMonitor.BleSystemState? {
        return systemStateMonitor.getCurrentState()
    }
    
    /**
     * 권한 안내 메시지 반환
     * Get permission guidance message
     */
    fun getPermissionGuidanceMessage(): String? {
        return BlePermissionManager.getPermissionGuidanceMessage(context, role)
    }
    
    /**
     * 시스템 진단 정보 반환
     * Get system diagnostic information
     */
    fun getDiagnosticInfo(): String {
        return buildString {
            appendLine("=== ${this@BluetoothBaseController::class.simpleName} Diagnostic ===")
            appendLine("Role: $role")
            appendLine("Initialized: ${isInitialized.get()}")
            appendLine("Active resources: ${activeResources.size}")
            
            if (activeResources.isNotEmpty()) {
                appendLine("Resource IDs: ${activeResources.keys.joinToString(", ")}")
            }
            
            appendLine()
            append(systemStateMonitor.getDiagnosticInfo())
        }
    }
    
    // =================================================
    // 상태 변화 처리 메서드들
    // State change handling methods
    // =================================================
    
    /**
     * Bluetooth 상태 변화 처리
     * Handle Bluetooth state change
     */
    protected open fun handleBluetoothStateChange(oldState: Int, newState: Int) {
        Logx.i(this::class.simpleName, "Bluetooth state changed: $oldState -> $newState")
        
        when (newState) {
            BluetoothAdapter.STATE_OFF -> {
                // Bluetooth가 꺼지면 모든 BLE 작업 중단
                onBluetoothDisabled()
            }
            BluetoothAdapter.STATE_ON -> {
                // Bluetooth가 켜지면 복구 작업 수행
                onBluetoothEnabled()
            }
        }
    }
    
    /**
     * 위치 서비스 상태 변화 처리
     * Handle location service state change
     */
    protected open fun handleLocationServiceChange(enabled: Boolean) {
        Logx.i(this::class.simpleName, "Location service changed: $enabled")
        
        if (!enabled && (role == BlePermissionManager.BleRole.CENTRAL || role == BlePermissionManager.BleRole.BOTH)) {
            onLocationServiceDisabled()
        }
    }
    
    /**
     * 배터리 최적화 상태 변화 처리
     * Handle battery optimization state change
     */
    protected open fun handleBatteryOptimizationChange(enabled: Boolean) {
        Logx.i(this::class.simpleName, "Battery optimization changed: $enabled")
        
        if (enabled) {
            onBatteryOptimizationEnabled()
        }
    }
    
    /**
     * 전체 시스템 상태 변화 처리
     * Handle overall system state change
     */
    protected open fun handleSystemStateChange(state: BleSystemStateMonitor.BleSystemState) {
        // 하위 클래스에서 필요시 오버라이드
    }
    
    // =================================================
    // 하위 클래스에서 구현할 이벤트 핸들러들
    // Event handlers for subclasses to implement
    // =================================================
    
    /**
     * Bluetooth 비활성화 시 호출
     */
    protected open fun onBluetoothDisabled() {
        Logx.w(this::class.simpleName, "Bluetooth disabled - stopping BLE operations")
    }
    
    /**
     * Bluetooth 활성화 시 호출
     */
    protected open fun onBluetoothEnabled() {
        Logx.i(this::class.simpleName, "Bluetooth enabled - resuming BLE operations")
    }
    
    /**
     * 위치 서비스 비활성화 시 호출 (API 23-30 스캐닝)
     */
    protected open fun onLocationServiceDisabled() {
        Logx.w(this::class.simpleName, "Location service disabled - scanning may not work")
    }
    
    /**
     * 배터리 최적화 활성화 시 호출
     */
    protected open fun onBatteryOptimizationEnabled() {
        Logx.w(this::class.simpleName, "Battery optimization enabled - BLE operations may be limited")
    }
    
    // =================================================
    // 생명주기 관리
    // Lifecycle management
    // =================================================
    
    override fun onDestroy() {
        try {
            if (isInitialized.get()) {
                // 시스템 상태 모니터링 중단
                systemStateMonitor.removeStateChangeListener(stateChangeListener)
                systemStateMonitor.stopMonitoring()
                
                // 모든 리소스 정리
                cleanupAllResources()
                
                // 하위 클래스 정리
                onCleanup()
                
                isInitialized.set(false)
                Logx.i(this::class.simpleName, "BLE controller destroyed")
            }
        } catch (e: Exception) {
            Logx.e(this::class.simpleName, "Error during BLE controller destruction", e)
        } finally {
            super.onDestroy()
        }
    }
    
    /**
     * 하위 클래스에서 구현할 정리 로직
     * Cleanup logic to be implemented by subclasses
     */
    protected open fun onCleanup() {
        // Override in subclasses if needed
    }
}