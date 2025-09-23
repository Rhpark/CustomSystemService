package kr.open.library.systemmanager.controller.bluetooth.base

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.util.Log
import java.util.*

object BleUtils {
    
    private const val TAG = "BleUtils"
    
    // 표준 BLE UUID들
    object StandardUuids {
        // 표준 서비스 UUID
        val GENERIC_ACCESS = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        val GENERIC_ATTRIBUTE = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb")
        val DEVICE_INFORMATION = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        
        // 표준 특성 UUID
        val DEVICE_NAME = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
        val APPEARANCE = UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        
        // 디스크립터 UUID
        val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    
    // 프로젝트 전용 UUID들 (Nordic UART Service 호환)
    object ProjectUuids {
        val SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val TX_CHARACTERISTIC = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // Peripheral -> Central
        val RX_CHARACTERISTIC = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // Central -> Peripheral
    }
    
    // UUID 문자열 정규화
    fun normalizeUuid(uuid: String): String {
        val cleanUuid = uuid.lowercase().replace("-", "")
        return if (cleanUuid.length == 32) {
            "${cleanUuid.substring(0, 8)}-${cleanUuid.substring(8, 12)}-${cleanUuid.substring(12, 16)}-${cleanUuid.substring(16, 20)}-${cleanUuid.substring(20, 32)}"
        } else {
            uuid
        }
    }
    
    // UUID에서 16bit 단축형 추출
    fun extractShortUuid(uuid: UUID): String? {
        val longUuid = uuid.toString()
        return if (longUuid.startsWith("0000") && longUuid.endsWith("-0000-1000-8000-00805f9b34fb")) {
            longUuid.substring(4, 8)
        } else {
            null
        }
    }
    
    // 특성 속성을 문자열로 변환
    fun getCharacteristicPropertiesString(properties: Int): String {
        val props = mutableListOf<String>()
        
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) props.add("READ")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) props.add("WRITE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) props.add("WRITE_NO_RESPONSE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) props.add("NOTIFY")
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) props.add("INDICATE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) props.add("SIGNED_WRITE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) props.add("EXTENDED_PROPS")
        
        return if (props.isNotEmpty()) props.joinToString(", ") else "NONE"
    }
    
    // 쓰기 가능한 특성인지 확인
    fun isWritableCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        val properties = characteristic.properties
        return (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
               (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
    }
    
    // 읽기 가능한 특성인지 확인
    fun isReadableCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
    }
    
    // 알림 가능한 특성인지 확인
    fun isNotifiableCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        val properties = characteristic.properties
        return (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) ||
               (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
    }
    
    // RSSI를 신호 강도 텍스트로 변환
    fun getRssiStrengthText(rssi: Int): String {
        return when {
            rssi > -30 -> "매우 강함"
            rssi > -50 -> "강함" 
            rssi > -70 -> "보통"
            rssi > -90 -> "약함"
            else -> "매우 약함"
        }
    }
    
    // 바이트 배열을 16진수 문자열로 변환
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    // 16진수 문자열을 바이트 배열로 변환
    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("-", "")
        return cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    // 서비스 정보 출력
    fun getServiceInfo(service: BluetoothGattService): String {
        val sb = StringBuilder()
        sb.appendLine("Service UUID: ${service.uuid}")
        sb.appendLine("Type: ${if (service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY) "PRIMARY" else "SECONDARY"}")
        sb.appendLine("Characteristics: ${service.characteristics.size}")
        
        service.characteristics.forEach { characteristic ->
            sb.appendLine("  - ${characteristic.uuid}")
            sb.appendLine("    Properties: ${getCharacteristicPropertiesString(characteristic.properties)}")
            sb.appendLine("    Descriptors: ${characteristic.descriptors.size}")
        }
        
        return sb.toString()
    }
    
    // 디바이스 주소 검증
    fun isValidBluetoothAddress(address: String): Boolean {
        val regex = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
        return regex.matches(address)
    }
    
    // 랜덤 디바이스 주소 생성 (테스트용)
    fun generateRandomAddress(): String {
        val random = Random()
        return (0..5).map { 
            String.format("%02x", random.nextInt(256))
        }.joinToString(":")
    }
    
    // 로그 출력 헬퍼
    fun logWithTimestamp(tag: String, message: String) {
        val timestamp = System.currentTimeMillis()
        Log.d(tag, "[$timestamp] $message")
    }
}