package kr.open.library.systemmanager.controller.bluetooth

import com.google.gson.JsonElement
import kr.open.library.systemmanager.controller.bluetooth.data.*
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * ObjectSerializer의 단위 테스트
 * JSON 직렬화/역직렬화 기능을 검증합니다
 */
class ObjectSerializerTest {
    
    private lateinit var serializer: ObjectSerializer
    
    @Before
    fun setUp() {
        serializer = ObjectSerializer(SerializationFormat.JSON, maxDataSize = 500)
    }
    
    // ============================================================================
    // 기본 타입 테스트
    // ============================================================================
    
    @Test
    fun testStringSerializationDeserialization() {
        // Given
        val original = "Hello BLE World!"
        
        // When
        val serialized = serializer.serialize(original)
        val deserialized = serializer.deserialize(serialized) as String
        
        // Then
        assertEquals(original, deserialized)
    }
    
    @Test
    fun testIntSerializationDeserialization() {
        // Given
        val original = 42
        
        // When
        val serialized = serializer.serialize(original)
        val deserialized = serializer.deserialize(serialized) as Int
        
        // Then
        assertEquals(original, deserialized)
    }
    
    @Test
    fun testFloatSerializationDeserialization() {
        // Given
        val original = 3.14159f
        
        // When
        val serialized = serializer.serialize(original)
        val deserialized = serializer.deserialize(serialized) as Float
        
        // Then
        assertEquals(original, deserialized, 0.00001f)
    }
    
    @Test
    fun testBooleanSerializationDeserialization() {
        // Given
        val originalTrue = true
        val originalFalse = false
        
        // When
        val serializedTrue = serializer.serialize(originalTrue)
        val serializedFalse = serializer.serialize(originalFalse)
        val deserializedTrue = serializer.deserialize(serializedTrue) as Boolean
        val deserializedFalse = serializer.deserialize(serializedFalse) as Boolean
        
        // Then
        assertTrue(deserializedTrue)
        assertFalse(deserializedFalse)
    }
    
    // ============================================================================
    // 데이터 모델 테스트
    // ============================================================================
    
    @Test
    fun testSimpleMessageSerializationDeserialization() {
        // Given
        val original = SimpleMessage("Test message", 1234567890L)
        
        // When
        val serialized = serializer.serialize(original)
        val deserialized = serializer.deserialize(serialized) as SimpleMessage
        
        // Then
        assertEquals(original.text, deserialized.text)
        assertEquals(original.timestamp, deserialized.timestamp)
    }
    
    @Test
    fun testSensorDataSerializationDeserialization() {
        // Given
        val original = SensorData(25.5f, 60.0f, 1234567890L)
        
        // When
        val serialized = serializer.serialize(original)
        val deserialized = serializer.deserialize(serialized) as SensorData
        
        // Then
        assertEquals(original.temperature, deserialized.temperature, 0.01f)
        assertEquals(original.humidity, deserialized.humidity, 0.01f)
        assertEquals(original.timestamp, deserialized.timestamp)
    }
    
    @Test
    fun testLocationDataSerializationDeserialization() {
        // Given
        val original = LocationData(37.5665, 126.9780, 5.0f, 1234567890L)
        
        // When
        val serialized = serializer.serialize(original)
        val deserialized = serializer.deserialize(serialized) as LocationData
        
        // Then
        assertEquals(original.latitude, deserialized.latitude, 0.000001)
        assertEquals(original.longitude, deserialized.longitude, 0.000001)
        assertEquals(original.accuracy, deserialized.accuracy, 0.01f)
        assertEquals(original.timestamp, deserialized.timestamp)
    }
    
    @Test
    fun testDeviceStatusSerializationDeserialization() {
        // Given
        val original = DeviceStatus("device-001", 85, true, -50, 1234567890L)
        
        // When
        val serialized = serializer.serialize(original)
        val deserialized = serializer.deserialize(serialized) as DeviceStatus
        
        // Then
        assertEquals(original.deviceId, deserialized.deviceId)
        assertEquals(original.batteryLevel, deserialized.batteryLevel)
        assertEquals(original.isCharging, deserialized.isCharging)
        assertEquals(original.signalStrength, deserialized.signalStrength)
        assertEquals(original.timestamp, deserialized.timestamp)
    }
    
    @Test
    fun testControlCommandSerializationDeserialization() {
        // Given
        val parameters = mapOf("led" to "on", "brightness" to "80")
        val original = ControlCommand("SET_LED", parameters, 1234567890L)
        
        // When
        val serialized = serializer.serialize(original)
        val deserialized = serializer.deserialize(serialized) as ControlCommand
        
        // Then
        assertEquals(original.command, deserialized.command)
        assertEquals(original.parameters, deserialized.parameters)
        assertEquals(original.timestamp, deserialized.timestamp)
    }
    
    @Test
    fun testResponseMessageSerializationDeserialization() {
        // Given
        val original = ResponseMessage(true, "Operation successful", "result_data", 1234567890L)
        
        // When
        val serialized = serializer.serialize(original)
        val deserialized = serializer.deserialize(serialized) as ResponseMessage
        
        // Then
        assertEquals(original.success, deserialized.success)
        assertEquals(original.message, deserialized.message)
        assertEquals(original.data, deserialized.data)
        assertEquals(original.timestamp, deserialized.timestamp)
    }
    
    // ============================================================================
    // 크기 제한 테스트
    // ============================================================================
    
    @Test
    fun testDataSizeLimit() {
        // Given
        val smallSerializer = ObjectSerializer(SerializationFormat.JSON, maxDataSize = 50)
        val largeString = "x".repeat(100) // 큰 문자열
        
        // When & Then
        assertThrows(IllegalArgumentException::class.java) {
            smallSerializer.serialize(largeString)
        }
    }
    
    @Test
    fun testEstimateSize() {
        // Given
        val shortString = "Hello"
        val longString = "x".repeat(100)
        val simpleMessage = SimpleMessage("Test")
        
        // When
        val shortSize = serializer.estimateSize(shortString)
        val longSize = serializer.estimateSize(longString)
        val messageSize = serializer.estimateSize(simpleMessage)
        
        // Then
        assertTrue("Short string should be smaller", shortSize < longSize)
        assertTrue("Message should be larger than short string", messageSize > shortSize)
    }
    
    @Test
    fun testCanSerialize() {
        // Given
        val shortMessage = SimpleMessage("Short")
        val longMessage = SimpleMessage("x".repeat(400))
        
        // When & Then
        assertTrue("Short message should be serializable", serializer.canSerialize(shortMessage))
        assertFalse("Long message should not be serializable", serializer.canSerialize(longMessage))
    }
    
    // ============================================================================
    // 최적화 테스트
    // ============================================================================
    
    @Test
    fun testOptimizeForSize() {
        // Given
        val longString = "x".repeat(200)
        val longMessage = SimpleMessage("x".repeat(150))
        val sensorData = SensorData(25.123456f, 60.987654f)
        
        // When
        val optimizedString = serializer.optimizeForSize(longString, 50) as String
        val optimizedMessage = serializer.optimizeForSize(longMessage, 100) as SimpleMessage
        val optimizedSensor = serializer.optimizeForSize(sensorData, 100) as SensorData
        
        // Then
        assertTrue("Optimized string should be shorter", optimizedString.length < longString.length)
        assertTrue("Optimized message should be shorter", optimizedMessage.text.length < longMessage.text.length)
        assertTrue("Optimized sensor temperature precision should be reduced", 
            optimizedSensor.temperature.toString().length <= sensorData.temperature.toString().length)
    }
    
    // ============================================================================
    // 패킷 구조 테스트
    // ============================================================================
    
    @Test
    fun testPacketStructure() {
        // Given
        val message = SimpleMessage("Test")
        
        // When
        val serialized = serializer.serialize(message)
        val analysis = serializer.analyzePacket(serialized)
        
        // Then
        assertTrue("Serialized data should not be empty", serialized.isNotEmpty())
        assertTrue("Analysis should contain packet info", analysis.contains("Packet Analysis"))
        assertTrue("Serialized data should be at least header size", serialized.size >= 3)
    }
    
    @Test
    fun testInvalidPacket() {
        // Given
        val invalidData = byteArrayOf(0x99.toByte()) // 단일 바이트 (헤더보다 작음)
        
        // When & Then
        assertThrows(IllegalArgumentException::class.java) {
            serializer.deserialize(invalidData)
        }
    }
    
    @Test
    fun testPacketTypeRecognition() {
        // Given
        val testCases = listOf(
            "Hello" to "String",
            42 to "Int",
            3.14f to "Float",
            true to "Boolean",
            SimpleMessage("Test") to "SimpleMessage",
            SensorData(25.0f, 60.0f) to "SensorData"
        )
        
        testCases.forEach { (obj, expectedType) ->
            // When
            val serialized = serializer.serialize(obj)
            val analysis = serializer.analyzePacket(serialized)
            
            // Then
            assertTrue("Analysis should contain type info for $expectedType", 
                analysis.contains(expectedType))
        }
    }
    
    // ============================================================================
    // 통계 테스트
    // ============================================================================
    
    @Test
    fun testSerializationStats() {
        // Given
        val message = SimpleMessage("Test message for statistics")
        
        // When
        val stats = serializer.getSerializationStats(message)
        
        // Then
        assertTrue("Original size should be positive", stats.originalSize > 0)
        assertTrue("Serialized size should be positive", stats.serializedSize > 0)
        assertTrue("Compression ratio should be positive", stats.compressionRatio > 0)
        assertEquals("Type should match", "SimpleMessage", stats.type)
        assertEquals("Format should match", SerializationFormat.JSON, stats.format)
    }
    
    // ============================================================================
    // 헬퍼 메서드 테스트
    // ============================================================================
    
    @Test
    fun testBytesToHex() {
        // Given
        val data = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte())
        
        // When
        val hex = serializer.bytesToHex(data)
        
        // Then
        assertEquals("01 02 03 ff", hex)
    }
    
    // ============================================================================
    // 엣지 케이스 테스트
    // ============================================================================
    
    @Test
    fun testEmptyString() {
        // Given
        val empty = ""
        
        // When
        val serialized = serializer.serialize(empty)
        val deserialized = serializer.deserialize(serialized) as String
        
        // Then
        assertEquals(empty, deserialized)
    }
    
    @Test
    fun testSpecialCharacters() {
        // Given
        val special = "Hello 🌍! 한글 테스트 #@$%^&*()"
        
        // When
        val serialized = serializer.serialize(special)
        val deserialized = serializer.deserialize(serialized) as String
        
        // Then
        assertEquals(special, deserialized)
    }
    
    @Test
    fun testNegativeNumbers() {
        // Given
        val negativeInt = -42
        val negativeFloat = -3.14f
        
        // When
        val serializedInt = serializer.serialize(negativeInt)
        val serializedFloat = serializer.serialize(negativeFloat)
        val deserializedInt = serializer.deserialize(serializedInt) as Int
        val deserializedFloat = serializer.deserialize(serializedFloat) as Float
        
        // Then
        assertEquals(negativeInt, deserializedInt)
        assertEquals(negativeFloat, deserializedFloat, 0.001f)
    }
}