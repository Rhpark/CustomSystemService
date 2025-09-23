package kr.open.library.systemmanager.controller.bluetooth.data

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * BLE 연결 상태 정보
 * BLE Connection State Information
 * 
 * BLE 기기의 연결 상태와 관련 메타데이터를 관리합니다.
 * Manages BLE device connection state and related metadata.
 */
@Parcelize
data class BleConnectionState(
    val deviceAddress: String,
    val deviceName: String?,
    val connectionState: Int,
    val connectionId: String,
    val connectionTimestamp: Long,
    val lastUpdateTimestamp: Long = System.currentTimeMillis(),
    val connectionAttempts: Int = 0,
    val maxConnectionAttempts: Int = 3,
    val connectionTimeoutMs: Long = 10_000,
    val autoConnect: Boolean = false,
    val mtuSize: Int = 23, // 기본 MTU 크기
    val services: List<BleServiceInfo> = emptyList(),
    val lastError: String? = null
) : Parcelable {
    
    /**
     * 연결 상태 확인
     */
    fun isConnected(): Boolean = connectionState == BluetoothProfile.STATE_CONNECTED
    
    /**
     * 연결 중인지 확인
     */
    fun isConnecting(): Boolean = connectionState == BluetoothProfile.STATE_CONNECTING
    
    /**
     * 연결 해제 중인지 확인
     */
    fun isDisconnecting(): Boolean = connectionState == BluetoothProfile.STATE_DISCONNECTING
    
    /**
     * 연결 해제된 상태인지 확인
     */
    fun isDisconnected(): Boolean = connectionState == BluetoothProfile.STATE_DISCONNECTED
    
    /**
     * 재연결 가능한지 확인
     */
    fun canRetryConnection(): Boolean {
        return connectionAttempts < maxConnectionAttempts && 
               (isDisconnected() || lastError != null)
    }
    
    /**
     * 연결 시간 계산 (밀리초)
     */
    fun getConnectionDuration(): Long {
        return if (isConnected()) {
            lastUpdateTimestamp - connectionTimestamp
        } else {
            0L
        }
    }
    
    /**
     * 연결 상태 이름 반환
     */
    fun getConnectionStateName(): String = when (connectionState) {
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN($connectionState)"
    }
    
    /**
     * 서비스 탐색 완료 여부 확인
     */
    fun isServiceDiscovered(): Boolean = services.isNotEmpty()
    
    /**
     * 특정 서비스 존재 여부 확인
     */
    fun hasService(serviceUuid: String): Boolean {
        return services.any { it.uuid.equals(serviceUuid, ignoreCase = true) }
    }
    
    /**
     * MTU 크기가 기본값에서 협상되었는지 확인
     */
    fun isMtuNegotiated(): Boolean = mtuSize > 23
    
    /**
     * 연결 상태 업데이트
     */
    fun updateConnectionState(
        newState: Int,
        error: String? = null
    ): BleConnectionState {
        return copy(
            connectionState = newState,
            lastUpdateTimestamp = System.currentTimeMillis(),
            lastError = error
        )
    }
    
    /**
     * 재연결 시도 카운터 증가
     */
    fun incrementConnectionAttempts(): BleConnectionState {
        return copy(
            connectionAttempts = connectionAttempts + 1,
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 연결 상태 초기화 (새 연결 시도)
     */
    fun resetForNewConnection(): BleConnectionState {
        return copy(
            connectionState = BluetoothProfile.STATE_CONNECTING,
            connectionTimestamp = System.currentTimeMillis(),
            lastUpdateTimestamp = System.currentTimeMillis(),
            connectionAttempts = 0,
            services = emptyList(),
            lastError = null
        )
    }
    
    /**
     * MTU 크기 업데이트
     */
    fun updateMtu(newMtuSize: Int): BleConnectionState {
        return copy(
            mtuSize = newMtuSize,
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 서비스 목록 업데이트
     */
    fun updateServices(newServices: List<BleServiceInfo>): BleConnectionState {
        return copy(
            services = newServices,
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }
    
    override fun toString(): String {
        return "BleConnectionState(" +
               "address='$deviceAddress', " +
               "name='$deviceName', " +
               "state=${getConnectionStateName()}, " +
               "attempts=$connectionAttempts/$maxConnectionAttempts, " +
               "mtu=$mtuSize, " +
               "services=${services.size}, " +
               "duration=${getConnectionDuration()}ms" +
               ")"
    }
    
    companion object {
        /**
         * 새 연결을 위한 초기 상태 생성
         */
        fun createInitial(
            deviceAddress: String,
            deviceName: String?,
            autoConnect: Boolean = false,
            connectionTimeoutMs: Long = 10_000,
            maxConnectionAttempts: Int = 3
        ): BleConnectionState {
            return BleConnectionState(
                deviceAddress = deviceAddress,
                deviceName = deviceName,
                connectionState = BluetoothProfile.STATE_DISCONNECTED,
                connectionId = generateConnectionId(deviceAddress),
                connectionTimestamp = System.currentTimeMillis(),
                autoConnect = autoConnect,
                connectionTimeoutMs = connectionTimeoutMs,
                maxConnectionAttempts = maxConnectionAttempts
            )
        }
        
        /**
         * 연결 ID 생성
         */
        private fun generateConnectionId(deviceAddress: String): String {
            return "${deviceAddress}_${System.currentTimeMillis()}"
        }
    }
}

/**
 * BLE 서비스 정보
 * BLE Service Information
 */
@Parcelize
data class BleServiceInfo(
    val uuid: String,
    val isPrimary: Boolean,
    val characteristics: List<BleCharacteristicInfo> = emptyList()
) : Parcelable {
    
    /**
     * 특정 특성 존재 여부 확인
     */
    fun hasCharacteristic(characteristicUuid: String): Boolean {
        return characteristics.any { it.uuid.equals(characteristicUuid, ignoreCase = true) }
    }
    
    /**
     * 특정 특성 반환
     */
    fun getCharacteristic(characteristicUuid: String): BleCharacteristicInfo? {
        return characteristics.find { it.uuid.equals(characteristicUuid, ignoreCase = true) }
    }
    
    companion object {
        /**
         * BluetoothGattService로부터 생성
         */
        fun fromGattService(service: android.bluetooth.BluetoothGattService): BleServiceInfo {
            val characteristics = service.characteristics.map { characteristic ->
                BleCharacteristicInfo.fromGattCharacteristic(characteristic)
            }
            
            return BleServiceInfo(
                uuid = service.uuid.toString(),
                isPrimary = service.type == android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY,
                characteristics = characteristics
            )
        }
    }
}

/**
 * BLE 특성 정보
 * BLE Characteristic Information
 */
@Parcelize
data class BleCharacteristicInfo(
    val uuid: String,
    val properties: Int,
    val permissions: Int,
    val descriptors: List<BleDescriptorInfo> = emptyList()
) : Parcelable {
    
    /**
     * 읽기 가능한지 확인
     */
    fun isReadable(): Boolean {
        return properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ != 0
    }
    
    /**
     * 쓰기 가능한지 확인
     */
    fun isWritable(): Boolean {
        return properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE != 0
    }
    
    /**
     * 알림 지원하는지 확인
     */
    fun isNotifiable(): Boolean {
        return properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
    }
    
    /**
     * 지시 지원하는지 확인
     */
    fun isIndicatable(): Boolean {
        return properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
    }
    
    /**
     * 특성 속성들을 문자열로 반환
     */
    fun getPropertiesString(): String {
        val props = mutableListOf<String>()
        
        if (isReadable()) props.add("READ")
        if (isWritable()) props.add("WRITE")
        if (isNotifiable()) props.add("NOTIFY")
        if (isIndicatable()) props.add("INDICATE")
        
        return props.joinToString(", ")
    }
    
    companion object {
        /**
         * BluetoothGattCharacteristic으로부터 생성
         */
        fun fromGattCharacteristic(characteristic: android.bluetooth.BluetoothGattCharacteristic): BleCharacteristicInfo {
            val descriptors = characteristic.descriptors.map { descriptor ->
                BleDescriptorInfo.fromGattDescriptor(descriptor)
            }
            
            return BleCharacteristicInfo(
                uuid = characteristic.uuid.toString(),
                properties = characteristic.properties,
                permissions = characteristic.permissions,
                descriptors = descriptors
            )
        }
    }
}

/**
 * BLE 디스크립터 정보
 * BLE Descriptor Information
 */
@Parcelize
data class BleDescriptorInfo(
    val uuid: String,
    val permissions: Int
) : Parcelable {
    
    companion object {
        /**
         * BluetoothGattDescriptor로부터 생성
         */
        fun fromGattDescriptor(descriptor: android.bluetooth.BluetoothGattDescriptor): BleDescriptorInfo {
            return BleDescriptorInfo(
                uuid = descriptor.uuid.toString(),
                permissions = descriptor.permissions
            )
        }
    }
}