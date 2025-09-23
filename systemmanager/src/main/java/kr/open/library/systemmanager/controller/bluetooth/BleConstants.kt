package kr.open.library.systemmanager.controller.bluetooth

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.AdvertiseSettings
import java.util.UUID

/**
 * BLE 통신에 사용되는 모든 상수와 설정을 정의하는 클래스
 * Nordic UART 서비스와 호환되는 UUID 사용
 * 
 * @author SystemService Library
 * @since 2025-01-13
 */
object BleConstants {
    
    // ============================================================================
    // GATT Service & Characteristic UUIDs (Nordic UART Service 호환)
    // ============================================================================
    
    /** 메인 서비스 UUID - Nordic UART Service */
    val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    
    /** TX Characteristic UUID (Peripheral → Central, NOTIFY) */
    val TX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    
    /** RX Characteristic UUID (Central → Peripheral, WRITE) */
    val RX_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    
    /** Client Characteristic Configuration Descriptor UUID (CCCD) */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    // ============================================================================
    // MTU (Maximum Transmission Unit) 설정
    // ============================================================================
    
    /** 기본 MTU 크기 (BLE 4.0 표준) */
    const val DEFAULT_MTU = 23
    
    /** 목표 MTU 크기 (BLE 4.2+ 지원) */
    const val TARGET_MTU = 247
    
    /** 최소 MTU 크기 */
    const val MIN_MTU = 23
    
    /** 최대 MTU 크기 (Android 제한) */
    const val MAX_MTU = 517
    
    /** ATT 헤더 크기 (실제 데이터는 MTU - ATT_HEADER_SIZE) */
    const val ATT_HEADER_SIZE = 3
    
    /** 안전한 데이터 최대 크기 (TARGET_MTU - ATT_HEADER_SIZE - 여유분) */
    const val SAFE_DATA_SIZE = TARGET_MTU - ATT_HEADER_SIZE - 10
    
    // ============================================================================
    // 타임아웃 설정 (밀리초)
    // ============================================================================
    
    /** 스캔 타임아웃 */
    const val SCAN_TIMEOUT_MS = 30_000L
    
    /** 연결 타임아웃 */
    const val CONNECTION_TIMEOUT_MS = 15_000L
    
    /** GATT 작업 타임아웃 */
    const val GATT_OPERATION_TIMEOUT_MS = 5_000L
    
    /** MTU 협상 타임아웃 */
    const val MTU_NEGOTIATION_TIMEOUT_MS = 3_000L
    
    /** 서비스 검색 타임아웃 */
    const val SERVICE_DISCOVERY_TIMEOUT_MS = 10_000L
    
    // ============================================================================
    // 재시도 설정
    // ============================================================================
    
    /** 최대 연결 재시도 횟수 */
    const val MAX_CONNECTION_RETRY = 3
    
    /** 최대 작업 재시도 횟수 */
    const val MAX_OPERATION_RETRY = 2
    
    /** 재시도 간격 (밀리초) */
    const val RETRY_DELAY_MS = 1_000L
    
    /** 백오프 승수 (재시도할 때마다 지연시간 증가) */
    const val BACKOFF_MULTIPLIER = 1.5f
    
    // ============================================================================
    // 스캔 설정
    // ============================================================================
    
    /** 기본 스캔 모드 */
    const val DEFAULT_SCAN_MODE = ScanSettings.SCAN_MODE_LOW_LATENCY
    
    /** 배터리 절약 스캔 모드 */
    const val BATTERY_SAVING_SCAN_MODE = ScanSettings.SCAN_MODE_LOW_POWER
    
    /** 스캔 콜백 타입 */
    const val SCAN_CALLBACK_TYPE = ScanSettings.CALLBACK_TYPE_ALL_MATCHES
    
    /** 첫 번째 매치 콜백 타입 (즉시 중단용) */
    const val FIRST_MATCH_CALLBACK_TYPE = ScanSettings.CALLBACK_TYPE_FIRST_MATCH
    
    // ============================================================================
    // 광고 설정
    // ============================================================================
    
    /** 기본 광고 모드 */
    const val DEFAULT_ADVERTISE_MODE = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
    
    /** 배터리 절약 광고 모드 */
    const val BATTERY_SAVING_ADVERTISE_MODE = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
    
    /** 기본 TX 파워 레벨 */
    const val DEFAULT_TX_POWER = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
    
    /** 배터리 절약 TX 파워 레벨 */
    const val BATTERY_SAVING_TX_POWER = AdvertiseSettings.ADVERTISE_TX_POWER_LOW
    
    /** 광고 타임아웃 (0 = 무제한, 연결 시 자동 중단) */
    const val ADVERTISE_TIMEOUT_MS = 0
    
    // ============================================================================
    // 연결 설정
    // ============================================================================
    
    /** 최대 동시 연결 수 (1:1 통신이므로 1개만) */
    const val MAX_CONNECTIONS = 1
    
    /** 연결 우선순위 - 고속 */
    const val CONNECTION_PRIORITY_HIGH = android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH
    
    /** 연결 우선순위 - 균형 */
    const val CONNECTION_PRIORITY_BALANCED = android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_BALANCED
    
    /** 연결 우선순위 - 저전력 */
    const val CONNECTION_PRIORITY_LOW_POWER = android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
    
    // ============================================================================
    // GATT 작업 지연 설정 (Android의 GATT 큐 처리를 위한 지연)
    // ============================================================================
    
    /** GATT 작업 간 기본 지연 시간 */
    const val GATT_OPERATION_DELAY_MS = 100L
    
    /** 중요한 GATT 작업 간 지연 시간 */
    const val CRITICAL_GATT_OPERATION_DELAY_MS = 300L
    
    // ============================================================================
    // 디버깅 및 로깅
    // ============================================================================
    
    /** BLE 관련 로그 태그 접두사 */
    const val LOG_TAG_PREFIX = "BLE"
    
    /** 상세 로깅 활성화 여부 */
    const val ENABLE_VERBOSE_LOGGING = false
    
    /** 성능 측정 로깅 활성화 여부 */
    const val ENABLE_PERFORMANCE_LOGGING = false
    
    // ============================================================================
    // 유틸리티 메서드들
    // ============================================================================
    
    /**
     * GATT 서비스를 생성합니다
     * Nordic UART 서비스와 호환되는 구조
     */
    fun createGattService(): android.bluetooth.BluetoothGattService {
        val service = android.bluetooth.BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        
        // TX Characteristic (Peripheral → Central, NOTIFY)
        val txCharacteristic = BluetoothGattCharacteristic(
            TX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0 // 권한 없음 (NOTIFY만)
        )
        
        // CCCD 디스크립터 추가 (알림 활성화용)
        val cccdDescriptor = android.bluetooth.BluetoothGattDescriptor(
            CCCD_UUID,
            android.bluetooth.BluetoothGattDescriptor.PERMISSION_READ or 
            android.bluetooth.BluetoothGattDescriptor.PERMISSION_WRITE
        )
        txCharacteristic.addDescriptor(cccdDescriptor)
        
        // RX Characteristic (Central → Peripheral, WRITE)
        val rxCharacteristic = BluetoothGattCharacteristic(
            RX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or 
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        service.addCharacteristic(txCharacteristic)
        service.addCharacteristic(rxCharacteristic)
        
        return service
    }
    
    /**
     * 주어진 MTU에서 실제 사용 가능한 데이터 크기를 계산합니다
     */
    fun getUsableDataSize(mtu: Int): Int {
        return maxOf(0, mtu - ATT_HEADER_SIZE)
    }
    
    /**
     * 설정 정보를 문자열로 반환합니다 (디버깅용)
     */
    fun getConfigInfo(): String {
        return buildString {
            appendLine("=== BLE Configuration ===")
            appendLine("Service UUID: $SERVICE_UUID")
            appendLine("TX Char UUID: $TX_CHAR_UUID")
            appendLine("RX Char UUID: $RX_CHAR_UUID")
            appendLine("Target MTU: $TARGET_MTU")
            appendLine("Safe Data Size: $SAFE_DATA_SIZE")
            appendLine("Max Connections: $MAX_CONNECTIONS")
        }
    }
    
    /**
     * 타임아웃 정보를 문자열로 반환합니다 (디버깅용)
     */
    fun getTimeoutInfo(): String {
        return buildString {
            appendLine("=== BLE Timeouts ===")
            appendLine("Scan: ${SCAN_TIMEOUT_MS}ms")
            appendLine("Connection: ${CONNECTION_TIMEOUT_MS}ms")
            appendLine("GATT Operation: ${GATT_OPERATION_TIMEOUT_MS}ms")
            appendLine("MTU Negotiation: ${MTU_NEGOTIATION_TIMEOUT_MS}ms")
            appendLine("Service Discovery: ${SERVICE_DISCOVERY_TIMEOUT_MS}ms")
        }
    }
    
    /**
     * 재시도 설정 정보를 문자열로 반환합니다 (디버깅용)
     */
    fun getRetryInfo(): String {
        return buildString {
            appendLine("=== BLE Retry Settings ===")
            appendLine("Max Connection Retry: $MAX_CONNECTION_RETRY")
            appendLine("Max Operation Retry: $MAX_OPERATION_RETRY")
            appendLine("Retry Delay: ${RETRY_DELAY_MS}ms")
            appendLine("Backoff Multiplier: $BACKOFF_MULTIPLIER")
        }
    }
}