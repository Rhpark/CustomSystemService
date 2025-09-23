package kr.open.library.systemmanager.controller.bluetooth.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BLE 통신을 위한 바이너리 프로토콜 (TLV 구조)
 * Type (1byte) + Length (2bytes) + Data (variable)
 */
object BinaryProtocol {
    
    // 메시지 타입 정의
    object MessageType {
        const val HEARTBEAT: Byte = 0x01        // 연결 확인
        const val TEXT_MESSAGE: Byte = 0x02     // 텍스트 메시지  
        const val SENSOR_DATA: Byte = 0x03      // 센서 데이터
        const val CONTROL_CMD: Byte = 0x04      // 제어 명령
        const val ACK: Byte = 0x05              // 응답 확인
        const val ERROR: Byte = 0xFF.toByte()   // 오류
    }
    
    // 바이트 오더: Little Endian (Android 네이티브)
    private val BYTE_ORDER = ByteOrder.LITTLE_ENDIAN
    
    // 최대 데이터 크기 (MTU 512 - 헤더 3바이트)
    private const val MAX_DATA_SIZE = 509
    
    /**
     * TLV 인코딩
     * @param type 메시지 타입
     * @param data 메시지 데이터
     * @return 인코딩된 바이트 배열
     */
    fun encode(type: Byte, data: ByteArray): ByteArray {
        if (data.size > MAX_DATA_SIZE) {
            throw IllegalArgumentException("Data size exceeds maximum: ${data.size} > $MAX_DATA_SIZE")
        }
        
        val buffer = ByteBuffer.allocate(3 + data.size).apply {
            order(BYTE_ORDER)
            put(type)                    // Type (1 byte)
            putShort(data.size.toShort()) // Length (2 bytes, Little Endian)
            put(data)                    // Data (variable)
        }
        
        return buffer.array()
    }
    
    /**
     * TLV 디코딩
     * @param packet 디코딩할 바이트 배열
     * @return Triple<Type, Length, Data> 또는 null (실패 시)
     */
    fun decode(packet: ByteArray): Triple<Byte, Int, ByteArray>? {
        if (packet.size < 3) return null
        
        val buffer = ByteBuffer.wrap(packet).apply { order(BYTE_ORDER) }
        
        val type = buffer.get()
        val length = buffer.short.toInt() and 0xFFFF // Unsigned short
        
        if (packet.size < 3 + length) return null
        
        val data = ByteArray(length)
        buffer.get(data)
        
        return Triple(type, length, data)
    }
    
    // 유틸리티 메서드들
    
    /**
     * 하트비트 메시지 생성
     */
    fun createHeartbeat(): ByteArray = encode(MessageType.HEARTBEAT, byteArrayOf())
    
    /**
     * 텍스트 메시지 생성
     * @param text UTF-8 인코딩될 텍스트
     */
    fun createTextMessage(text: String): ByteArray {
        val data = text.toByteArray(Charsets.UTF_8)
        return encode(MessageType.TEXT_MESSAGE, data)
    }
    
    /**
     * 센서 데이터 메시지 생성
     * @param temperature 온도 (Float, 4 bytes)
     * @param humidity 습도 (Float, 4 bytes)  
     * @param timestamp 타임스탬프 (Long, 8 bytes)
     */
    fun createSensorData(
        temperature: Float, 
        humidity: Float, 
        timestamp: Long = System.currentTimeMillis()
    ): ByteArray {
        val buffer = ByteBuffer.allocate(16).apply {
            order(BYTE_ORDER)
            putFloat(temperature)  // 4 bytes
            putFloat(humidity)     // 4 bytes  
            putLong(timestamp)     // 8 bytes
        }
        return encode(MessageType.SENSOR_DATA, buffer.array())
    }
    
    /**
     * 제어 명령 메시지 생성
     * @param commandId 명령 ID (1 byte)
     * @param parameter 파라미터 문자열
     */
    fun createControlCommand(commandId: Byte, parameter: String): ByteArray {
        val paramBytes = parameter.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(1 + paramBytes.size).apply {
            order(BYTE_ORDER)
            put(commandId)
            put(paramBytes)
        }
        return encode(MessageType.CONTROL_CMD, buffer.array())
    }
    
    /**
     * ACK 메시지 생성
     * @param originalType 원본 메시지 타입
     */
    fun createAck(originalType: Byte): ByteArray {
        return encode(MessageType.ACK, byteArrayOf(originalType))
    }
    
    /**
     * 에러 메시지 생성
     * @param errorCode 에러 코드
     * @param message 에러 메시지
     */
    fun createError(errorCode: Byte, message: String): ByteArray {
        val msgBytes = message.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(1 + msgBytes.size).apply {
            order(BYTE_ORDER)
            put(errorCode)
            put(msgBytes)
        }
        return encode(MessageType.ERROR, buffer.array())
    }
    
    /**
     * 바이트 배열을 16진수 문자열로 변환 (디버깅용)
     * @param bytes 변환할 바이트 배열
     * @return 16진수 문자열 (공백으로 구분)
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02x".format(it) }
    }
    
    /**
     * 센서 데이터 디코딩 헬퍼
     * @param data 센서 데이터 페이로드
     * @return Triple<temperature, humidity, timestamp> 또는 null
     */
    fun parseSensorData(data: ByteArray): Triple<Float, Float, Long>? {
        if (data.size != 16) return null
        
        val buffer = ByteBuffer.wrap(data).apply { order(BYTE_ORDER) }
        
        val temperature = buffer.float
        val humidity = buffer.float
        val timestamp = buffer.long
        
        return Triple(temperature, humidity, timestamp)
    }
    
    /**
     * 텍스트 메시지 디코딩 헬퍼
     * @param data 텍스트 데이터 페이로드
     * @return 디코딩된 텍스트 또는 null
     */
    fun parseTextMessage(data: ByteArray): String? {
        return try {
            String(data, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 제어 명령 디코딩 헬퍼
     * @param data 제어 명령 페이로드
     * @return Pair<commandId, parameter> 또는 null
     */
    fun parseControlCommand(data: ByteArray): Pair<Byte, String>? {
        if (data.isEmpty()) return null
        
        val commandId = data[0]
        val parameter = if (data.size > 1) {
            try {
                String(data, 1, data.size - 1, Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
        
        return Pair(commandId, parameter)
    }
}