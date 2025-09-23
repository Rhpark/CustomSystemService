package kr.open.library.systemmanager.controller.bluetooth.base

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.base.BaseSystemService
import kr.open.library.systemmanager.base.SystemServiceException
import kr.open.library.systemmanager.controller.bluetooth.error.BleServiceError
import java.util.concurrent.CopyOnWriteArrayList

/**
 * BLE 시스템 상태 모니터링
 * BLE System State Monitoring
 * 
 * BLE 동작에 영향을 주는 모든 시스템 상태를 실시간 모니터링합니다.
 * Real-time monitoring of all system states affecting BLE operations.
 * 
 * 모니터링 대상:
 * Monitoring targets:
 * - Bluetooth adapter state
 * - Location service state
 * - Battery optimization state
 * - Doze mode state
 * - App permission changes
 */
class BleSystemStateMonitor(context: Context) : BaseSystemService(context, emptyList()) {
    
    /**
     * BLE 시스템 상태
     * BLE System State
     */
    data class BleSystemState(
        val bluetoothState: Int,
        val bluetoothEnabled: Boolean,
        val bleSupported: Boolean,
        val locationServiceEnabled: Boolean,
        val batteryOptimizationEnabled: Boolean,
        val isDozeMode: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        /**
         * BLE 작업이 가능한 상태인지 확인
         */
        fun isReadyForBleOperations(): Boolean {
            return bleSupported && bluetoothEnabled && 
                   (!batteryOptimizationEnabled || !isDozeMode)
        }
        
        /**
         * 스캐닝 가능한 상태인지 확인 (API 23-30)
         */
        fun isReadyForScanning(): Boolean {
            return isReadyForBleOperations() && 
                   (Build.VERSION.SDK_INT >= 31 || locationServiceEnabled)
        }
    }
    
    /**
     * 상태 변화 리스너
     * State change listener
     */
    interface StateChangeListener {
        fun onBluetoothStateChanged(oldState: Int, newState: Int)
        fun onLocationServiceChanged(enabled: Boolean)
        fun onBatteryOptimizationChanged(enabled: Boolean)
        fun onSystemStateChanged(state: BleSystemState)
    }
    
    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    
    private val powerManager: PowerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    
    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    
    private val listeners = CopyOnWriteArrayList<StateChangeListener>()
    private var currentState: BleSystemState? = null
    private var isMonitoring = false
    
    /**
     * Bluetooth 상태 변화 수신기
     */
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val oldState = currentState?.bluetoothState ?: BluetoothAdapter.STATE_OFF
                    val newState = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE, 
                        BluetoothAdapter.STATE_OFF
                    )
                    
                    Logx.d("BleSystemStateMonitor", "Bluetooth state changed: $oldState -> $newState")
                    
                    listeners.forEach { it.onBluetoothStateChanged(oldState, newState) }
                    updateSystemState()
                }
            }
        }
    }
    
    /**
     * 위치 서비스 상태 변화 수신기
     */
    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                LocationManager.PROVIDERS_CHANGED_ACTION -> {
                    val enabled = isLocationServiceEnabled()
                    Logx.d("BleSystemStateMonitor", "Location service changed: $enabled")
                    
                    listeners.forEach { it.onLocationServiceChanged(enabled) }
                    updateSystemState()
                }
            }
        }
    }
    
    /**
     * 배터리 최적화 상태 변화 수신기
     */
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    // MinSdk 28이므로 항상 사용 가능
                    val isDozeMode = powerManager.isDeviceIdleMode
                    Logx.d("BleSystemStateMonitor", "Doze mode changed: $isDozeMode")
                    updateSystemState()
                }
            }
        }
    }
    
    /**
     * 모니터링 시작
     * Start monitoring
     */
    fun startMonitoring(): Result<Unit> {
        return safeExecute("startMonitoring", requiresPermission = false) {
            if (isMonitoring) {
                Logx.w("BleSystemStateMonitor", "Already monitoring")
                return@safeExecute
            }
            
            // 초기 상태 설정
            updateSystemState()
            
            // Bluetooth 상태 모니터링
            val bluetoothFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            context.registerReceiver(bluetoothReceiver, bluetoothFilter)
            
            // 위치 서비스 모니터링
            val locationFilter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
            context.registerReceiver(locationReceiver, locationFilter)
            
            // 배터리 최적화 모니터링
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val batteryFilter = IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                context.registerReceiver(batteryReceiver, batteryFilter)
            }
            
            isMonitoring = true
            Logx.i("BleSystemStateMonitor", "System state monitoring started")
        }
    }
    
    /**
     * 모니터링 중단
     * Stop monitoring
     */
    fun stopMonitoring(): Result<Unit> {
        return safeExecute("stopMonitoring", requiresPermission = false) {
            if (!isMonitoring) {
                return@safeExecute
            }
            
            try {
                context.unregisterReceiver(bluetoothReceiver)
            } catch (e: IllegalArgumentException) {
                Logx.w("BleSystemStateMonitor", "Bluetooth receiver not registered", e)
            }
            
            try {
                context.unregisterReceiver(locationReceiver)
            } catch (e: IllegalArgumentException) {
                Logx.w("BleSystemStateMonitor", "Location receiver not registered", e)
            }
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.unregisterReceiver(batteryReceiver)
                }
            } catch (e: IllegalArgumentException) {
                Logx.w("BleSystemStateMonitor", "Battery receiver not registered", e)
            }
            
            isMonitoring = false
            listeners.clear()
            currentState = null
            
            Logx.i("BleSystemStateMonitor", "System state monitoring stopped")
        }
    }
    
    /**
     * 상태 변화 리스너 추가
     */
    fun addStateChangeListener(listener: StateChangeListener) {
        listeners.addIfAbsent(listener)
        
        // 현재 상태 즉시 전달
        currentState?.let { listener.onSystemStateChanged(it) }
    }
    
    /**
     * 상태 변화 리스너 제거
     */
    fun removeStateChangeListener(listener: StateChangeListener) {
        listeners.remove(listener)
    }
    
    /**
     * 현재 시스템 상태 반환
     */
    fun getCurrentState(): BleSystemState? = currentState
    
    /**
     * BLE 지원 여부 확인
     */
    fun isBleSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
               bluetoothAdapter != null
    }
    
    /**
     * Bluetooth 활성화 상태 확인
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * 위치 서비스 활성화 상태 확인
     */
    fun isLocationServiceEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            Logx.w("BleSystemStateMonitor", "Failed to check location service", e)
            false
        }
    }
    
    /**
     * 배터리 최적화 상태 확인
     */
    fun isBatteryOptimizationEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            !powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            false
        }
    }
    
    /**
     * Doze 모드 상태 확인
     */
    fun isDozeMode(): Boolean {
        // MinSdk 28이므로 항상 사용 가능
        return powerManager.isDeviceIdleMode
    }
    
    /**
     * 시스템 상태 업데이트
     */
    private fun updateSystemState() {
        val newState = BleSystemState(
            bluetoothState = bluetoothAdapter?.state ?: BluetoothAdapter.STATE_OFF,
            bluetoothEnabled = isBluetoothEnabled(),
            bleSupported = isBleSupported(),
            locationServiceEnabled = isLocationServiceEnabled(),
            batteryOptimizationEnabled = isBatteryOptimizationEnabled(),
            isDozeMode = isDozeMode()
        )
        
        val oldState = currentState
        currentState = newState
        
        // 리스너들에게 상태 변화 알림
        listeners.forEach { it.onSystemStateChanged(newState) }
        
        // 상태 로깅
        logStateChange(oldState, newState)
    }
    
    /**
     * 상태 변화 로깅
     */
    private fun logStateChange(oldState: BleSystemState?, newState: BleSystemState) {
        if (oldState == null) {
            Logx.i("BleSystemStateMonitor", "Initial BLE system state: $newState")
            return
        }
        
        val changes = mutableListOf<String>()
        
        if (oldState.bluetoothEnabled != newState.bluetoothEnabled) {
            changes.add("Bluetooth: ${oldState.bluetoothEnabled} -> ${newState.bluetoothEnabled}")
        }
        
        if (oldState.locationServiceEnabled != newState.locationServiceEnabled) {
            changes.add("Location: ${oldState.locationServiceEnabled} -> ${newState.locationServiceEnabled}")
        }
        
        if (oldState.batteryOptimizationEnabled != newState.batteryOptimizationEnabled) {
            changes.add("BatteryOpt: ${oldState.batteryOptimizationEnabled} -> ${newState.batteryOptimizationEnabled}")
        }
        
        if (oldState.isDozeMode != newState.isDozeMode) {
            changes.add("DozeMode: ${oldState.isDozeMode} -> ${newState.isDozeMode}")
        }
        
        if (changes.isNotEmpty()) {
            Logx.i("BleSystemStateMonitor", "BLE system state changed: ${changes.joinToString(", ")}")
        }
    }
    
    /**
     * 시스템 상태 기반 BLE 오류 생성
     */
    fun createSystemStateError(): BleServiceError? {
        val state = currentState ?: return null
        
        return when {
            !state.bleSupported -> BleServiceError.Hardware.NotSupported
            !state.bluetoothEnabled -> {
                when (state.bluetoothState) {
                    BluetoothAdapter.STATE_TURNING_ON -> BleServiceError.Hardware.TurningOn
                    BluetoothAdapter.STATE_TURNING_OFF -> BleServiceError.Hardware.TurningOff
                    else -> BleServiceError.Hardware.BluetoothOff
                }
            }
            state.batteryOptimizationEnabled -> BleServiceError.SystemState.BatteryOptimizationEnabled
            state.isDozeMode -> BleServiceError.SystemState.DozeMode
            else -> null
        }
    }
    
    /**
     * BLE 준비 상태 확인
     */
    fun checkBleReadiness(role: BlePermissionManager.BleRole): Result<Unit> {
        return safeExecute("checkBleReadiness", requiresPermission = false) {
            val state = currentState 
                ?: throw IllegalStateException("System state not initialized. Call startMonitoring() first")
            
            // 시스템 상태 확인
            createSystemStateError()?.let { error ->
                throw SystemServiceException(error)
            }
            
            // 권한 상태 확인
            val permissionStatus = BlePermissionManager.checkPermissionStatus(context, role)
            if (!permissionStatus.isAllGranted) {
                val permissionError = BlePermissionManager.createPermissionError(permissionStatus)
                throw SystemServiceException(permissionError)
            }
            
            // 스캐닝 특별 확인 (API 23-30)
            if (role == BlePermissionManager.BleRole.CENTRAL || role == BlePermissionManager.BleRole.BOTH) {
                if (!state.isReadyForScanning()) {
                    if (Build.VERSION.SDK_INT in 23..30 && !state.locationServiceEnabled) {
                        throw SystemServiceException(BleServiceError.Permission.LocationServiceDisabled)
                    }
                }
            }
        }
    }
    
    /**
     * 시스템 상태 진단 정보 반환
     */
    fun getDiagnosticInfo(): String {
        val state = currentState
        return buildString {
            appendLine("=== BLE System Diagnostic ===")
            appendLine("Monitoring active: $isMonitoring")
            appendLine("Android API Level: ${Build.VERSION.SDK_INT}")
            
            if (state != null) {
                appendLine("BLE supported: ${state.bleSupported}")
                appendLine("Bluetooth state: ${getBluetoothStateName(state.bluetoothState)}")
                appendLine("Bluetooth enabled: ${state.bluetoothEnabled}")
                appendLine("Location service enabled: ${state.locationServiceEnabled}")
                appendLine("Battery optimization enabled: ${state.batteryOptimizationEnabled}")
                appendLine("Doze mode active: ${state.isDozeMode}")
                appendLine("Ready for BLE operations: ${state.isReadyForBleOperations()}")
                appendLine("Ready for scanning: ${state.isReadyForScanning()}")
                appendLine("Last updated: ${java.util.Date(state.timestamp)}")
            } else {
                appendLine("System state: Not initialized")
            }
            
            appendLine("=============================")
        }
    }
    
    /**
     * Bluetooth 상태 이름 반환
     */
    private fun getBluetoothStateName(state: Int): String = when (state) {
        BluetoothAdapter.STATE_OFF -> "OFF"
        BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"
        BluetoothAdapter.STATE_ON -> "ON"
        BluetoothAdapter.STATE_TURNING_OFF -> "TURNING_OFF"
        else -> "UNKNOWN($state)"
    }
    
    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }
}