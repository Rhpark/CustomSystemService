package kr.open.library.systemmanager.controller.bluetooth.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.BleConfig
import kr.open.library.systemmanager.controller.bluetooth.BleConstants
import kr.open.library.systemmanager.controller.bluetooth.SerializationFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BLE 통신용 객체 직렬화/역직렬화를 담당하는 클래스
 * 
 * 지원 형식:
 * - JSON: 가독성 좋음, 디버깅 용이
 * - KOTLINX: Kotlin 최적화 (향후 구현)
 * - PROTOBUF: 최소 크기 (향후 구현)
 * 
 * 패킷 구조: [Type:1byte][Length:2bytes][Data:variable]
 * 
 * @author SystemService Library
 * @since 2025-01-13
 */
class ObjectSerializer(
    private val format: SerializationFormat = SerializationFormat.JSON,
    private val maxDataSize: Int = BleConstants.SAFE_DATA_SIZE
) {
    
    companion object {
        // 타입 헤더 (1 byte) - 패킷의 첫 번째 바이트로 데이터 타입 식별
        const val TYPE_STRING: Byte = 0x01
        const val TYPE_INT: Byte = 0x02
        const val TYPE_LONG: Byte = 0x03
        const val TYPE_FLOAT: Byte = 0x04
        const val TYPE_DOUBLE: Byte = 0x05
        const val TYPE_BOOLEAN: Byte = 0x06
        const val TYPE_SIMPLE_MESSAGE: Byte = 0x10
        const val TYPE_SENSOR_DATA: Byte = 0x11
        const val TYPE_LOCATION_DATA: Byte = 0x12
        const val TYPE_DEVICE_STATUS: Byte = 0x13
        const val TYPE_CONTROL_COMMAND: Byte = 0x14
        const val TYPE_RESPONSE_MESSAGE: Byte = 0x15
        const val TYPE_KEY_VALUE_DATA: Byte = 0x16
        const val TYPE_SENSOR_STREAM: Byte = 0x17
        const val TYPE_HEARTBEAT_MESSAGE: Byte = 0x18
        const val TYPE_CONNECTION_INFO: Byte = 0x19
        const val TYPE_CONFIG_DATA: Byte = 0x1A
        const val TYPE_JSON_OBJECT: Byte = 0x50
        const val TYPE_CUSTOM: Byte = 0x60
        const val TYPE_ERROR: Byte = 0xFF.toByte()
        
        // 바이트 순서 (Little Endian - Android 네이티브)
        private val BYTE_ORDER = ByteOrder.LITTLE_ENDIAN
        
        // 헤더 크기 (타입 1바이트 + 길이 2바이트)
        const val HEADER_SIZE = 3
    }
    
    private val gson = Gson()
    
    /**
     * 객체를 바이트 배열로 직렬화합니다
     */
    fun serialize(obj: Any): ByteArray {
        return when (format) {
            SerializationFormat.JSON -> serializeWithJson(obj)
            SerializationFormat.KOTLINX -> throw NotImplementedError("Kotlinx Serialization not implemented yet")
            SerializationFormat.PROTOBUF -> throw NotImplementedError("Protocol Buffers not implemented yet")
            SerializationFormat.CUSTOM_BINARY -> throw NotImplementedError("Custom Binary format not implemented yet")
        }
    }
    
    /**
     * 바이트 배열을 객체로 역직렬화합니다
     */
    fun deserialize(data: ByteArray): Any {
        return when (format) {
            SerializationFormat.JSON -> deserializeFromJson(data)
            SerializationFormat.KOTLINX -> throw NotImplementedError("Kotlinx Serialization not implemented yet")
            SerializationFormat.PROTOBUF -> throw NotImplementedError("Protocol Buffers not implemented yet")
            SerializationFormat.CUSTOM_BINARY -> throw NotImplementedError("Custom Binary format not implemented yet")
        }
    }
    
    /**
     * JSON 형식으로 직렬화
     */
    private fun serializeWithJson(obj: Any): ByteArray {
        val (type, jsonData) = when (obj) {
            is String -> TYPE_STRING to obj.toByteArray(Charsets.UTF_8)
            is Int -> TYPE_INT to obj.toString().toByteArray(Charsets.UTF_8)
            is Long -> TYPE_LONG to obj.toString().toByteArray(Charsets.UTF_8)
            is Float -> TYPE_FLOAT to obj.toString().toByteArray(Charsets.UTF_8)
            is Double -> TYPE_DOUBLE to obj.toString().toByteArray(Charsets.UTF_8)
            is Boolean -> TYPE_BOOLEAN to obj.toString().toByteArray(Charsets.UTF_8)
            
            // 특정 데이터 모델들
            is SimpleMessage -> TYPE_SIMPLE_MESSAGE to gson.toJson(obj).toByteArray(Charsets.UTF_8)
            is SensorData -> TYPE_SENSOR_DATA to gson.toJson(obj).toByteArray(Charsets.UTF_8)
            is LocationData -> TYPE_LOCATION_DATA to gson.toJson(obj).toByteArray(Charsets.UTF_8)
            is DeviceStatus -> TYPE_DEVICE_STATUS to gson.toJson(obj).toByteArray(Charsets.UTF_8)
            is ControlCommand -> TYPE_CONTROL_COMMAND to gson.toJson(obj).toByteArray(Charsets.UTF_8)
            is ResponseMessage -> TYPE_RESPONSE_MESSAGE to gson.toJson(obj).toByteArray(Charsets.UTF_8)
            is KeyValueData -> TYPE_KEY_VALUE_DATA to gson.toJson(obj).toByteArray(Charsets.UTF_8)
            is SensorStream -> TYPE_SENSOR_STREAM to gson.toJson(obj).toByteArray(Charsets.UTF_8)
            is HeartbeatMessage -> TYPE_HEARTBEAT_MESSAGE to gson.toJson(obj).toByteArray(Charsets.UTF_8)
            is ConnectionInfo -> TYPE_CONNECTION_INFO to gson.toJson(obj).toByteArray(Charsets.UTF_8)
            is ConfigData -> TYPE_CONFIG_DATA to gson.toJson(obj).toByteArray(Charsets.UTF_8)
            
            // 일반적인 객체는 JSON으로 직렬화
            else -> TYPE_JSON_OBJECT to gson.toJson(obj).toByteArray(Charsets.UTF_8)
        }
        
        // 크기 제한 확인
        val totalSize = HEADER_SIZE + jsonData.size
        if (totalSize > maxDataSize) {
            throw IllegalArgumentException(
                "Serialized data size ($totalSize bytes) exceeds limit ($maxDataSize bytes). " +
                "Object: ${obj::class.java.simpleName}"
            )
        }
        
        // TLV 구조로 패킷 생성: Type(1) + Length(2) + Value(variable)
        return createPacket(type, jsonData)
    }
    
    /**
     * JSON 형식에서 역직렬화
     */
    private fun deserializeFromJson(data: ByteArray): Any {
        if (data.size < HEADER_SIZE) {
            throw IllegalArgumentException("Data too short: ${data.size} bytes")
        }
        
        val (type, length, payload) = parsePacket(data)
        val jsonString = String(payload, Charsets.UTF_8)
        
        return when (type) {
            TYPE_STRING -> jsonString
            TYPE_INT -> jsonString.toInt()
            TYPE_LONG -> jsonString.toLong()
            TYPE_FLOAT -> jsonString.toFloat()
            TYPE_DOUBLE -> jsonString.toDouble()
            TYPE_BOOLEAN -> jsonString.toBoolean()
            
            // 특정 데이터 모델들 - 타입별로 역직렬화
            TYPE_SIMPLE_MESSAGE -> gson.fromJson(jsonString, SimpleMessage::class.java)
            TYPE_SENSOR_DATA -> gson.fromJson(jsonString, SensorData::class.java)
            TYPE_LOCATION_DATA -> gson.fromJson(jsonString, LocationData::class.java)
            TYPE_DEVICE_STATUS -> gson.fromJson(jsonString, DeviceStatus::class.java)
            TYPE_CONTROL_COMMAND -> gson.fromJson(jsonString, ControlCommand::class.java)
            TYPE_RESPONSE_MESSAGE -> gson.fromJson(jsonString, ResponseMessage::class.java)
            TYPE_KEY_VALUE_DATA -> gson.fromJson(jsonString, KeyValueData::class.java)
            TYPE_SENSOR_STREAM -> gson.fromJson(jsonString, SensorStream::class.java)
            TYPE_HEARTBEAT_MESSAGE -> gson.fromJson(jsonString, HeartbeatMessage::class.java)
            TYPE_CONNECTION_INFO -> gson.fromJson(jsonString, ConnectionInfo::class.java)
            TYPE_CONFIG_DATA -> gson.fromJson(jsonString, ConfigData::class.java)
            
            // 일반적인 JSON 객체는 JsonElement로 반환
            TYPE_JSON_OBJECT -> JsonParser.parseString(jsonString)
            TYPE_CUSTOM -> jsonString // 커스텀 타입은 문자열로 반환
            
            else -> throw IllegalArgumentException("Unknown data type: 0x${type.toString(16)}")
        }
    }
    
    /**
     * TLV 패킷을 생성합니다
     */
    private fun createPacket(type: Byte, data: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + data.size).apply {
            order(BYTE_ORDER)
            put(type)                           // Type (1 byte)
            putShort(data.size.toShort())       // Length (2 bytes, Little Endian)
            put(data)                           // Value (variable)
        }
        
        return buffer.array()
    }
    
    /**
     * TLV 패킷을 파싱합니다
     */
    private fun parsePacket(packet: ByteArray): Triple<Byte, Int, ByteArray> {
        val buffer = ByteBuffer.wrap(packet).apply { order(BYTE_ORDER) }
        
        val type = buffer.get()
        val length = buffer.short.toInt() and 0xFFFF // Unsigned short
        
        if (packet.size < HEADER_SIZE + length) {
            throw IllegalArgumentException(
                "Packet too short: expected ${HEADER_SIZE + length}, got ${packet.size}"
            )
        }
        
        val data = ByteArray(length)
        buffer.get(data)
        
        return Triple(type, length, data)
    }
    
    /**
     * 객체의 예상 직렬화 크기를 계산합니다 (실제 직렬화 없이)
     */
    fun estimateSize(obj: Any): Int {
        return when (obj) {
            is String -> HEADER_SIZE + obj.toByteArray(Charsets.UTF_8).size
            is Int -> HEADER_SIZE + obj.toString().toByteArray(Charsets.UTF_8).size
            is Long -> HEADER_SIZE + obj.toString().toByteArray(Charsets.UTF_8).size
            is Float -> HEADER_SIZE + obj.toString().toByteArray(Charsets.UTF_8).size
            is Double -> HEADER_SIZE + obj.toString().toByteArray(Charsets.UTF_8).size
            is Boolean -> HEADER_SIZE + obj.toString().toByteArray(Charsets.UTF_8).size
            else -> {
                // 복잡한 객체는 JSON 크기 추정
                val jsonSize = gson.toJson(obj).toByteArray(Charsets.UTF_8).size
                HEADER_SIZE + jsonSize
            }
        }
    }
    
    /**
     * 객체가 크기 제한 내에 있는지 확인합니다
     */
    fun canSerialize(obj: Any): Boolean {
        return estimateSize(obj) <= maxDataSize
    }
    
    /**
     * 크기를 줄이기 위해 객체를 최적화합니다
     */
    fun optimizeForSize(obj: Any, targetSize: Int = maxDataSize): Any {
        return when (obj) {
            is String -> {
                val maxTextLength = targetSize - HEADER_SIZE - 10 // 여유분
                BleDataOptimizer.truncateString(obj, maxTextLength)
            }
            is SimpleMessage -> {
                val maxTextLength = targetSize - HEADER_SIZE - 50 // JSON 오버헤드 고려
                obj.copy(text = BleDataOptimizer.truncateString(obj.text, maxTextLength))
            }
            is SensorData -> {
                obj.copy(
                    temperature = BleDataOptimizer.limitPrecision(obj.temperature, 1),
                    humidity = BleDataOptimizer.limitPrecision(obj.humidity, 1)
                )
            }
            is ControlCommand -> {
                // 파라미터 수 제한
                val limitedParams = obj.parameters.entries.take(5).associate { it.key to it.value }
                obj.copy(parameters = limitedParams)
            }
            else -> obj // 최적화 불가능한 타입은 그대로 반환
        }
    }
    
    /**
     * 바이트 배열을 16진수 문자열로 변환 (디버깅용)
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02x".format(it) }
    }
    
    /**
     * 패킷 정보를 문자열로 반환 (디버깅용)
     */
    fun analyzePacket(data: ByteArray): String = buildString {
        try {
            val (type, length, payload) = parsePacket(data)
            appendLine("=== Packet Analysis ===")
            appendLine("Total Size: ${data.size} bytes")
            appendLine("Type: 0x${type.toString(16)} (${getTypeName(type)})")
            appendLine("Length: $length bytes")
            appendLine("Payload: ${if (payload.size <= 50) String(payload, Charsets.UTF_8) else "...too long..."}")
            appendLine("Hex: ${bytesToHex(data.take(20).toByteArray())}${if (data.size > 20) "..." else ""}")
        } catch (e: Exception) {
            appendLine("=== Invalid Packet ===")
            appendLine("Error: ${e.message}")
            appendLine("Size: ${data.size} bytes")
            appendLine("Hex: ${bytesToHex(data.take(20).toByteArray())}${if (data.size > 20) "..." else ""}")
        }
    }
    
    /**
     * 타입 코드에서 타입 이름을 반환합니다
     */
    private fun getTypeName(type: Byte): String = when (type) {
        TYPE_STRING -> "String"
        TYPE_INT -> "Int"
        TYPE_LONG -> "Long"
        TYPE_FLOAT -> "Float"
        TYPE_DOUBLE -> "Double"
        TYPE_BOOLEAN -> "Boolean"
        TYPE_SIMPLE_MESSAGE -> "SimpleMessage"
        TYPE_SENSOR_DATA -> "SensorData"
        TYPE_LOCATION_DATA -> "LocationData"
        TYPE_DEVICE_STATUS -> "DeviceStatus"
        TYPE_CONTROL_COMMAND -> "ControlCommand"
        TYPE_RESPONSE_MESSAGE -> "ResponseMessage"
        TYPE_KEY_VALUE_DATA -> "KeyValueData"
        TYPE_SENSOR_STREAM -> "SensorStream"
        TYPE_HEARTBEAT_MESSAGE -> "HeartbeatMessage"
        TYPE_CONNECTION_INFO -> "ConnectionInfo"
        TYPE_CONFIG_DATA -> "ConfigData"
        TYPE_JSON_OBJECT -> "JsonObject"
        TYPE_CUSTOM -> "Custom"
        TYPE_ERROR -> "Error"
        else -> "Unknown"
    }
    
    /**
     * 직렬화 통계 정보
     */
    data class SerializationStats(
        val originalSize: Int,
        val serializedSize: Int,
        val compressionRatio: Float,
        val type: String,
        val format: SerializationFormat
    ) {
        val efficiency: String
            get() = "${(compressionRatio * 100).toInt()}% of original"
    }
    
    /**
     * 직렬화 통계를 생성합니다
     */
    fun getSerializationStats(obj: Any): SerializationStats {
        val originalSize = estimateSize(obj)
        val serialized = serialize(obj)
        val serializedSize = serialized.size
        val compressionRatio = serializedSize.toFloat() / originalSize
        
        return SerializationStats(
            originalSize = originalSize,
            serializedSize = serializedSize,
            compressionRatio = compressionRatio,
            type = obj::class.java.simpleName,
            format = format
        )
    }
}