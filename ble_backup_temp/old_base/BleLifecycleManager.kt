package kr.open.library.systemmanager.controller.bluetooth.base

import android.util.Log

class BleLifecycleManager {
    
    companion object {
        private const val TAG = "BleLifecycleManager"
    }
    
    enum class BleState {
        IDLE,
        INITIALIZING,
        CENTRAL_MODE,
        PERIPHERAL_MODE,
        DUAL_MODE,
        ERROR
    }
    
    var currentState: BleState = BleState.IDLE
        set(value) {
            if (field != value) {
                Log.d(TAG, "State changed: $field -> $value")
                field = value
            }
        }
    
    private var startTime: Long = 0
    private var isInitialized = false
    
    fun initialize() {
        Log.d(TAG, "Initializing lifecycle manager...")
        
        startTime = System.currentTimeMillis()
        currentState = BleState.INITIALIZING
        isInitialized = true
        
        Log.d(TAG, "Lifecycle manager initialized")
    }
    
    fun cleanup() {
        Log.d(TAG, "Cleaning up lifecycle manager...")
        
        currentState = BleState.IDLE
        isInitialized = false
        startTime = 0
        
        Log.d(TAG, "Lifecycle manager cleaned up")
    }
    
    fun getUptime(): Long {
        return if (startTime > 0) {
            System.currentTimeMillis() - startTime
        } else {
            0
        }
    }
    
    fun getUptimeText(): String {
        val uptime = getUptime()
        val seconds = (uptime / 1000) % 60
        val minutes = (uptime / (1000 * 60)) % 60
        val hours = (uptime / (1000 * 60 * 60))
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
    
    fun getStateDescription(): String {
        return when (currentState) {
            BleState.IDLE -> "대기 상태"
            BleState.INITIALIZING -> "초기화 중"
            BleState.CENTRAL_MODE -> "Central 모드 활성"
            BleState.PERIPHERAL_MODE -> "Peripheral 모드 활성"
            BleState.DUAL_MODE -> "Dual 모드 활성"
            BleState.ERROR -> "오류 상태"
        }
    }
    
    fun isActive(): Boolean {
        return currentState in setOf(
            BleState.CENTRAL_MODE,
            BleState.PERIPHERAL_MODE,
            BleState.DUAL_MODE
        )
    }
    
    fun isError(): Boolean = currentState == BleState.ERROR
    fun isInitialized(): Boolean = isInitialized
}