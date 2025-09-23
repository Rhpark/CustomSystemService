package kr.open.library.systemmanager.controller.bluetooth

import android.bluetooth.le.ScanSettings
import android.bluetooth.le.AdvertiseSettings

/**
 * BLE 동작 설정을 관리하는 데이터 클래스
 * 다양한 시나리오에 맞는 최적화된 설정을 제공
 * 
 * @author SystemService Library
 * @since 2025-01-13
 */
data class BleConfig(
    // ============================================================================
    // 스캔 설정
    // ============================================================================
    
    /** 스캔 타임아웃 (밀리초) */
    val scanTimeoutMs: Long = BleConstants.SCAN_TIMEOUT_MS,
    
    /** 스캔 모드 (배터리 vs 성능) */
    val scanMode: Int = BleConstants.DEFAULT_SCAN_MODE,
    
    /** 스캔 콜백 타입 */
    val scanCallbackType: Int = BleConstants.SCAN_CALLBACK_TYPE,
    
    /** 즉시 중단 모드 (첫 번째 디바이스 발견 시 스캔 중단) */
    val stopScanOnFirstMatch: Boolean = true,
    
    // ============================================================================
    // 연결 설정
    // ============================================================================
    
    /** 연결 타임아웃 (밀리초) */
    val connectionTimeoutMs: Long = BleConstants.CONNECTION_TIMEOUT_MS,
    
    /** 목표 MTU 크기 */
    val targetMtu: Int = BleConstants.TARGET_MTU,
    
    /** MTU 협상 활성화 여부 */
    val enableMtuNegotiation: Boolean = true,
    
    /** 연결 우선순위 */
    val connectionPriority: Int = BleConstants.CONNECTION_PRIORITY_BALANCED,
    
    // ============================================================================
    // 광고 설정 (Peripheral 모드)
    // ============================================================================
    
    /** 광고 모드 */
    val advertiseMode: Int = BleConstants.DEFAULT_ADVERTISE_MODE,
    
    /** TX 파워 레벨 */
    val advertiseTxPower: Int = BleConstants.DEFAULT_TX_POWER,
    
    /** 광고 타임아웃 (0 = 무제한) */
    val advertiseTimeoutMs: Int = BleConstants.ADVERTISE_TIMEOUT_MS,
    
    /** 연결 가능 여부 */
    val advertiseConnectable: Boolean = true,
    
    /** 디바이스 이름 포함 여부 */
    val includeDeviceName: Boolean = true,
    
    /** TX 파워 레벨 포함 여부 */
    val includeTxPowerLevel: Boolean = true,
    
    // ============================================================================
    // 데이터 설정
    // ============================================================================
    
    /** 직렬화 형식 */
    val serializationFormat: SerializationFormat = SerializationFormat.JSON,
    
    /** 최대 객체 크기 (bytes) */
    val maxObjectSizeBytes: Int = BleConstants.SAFE_DATA_SIZE,
    
    /** 데이터 압축 활성화 여부 */
    val enableDataCompression: Boolean = false,
    
    // ============================================================================
    // 재시도 설정
    // ============================================================================
    
    /** 최대 연결 재시도 횟수 */
    val maxConnectionRetries: Int = BleConstants.MAX_CONNECTION_RETRY,
    
    /** 최대 작업 재시도 횟수 */
    val maxOperationRetries: Int = BleConstants.MAX_OPERATION_RETRY,
    
    /** 재시도 간격 (밀리초) */
    val retryDelayMs: Long = BleConstants.RETRY_DELAY_MS,
    
    /** 백오프 승수 */
    val backoffMultiplier: Float = BleConstants.BACKOFF_MULTIPLIER,
    
    // ============================================================================
    // 타임아웃 설정
    // ============================================================================
    
    /** GATT 작업 타임아웃 */
    val gattOperationTimeoutMs: Long = BleConstants.GATT_OPERATION_TIMEOUT_MS,
    
    /** 서비스 검색 타임아웃 */
    val serviceDiscoveryTimeoutMs: Long = BleConstants.SERVICE_DISCOVERY_TIMEOUT_MS,
    
    /** MTU 협상 타임아웃 */
    val mtuNegotiationTimeoutMs: Long = BleConstants.MTU_NEGOTIATION_TIMEOUT_MS,
    
    // ============================================================================
    // 자동 재연결 설정
    // ============================================================================
    
    /** 자동 재연결 활성화 여부 */
    val enableAutoReconnect: Boolean = false,
    
    /** 자동 재연결 최대 시도 횟수 */
    val autoReconnectMaxAttempts: Int = 3,
    
    /** 자동 재연결 간격 (밀리초) */
    val autoReconnectIntervalMs: Long = 5_000L,
    
    // ============================================================================
    // 로깅 및 디버깅 설정
    // ============================================================================
    
    /** 상세 로깅 활성화 여부 */
    val enableVerboseLogging: Boolean = BleConstants.ENABLE_VERBOSE_LOGGING,
    
    /** 성능 측정 로깅 활성화 여부 */
    val enablePerformanceLogging: Boolean = BleConstants.ENABLE_PERFORMANCE_LOGGING,
    
    /** 바이트 배열 로깅 활성화 여부 (디버깅용) */
    val enableDataLogging: Boolean = false
) {
    
    companion object {
        
        /**
         * 기본 설정 (균형잡힌 성능과 배터리 사용)
         */
        fun default(): BleConfig = BleConfig()
        
        /**
         * 배터리 최적화 설정
         * 배터리 수명을 최우선으로 하는 설정
         */
        fun optimizedForBattery(): BleConfig = BleConfig(
            scanTimeoutMs = 10_000L,
            scanMode = ScanSettings.SCAN_MODE_LOW_POWER,
            connectionTimeoutMs = 20_000L,
            targetMtu = BleConstants.DEFAULT_MTU, // MTU 협상 생략
            enableMtuNegotiation = false,
            connectionPriority = BleConstants.CONNECTION_PRIORITY_LOW_POWER,
            advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER,
            advertiseTxPower = AdvertiseSettings.ADVERTISE_TX_POWER_LOW,
            maxConnectionRetries = 2,
            maxOperationRetries = 1,
            retryDelayMs = 2_000L,
            enableAutoReconnect = false
        )
        
        /**
         * 성능 최적화 설정
         * 속도와 응답성을 최우선으로 하는 설정
         */
        fun optimizedForSpeed(): BleConfig = BleConfig(
            scanTimeoutMs = 5_000L,
            scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY,
            connectionTimeoutMs = 8_000L,
            targetMtu = BleConstants.TARGET_MTU,
            enableMtuNegotiation = true,
            connectionPriority = BleConstants.CONNECTION_PRIORITY_HIGH,
            advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY,
            advertiseTxPower = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH,
            maxConnectionRetries = 3,
            maxOperationRetries = 3,
            retryDelayMs = 500L,
            gattOperationTimeoutMs = 3_000L,
            enableAutoReconnect = true
        )
        
        /**
         * 안정성 최우선 설정
         * 연결 안정성과 신뢰성을 최우선으로 하는 설정
         */
        fun optimizedForReliability(): BleConfig = BleConfig(
            scanTimeoutMs = 45_000L,
            connectionTimeoutMs = 30_000L,
            targetMtu = BleConstants.DEFAULT_MTU, // 안정성을 위해 기본 MTU 사용
            enableMtuNegotiation = false,
            connectionPriority = BleConstants.CONNECTION_PRIORITY_BALANCED,
            maxConnectionRetries = 5,
            maxOperationRetries = 4,
            retryDelayMs = 2_000L,
            backoffMultiplier = 2.0f,
            gattOperationTimeoutMs = 10_000L,
            serviceDiscoveryTimeoutMs = 15_000L,
            enableAutoReconnect = true,
            autoReconnectMaxAttempts = 5,
            autoReconnectIntervalMs = 3_000L
        )
        
        /**
         * 디버깅 및 개발용 설정
         * 상세한 로깅과 분석을 위한 설정
         */
        fun forDebugging(): BleConfig = BleConfig(
            enableVerboseLogging = true,
            enablePerformanceLogging = true,
            enableDataLogging = true,
            scanTimeoutMs = 60_000L,
            connectionTimeoutMs = 30_000L,
            maxConnectionRetries = 1, // 빠른 실패로 디버깅 용이
            maxOperationRetries = 1,
            enableAutoReconnect = false
        )
        
        /**
         * 1:1 통신 전용 최적화 설정
         * 설계서의 핵심 요구사항에 맞춘 설정
         */
        fun optimizedForOneToOne(): BleConfig = BleConfig(
            scanTimeoutMs = 15_000L,
            stopScanOnFirstMatch = true, // 첫 번째 매치 시 즉시 중단
            connectionTimeoutMs = 10_000L,
            targetMtu = BleConstants.TARGET_MTU,
            enableMtuNegotiation = true,
            maxConnectionRetries = 2,
            retryDelayMs = 1_000L,
            enableAutoReconnect = true,
            autoReconnectMaxAttempts = 3,
            autoReconnectIntervalMs = 2_000L,
            serializationFormat = SerializationFormat.JSON,
            maxObjectSizeBytes = 200 // 안전한 크기로 제한
        )
    }
    
    // ============================================================================
    // 검증 메서드들
    // ============================================================================
    
    /**
     * 설정값들이 유효한지 검증합니다
     */
    fun validate(): Result<Unit> = runCatching {
        require(scanTimeoutMs > 0) { "scanTimeoutMs must be positive" }
        require(connectionTimeoutMs > 0) { "connectionTimeoutMs must be positive" }
        require(targetMtu in BleConstants.MIN_MTU..BleConstants.MAX_MTU) { 
            "targetMtu must be between ${BleConstants.MIN_MTU} and ${BleConstants.MAX_MTU}" 
        }
        require(maxObjectSizeBytes > 0) { "maxObjectSizeBytes must be positive" }
        require(maxObjectSizeBytes <= BleConstants.SAFE_DATA_SIZE) { 
            "maxObjectSizeBytes must not exceed ${BleConstants.SAFE_DATA_SIZE}" 
        }
        require(maxConnectionRetries >= 0) { "maxConnectionRetries must be non-negative" }
        require(retryDelayMs >= 0) { "retryDelayMs must be non-negative" }
        require(backoffMultiplier >= 1.0f) { "backoffMultiplier must be >= 1.0" }
    }
    
    /**
     * 실제 사용 가능한 데이터 크기를 계산합니다
     */
    fun getUsableDataSize(): Int {
        val mtu = if (enableMtuNegotiation) targetMtu else BleConstants.DEFAULT_MTU
        return minOf(maxObjectSizeBytes, BleConstants.getUsableDataSize(mtu))
    }
    
    /**
     * 배터리 사용량을 예측합니다 (상대적 점수)
     */
    fun estimateBatteryUsage(): BatteryUsage {
        var score = 0
        
        // 스캔 설정
        score += when (scanMode) {
            ScanSettings.SCAN_MODE_LOW_POWER -> 1
            ScanSettings.SCAN_MODE_BALANCED -> 2
            ScanSettings.SCAN_MODE_LOW_LATENCY -> 3
            else -> 2
        }
        
        // 광고 설정
        score += when (advertiseMode) {
            AdvertiseSettings.ADVERTISE_MODE_LOW_POWER -> 1
            AdvertiseSettings.ADVERTISE_MODE_BALANCED -> 2
            AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY -> 3
            else -> 2
        }
        
        // TX 파워
        score += when (advertiseTxPower) {
            AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW -> 0
            AdvertiseSettings.ADVERTISE_TX_POWER_LOW -> 1
            AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM -> 2
            AdvertiseSettings.ADVERTISE_TX_POWER_HIGH -> 3
            else -> 2
        }
        
        // 타임아웃 (긴 타임아웃은 배터리 소모 증가)
        score += when {
            scanTimeoutMs <= 10_000L -> 1
            scanTimeoutMs <= 30_000L -> 2
            else -> 3
        }
        
        return when {
            score <= 4 -> BatteryUsage.LOW
            score <= 8 -> BatteryUsage.MEDIUM
            else -> BatteryUsage.HIGH
        }
    }
    
    /**
     * 설정 정보를 문자열로 반환합니다
     */
    fun getSummary(): String = buildString {
        appendLine("=== BLE Configuration Summary ===")
        appendLine("Scan: ${scanTimeoutMs}ms, Mode: $scanMode")
        appendLine("Connection: ${connectionTimeoutMs}ms, MTU: $targetMtu")
        appendLine("Retry: Connection($maxConnectionRetries), Operation($maxOperationRetries)")
        appendLine("Serialization: $serializationFormat, MaxSize: ${maxObjectSizeBytes}B")
        appendLine("Auto-reconnect: $enableAutoReconnect")
        appendLine("Battery Usage: ${estimateBatteryUsage()}")
        appendLine("Usable Data Size: ${getUsableDataSize()}B")
    }
}

/**
 * 직렬화 형식을 정의하는 열거형
 */
enum class SerializationFormat {
    /** JSON 형식 (가독성 좋음, 크기 큼) */
    JSON,
    
    /** Kotlinx Serialization (중간 크기, Kotlin 최적화) */
    KOTLINX,
    
    /** Protocol Buffers (크기 작음, 성능 좋음) - 향후 구현 */
    PROTOBUF,
    
    /** 커스텀 바이너리 형식 (최소 크기) - 향후 구현 */
    CUSTOM_BINARY
}

/**
 * 배터리 사용량 예측 결과
 */
enum class BatteryUsage {
    /** 낮은 배터리 사용량 */
    LOW,
    
    /** 중간 배터리 사용량 */
    MEDIUM,
    
    /** 높은 배터리 사용량 */
    HIGH
}