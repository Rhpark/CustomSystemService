package kr.open.library.systemmanager.controller.bluetooth

import android.content.Context
import android.util.Log
import kr.open.library.systemmanager.controller.bluetooth.base.*
import kr.open.library.systemmanager.controller.bluetooth.central.CentralModule
import kr.open.library.systemmanager.controller.bluetooth.peripheral.PeripheralModule

class BleController private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "BleController"
        
        @Volatile
        private var INSTANCE: BleController? = null
        
        fun getInstance(context: Context): BleController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BleController(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 기반 컴포넌트들
    private val roleManager = BleRoleManager()
    private val lifecycleManager = BleLifecycleManager()
    private val eventBus = BleEventBus()
    private val permissionManager = BlePermissionManager(context)
    
    // 모듈들
    private val centralModule = CentralModule(context, eventBus)
    private val peripheralModule = PeripheralModule(context, eventBus)
    
    // 상태
    private var isInitialized = false
    
    // 공개 API
    enum class BleRole {
        CENTRAL,    // 스캔, 연결 시작
        PERIPHERAL, // 어드버타이징, 서비스 제공  
        DUAL        // 양방향 모드
    }
    
    enum class BleState {
        IDLE,
        INITIALIZING,
        CENTRAL_MODE,
        PERIPHERAL_MODE,
        DUAL_MODE,
        ERROR
    }
    
    interface BleEventListener {
        fun onStateChanged(state: BleState)
        fun onRoleChanged(role: BleRole)
        fun onError(error: String)
    }
    
    private val eventListeners = mutableSetOf<BleEventListener>()
    
    // 초기화
    fun initialize(): Boolean {
        Log.d(TAG, "Initializing BLE Controller...")
        
        try {
            // 1. 권한 확인
            if (!permissionManager.checkAllPermissions()) {
                Log.e(TAG, "BLE permissions not granted")
                return false
            }
            
            // 2. 생명주기 초기화
            lifecycleManager.initialize()
            
            // 3. 이벤트 버스 설정
            eventBus.initialize()
            
            // 4. 모듈 초기화
            centralModule.initialize()
            peripheralModule.initialize()
            
            isInitialized = true
            notifyStateChanged(BleState.IDLE)
            
            Log.d(TAG, "BLE Controller initialized successfully")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BLE Controller", e)
            notifyStateChanged(BleState.ERROR)
            return false
        }
    }
    
    // 역할 시작
    fun startAsCentral(): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "Controller not initialized")
            return false
        }
        
        Log.d(TAG, "Starting as Central...")
        
        return if (roleManager.switchTo(BleRole.CENTRAL)) {
            centralModule.start()
            notifyStateChanged(BleState.CENTRAL_MODE)
            notifyRoleChanged(BleRole.CENTRAL)
            true
        } else {
            Log.e(TAG, "Failed to switch to Central role")
            false
        }
    }
    
    fun startAsPeripheral(): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "Controller not initialized")
            return false
        }
        
        Log.d(TAG, "Starting as Peripheral...")
        
        return if (roleManager.switchTo(BleRole.PERIPHERAL)) {
            peripheralModule.start()
            notifyStateChanged(BleState.PERIPHERAL_MODE)
            notifyRoleChanged(BleRole.PERIPHERAL)
            true
        } else {
            Log.e(TAG, "Failed to switch to Peripheral role")
            false
        }
    }
    
    fun startAsDual(): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "Controller not initialized")
            return false
        }
        
        Log.d(TAG, "Starting as Dual mode...")
        
        return if (roleManager.switchTo(BleRole.DUAL)) {
            centralModule.start()
            peripheralModule.start()
            notifyStateChanged(BleState.DUAL_MODE)
            notifyRoleChanged(BleRole.DUAL)
            true
        } else {
            Log.e(TAG, "Failed to switch to Dual role")
            false
        }
    }
    
    // 정지
    fun stop() {
        Log.d(TAG, "Stopping BLE Controller...")
        
        centralModule.stop()
        peripheralModule.stop()
        
        notifyStateChanged(BleState.IDLE)
    }
    
    // 정리
    fun cleanup() {
        Log.d(TAG, "Cleaning up BLE Controller...")
        
        stop()
        centralModule.cleanup()
        peripheralModule.cleanup()
        lifecycleManager.cleanup()
        eventBus.cleanup()
        eventListeners.clear()
        
        isInitialized = false
    }
    
    // 이벤트 리스너 관리
    fun addEventListener(listener: BleEventListener) {
        eventListeners.add(listener)
    }
    
    fun removeEventListener(listener: BleEventListener) {
        eventListeners.remove(listener)
    }
    
    // 상태 조회
    fun getCurrentState(): BleState = lifecycleManager.currentState
    fun getCurrentRole(): BleRole = roleManager.currentRole
    fun isInitialized(): Boolean = isInitialized
    
    // Central 모듈 접근 (필요한 경우)
    fun getCentralModule(): CentralModule? = if (isInitialized) centralModule else null
    fun getPeripheralModule(): PeripheralModule? = if (isInitialized) peripheralModule else null
    
    // 내부 헬퍼
    private fun notifyStateChanged(state: BleState) {
        lifecycleManager.currentState = state
        eventListeners.forEach { it.onStateChanged(state) }
    }
    
    private fun notifyRoleChanged(role: BleRole) {
        roleManager.currentRole = role
        eventListeners.forEach { it.onRoleChanged(role) }
    }
    
    private fun notifyError(error: String) {
        eventListeners.forEach { it.onError(error) }
    }
}