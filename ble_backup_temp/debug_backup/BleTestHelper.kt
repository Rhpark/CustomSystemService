package kr.open.library.systemmanager.controller.bluetooth.debug

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.base.BlePermissionManager
import kr.open.library.systemmanager.controller.bluetooth.base.BleSystemStateMonitor
import kr.open.library.systemmanager.controller.bluetooth.error.BleServiceError
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit

/**
 * BLE 테스트 도구
 * BLE Testing Helper
 * 
 * BLE 기능 테스트와 문제 진단을 위한 유틸리티 클래스입니다.
 * Utility class for BLE functionality testing and problem diagnosis.
 */
class BleTestHelper(private val context: Context) {
    
    private val TAG = "BleTestHelper"
    
    /**
     * 테스트 결과
     */
    data class TestResult(
        val testName: String,
        val success: Boolean,
        val message: String,
        val details: Map<String, Any> = emptyMap(),
        val duration: Long = 0
    ) {
        override fun toString(): String {
            val status = if (success) "PASS" else "FAIL"
            return "[$status] $testName: $message (${duration}ms)"
        }
    }
    
    /**
     * 테스트 보고서
     */
    data class TestReport(
        val totalTests: Int,
        val passedTests: Int,
        val failedTests: Int,
        val results: List<TestResult>,
        val summary: String
    ) {
        val successRate: Double = if (totalTests > 0) (passedTests.toDouble() / totalTests) * 100 else 0.0
        
        override fun toString(): String {
            return buildString {
                appendLine("=== BLE Test Report ===")
                appendLine("Total Tests: $totalTests")
                appendLine("Passed: $passedTests")
                appendLine("Failed: $failedTests") 
                appendLine("Success Rate: ${"%.1f".format(successRate)}%")
                appendLine()
                appendLine("Test Results:")
                results.forEach { result ->
                    appendLine(result.toString())
                    if (result.details.isNotEmpty()) {
                        result.details.forEach { (key, value) ->
                            appendLine("  $key: $value")
                        }
                    }
                }
                appendLine()
                appendLine("Summary: $summary")
            }
        }
    }
    
    /**
     * 전체 BLE 시스템 테스트 실행
     */
    suspend fun runFullBleTest(): TestReport {
        val results = mutableListOf<TestResult>()
        
        Logx.i(TAG, "Starting comprehensive BLE system test...")
        
        // 기본 하드웨어 테스트
        results.add(testBleHardwareSupport())
        results.add(testBluetoothAdapter())
        results.add(testBluetoothState())
        
        // 권한 테스트
        results.add(testBlePermissions(BlePermissionManager.BleRole.CENTRAL))
        results.add(testBlePermissions(BlePermissionManager.BleRole.PERIPHERAL))
        
        // 시스템 상태 테스트
        results.add(testSystemState())
        results.add(testLocationService())
        results.add(testBatteryOptimization())
        
        // 기능 테스트
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            results.add(testScanSupport())
            results.add(testAdvertiseSupport())
        }
        
        // 성능 테스트
        results.add(testSystemPerformance())
        
        val passedTests = results.count { it.success }
        val failedTests = results.count { !it.success }
        
        val summary = generateTestSummary(results)
        
        val report = TestReport(
            totalTests = results.size,
            passedTests = passedTests,
            failedTests = failedTests,
            results = results,
            summary = summary
        )
        
        Logx.i(TAG, "BLE system test completed: $passedTests/$failedTests/${results.size}")
        return report
    }
    
    /**
     * BLE 하드웨어 지원 테스트
     */
    private fun testBleHardwareSupport(): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val hasBluetoothFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
            val hasBleFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
            
            val details = mapOf(
                "bluetooth_feature" to hasBluetoothFeature,
                "ble_feature" to hasBleFeature,
                "android_version" to Build.VERSION.SDK_INT,
                "device_model" to "${Build.MANUFACTURER} ${Build.MODEL}"
            )
            
            when {
                !hasBluetoothFeature -> TestResult(
                    "BLE Hardware Support",
                    false,
                    "Bluetooth feature not supported",
                    details,
                    System.currentTimeMillis() - startTime
                )
                !hasBleFeature -> TestResult(
                    "BLE Hardware Support", 
                    false,
                    "BLE feature not supported",
                    details,
                    System.currentTimeMillis() - startTime
                )
                else -> TestResult(
                    "BLE Hardware Support",
                    true,
                    "BLE hardware supported",
                    details,
                    System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            TestResult(
                "BLE Hardware Support",
                false,
                "Test failed: ${e.message}",
                mapOf("exception" to e.toString()),
                System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * Bluetooth 어댑터 테스트
     */
    private fun testBluetoothAdapter(): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            
            val details = mutableMapOf<String, Any>()
            
            if (adapter == null) {
                TestResult(
                    "Bluetooth Adapter",
                    false,
                    "Bluetooth adapter not found",
                    details,
                    System.currentTimeMillis() - startTime
                )
            } else {
                details["adapter_name"] = adapter.name ?: "Unknown"
                details["adapter_address"] = adapter.address ?: "Unknown"
                details["adapter_state"] = getBluetoothStateName(adapter.state)
                details["adapter_enabled"] = adapter.isEnabled
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    details["le_scan_supported"] = adapter.bluetoothLeScanner != null
                    details["le_advertiser_supported"] = adapter.bluetoothLeAdvertiser != null
                }
                
                TestResult(
                    "Bluetooth Adapter",
                    true,
                    "Bluetooth adapter available: ${adapter.name}",
                    details,
                    System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            TestResult(
                "Bluetooth Adapter",
                false,
                "Test failed: ${e.message}",
                mapOf("exception" to e.toString()),
                System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * Bluetooth 상태 테스트
     */
    private fun testBluetoothState(): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            
            if (adapter == null) {
                return TestResult(
                    "Bluetooth State",
                    false,
                    "No Bluetooth adapter",
                    emptyMap(),
                    System.currentTimeMillis() - startTime
                )
            }
            
            val state = adapter.state
            val stateName = getBluetoothStateName(state)
            val enabled = adapter.isEnabled
            
            val details = mapOf(
                "state_code" to state,
                "state_name" to stateName,
                "enabled" to enabled
            )
            
            when (state) {
                BluetoothAdapter.STATE_ON -> TestResult(
                    "Bluetooth State",
                    true,
                    "Bluetooth is enabled and ready",
                    details,
                    System.currentTimeMillis() - startTime
                )
                BluetoothAdapter.STATE_OFF -> TestResult(
                    "Bluetooth State",
                    false,
                    "Bluetooth is disabled",
                    details,
                    System.currentTimeMillis() - startTime
                )
                BluetoothAdapter.STATE_TURNING_ON -> TestResult(
                    "Bluetooth State",
                    false,
                    "Bluetooth is turning on",
                    details,
                    System.currentTimeMillis() - startTime
                )
                BluetoothAdapter.STATE_TURNING_OFF -> TestResult(
                    "Bluetooth State",
                    false,
                    "Bluetooth is turning off",
                    details,
                    System.currentTimeMillis() - startTime
                )
                else -> TestResult(
                    "Bluetooth State",
                    false,
                    "Unknown Bluetooth state: $state",
                    details,
                    System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            TestResult(
                "Bluetooth State",
                false,
                "Test failed: ${e.message}",
                mapOf("exception" to e.toString()),
                System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * BLE 권한 테스트
     */
    private fun testBlePermissions(role: BlePermissionManager.BleRole): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val permissionStatus = BlePermissionManager.checkPermissionStatus(context, role)
            val requiredPermissions = BlePermissionManager.getRequiredPermissions(role)
            
            val details = mapOf(
                "role" to role.name,
                "android_version" to Build.VERSION.SDK_INT,
                "all_granted" to permissionStatus.isAllGranted,
                "required_permissions" to requiredPermissions,
                "missing_permissions" to permissionStatus.missingPermissions,
                "location_service_required" to permissionStatus.isLocationServiceRequired,
                "location_service_enabled" to permissionStatus.isLocationServiceEnabled,
                "suggested_action" to permissionStatus.suggestedAction
            )
            
            TestResult(
                "BLE Permissions (${role.name})",
                permissionStatus.isAllGranted,
                if (permissionStatus.isAllGranted) {
                    "All required permissions granted"
                } else {
                    "Missing permissions: ${permissionStatus.missingPermissions.joinToString(", ")}"
                },
                details,
                System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            TestResult(
                "BLE Permissions (${role.name})",
                false,
                "Test failed: ${e.message}",
                mapOf("exception" to e.toString()),
                System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * 시스템 상태 테스트
     */
    private fun testSystemState(): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val monitor = BleSystemStateMonitor(context)
            monitor.startMonitoring()
            
            // 짧은 대기 후 상태 확인
            Thread.sleep(100)
            
            val state = monitor.getCurrentState()
            monitor.stopMonitoring()
            
            if (state == null) {
                TestResult(
                    "System State",
                    false,
                    "Failed to get system state",
                    emptyMap(),
                    System.currentTimeMillis() - startTime
                )
            } else {
                val details = mapOf(
                    "ble_supported" to state.bleSupported,
                    "bluetooth_enabled" to state.bluetoothEnabled,
                    "bluetooth_state" to state.bluetoothState,
                    "location_service_enabled" to state.locationServiceEnabled,
                    "battery_optimization_enabled" to state.batteryOptimizationEnabled,
                    "doze_mode" to state.isDozeMode,
                    "ready_for_ble" to state.isReadyForBleOperations(),
                    "ready_for_scanning" to state.isReadyForScanning()
                )
                
                TestResult(
                    "System State",
                    state.isReadyForBleOperations(),
                    if (state.isReadyForBleOperations()) {
                        "System ready for BLE operations"
                    } else {
                        "System not ready for BLE operations"
                    },
                    details,
                    System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            TestResult(
                "System State",
                false,
                "Test failed: ${e.message}",
                mapOf("exception" to e.toString()),
                System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * 위치 서비스 테스트
     */
    private fun testLocationService(): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val monitor = BleSystemStateMonitor(context)
            val locationEnabled = monitor.isLocationServiceEnabled()
            val locationRequired = Build.VERSION.SDK_INT in 23..30
            
            val details = mapOf(
                "android_version" to Build.VERSION.SDK_INT,
                "location_required" to locationRequired,
                "location_enabled" to locationEnabled
            )
            
            when {
                !locationRequired -> TestResult(
                    "Location Service",
                    true,
                    "Location service not required for this Android version",
                    details,
                    System.currentTimeMillis() - startTime
                )
                locationRequired && locationEnabled -> TestResult(
                    "Location Service",
                    true,
                    "Location service is enabled",
                    details,
                    System.currentTimeMillis() - startTime
                )
                locationRequired && !locationEnabled -> TestResult(
                    "Location Service",
                    false,
                    "Location service is disabled (required for BLE scanning on API 23-30)",
                    details,
                    System.currentTimeMillis() - startTime
                )
                else -> TestResult(
                    "Location Service",
                    true,
                    "Location service check completed",
                    details,
                    System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            TestResult(
                "Location Service",
                false,
                "Test failed: ${e.message}",
                mapOf("exception" to e.toString()),
                System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * 배터리 최적화 테스트
     */
    private fun testBatteryOptimization(): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val monitor = BleSystemStateMonitor(context)
            val batteryOptEnabled = monitor.isBatteryOptimizationEnabled()
            val dozeMode = monitor.isDozeMode()
            
            val details = mapOf(
                "battery_optimization_enabled" to batteryOptEnabled,
                "doze_mode_active" to dozeMode,
                "android_version" to Build.VERSION.SDK_INT
            )
            
            val hasIssues = batteryOptEnabled || dozeMode
            
            TestResult(
                "Battery Optimization",
                !hasIssues,
                when {
                    batteryOptEnabled && dozeMode -> "Battery optimization enabled and device in Doze mode"
                    batteryOptEnabled -> "Battery optimization enabled (may affect BLE operations)"
                    dozeMode -> "Device in Doze mode (may affect BLE operations)"
                    else -> "No battery optimization issues detected"
                },
                details,
                System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            TestResult(
                "Battery Optimization",
                false,
                "Test failed: ${e.message}",
                mapOf("exception" to e.toString()),
                System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * BLE 스캔 지원 테스트
     */
    private fun testScanSupport(): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            
            if (adapter == null) {
                return TestResult(
                    "BLE Scan Support",
                    false,
                    "No Bluetooth adapter",
                    emptyMap(),
                    System.currentTimeMillis() - startTime
                )
            }
            
            val scanner = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                adapter.bluetoothLeScanner
            } else {
                null
            }
            
            val details = mapOf(
                "android_version" to Build.VERSION.SDK_INT,
                "scanner_available" to (scanner != null),
                "adapter_enabled" to adapter.isEnabled
            )
            
            TestResult(
                "BLE Scan Support",
                scanner != null && adapter.isEnabled,
                when {
                    scanner == null -> "BLE scanner not available"
                    !adapter.isEnabled -> "Bluetooth not enabled"
                    else -> "BLE scanning supported"
                },
                details,
                System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            TestResult(
                "BLE Scan Support",
                false,
                "Test failed: ${e.message}",
                mapOf("exception" to e.toString()),
                System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * BLE 광고 지원 테스트
     */
    private fun testAdvertiseSupport(): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            
            if (adapter == null) {
                return TestResult(
                    "BLE Advertise Support",
                    false,
                    "No Bluetooth adapter",
                    emptyMap(),
                    System.currentTimeMillis() - startTime
                )
            }
            
            val advertiser = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                adapter.bluetoothLeAdvertiser
            } else {
                null
            }
            
            val multipleAdvertisementSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                adapter.isMultipleAdvertisementSupported
            } else {
                false
            }
            
            val details = mapOf(
                "android_version" to Build.VERSION.SDK_INT,
                "advertiser_available" to (advertiser != null),
                "multiple_advertisement_supported" to multipleAdvertisementSupported,
                "adapter_enabled" to adapter.isEnabled
            )
            
            TestResult(
                "BLE Advertise Support",
                advertiser != null && adapter.isEnabled,
                when {
                    advertiser == null -> "BLE advertiser not available"
                    !adapter.isEnabled -> "Bluetooth not enabled"
                    else -> "BLE advertising supported (Multiple: $multipleAdvertisementSupported)"
                },
                details,
                System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            TestResult(
                "BLE Advertise Support",
                false,
                "Test failed: ${e.message}",
                mapOf("exception" to e.toString()),
                System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * 시스템 성능 테스트
     */
    private suspend fun testSystemPerformance(): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // 메모리 사용량 측정
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val maxMemory = runtime.maxMemory()
            
            // 간단한 성능 테스트 (코루틴 생성 시간)
            val coroutineTestStart = System.nanoTime()
            withContext(Dispatchers.IO) {
                delay(10)
            }
            val coroutineTestEnd = System.nanoTime()
            val coroutineLatency = (coroutineTestEnd - coroutineTestStart) / 1_000_000.0 // ms
            
            val memoryUsagePercent = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
            
            val details = mapOf(
                "total_memory_mb" to (totalMemory / 1024 / 1024),
                "used_memory_mb" to (usedMemory / 1024 / 1024),
                "free_memory_mb" to (freeMemory / 1024 / 1024),
                "max_memory_mb" to (maxMemory / 1024 / 1024),
                "memory_usage_percent" to "%.1f".format(memoryUsagePercent),
                "coroutine_latency_ms" to "%.2f".format(coroutineLatency)
            )
            
            val hasMemoryIssue = memoryUsagePercent > 85.0
            val hasLatencyIssue = coroutineLatency > 50.0
            
            TestResult(
                "System Performance",
                !hasMemoryIssue && !hasLatencyIssue,
                when {
                    hasMemoryIssue && hasLatencyIssue -> "High memory usage and latency detected"
                    hasMemoryIssue -> "High memory usage detected (${String.format("%.1f", memoryUsagePercent)}%)"
                    hasLatencyIssue -> "High latency detected (${String.format("%.2f", coroutineLatency)}ms)"
                    else -> "System performance is acceptable"
                },
                details,
                System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            TestResult(
                "System Performance",
                false,
                "Test failed: ${e.message}",
                mapOf("exception" to e.toString()),
                System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * 테스트 요약 생성
     */
    private fun generateTestSummary(results: List<TestResult>): String {
        val failedResults = results.filter { !it.success }
        
        return when {
            failedResults.isEmpty() -> "All BLE tests passed. System is ready for BLE operations."
            
            failedResults.any { it.testName.contains("Hardware") || it.testName.contains("Adapter") } ->
                "Critical BLE hardware issues detected. BLE operations not possible."
                
            failedResults.any { it.testName.contains("Permission") } ->
                "Permission issues detected. Grant required permissions and try again."
                
            failedResults.any { it.testName.contains("State") } ->
                "System state issues detected. Enable Bluetooth and check system settings."
                
            else ->
                "Some BLE tests failed but basic functionality may still work. Check individual test results."
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
}