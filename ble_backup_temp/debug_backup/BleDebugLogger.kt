package kr.open.library.systemmanager.controller.bluetooth.debug

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.ScanResult
import android.os.Build
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.data.BleDevice
import kr.open.library.systemmanager.controller.bluetooth.error.BleServiceError
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * BLE 전용 디버깅 로거
 * BLE-specific debugging logger
 * 
 * BLE 작업의 모든 이벤트를 상세히 로깅하고 디버깅을 위한 정보를 제공합니다.
 * Logs all BLE operation events in detail and provides debugging information.
 */
object BleDebugLogger {
    
    private const val TAG = "BleDebugLogger"
    private const val MAX_LOG_ENTRIES = 1000
    
    private val logEntries = ConcurrentLinkedQueue<BleLogEntry>()
    private val dateFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * BLE 로그 엔트리
     */
    data class BleLogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val category: LogCategory,
        val message: String,
        val deviceAddress: String? = null,
        val data: Map<String, Any> = emptyMap()
    ) {
        fun getFormattedTime(): String = dateFormatter.format(Date(timestamp))
        
        override fun toString(): String {
            val deviceInfo = deviceAddress?.let { " [$it]" } ?: ""
            return "${getFormattedTime()} ${level.name} ${category.name}$deviceInfo: $message"
        }
    }
    
    /**
     * 로그 레벨
     */
    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * 로그 카테고리
     */
    enum class LogCategory {
        SYSTEM,         // 시스템 상태
        PERMISSION,     // 권한 관련
        SCAN,          // 스캐닝
        ADVERTISE,     // 광고
        CONNECTION,    // 연결/해제
        GATT,          // GATT 작업
        DATA,          // 데이터 송수신
        ERROR          // 오류
    }
    
    /**
     * 로그 레벨별 활성화 설정
     */
    private val enabledLevels = mutableSetOf(
        LogLevel.INFO,
        LogLevel.WARN, 
        LogLevel.ERROR
    )
    
    /**
     * 디버그 모드 설정
     */
    fun setDebugMode(enabled: Boolean) {
        if (enabled) {
            enabledLevels.addAll(listOf(LogLevel.VERBOSE, LogLevel.DEBUG))
        } else {
            enabledLevels.removeAll(listOf(LogLevel.VERBOSE, LogLevel.DEBUG))
        }
        log(LogLevel.INFO, LogCategory.SYSTEM, "Debug mode: $enabled")
    }
    
    /**
     * 특정 로그 레벨 활성화/비활성화
     */
    fun setLogLevel(level: LogLevel, enabled: Boolean) {
        if (enabled) {
            enabledLevels.add(level)
        } else {
            enabledLevels.remove(level)
        }
    }
    
    /**
     * 기본 로그 메서드
     */
    private fun log(
        level: LogLevel,
        category: LogCategory,
        message: String,
        deviceAddress: String? = null,
        data: Map<String, Any> = emptyMap()
    ) {
        if (!enabledLevels.contains(level)) {
            return
        }
        
        val entry = BleLogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            category = category,
            message = message,
            deviceAddress = deviceAddress,
            data = data
        )
        
        // 내부 로그 큐에 저장
        logEntries.offer(entry)
        if (logEntries.size > MAX_LOG_ENTRIES) {
            logEntries.poll() // 오래된 로그 제거
        }
        
        // Logx로 출력
        val logMessage = entry.toString()
        when (level) {
            LogLevel.VERBOSE -> Logx.v(TAG, logMessage)
            LogLevel.DEBUG -> Logx.d(TAG, logMessage)
            LogLevel.INFO -> Logx.i(TAG, logMessage)
            LogLevel.WARN -> Logx.w(TAG, logMessage)
            LogLevel.ERROR -> Logx.e(TAG, logMessage)
        }
    }
    
    // =================================================
    // 시스템 상태 로깅
    // =================================================
    
    fun logSystemState(state: String, details: String = "") {
        log(LogLevel.INFO, LogCategory.SYSTEM, "System state: $state", data = mapOf("details" to details))
    }
    
    fun logBluetoothStateChange(oldState: Int, newState: Int) {
        val oldStateName = getBluetoothStateName(oldState)
        val newStateName = getBluetoothStateName(newState)
        log(
            LogLevel.INFO, 
            LogCategory.SYSTEM, 
            "Bluetooth state changed: $oldStateName -> $newStateName",
            data = mapOf("oldState" to oldState, "newState" to newState)
        )
    }
    
    fun logPermissionCheck(role: String, granted: Boolean, missingPermissions: List<String>) {
        val level = if (granted) LogLevel.INFO else LogLevel.WARN
        val message = if (granted) {
            "All permissions granted for role: $role"
        } else {
            "Missing permissions for role $role: ${missingPermissions.joinToString(", ")}"
        }
        log(level, LogCategory.PERMISSION, message, data = mapOf("role" to role, "missing" to missingPermissions))
    }
    
    // =================================================
    // 스캐닝 로깅
    // =================================================
    
    fun logScanStart(filters: Int, settings: String) {
        log(
            LogLevel.INFO,
            LogCategory.SCAN,
            "Scan started with $filters filters",
            data = mapOf("settings" to settings)
        )
    }
    
    fun logScanStop() {
        log(LogLevel.INFO, LogCategory.SCAN, "Scan stopped")
    }
    
    fun logScanResult(device: BleDevice) {
        log(
            LogLevel.DEBUG,
            LogCategory.SCAN,
            "Device found: ${device.name ?: "Unknown"} (RSSI: ${device.rssi}dBm, Quality: ${device.getSignalQuality()})",
            deviceAddress = device.address,
            data = mapOf(
                "name" to (device.name ?: ""),
                "rssi" to device.rssi,
                "connectable" to device.canConnect(),
                "distance" to device.getEstimatedDistance()
            )
        )
    }
    
    fun logScanFailed(errorCode: Int, reason: String) {
        log(
            LogLevel.ERROR,
            LogCategory.SCAN,
            "Scan failed: $reason (code: $errorCode)",
            data = mapOf("errorCode" to errorCode, "reason" to reason)
        )
    }
    
    // =================================================
    // 광고 로깅
    // =================================================
    
    fun logAdvertisingStart(settings: String) {
        log(
            LogLevel.INFO,
            LogCategory.ADVERTISE,
            "Advertising started",
            data = mapOf("settings" to settings)
        )
    }
    
    fun logAdvertisingStop() {
        log(LogLevel.INFO, LogCategory.ADVERTISE, "Advertising stopped")
    }
    
    fun logAdvertisingFailed(errorCode: Int, reason: String) {
        log(
            LogLevel.ERROR,
            LogCategory.ADVERTISE,
            "Advertising failed: $reason (code: $errorCode)",
            data = mapOf("errorCode" to errorCode, "reason" to reason)
        )
    }
    
    // =================================================
    // 연결 로깅
    // =================================================
    
    fun logConnectionAttempt(deviceAddress: String, deviceName: String?, autoConnect: Boolean) {
        log(
            LogLevel.INFO,
            LogCategory.CONNECTION,
            "Connection attempt to ${deviceName ?: "Unknown"} (autoConnect: $autoConnect)",
            deviceAddress = deviceAddress,
            data = mapOf("autoConnect" to autoConnect, "deviceName" to (deviceName ?: ""))
        )
    }
    
    fun logConnectionSuccess(deviceAddress: String, deviceName: String?) {
        log(
            LogLevel.INFO,
            LogCategory.CONNECTION,
            "Connected to ${deviceName ?: "Unknown"}",
            deviceAddress = deviceAddress
        )
    }
    
    fun logConnectionFailed(deviceAddress: String, status: Int, reason: String) {
        log(
            LogLevel.ERROR,
            LogCategory.CONNECTION,
            "Connection failed: $reason (status: $status)",
            deviceAddress = deviceAddress,
            data = mapOf("status" to status, "reason" to reason)
        )
    }
    
    fun logDisconnection(deviceAddress: String, status: Int, reason: String = "") {
        val level = if (status == 0) LogLevel.INFO else LogLevel.WARN
        val message = if (reason.isNotEmpty()) {
            "Disconnected: $reason (status: $status)"
        } else {
            "Disconnected (status: $status)"
        }
        log(level, LogCategory.CONNECTION, message, deviceAddress = deviceAddress)
    }
    
    // =================================================
    // GATT 작업 로깅
    // =================================================
    
    fun logServiceDiscovery(deviceAddress: String, serviceCount: Int) {
        log(
            LogLevel.INFO,
            LogCategory.GATT,
            "Discovered $serviceCount services",
            deviceAddress = deviceAddress,
            data = mapOf("serviceCount" to serviceCount)
        )
    }
    
    fun logServiceDiscoveryFailed(deviceAddress: String, status: Int) {
        log(
            LogLevel.ERROR,
            LogCategory.GATT,
            "Service discovery failed (status: $status)",
            deviceAddress = deviceAddress,
            data = mapOf("status" to status)
        )
    }
    
    fun logCharacteristicRead(
        deviceAddress: String,
        serviceUuid: String,
        characteristicUuid: String,
        success: Boolean,
        dataSize: Int = 0
    ) {
        val level = if (success) LogLevel.DEBUG else LogLevel.ERROR
        val message = if (success) {
            "Read characteristic: $characteristicUuid ($dataSize bytes)"
        } else {
            "Read characteristic failed: $characteristicUuid"
        }
        log(
            level,
            LogCategory.GATT,
            message,
            deviceAddress = deviceAddress,
            data = mapOf("service" to serviceUuid, "characteristic" to characteristicUuid, "dataSize" to dataSize)
        )
    }
    
    fun logCharacteristicWrite(
        deviceAddress: String,
        serviceUuid: String,
        characteristicUuid: String,
        success: Boolean,
        dataSize: Int = 0
    ) {
        val level = if (success) LogLevel.DEBUG else LogLevel.ERROR
        val message = if (success) {
            "Write characteristic: $characteristicUuid ($dataSize bytes)"
        } else {
            "Write characteristic failed: $characteristicUuid"
        }
        log(
            level,
            LogCategory.GATT,
            message,
            deviceAddress = deviceAddress,
            data = mapOf("service" to serviceUuid, "characteristic" to characteristicUuid, "dataSize" to dataSize)
        )
    }
    
    fun logMtuChange(deviceAddress: String, mtu: Int, status: Int) {
        val level = if (status == 0) LogLevel.INFO else LogLevel.WARN
        val message = if (status == 0) {
            "MTU changed to $mtu"
        } else {
            "MTU change failed: requested $mtu (status: $status)"
        }
        log(level, LogCategory.GATT, message, deviceAddress = deviceAddress)
    }
    
    // =================================================
    // 데이터 로깅
    // =================================================
    
    fun logDataReceived(deviceAddress: String, characteristicUuid: String, data: ByteArray) {
        log(
            LogLevel.VERBOSE,
            LogCategory.DATA,
            "Data received: $characteristicUuid (${data.size} bytes)",
            deviceAddress = deviceAddress,
            data = mapOf(
                "characteristic" to characteristicUuid,
                "size" to data.size,
                "hex" to data.toHexString(),
                "ascii" to data.toAsciiString()
            )
        )
    }
    
    fun logDataSent(deviceAddress: String, characteristicUuid: String, data: ByteArray) {
        log(
            LogLevel.VERBOSE,
            LogCategory.DATA,
            "Data sent: $characteristicUuid (${data.size} bytes)",
            deviceAddress = deviceAddress,
            data = mapOf(
                "characteristic" to characteristicUuid,
                "size" to data.size,
                "hex" to data.toHexString(),
                "ascii" to data.toAsciiString()
            )
        )
    }
    
    // =================================================
    // 오류 로깅
    // =================================================
    
    fun logError(error: BleServiceError, deviceAddress: String? = null, context: String = "") {
        val message = if (context.isNotEmpty()) {
            "$context: ${error.getDeveloperMessage()}"
        } else {
            error.getDeveloperMessage()
        }
        
        log(
            LogLevel.ERROR,
            LogCategory.ERROR,
            message,
            deviceAddress = deviceAddress,
            data = mapOf(
                "errorType" to error::class.simpleName,
                "recoverable" to error.isRecoverable(),
                "userAction" to error.requiresUserAction()
            )
        )
    }
    
    fun logException(exception: Exception, context: String = "", deviceAddress: String? = null) {
        log(
            LogLevel.ERROR,
            LogCategory.ERROR,
            "$context: ${exception.message}",
            deviceAddress = deviceAddress,
            data = mapOf(
                "exception" to exception::class.simpleName,
                "stackTrace" to exception.stackTraceToString()
            )
        )
    }
    
    // =================================================
    // 로그 조회 및 관리
    // =================================================
    
    /**
     * 모든 로그 엔트리 반환
     */
    fun getAllLogs(): List<BleLogEntry> = logEntries.toList()
    
    /**
     * 특정 기기의 로그만 반환
     */
    fun getLogsForDevice(deviceAddress: String): List<BleLogEntry> {
        return logEntries.filter { it.deviceAddress == deviceAddress }
    }
    
    /**
     * 특정 카테고리의 로그만 반환
     */
    fun getLogsByCategory(category: LogCategory): List<BleLogEntry> {
        return logEntries.filter { it.category == category }
    }
    
    /**
     * 최근 N개 로그 반환
     */
    fun getRecentLogs(count: Int): List<BleLogEntry> {
        return logEntries.takeLast(count)
    }
    
    /**
     * 로그 초기화
     */
    fun clearLogs() {
        logEntries.clear()
        log(LogLevel.INFO, LogCategory.SYSTEM, "Log entries cleared")
    }
    
    /**
     * 로그를 문자열로 내보내기
     */
    fun exportLogs(): String {
        return buildString {
            appendLine("=== BLE Debug Log Export ===")
            appendLine("Generated: ${Date()}")
            appendLine("Android API: ${Build.VERSION.SDK_INT}")
            appendLine("Total entries: ${logEntries.size}")
            appendLine()
            
            logEntries.forEach { entry ->
                appendLine(entry.toString())
                if (entry.data.isNotEmpty()) {
                    entry.data.forEach { (key, value) ->
                        appendLine("  $key: $value")
                    }
                }
            }
        }
    }
    
    // =================================================
    // 유틸리티 메서드들
    // =================================================
    
    private fun getBluetoothStateName(state: Int): String = when (state) {
        BluetoothAdapter.STATE_OFF -> "OFF"
        BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"
        BluetoothAdapter.STATE_ON -> "ON"
        BluetoothAdapter.STATE_TURNING_OFF -> "TURNING_OFF"
        else -> "UNKNOWN($state)"
    }
    
    private fun ByteArray.toHexString(): String {
        return joinToString(" ") { "%02X".format(it) }
    }
    
    private fun ByteArray.toAsciiString(): String {
        return String(this.map { byte ->
            if (byte in 32..126) byte.toInt().toChar() else '.'
        }.toCharArray())
    }
}