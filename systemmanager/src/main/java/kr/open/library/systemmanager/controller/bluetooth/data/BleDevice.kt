package kr.open.library.systemmanager.controller.bluetooth.data

import android.bluetooth.le.ScanResult

/**
 * BLE 디바이스 정보를 담는 데이터 클래스
 */
data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val scanRecord: ByteArray?,
    val isConnectable: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
) {
    
    // 표시용 이름
    val displayName: String
        get() = name ?: "Unknown Device"
    
    // 신호 강도 텍스트
    val signalStrengthText: String
        get() = when {
            rssi > -30 -> "매우 강함"
            rssi > -50 -> "강함"
            rssi > -70 -> "보통"
            rssi > -90 -> "약함"
            else -> "매우 약함"
        }
    
    // Bluetooth 주소 유효성 검사
    val isValidAddress: Boolean
        get() = address.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"))
    
    // 마지막 발견 시간 (분 단위)
    val lastSeenMinutesAgo: Long
        get() = (System.currentTimeMillis() - timestamp) / 60_000
    
    // RSSI를 백분율로 변환 (대략적)
    val signalStrengthPercent: Int
        get() = when {
            rssi >= -30 -> 100
            rssi >= -50 -> 80
            rssi >= -70 -> 60
            rssi >= -90 -> 40
            else -> 20
        }
    
    companion object {
        /**
         * ScanResult에서 BleDevice 생성
         * @param result 스캔 결과
         * @return BleDevice 인스턴스
         */
        fun fromScanResult(result: ScanResult): BleDevice {
            return BleDevice(
                name = result.device.name,
                address = result.device.address,
                rssi = result.rssi,
                scanRecord = result.scanRecord?.bytes,
                isConnectable = result.isConnectable
            )
        }
        
        /**
         * 테스트용 BleDevice 생성
         * @param name 디바이스 이름
         * @param address MAC 주소
         * @param rssi 신호 강도
         * @return BleDevice 인스턴스
         */
        fun createTestDevice(name: String, address: String, rssi: Int = -50): BleDevice {
            return BleDevice(
                name = name,
                address = address,
                rssi = rssi,
                scanRecord = null,
                isConnectable = true
            )
        }
    }
    
    // equals와 hashCode는 address 기준으로 (동일 디바이스 판정)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleDevice) return false
        return address == other.address
    }
    
    override fun hashCode(): Int = address.hashCode()
    
    // scanRecord는 equals/hashCode에서 제외하되, toString에서는 표시하지 않음
    override fun toString(): String {
        return "BleDevice(name='$displayName', address='$address', rssi=$rssi, " +
                "strength='$signalStrengthText', connectable=$isConnectable, " +
                "lastSeen=${lastSeenMinutesAgo}m ago)"
    }
    
    /**
     * 디바이스 정보를 상세 문자열로 반환
     */
    fun toDetailString(): String {
        return buildString {
            appendLine("=== BLE Device Details ===")
            appendLine("Name: $displayName")
            appendLine("Address: $address (${if (isValidAddress) "Valid" else "Invalid"})")
            appendLine("RSSI: $rssi dBm ($signalStrengthText, ${signalStrengthPercent}%)")
            appendLine("Connectable: $isConnectable")
            appendLine("Last Seen: ${lastSeenMinutesAgo} minutes ago")
            appendLine("Scan Record: ${scanRecord?.size ?: 0} bytes")
            appendLine("Timestamp: $timestamp")
        }
    }
    
    /**
     * 디바이스가 특정 이름과 일치하는지 확인 (대소문자 무시)
     */
    fun matchesName(targetName: String?): Boolean {
        if (targetName.isNullOrBlank()) return false
        if (name.isNullOrBlank()) return false
        
        return name.equals(targetName, ignoreCase = true)
    }
    
    /**
     * 디바이스가 연결 가능한 상태인지 확인
     */
    fun canConnect(): Boolean = isConnectable && isValidAddress
    
    /**
     * 신호가 충분히 강한지 확인
     * @param minimumRssi 최소 RSSI 값 (기본값: -80)
     */
    fun hasStrongSignal(minimumRssi: Int = -80): Boolean = rssi >= minimumRssi
    
    /**
     * 최근에 발견된 디바이스인지 확인
     * @param maxMinutesAgo 최대 경과 시간 (분, 기본값: 5분)
     */
    fun isRecentlyFound(maxMinutesAgo: Long = 5): Boolean = lastSeenMinutesAgo <= maxMinutesAgo
}