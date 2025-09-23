package kr.open.library.systemmanager.controller.bluetooth.error

import kr.open.library.systemmanager.base.SystemServiceError

/**
 * BLE 전용 오류 처리 시스템
 * BLE-specific error handling system
 * 
 * Android BLE API의 복잡한 오류 상황들을 체계적으로 분류하고 처리합니다.
 * Systematically categorizes and handles complex error situations in Android BLE API.
 */
sealed class BleServiceError : SystemServiceError() {
    
    /**
     * BLE 하드웨어 관련 오류
     * BLE hardware-related errors
     */
    sealed class Hardware : BleServiceError() {
        /** BLE가 지원되지 않는 기기 */
        object NotSupported : Hardware() {
            override fun toString() = "BLE not supported on this device"
        }
        
        /** 블루투스 어댑터를 찾을 수 없음 */
        object AdapterNotFound : Hardware() {
            override fun toString() = "Bluetooth adapter not found"
        }
        
        /** 블루투스가 꺼져 있음 */
        object BluetoothOff : Hardware() {
            override fun toString() = "Bluetooth is turned off"
        }
        
        /** 블루투스가 켜지는 중 */
        object TurningOn : Hardware() {
            override fun toString() = "Bluetooth is turning on"
        }
        
        /** 블루투스가 꺼지는 중 */
        object TurningOff : Hardware() {
            override fun toString() = "Bluetooth is turning off"
        }
    }
    
    /**
     * 권한 관련 오류 (BLE 특화)
     * Permission-related errors (BLE specific)
     */
    sealed class Permission : BleServiceError() {
        /** 스캔 권한 없음 (API 31+) */
        object ScanPermissionMissing : Permission() {
            override fun toString() = "BLUETOOTH_SCAN permission required (API 31+)"
        }
        
        /** 광고 권한 없음 (API 31+) */
        object AdvertisePermissionMissing : Permission() {
            override fun toString() = "BLUETOOTH_ADVERTISE permission required (API 31+)"
        }
        
        /** 연결 권한 없음 (API 31+) */
        object ConnectPermissionMissing : Permission() {
            override fun toString() = "BLUETOOTH_CONNECT permission required (API 31+)"
        }
        
        /** 위치 권한 없음 (API 23-30) */
        object LocationPermissionMissing : Permission() {
            override fun toString() = "Location permission required for BLE scanning (API 23-30)"
        }
        
        /** 위치 서비스 비활성화 */
        object LocationServiceDisabled : Permission() {
            override fun toString() = "Location service must be enabled for BLE scanning"
        }
        
        /** 다중 권한 누락 */
        data class MultiplePermissionsMissing(val permissions: List<String>) : Permission() {
            override fun toString() = "Missing permissions: ${permissions.joinToString(", ")}"
        }
    }
    
    /**
     * 스캐닝 관련 오류
     * Scanning-related errors
     */
    sealed class Scanning : BleServiceError() {
        /** 스캔 시작 실패 */
        data class StartFailed(val errorCode: Int) : Scanning() {
            override fun toString() = "Scan start failed with error code: $errorCode"
        }
        
        /** 이미 스캔 중 */
        object AlreadyStarted : Scanning() {
            override fun toString() = "Scanning is already started"
        }
        
        /** 스캔 기능 미지원 */
        object FeatureUnsupported : Scanning() {
            override fun toString() = "BLE scanning feature is not supported"
        }
        
        /** 내부 오류 */
        object InternalError : Scanning() {
            override fun toString() = "Internal error occurred during scanning"
        }
        
        /** 앱이 스캔 정책을 위반함 */
        object ApplicationRegistrationFailed : Scanning() {
            override fun toString() = "Application registration failed for scanning"
        }
        
        /** 스캔 필터 오류 */
        data class FilterError(val reason: String) : Scanning() {
            override fun toString() = "Scan filter error: $reason"
        }
    }
    
    /**
     * 광고 관련 오류
     * Advertising-related errors
     */
    sealed class Advertising : BleServiceError() {
        /** 광고 시작 실패 */
        data class StartFailed(val errorCode: Int) : Advertising() {
            override fun toString() = "Advertising start failed with error code: $errorCode"
        }
        
        /** 광고 데이터 크기 초과 */
        object DataTooLarge : Advertising() {
            override fun toString() = "Advertisement data exceeds the maximum allowed size"
        }
        
        /** 동시 광고 수 제한 초과 */
        object TooManyAdvertisers : Advertising() {
            override fun toString() = "Maximum number of advertisers exceeded"
        }
        
        /** 이미 광고 중 */
        object AlreadyStarted : Advertising() {
            override fun toString() = "Advertising is already started"
        }
        
        /** 광고 기능 미지원 */
        object FeatureUnsupported : Advertising() {
            override fun toString() = "BLE advertising feature is not supported"
        }
        
        /** 내부 오류 */
        object InternalError : Advertising() {
            override fun toString() = "Internal error occurred during advertising"
        }
        
        /** 전력 부족 */
        object InsufficientPower : Advertising() {
            override fun toString() = "Insufficient power for advertising"
        }
    }
    
    /**
     * 연결 관련 오류
     * Connection-related errors
     */
    sealed class Connection : BleServiceError() {
        /** 연결 실패 */
        data class Failed(val status: Int, val deviceAddress: String) : Connection() {
            override fun toString() = "Connection failed to $deviceAddress with status: $status"
        }
        
        /** 연결 시간 초과 */
        data class Timeout(val deviceAddress: String, val timeoutMs: Long) : Connection() {
            override fun toString() = "Connection timeout to $deviceAddress after ${timeoutMs}ms"
        }
        
        /** 이미 연결됨 */
        data class AlreadyConnected(val deviceAddress: String) : Connection() {
            override fun toString() = "Already connected to $deviceAddress"
        }
        
        /** 연결 해제됨 */
        data class Disconnected(val deviceAddress: String, val status: Int) : Connection() {
            override fun toString() = "Disconnected from $deviceAddress with status: $status"
        }
        
        /** 최대 연결 수 초과 */
        data class MaxConnectionsReached(val maxConnections: Int) : Connection() {
            override fun toString() = "Maximum connections reached: $maxConnections"
        }
        
        /** 잘못된 기기 주소 */
        data class InvalidDeviceAddress(val address: String) : Connection() {
            override fun toString() = "Invalid device address: $address"
        }
    }
    
    /**
     * GATT 작업 관련 오류
     * GATT operation-related errors
     */
    sealed class Gatt : BleServiceError() {
        /** GATT 작업 실패 */
        data class OperationFailed(val status: Int, val operation: String, val deviceAddress: String) : Gatt() {
            override fun toString() = "GATT $operation failed for $deviceAddress with status: $status"
        }
        
        /** 서비스 탐색 실패 */
        data class ServiceDiscoveryFailed(val deviceAddress: String) : Gatt() {
            override fun toString() = "Service discovery failed for $deviceAddress"
        }
        
        /** 특성을 찾을 수 없음 */
        data class CharacteristicNotFound(val uuid: String, val deviceAddress: String = "") : Gatt() {
            constructor(message: String) : this(message, "")
            override fun toString() = "Characteristic $uuid not found" + if (deviceAddress.isNotEmpty()) " on $deviceAddress" else ""
        }
        
        /** 특성 읽기 실패 */
        data class CharacteristicReadFailed(val message: String, val status: Int = -1) : Gatt() {
            override fun toString() = message + if (status != -1) " (status: $status)" else ""
        }
        
        /** 특성 쓰기 실패 */
        data class CharacteristicWriteFailed(val message: String, val status: Int = -1) : Gatt() {
            override fun toString() = message + if (status != -1) " (status: $status)" else ""
        }
        
        /** 작업 지원되지 않음 */
        data class OperationNotSupported(val message: String) : Gatt() {
            override fun toString() = "Operation not supported: $message"
        }
        
        /** 서비스를 찾을 수 없음 */
        data class ServiceNotFound(val uuid: String, val deviceAddress: String) : Gatt() {
            override fun toString() = "Service $uuid not found on $deviceAddress"
        }
        
        /** 디스크립터를 찾을 수 없음 */
        data class DescriptorNotFound(val uuid: String, val deviceAddress: String) : Gatt() {
            override fun toString() = "Descriptor $uuid not found on $deviceAddress"
        }
        
        /** MTU 협상 실패 */
        data class MtuNegotiationFailed(val requestedMtu: Int, val deviceAddress: String) : Gatt() {
            override fun toString() = "MTU negotiation failed for $deviceAddress (requested: $requestedMtu)"
        }
        
        /** GATT 작업 큐 오버플로우 */
        data class OperationQueueFull(val deviceAddress: String) : Gatt() {
            override fun toString() = "GATT operation queue is full for $deviceAddress"
        }
        
        /** GATT 작업 타임아웃 */
        data class OperationTimeout(val operation: String, val deviceAddress: String) : Gatt() {
            override fun toString() = "GATT $operation timeout for $deviceAddress"
        }
    }
    
    /**
     * GATT 서버 관련 오류
     * GATT server-related errors
     */
    sealed class GattServer : BleServiceError() {
        /** GATT 서버 시작 실패 */
        data class StartFailed(val reason: String) : GattServer() {
            override fun toString() = "GATT server start failed: $reason"
        }
        
        /** GATT 서버 생성 실패 */
        data class ServerCreationFailed(val message: String, val cause: Throwable? = null) : GattServer() {
            override fun toString() = "GATT server creation failed: $message"
        }
        
        /** GATT 서버 중지 실패 */
        data class ServerStopFailed(val message: String, val cause: Throwable? = null) : GattServer() {
            override fun toString() = "GATT server stop failed: $message"
        }
        
        /** GATT 서버 실행되지 않음 */
        data class ServerNotRunning(val message: String) : GattServer() {
            override fun toString() = "GATT server not running: $message"
        }
        
        /** 클라이언트 연결되지 않음 */
        data class ClientNotConnected(val message: String) : GattServer() {
            override fun toString() = "Client not connected: $message"
        }
        
        /** 클라이언트 연결 해제 실패 */
        data class ClientDisconnectFailed(val message: String, val cause: Throwable? = null) : GattServer() {
            override fun toString() = "Client disconnect failed: $message"
        }
        
        /** 서비스 추가 실패 */
        data class ServiceAddFailed(val message: String, val cause: Throwable? = null) : GattServer() {
            override fun toString() = "Service add failed: $message"
        }
        
        /** 서비스 제거 실패 */
        data class ServiceRemoveFailed(val message: String, val cause: Throwable? = null) : GattServer() {
            override fun toString() = "Service remove failed: $message"
        }
        
        /** 알림 전송 실패 */
        data class NotificationFailed(val message: String, val cause: Throwable? = null) : GattServer() {
            override fun toString() = "Notification failed: $message"
        }
        
        /** 클라이언트 응답 실패 */
        data class ResponseFailed(val deviceAddress: String, val requestId: Int) : GattServer() {
            override fun toString() = "Failed to respond to client $deviceAddress (request: $requestId)"
        }
    }
    
    /**
     * 시스템 상태 관련 오류
     * System state-related errors
     */
    sealed class SystemState : BleServiceError() {
        /** 배터리 최적화 활성화됨 */
        object BatteryOptimizationEnabled : SystemState() {
            override fun toString() = "Battery optimization is enabled - BLE operations may be restricted"
        }
        
        /** Doze 모드 활성 */
        object DozeMode : SystemState() {
            override fun toString() = "Device is in Doze mode - BLE operations may be limited"
        }
        
        /** 백그라운드 제한 */
        object BackgroundRestricted : SystemState() {
            override fun toString() = "Background execution is restricted"
        }
        
        /** 메모리 부족 */
        object LowMemory : SystemState() {
            override fun toString() = "Low memory condition detected"
        }
        
        /** 초기화 실패 */
        data class InitializationFailed(val message: String, val cause: Throwable? = null) : SystemState() {
            override fun toString() = "Initialization failed: $message"
        }
        
        /** 초기화되지 않음 */
        data class NotInitialized(val message: String) : SystemState() {
            override fun toString() = "Not initialized: $message"
        }
        
        /** 작업 실패 */
        data class OperationFailed(val message: String, val cause: Throwable? = null) : SystemState() {
            override fun toString() = "Operation failed: $message"
        }
        
        /** 작업 지원되지 않음 */
        data class OperationNotSupported(val message: String) : SystemState() {
            override fun toString() = "Operation not supported: $message"
        }
    }
}

/**
 * BLE 오류 확장 함수들
 * BLE error extension functions
 */

/**
 * 사용자 친화적 메시지 반환
 * Returns user-friendly message
 */
fun BleServiceError.getUserMessage(): String = when (this) {
    is BleServiceError.Hardware.NotSupported -> "이 기기는 BLE를 지원하지 않습니다"
    is BleServiceError.Hardware.BluetoothOff -> "블루투스를 켜주세요"
    is BleServiceError.Permission.LocationPermissionMissing -> "위치 권한이 필요합니다"
    is BleServiceError.Permission.LocationServiceDisabled -> "위치 서비스를 켜주세요"
    is BleServiceError.Scanning.StartFailed -> "기기 검색을 시작할 수 없습니다"
    is BleServiceError.Connection.Failed -> "기기에 연결할 수 없습니다"
    is BleServiceError.Connection.Timeout -> "연결 시간이 초과되었습니다"
    else -> "BLE 작업 중 오류가 발생했습니다"
}

/**
 * 개발자용 상세 메시지 반환
 * Returns detailed message for developers
 */
fun BleServiceError.getDeveloperMessage(): String = toString()

/**
 * 복구 가능한 오류인지 확인
 * Checks if the error is recoverable
 */
fun BleServiceError.isRecoverable(): Boolean = when (this) {
    is BleServiceError.Hardware.NotSupported -> false
    is BleServiceError.Hardware.AdapterNotFound -> false
    is BleServiceError.Hardware.BluetoothOff -> true
    is BleServiceError.Permission -> true
    is BleServiceError.Scanning.FeatureUnsupported -> false
    is BleServiceError.Connection.Timeout -> true
    is BleServiceError.Connection.Failed -> true
    else -> true
}

/**
 * 사용자 조치가 필요한 오류인지 확인
 * Checks if user action is required
 */
fun BleServiceError.requiresUserAction(): Boolean = when (this) {
    is BleServiceError.Hardware.BluetoothOff -> true
    is BleServiceError.Permission -> true
    is BleServiceError.SystemState.BatteryOptimizationEnabled -> true
    else -> false
}