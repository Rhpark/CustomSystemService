package kr.open.library.systemmanager.controller.bluetooth.base

import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanSettings
import java.util.*

/**
 * BLE 관련 상수 정의
 */
object BleConstants {
    
    // 서비스 UUID (Nordic UART Service 호환)
    val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    
    // 특성 UUID
    val TX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // Peripheral → Central (NOTIFY)
    val RX_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // Central → Peripheral (WRITE)
    
    // 디스크립터 UUID
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    // 타임아웃 설정 (밀리초)
    const val CONNECTION_TIMEOUT = 10_000L      // 연결 타임아웃: 10초
    const val SCAN_TIMEOUT = 30_000L            // 스캔 타임아웃: 30초
    const val MESSAGE_TIMEOUT = 5_000L          // 메시지 전송 타임아웃: 5초
    const val MTU_REQUEST_TIMEOUT = 3_000L      // MTU 협상 타임아웃: 3초
    
    // 재시도 설정
    const val MAX_CONNECTION_RETRY = 3          // 최대 연결 재시도 횟수
    const val MAX_MESSAGE_RETRY = 2             // 최대 메시지 재전송 횟수
    const val RETRY_DELAY = 1_000L              // 재시도 간격: 1초
    
    // MTU 설정
    const val DEFAULT_MTU = 23                  // 기본 MTU
    const val TARGET_MTU = 512                  // 목표 MTU
    const val MIN_MTU = 23                      // 최소 MTU
    
    // 1:1 연결 보장
    const val MAX_CONNECTIONS = 1               // 최대 연결 수: 1개만
    
    // 스캔 설정
    const val SCAN_MODE = ScanSettings.SCAN_MODE_LOW_LATENCY
    const val SCAN_CALLBACK_TYPE = ScanSettings.CALLBACK_TYPE_ALL_MATCHES
    
    // 광고 설정
    const val ADVERTISE_MODE = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
    const val ADVERTISE_TX_POWER = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
    const val ADVERTISE_TIMEOUT = 0            // 0 = 무제한 (연결 시 중단)
    
    // 디바이스 이름 관련
    const val MAX_DEVICE_NAME_LENGTH = 20      // 최대 디바이스 이름 길이
    const val DEFAULT_DEVICE_NAME = "BLE Device"
    
    // 연결 파라미터
    const val CONNECTION_INTERVAL_MIN = 7      // 최소 연결 간격 (7.5ms 단위)
    const val CONNECTION_INTERVAL_MAX = 10     // 최대 연결 간격 (7.5ms 단위)  
    const val CONNECTION_LATENCY = 0           // 연결 레이턴시
    const val CONNECTION_SUPERVISION_TIMEOUT = 42 // 감시 타임아웃 (10ms 단위)
    
    // 에러 코드
    object ErrorCodes {
        const val PERMISSION_DENIED: Byte = 0x01
        const val BLUETOOTH_DISABLED: Byte = 0x02
        const val CONNECTION_FAILED: Byte = 0x03
        const val MESSAGE_SEND_FAILED: Byte = 0x04
        const val SCAN_FAILED: Byte = 0x05
        const val ADVERTISE_FAILED: Byte = 0x06
        const val MTU_REQUEST_FAILED: Byte = 0x07
        const val SERVICE_DISCOVERY_FAILED: Byte = 0x08
        const val CHARACTERISTIC_NOT_FOUND: Byte = 0x09
        const val GATT_RESOURCE_EXHAUSTED: Byte = 0x0A
    }
    
    // GATT 상태 코드 (BluetoothGatt.GATT_* 상수들)
    object GattStatus {
        const val GATT_SUCCESS = 0
        const val GATT_READ_NOT_PERMITTED = 2
        const val GATT_WRITE_NOT_PERMITTED = 3
        const val GATT_INSUFFICIENT_AUTHENTICATION = 5
        const val GATT_REQUEST_NOT_SUPPORTED = 6
        const val GATT_INSUFFICIENT_ENCRYPTION = 15
        const val GATT_CONNECTION_CONGESTED = 143
        const val GATT_FAILURE = 257
    }
    
    /**
     * GATT 상태 코드를 사람이 읽을 수 있는 문자열로 변환
     */
    fun getGattStatusString(status: Int): String {
        return when (status) {
            GattStatus.GATT_SUCCESS -> "SUCCESS"
            GattStatus.GATT_READ_NOT_PERMITTED -> "READ_NOT_PERMITTED"
            GattStatus.GATT_WRITE_NOT_PERMITTED -> "WRITE_NOT_PERMITTED"
            GattStatus.GATT_INSUFFICIENT_AUTHENTICATION -> "INSUFFICIENT_AUTHENTICATION"
            GattStatus.GATT_REQUEST_NOT_SUPPORTED -> "REQUEST_NOT_SUPPORTED"
            GattStatus.GATT_INSUFFICIENT_ENCRYPTION -> "INSUFFICIENT_ENCRYPTION"
            GattStatus.GATT_CONNECTION_CONGESTED -> "CONNECTION_CONGESTED"
            GattStatus.GATT_FAILURE -> "FAILURE"
            else -> "UNKNOWN_STATUS($status)"
        }
    }
    
    /**
     * UUID를 짧은 형태로 표시 (디버깅용)
     */
    fun getShortUuidString(uuid: UUID): String {
        val uuidString = uuid.toString()
        return when (uuid) {
            SERVICE_UUID -> "SERVICE"
            TX_CHAR_UUID -> "TX_CHAR"
            RX_CHAR_UUID -> "RX_CHAR"
            CLIENT_CHARACTERISTIC_CONFIG -> "CCCD"
            else -> uuidString.substring(0, 8) + "..."
        }
    }
    
    /**
     * 타임아웃 값들을 문자열로 반환 (디버깅용)
     */
    fun getTimeoutInfo(): String {
        return buildString {
            appendLine("=== BLE Timeout Settings ===")
            appendLine("Connection: ${CONNECTION_TIMEOUT}ms")
            appendLine("Scan: ${SCAN_TIMEOUT}ms")
            appendLine("Message: ${MESSAGE_TIMEOUT}ms")
            appendLine("MTU Request: ${MTU_REQUEST_TIMEOUT}ms")
            appendLine("Retry Delay: ${RETRY_DELAY}ms")
            appendLine("Max Connection Retry: $MAX_CONNECTION_RETRY")
            appendLine("Max Message Retry: $MAX_MESSAGE_RETRY")
        }
    }
    
    /**
     * 현재 BLE 설정 정보를 반환
     */
    fun getConfigInfo(): String {
        return buildString {
            appendLine("=== BLE Configuration ===")
            appendLine("Service UUID: ${getShortUuidString(SERVICE_UUID)}")
            appendLine("TX Char UUID: ${getShortUuidString(TX_CHAR_UUID)}")
            appendLine("RX Char UUID: ${getShortUuidString(RX_CHAR_UUID)}")
            appendLine("Target MTU: $TARGET_MTU bytes")
            appendLine("Max Connections: $MAX_CONNECTIONS")
            appendLine("Scan Mode: $SCAN_MODE")
            appendLine("Advertise Mode: $ADVERTISE_MODE")
        }
    }
}