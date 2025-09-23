package kr.open.library.systemmanager.controller.bluetooth

import android.bluetooth.le.ScanSettings
import android.bluetooth.le.AdvertiseSettings
import org.junit.Test
import org.junit.Assert.*

/**
 * BleConfig의 단위 테스트
 * 설정 유효성 검증과 프리셋 동작을 검증합니다
 */
class BleConfigTest {
    
    // ============================================================================
    // 기본 설정 테스트
    // ============================================================================
    
    @Test
    fun testDefaultConfig() {
        // When
        val config = BleConfig.default()
        
        // Then
        assertEquals(BleConstants.SCAN_TIMEOUT_MS, config.scanTimeoutMs)
        assertEquals(BleConstants.CONNECTION_TIMEOUT_MS, config.connectionTimeoutMs)
        assertEquals(BleConstants.TARGET_MTU, config.targetMtu)
        assertEquals(SerializationFormat.JSON, config.serializationFormat)
        assertTrue("Default config should be valid", config.validate().isSuccess)
    }
    
    @Test
    fun testBatteryOptimizedConfig() {
        // When
        val config = BleConfig.optimizedForBattery()
        
        // Then
        assertEquals(ScanSettings.SCAN_MODE_LOW_POWER, config.scanMode)
        assertEquals(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER, config.advertiseMode)
        assertEquals(AdvertiseSettings.ADVERTISE_TX_POWER_LOW, config.advertiseTxPower)
        assertEquals(BleConstants.DEFAULT_MTU, config.targetMtu)
        assertFalse("MTU negotiation should be disabled for battery saving", config.enableMtuNegotiation)
        assertEquals(BatteryUsage.LOW, config.estimateBatteryUsage())
    }
    
    @Test
    fun testSpeedOptimizedConfig() {
        // When
        val config = BleConfig.optimizedForSpeed()
        
        // Then
        assertEquals(ScanSettings.SCAN_MODE_LOW_LATENCY, config.scanMode)
        assertEquals(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY, config.advertiseMode)
        assertEquals(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH, config.advertiseTxPower)
        assertEquals(BleConstants.TARGET_MTU, config.targetMtu)
        assertTrue("MTU negotiation should be enabled for speed", config.enableMtuNegotiation)
        assertTrue("Auto-reconnect should be enabled for speed", config.enableAutoReconnect)
        assertEquals(500L, config.retryDelayMs)
    }
    
    @Test
    fun testReliabilityOptimizedConfig() {
        // When
        val config = BleConfig.optimizedForReliability()
        
        // Then
        assertEquals(45_000L, config.scanTimeoutMs)
        assertEquals(30_000L, config.connectionTimeoutMs)
        assertEquals(5, config.maxConnectionRetries)
        assertEquals(5, config.autoReconnectMaxAttempts)
        assertEquals(2.0f, config.backoffMultiplier)
        assertTrue("Auto-reconnect should be enabled for reliability", config.enableAutoReconnect)
    }
    
    @Test
    fun testDebuggingConfig() {
        // When
        val config = BleConfig.forDebugging()
        
        // Then
        assertTrue("Verbose logging should be enabled for debugging", config.enableVerboseLogging)
        assertTrue("Performance logging should be enabled for debugging", config.enablePerformanceLogging)
        assertTrue("Data logging should be enabled for debugging", config.enableDataLogging)
        assertEquals(1, config.maxConnectionRetries)
        assertEquals(1, config.maxOperationRetries)
        assertFalse("Auto-reconnect should be disabled for debugging", config.enableAutoReconnect)
    }
    
    @Test
    fun testOneToOneOptimizedConfig() {
        // When
        val config = BleConfig.optimizedForOneToOne()
        
        // Then
        assertTrue("Stop scan on first match should be enabled", config.stopScanOnFirstMatch)
        assertEquals(200, config.maxObjectSizeBytes)
        assertEquals(SerializationFormat.JSON, config.serializationFormat)
        assertTrue("Auto-reconnect should be enabled", config.enableAutoReconnect)
        assertEquals(3, config.autoReconnectMaxAttempts)
    }
    
    // ============================================================================
    // 검증 테스트
    // ============================================================================
    
    @Test
    fun testValidConfig() {
        // Given
        val validConfig = BleConfig(
            scanTimeoutMs = 10_000L,
            connectionTimeoutMs = 5_000L,
            targetMtu = 247,
            maxObjectSizeBytes = 200,
            maxConnectionRetries = 3,
            retryDelayMs = 1_000L,
            backoffMultiplier = 1.5f
        )
        
        // When
        val result = validConfig.validate()
        
        // Then
        assertTrue("Valid config should pass validation", result.isSuccess)
    }
    
    @Test
    fun testInvalidScanTimeout() {
        // Given
        val invalidConfig = BleConfig(scanTimeoutMs = -1L)
        
        // When
        val result = invalidConfig.validate()
        
        // Then
        assertTrue("Invalid scan timeout should fail validation", result.isFailure)
        assertTrue("Error message should mention scanTimeoutMs", 
            result.exceptionOrNull()?.message?.contains("scanTimeoutMs") == true)
    }
    
    @Test
    fun testInvalidMtu() {
        // Given
        val invalidLowMtu = BleConfig(targetMtu = 10)
        val invalidHighMtu = BleConfig(targetMtu = 1000)
        
        // When
        val resultLow = invalidLowMtu.validate()
        val resultHigh = invalidHighMtu.validate()
        
        // Then
        assertTrue("Low MTU should fail validation", resultLow.isFailure)
        assertTrue("High MTU should fail validation", resultHigh.isFailure)
    }
    
    @Test
    fun testInvalidObjectSize() {
        // Given
        val negativeSize = BleConfig(maxObjectSizeBytes = -1)
        val tooLargeSize = BleConfig(maxObjectSizeBytes = 1000)
        
        // When
        val resultNegative = negativeSize.validate()
        val resultLarge = tooLargeSize.validate()
        
        // Then
        assertTrue("Negative object size should fail validation", resultNegative.isFailure)
        assertTrue("Too large object size should fail validation", resultLarge.isFailure)
    }
    
    @Test
    fun testInvalidBackoffMultiplier() {
        // Given
        val invalidBackoff = BleConfig(backoffMultiplier = 0.5f)
        
        // When
        val result = invalidBackoff.validate()
        
        // Then
        assertTrue("Invalid backoff multiplier should fail validation", result.isFailure)
    }
    
    // ============================================================================
    // 계산 메서드 테스트
    // ============================================================================
    
    @Test
    fun testGetUsableDataSize() {
        // Given
        val defaultMtuConfig = BleConfig(enableMtuNegotiation = false)
        val customMtuConfig = BleConfig(targetMtu = 247, enableMtuNegotiation = true, maxObjectSizeBytes = 200)
        val largeLimitConfig = BleConfig(targetMtu = 247, enableMtuNegotiation = true, maxObjectSizeBytes = 300)
        
        // When
        val defaultSize = defaultMtuConfig.getUsableDataSize()
        val customSize = customMtuConfig.getUsableDataSize()
        val largeSize = largeLimitConfig.getUsableDataSize()
        
        // Then
        assertEquals(20, defaultSize) // DEFAULT_MTU(23) - ATT_HEADER_SIZE(3)
        assertEquals(200, customSize) // min(200, TARGET_MTU - ATT_HEADER_SIZE)
        assertEquals(244, largeSize) // min(300, 247 - 3)
    }
    
    @Test
    fun testEstimateBatteryUsage() {
        // Given
        val lowPowerConfig = BleConfig(
            scanMode = ScanSettings.SCAN_MODE_LOW_POWER,
            advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER,
            advertiseTxPower = AdvertiseSettings.ADVERTISE_TX_POWER_LOW,
            scanTimeoutMs = 5_000L
        )
        
        val highPowerConfig = BleConfig(
            scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY,
            advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY,
            advertiseTxPower = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH,
            scanTimeoutMs = 60_000L
        )
        
        // When
        val lowUsage = lowPowerConfig.estimateBatteryUsage()
        val highUsage = highPowerConfig.estimateBatteryUsage()
        
        // Then
        assertTrue("Low power config should have lower battery usage",
            lowUsage.ordinal <= highUsage.ordinal)
    }
    
    @Test
    fun testBatteryUsageEnum() {
        // Test enum ordering
        assertTrue("LOW should be less than MEDIUM", 
            BatteryUsage.LOW.ordinal < BatteryUsage.MEDIUM.ordinal)
        assertTrue("MEDIUM should be less than HIGH", 
            BatteryUsage.MEDIUM.ordinal < BatteryUsage.HIGH.ordinal)
    }
    
    // ============================================================================
    // 요약 정보 테스트
    // ============================================================================
    
    @Test
    fun testGetSummary() {
        // Given
        val config = BleConfig.default()
        
        // When
        val summary = config.getSummary()
        
        // Then
        assertTrue("Summary should contain scan info", summary.contains("Scan:"))
        assertTrue("Summary should contain connection info", summary.contains("Connection:"))
        assertTrue("Summary should contain retry info", summary.contains("Retry:"))
        assertTrue("Summary should contain serialization info", summary.contains("Serialization:"))
        assertTrue("Summary should contain auto-reconnect info", summary.contains("Auto-reconnect:"))
        assertTrue("Summary should contain battery usage", summary.contains("Battery Usage:"))
        assertTrue("Summary should contain usable data size", summary.contains("Usable Data Size:"))
    }
    
    // ============================================================================
    // 커스텀 설정 테스트
    // ============================================================================
    
    @Test
    fun testCustomConfig() {
        // Given
        val customConfig = BleConfig(
            scanTimeoutMs = 25_000L,
            connectionTimeoutMs = 12_000L,
            targetMtu = 185,
            serializationFormat = SerializationFormat.KOTLINX,
            maxObjectSizeBytes = 150,
            enableAutoReconnect = true,
            autoReconnectMaxAttempts = 4,
            enableVerboseLogging = true
        )
        
        // When
        val isValid = customConfig.validate().isSuccess
        val usableSize = customConfig.getUsableDataSize()
        val batteryUsage = customConfig.estimateBatteryUsage()
        
        // Then
        assertTrue("Custom config should be valid", isValid)
        assertEquals(150, usableSize)
        assertNotNull("Battery usage should be calculated", batteryUsage)
    }
    
    @Test
    fun testConfigCopy() {
        // Given
        val originalConfig = BleConfig.default()
        
        // When
        val modifiedConfig = originalConfig.copy(scanTimeoutMs = 60_000L, enableVerboseLogging = true)
        
        // Then
        assertEquals(60_000L, modifiedConfig.scanTimeoutMs)
        assertTrue("Verbose logging should be enabled", modifiedConfig.enableVerboseLogging)
        assertEquals("Other properties should remain same", 
            originalConfig.connectionTimeoutMs, modifiedConfig.connectionTimeoutMs)
    }
    
    // ============================================================================
    // SerializationFormat 열거형 테스트
    // ============================================================================
    
    @Test
    fun testSerializationFormatEnum() {
        // Test all enum values exist
        val formats = SerializationFormat.values()
        assertTrue("JSON should exist", formats.contains(SerializationFormat.JSON))
        assertTrue("KOTLINX should exist", formats.contains(SerializationFormat.KOTLINX))
        assertTrue("PROTOBUF should exist", formats.contains(SerializationFormat.PROTOBUF))
        assertTrue("CUSTOM_BINARY should exist", formats.contains(SerializationFormat.CUSTOM_BINARY))
    }
}