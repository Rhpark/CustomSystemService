package kr.open.library.systemmanager.controller.bluetooth

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

/**
 * BleConstants의 단위 테스트
 * 상수값들과 유틸리티 메서드들을 검증합니다
 */
class BleConstantsTest {
    
    // ============================================================================
    // UUID 테스트
    // ============================================================================
    
    @Test
    fun testServiceUUID() {
        // Given & When
        val serviceUuid = BleConstants.SERVICE_UUID
        
        // Then
        assertNotNull("Service UUID should not be null", serviceUuid)
        assertEquals("Service UUID should match Nordic UART Service", 
            "6e400001-b5a3-f393-e0a9-e50e24dcca9e", serviceUuid.toString())
    }
    
    @Test
    fun testCharacteristicUUIDs() {
        // Given & When
        val txUuid = BleConstants.TX_CHAR_UUID
        val rxUuid = BleConstants.RX_CHAR_UUID
        val cccdUuid = BleConstants.CCCD_UUID
        
        // Then
        assertNotNull("TX UUID should not be null", txUuid)
        assertNotNull("RX UUID should not be null", rxUuid)
        assertNotNull("CCCD UUID should not be null", cccdUuid)
        
        assertEquals("TX UUID should match Nordic UART TX", 
            "6e400002-b5a3-f393-e0a9-e50e24dcca9e", txUuid.toString())
        assertEquals("RX UUID should match Nordic UART RX", 
            "6e400003-b5a3-f393-e0a9-e50e24dcca9e", rxUuid.toString())
        assertEquals("CCCD UUID should match standard CCCD", 
            "00002902-0000-1000-8000-00805f9b34fb", cccdUuid.toString())
        
        // UUIDs should be different
        assertNotEquals("TX and RX UUIDs should be different", txUuid, rxUuid)
        assertNotEquals("RX and CCCD UUIDs should be different", rxUuid, cccdUuid)
    }
    
    // ============================================================================
    // MTU 상수 테스트
    // ============================================================================
    
    @Test
    fun testMtuConstants() {
        // When & Then
        assertEquals("Default MTU should be 23", 23, BleConstants.DEFAULT_MTU)
        assertEquals("Target MTU should be 247", 247, BleConstants.TARGET_MTU)
        assertEquals("Min MTU should be 23", 23, BleConstants.MIN_MTU)
        assertEquals("Max MTU should be 517", 517, BleConstants.MAX_MTU)
        assertEquals("ATT header size should be 3", 3, BleConstants.ATT_HEADER_SIZE)
        assertEquals("Safe data size should be calculated correctly", 
            BleConstants.TARGET_MTU - BleConstants.ATT_HEADER_SIZE - 10, BleConstants.SAFE_DATA_SIZE)
        
        // Logical relationships
        assertTrue("Target MTU should be greater than default", 
            BleConstants.TARGET_MTU > BleConstants.DEFAULT_MTU)
        assertTrue("Max MTU should be greater than target", 
            BleConstants.MAX_MTU > BleConstants.TARGET_MTU)
        assertTrue("Min MTU should equal default MTU", 
            BleConstants.MIN_MTU == BleConstants.DEFAULT_MTU)
    }
    
    // ============================================================================
    // 타임아웃 상수 테스트
    // ============================================================================
    
    @Test
    fun testTimeoutConstants() {
        // When & Then
        assertEquals("Scan timeout should be 30 seconds", 30_000L, BleConstants.SCAN_TIMEOUT_MS)
        assertEquals("Connection timeout should be 15 seconds", 15_000L, BleConstants.CONNECTION_TIMEOUT_MS)
        assertEquals("GATT operation timeout should be 5 seconds", 5_000L, BleConstants.GATT_OPERATION_TIMEOUT_MS)
        assertEquals("MTU negotiation timeout should be 3 seconds", 3_000L, BleConstants.MTU_NEGOTIATION_TIMEOUT_MS)
        assertEquals("Service discovery timeout should be 10 seconds", 10_000L, BleConstants.SERVICE_DISCOVERY_TIMEOUT_MS)
        
        // Logical relationships
        assertTrue("Scan timeout should be longer than connection timeout", 
            BleConstants.SCAN_TIMEOUT_MS > BleConstants.CONNECTION_TIMEOUT_MS)
        assertTrue("Connection timeout should be longer than service discovery", 
            BleConstants.CONNECTION_TIMEOUT_MS > BleConstants.SERVICE_DISCOVERY_TIMEOUT_MS)
        assertTrue("Service discovery should be longer than GATT operations", 
            BleConstants.SERVICE_DISCOVERY_TIMEOUT_MS > BleConstants.GATT_OPERATION_TIMEOUT_MS)
    }
    
    // ============================================================================
    // 재시도 상수 테스트
    // ============================================================================
    
    @Test
    fun testRetryConstants() {
        // When & Then
        assertEquals("Max connection retry should be 3", 3, BleConstants.MAX_CONNECTION_RETRY)
        assertEquals("Max operation retry should be 2", 2, BleConstants.MAX_OPERATION_RETRY)
        assertEquals("Retry delay should be 1 second", 1_000L, BleConstants.RETRY_DELAY_MS)
        assertEquals("Backoff multiplier should be 1.5", 1.5f, BleConstants.BACKOFF_MULTIPLIER, 0.01f)
        
        // Logical values
        assertTrue("Max connection retry should be positive", BleConstants.MAX_CONNECTION_RETRY > 0)
        assertTrue("Max operation retry should be positive", BleConstants.MAX_OPERATION_RETRY > 0)
        assertTrue("Retry delay should be positive", BleConstants.RETRY_DELAY_MS > 0)
        assertTrue("Backoff multiplier should be greater than 1", BleConstants.BACKOFF_MULTIPLIER > 1.0f)
    }
    
    // ============================================================================
    // 연결 상수 테스트
    // ============================================================================
    
    @Test
    fun testConnectionConstants() {
        // When & Then
        assertEquals("Max connections should be 1", 1, BleConstants.MAX_CONNECTIONS)
        assertEquals("GATT operation delay should be 100ms", 100L, BleConstants.GATT_OPERATION_DELAY_MS)
        assertEquals("Critical GATT operation delay should be 300ms", 300L, BleConstants.CRITICAL_GATT_OPERATION_DELAY_MS)
        
        // Logical relationships
        assertTrue("Critical delay should be longer than normal delay", 
            BleConstants.CRITICAL_GATT_OPERATION_DELAY_MS > BleConstants.GATT_OPERATION_DELAY_MS)
    }
    
    // ============================================================================
    // 유틸리티 메서드 테스트
    // ============================================================================
    
    @Test
    fun testCreateGattService() {
        // When
        val service = BleConstants.createGattService()
        
        // Then
        assertNotNull("Service should not be null", service)
        assertEquals("Service UUID should match", BleConstants.SERVICE_UUID, service.uuid)
        assertEquals("Service type should be primary", BluetoothGattService.SERVICE_TYPE_PRIMARY, service.type)
        
        // Check characteristics
        val characteristics = service.characteristics
        assertEquals("Service should have 2 characteristics", 2, characteristics.size)
        
        // Find TX and RX characteristics
        val txChar = characteristics.find { it.uuid == BleConstants.TX_CHAR_UUID }
        val rxChar = characteristics.find { it.uuid == BleConstants.RX_CHAR_UUID }
        
        assertNotNull("TX characteristic should exist", txChar)
        assertNotNull("RX characteristic should exist", rxChar)
        
        // Check TX characteristic properties
        assertEquals("TX should have NOTIFY property", 
            BluetoothGattCharacteristic.PROPERTY_NOTIFY, 
            txChar!!.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY)
        
        // Check RX characteristic properties
        assertTrue("RX should have WRITE property", 
            (rxChar!!.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0)
        
        // Check CCCD descriptor on TX characteristic
        val cccdDescriptor = txChar.getDescriptor(BleConstants.CCCD_UUID)
        assertNotNull("TX characteristic should have CCCD descriptor", cccdDescriptor)
    }
    
    @Test
    fun testGetUsableDataSize() {
        // Given
        val testCases = listOf(
            23 to 20,    // DEFAULT_MTU
            247 to 244,  // TARGET_MTU
            100 to 97,   // Custom MTU
            10 to 7      // Very small MTU
        )
        
        testCases.forEach { (mtu, expectedSize) ->
            // When
            val usableSize = BleConstants.getUsableDataSize(mtu)
            
            // Then
            assertEquals("Usable size should be MTU - ATT_HEADER_SIZE for MTU $mtu", 
                expectedSize, usableSize)
        }
    }
    
    @Test
    fun testGetUsableDataSizeEdgeCases() {
        // Test with MTU smaller than header
        val verySmallMtu = 2
        val usableSize = BleConstants.getUsableDataSize(verySmallMtu)
        assertEquals("Usable size should be 0 for very small MTU", 0, usableSize)
        
        // Test with zero MTU
        val zeroMtu = 0
        val zeroUsableSize = BleConstants.getUsableDataSize(zeroMtu)
        assertEquals("Usable size should be 0 for zero MTU", 0, zeroUsableSize)
    }
    
    // ============================================================================
    // 정보 메서드 테스트
    // ============================================================================
    
    @Test
    fun testGetConfigInfo() {
        // When
        val configInfo = BleConstants.getConfigInfo()
        
        // Then
        assertNotNull("Config info should not be null", configInfo)
        assertTrue("Config info should contain service UUID", configInfo.contains(BleConstants.SERVICE_UUID.toString()))
        assertTrue("Config info should contain TX UUID", configInfo.contains(BleConstants.TX_CHAR_UUID.toString()))
        assertTrue("Config info should contain RX UUID", configInfo.contains(BleConstants.RX_CHAR_UUID.toString()))
        assertTrue("Config info should contain target MTU", configInfo.contains(BleConstants.TARGET_MTU.toString()))
        assertTrue("Config info should contain safe data size", configInfo.contains(BleConstants.SAFE_DATA_SIZE.toString()))
        assertTrue("Config info should contain max connections", configInfo.contains(BleConstants.MAX_CONNECTIONS.toString()))
    }
    
    @Test
    fun testGetTimeoutInfo() {
        // When
        val timeoutInfo = BleConstants.getTimeoutInfo()
        
        // Then
        assertNotNull("Timeout info should not be null", timeoutInfo)
        assertTrue("Timeout info should contain scan timeout", timeoutInfo.contains("${BleConstants.SCAN_TIMEOUT_MS}ms"))
        assertTrue("Timeout info should contain connection timeout", timeoutInfo.contains("${BleConstants.CONNECTION_TIMEOUT_MS}ms"))
        assertTrue("Timeout info should contain GATT operation timeout", timeoutInfo.contains("${BleConstants.GATT_OPERATION_TIMEOUT_MS}ms"))
        assertTrue("Timeout info should contain MTU negotiation timeout", timeoutInfo.contains("${BleConstants.MTU_NEGOTIATION_TIMEOUT_MS}ms"))
        assertTrue("Timeout info should contain service discovery timeout", timeoutInfo.contains("${BleConstants.SERVICE_DISCOVERY_TIMEOUT_MS}ms"))
    }
    
    @Test
    fun testGetRetryInfo() {
        // When
        val retryInfo = BleConstants.getRetryInfo()
        
        // Then
        assertNotNull("Retry info should not be null", retryInfo)
        assertTrue("Retry info should contain max connection retry", retryInfo.contains(BleConstants.MAX_CONNECTION_RETRY.toString()))
        assertTrue("Retry info should contain max operation retry", retryInfo.contains(BleConstants.MAX_OPERATION_RETRY.toString()))
        assertTrue("Retry info should contain retry delay", retryInfo.contains("${BleConstants.RETRY_DELAY_MS}ms"))
        assertTrue("Retry info should contain backoff multiplier", retryInfo.contains(BleConstants.BACKOFF_MULTIPLIER.toString()))
    }
    
    // ============================================================================
    // 로깅 상수 테스트
    // ============================================================================
    
    @Test
    fun testLoggingConstants() {
        // When & Then
        assertEquals("Log tag prefix should be 'BLE'", "BLE", BleConstants.LOG_TAG_PREFIX)
        assertFalse("Verbose logging should be disabled by default", BleConstants.ENABLE_VERBOSE_LOGGING)
        assertFalse("Performance logging should be disabled by default", BleConstants.ENABLE_PERFORMANCE_LOGGING)
    }
    
    // ============================================================================
    // 스캔/광고 상수 테스트
    // ============================================================================
    
    @Test
    fun testScanAdvertiseConstants() {
        // When & Then
        assertTrue("Default scan mode should be a valid value", BleConstants.DEFAULT_SCAN_MODE >= 0)
        assertTrue("Battery saving scan mode should be a valid value", BleConstants.BATTERY_SAVING_SCAN_MODE >= 0)
        assertTrue("Default advertise mode should be a valid value", BleConstants.DEFAULT_ADVERTISE_MODE >= 0)
        assertTrue("Battery saving advertise mode should be a valid value", BleConstants.BATTERY_SAVING_ADVERTISE_MODE >= 0)
        assertEquals("Advertise timeout should be 0 (unlimited)", 0, BleConstants.ADVERTISE_TIMEOUT_MS)
    }
}