package kr.open.library.systemmanager.controller.bluetooth.base

import android.util.Log

class BleRoleManager {
    
    companion object {
        private const val TAG = "BleRoleManager"
    }
    
    enum class BleRole {
        CENTRAL,    // 스캔, 연결 시작
        PERIPHERAL, // 어드버타이징, 서비스 제공  
        DUAL        // 양방향 모드
    }
    
    var currentRole: BleRole = BleRole.CENTRAL
        private set
    
    private var previousRole: BleRole? = null
    
    fun switchTo(newRole: BleRole): Boolean {
        Log.d(TAG, "Switching role from $currentRole to $newRole")
        
        if (currentRole == newRole) {
            Log.d(TAG, "Already in role $newRole")
            return true
        }
        
        return try {
            previousRole = currentRole
            currentRole = newRole
            
            Log.d(TAG, "Role switched successfully to $newRole")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch role to $newRole", e)
            
            // 롤백
            previousRole?.let { currentRole = it }
            false
        }
    }
    
    fun canSwitchTo(targetRole: BleRole): Boolean {
        // 현재는 모든 역할 전환을 허용
        // 향후 상태 기반 제한 로직 추가 가능
        return true
    }
    
    fun isCentralMode(): Boolean = currentRole == BleRole.CENTRAL || currentRole == BleRole.DUAL
    fun isPeripheralMode(): Boolean = currentRole == BleRole.PERIPHERAL || currentRole == BleRole.DUAL
    fun isDualMode(): Boolean = currentRole == BleRole.DUAL
    
    fun getRoleDescription(): String {
        return when (currentRole) {
            BleRole.CENTRAL -> "Central mode - 스캔 및 연결 시작"
            BleRole.PERIPHERAL -> "Peripheral mode - 어드버타이징 및 서비스 제공"
            BleRole.DUAL -> "Dual mode - Central + Peripheral 동시 운영"
        }
    }
}