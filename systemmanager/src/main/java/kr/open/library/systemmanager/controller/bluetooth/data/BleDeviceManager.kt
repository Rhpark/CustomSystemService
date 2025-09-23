package kr.open.library.systemmanager.controller.bluetooth.data

import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * 스캔된 BLE 디바이스들을 관리하는 클래스
 * Thread-safe하게 디바이스 목록을 수집하고 관리
 */
class BleDeviceManager {
    
    // Thread-safe한 디바이스 맵 (MAC 주소를 키로 사용)
    private val scannedDevices = ConcurrentHashMap<String, BleDevice>()
    
    /**
     * 새로 스캔된 디바이스 추가 또는 업데이트
     * 동일한 MAC 주소의 디바이스는 RSSI 등을 업데이트
     */
    fun addOrUpdateDevice(device: BleDevice) {
        val existingDevice = scannedDevices[device.address]
        
        if (existingDevice != null) {
            // 기존 디바이스가 있으면 RSSI와 타임스탬프 업데이트
            val updatedDevice = device.copy(
                timestamp = System.currentTimeMillis()
            )
            scannedDevices[device.address] = updatedDevice
        } else {
            // 새 디바이스 추가
            scannedDevices[device.address] = device
        }
    }
    
    /**
     * 현재 스캔된 모든 디바이스 목록 반환
     * RSSI 순으로 정렬 (강한 신호부터)
     */
    fun getAllScannedDevices(): List<BleDevice> {
        return scannedDevices.values
            .toList()
            .sortedByDescending { it.rssi }
    }
    
    /**
     * 특정 MAC 주소의 디바이스 조회
     */
    fun getDevice(address: String): BleDevice? {
        return scannedDevices[address]
    }
    
    /**
     * 디바이스 제거
     */
    fun removeDevice(address: String): BleDevice? {
        return scannedDevices.remove(address)
    }
    
    /**
     * 모든 디바이스 제거
     */
    fun clearAll() {
        scannedDevices.clear()
    }
    
    /**
     * 오래된 디바이스들 제거 (마지막 발견 시간 기준)
     * @param maxAgeMs 최대 유지 시간 (밀리초)
     */
    fun removeOldDevices(maxAgeMs: Long = 30_000L) {
        val currentTime = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()
        
        scannedDevices.forEach { (address, device) ->
            if (currentTime - device.timestamp > maxAgeMs) {
                toRemove.add(address)
            }
        }
        
        toRemove.forEach { address ->
            scannedDevices.remove(address)
        }
    }
    
    /**
     * 현재 스캔된 디바이스 개수
     */
    fun getDeviceCount(): Int = scannedDevices.size
    
    /**
     * 특정 이름을 가진 디바이스들 필터링
     */
    fun getDevicesByName(name: String): List<BleDevice> {
        return scannedDevices.values
            .filter { device -> 
                device.name?.contains(name, ignoreCase = true) == true ||
                device.displayName.contains(name, ignoreCase = true)
            }
            .sortedByDescending { it.rssi }
    }
    
    /**
     * 연결 가능한 디바이스들만 반환
     */
    fun getConnectableDevices(): List<BleDevice> {
        return scannedDevices.values
            .filter { it.canConnect() }
            .sortedByDescending { it.rssi }
    }
    
    /**
     * 강한 신호의 디바이스들만 반환 (RSSI > threshold)
     */
    fun getStrongSignalDevices(rssiThreshold: Int = -70): List<BleDevice> {
        return scannedDevices.values
            .filter { it.rssi > rssiThreshold }
            .sortedByDescending { it.rssi }
    }
    
    /**
     * 스캔 결과 요약 정보
     */
    fun getScanSummary(): String {
        val totalDevices = scannedDevices.size
        val connectableDevices = scannedDevices.values.count { it.canConnect() }
        val strongSignalDevices = scannedDevices.values.count { it.rssi > -70 }
        
        return buildString {
            appendLine("=== 스캔 결과 요약 ===")
            appendLine("총 발견 디바이스: $totalDevices 개")
            appendLine("연결 가능: $connectableDevices 개")
            appendLine("강한 신호: $strongSignalDevices 개")
            
            if (totalDevices > 0) {
                val bestDevice = scannedDevices.values.maxByOrNull { it.rssi }
                appendLine("최고 신호: ${bestDevice?.displayName} (${bestDevice?.rssi}dBm)")
            }
        }
    }
    
    companion object {
        // 디바이스 만료 시간 (30초)
        const val DEFAULT_DEVICE_EXPIRY_MS = 30_000L
        
        // RSSI 임계값들
        const val RSSI_EXCELLENT = -30
        const val RSSI_GOOD = -50  
        const val RSSI_FAIR = -70
        const val RSSI_POOR = -90
    }
}