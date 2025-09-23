package kr.open.library.systemmanager.controller.bluetooth.data

import com.google.gson.annotations.SerializedName

/**
 * BLE 통신에서 전송 가능한 데이터 모델들을 정의하는 파일
 * 모든 데이터 클래스는 직렬화 가능하고 크기 제한을 고려하여 설계됨
 * 
 * @author SystemService Library
 * @since 2025-01-13
 */

// ============================================================================
// 기본 메시지 타입들
// ============================================================================

/**
 * 간단한 텍스트 메시지
 */
data class SimpleMessage(
    @SerializedName("text") val text: String,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun create(text: String): SimpleMessage = SimpleMessage(text)
    }
}

/**
 * 센서 데이터 (온도, 습도 등)
 */
data class SensorData(
    @SerializedName("temperature") val temperature: Float,
    @SerializedName("humidity") val humidity: Float,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun create(temperature: Float, humidity: Float): SensorData = 
            SensorData(temperature, humidity)
    }
}

/**
 * 위치 데이터
 */
data class LocationData(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Float,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun create(latitude: Double, longitude: Double, accuracy: Float = 0f): LocationData = 
            LocationData(latitude, longitude, accuracy)
    }
}

/**
 * 장치 상태 정보
 */
data class DeviceStatus(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("batteryLevel") val batteryLevel: Int,
    @SerializedName("isCharging") val isCharging: Boolean,
    @SerializedName("signalStrength") val signalStrength: Int,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun create(
            deviceId: String, 
            batteryLevel: Int, 
            isCharging: Boolean = false, 
            signalStrength: Int = 0
        ): DeviceStatus = DeviceStatus(deviceId, batteryLevel, isCharging, signalStrength)
    }
}

/**
 * 제어 명령
 */
data class ControlCommand(
    @SerializedName("command") val command: String,
    @SerializedName("parameters") val parameters: Map<String, String> = emptyMap(),
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun create(command: String, parameters: Map<String, String> = emptyMap()): ControlCommand = 
            ControlCommand(command, parameters)
            
        fun createSimple(command: String, value: String = ""): ControlCommand = 
            ControlCommand(command, if (value.isNotEmpty()) mapOf("value" to value) else emptyMap())
    }
}

/**
 * 응답 메시지
 */
data class ResponseMessage(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: String? = null,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun success(message: String, data: String? = null): ResponseMessage = 
            ResponseMessage(true, message, data)
            
        fun error(message: String): ResponseMessage = 
            ResponseMessage(false, message)
    }
}

/**
 * 키-값 쌍 데이터
 */
data class KeyValueData(
    @SerializedName("key") val key: String,
    @SerializedName("value") val value: String,
    @SerializedName("type") val type: String = "string",
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun create(key: String, value: String, type: String = "string"): KeyValueData = 
            KeyValueData(key, value, type)
            
        fun createInt(key: String, value: Int): KeyValueData = 
            KeyValueData(key, value.toString(), "int")
            
        fun createFloat(key: String, value: Float): KeyValueData = 
            KeyValueData(key, value.toString(), "float")
            
        fun createBoolean(key: String, value: Boolean): KeyValueData = 
            KeyValueData(key, value.toString(), "boolean")
    }
}

// ============================================================================
// 실시간 데이터 타입들
// ============================================================================

/**
 * 실시간 센서 스트림
 */
data class SensorStream(
    @SerializedName("sensorId") val sensorId: String,
    @SerializedName("values") val values: List<Float>,
    @SerializedName("unit") val unit: String,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun create(sensorId: String, values: List<Float>, unit: String): SensorStream = 
            SensorStream(sensorId, values, unit)
    }
}

/**
 * 하트비트/핑 메시지
 */
data class HeartbeatMessage(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("sequenceNumber") val sequenceNumber: Long,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun create(deviceId: String, sequenceNumber: Long): HeartbeatMessage = 
            HeartbeatMessage(deviceId, sequenceNumber)
    }
}

// ============================================================================
// 메타데이터 및 설정 타입들
// ============================================================================

/**
 * 연결 정보
 */
data class ConnectionInfo(
    @SerializedName("deviceName") val deviceName: String,
    @SerializedName("address") val address: String,
    @SerializedName("rssi") val rssi: Int,
    @SerializedName("mtu") val mtu: Int,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun create(deviceName: String, address: String, rssi: Int, mtu: Int): ConnectionInfo = 
            ConnectionInfo(deviceName, address, rssi, mtu)
    }
}

/**
 * 설정 데이터
 */
data class ConfigData(
    @SerializedName("settings") val settings: Map<String, String>,
    @SerializedName("version") val version: String = "1.0",
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun create(settings: Map<String, String>, version: String = "1.0"): ConfigData = 
            ConfigData(settings, version)
    }
}

// ============================================================================
// 공통 인터페이스 및 유틸리티
// ============================================================================

/**
 * 모든 BLE 데이터 모델이 구현해야 하는 인터페이스
 */
interface BleDataModel {
    val timestamp: Long
    
    /**
     * 데이터의 예상 직렬화 크기를 반환합니다
     */
    fun estimateSize(): Int {
        // 기본 구현: JSON 직렬화 크기 추정
        return toString().toByteArray(Charsets.UTF_8).size
    }
    
    /**
     * 데이터 유효성을 검증합니다
     */
    fun validate(): Boolean = true
}

/**
 * 크기 제한을 확인하는 확장 함수들
 */
fun BleDataModel.isWithinSizeLimit(maxSize: Int = 200): Boolean {
    return estimateSize() <= maxSize
}

fun BleDataModel.checkSizeOrThrow(maxSize: Int = 200) {
    if (!isWithinSizeLimit(maxSize)) {
        throw IllegalArgumentException("Data size ${estimateSize()} exceeds limit of $maxSize bytes")
    }
}

/**
 * 데이터 최적화 유틸리티
 */
object BleDataOptimizer {
    
    /**
     * 문자열을 지정된 길이로 잘라냅니다
     */
    fun truncateString(text: String, maxLength: Int): String {
        return if (text.length > maxLength) {
            text.substring(0, maxLength - 3) + "..."
        } else {
            text
        }
    }
    
    /**
     * Float 값의 정밀도를 제한합니다
     */
    fun limitPrecision(value: Float, decimalPlaces: Int = 2): Float {
        val factor = Math.pow(10.0, decimalPlaces.toDouble()).toFloat()
        return Math.round(value * factor) / factor
    }
    
    /**
     * 타임스탬프를 상대적 시간으로 변환합니다 (크기 절약)
     */
    fun toRelativeTimestamp(timestamp: Long, baseTime: Long = System.currentTimeMillis()): Int {
        return ((timestamp - baseTime) / 1000).toInt().coerceIn(Int.MIN_VALUE, Int.MAX_VALUE)
    }
}