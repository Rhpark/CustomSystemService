package kr.open.library.systemmanager.controller.bluetooth.data

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * BLE 기기 정보
 * BLE Device Information
 * 
 * BLE 스캔 결과와 연결된 기기 정보를 통합 관리합니다.
 * Manages BLE scan results and connected device information in an integrated way.
 */
@Parcelize
data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val isConnectable: Boolean,
    val advertisementData: BleAdvertisementData?,
    val deviceType: Int,
    val bondState: Int,
    val lastSeenTime: Long = System.currentTimeMillis()
) : Parcelable {
    
    /**
     * 기기 연결 가능성 확인
     */
    fun canConnect(): Boolean = isConnectable
    
    /**
     * 기기가 페어링된 상태인지 확인
     */
    fun isBonded(): Boolean = bondState == BluetoothDevice.BOND_BONDED
    
    /**
     * RSSI 기반 거리 추정 (미터)
     * Distance estimation based on RSSI (meters)
     */
    fun getEstimatedDistance(): Double {
        // 간단한 RSSI -> 거리 변환 공식 (정확하지 않음, 참고용)
        return if (rssi == 0) {
            -1.0
        } else {
            val ratio = rssi * 1.0 / -59 // -59dBm at 1m
            if (ratio < 1.0) {
                Math.pow(ratio, 10.0)
            } else {
                val accuracy = (0.89976) * Math.pow(ratio, 7.7095) + 0.111
                accuracy
            }
        }.coerceAtLeast(0.1)
    }
    
    /**
     * 신호 강도 품질 반환
     */
    fun getSignalQuality(): SignalQuality = when {
        rssi >= -50 -> SignalQuality.EXCELLENT
        rssi >= -60 -> SignalQuality.GOOD  
        rssi >= -70 -> SignalQuality.FAIR
        rssi >= -80 -> SignalQuality.POOR
        else -> SignalQuality.VERY_POOR
    }
    
    /**
     * 신호 강도 품질 열거형
     */
    enum class SignalQuality {
        EXCELLENT, GOOD, FAIR, POOR, VERY_POOR
    }
    
    companion object {
        /**
         * ScanResult로부터 BleDevice 생성
         */
        fun fromScanResult(scanResult: ScanResult): BleDevice {
            val device = scanResult.device
            val advertisementData = BleAdvertisementData.fromScanRecord(scanResult.scanRecord)
            
            return BleDevice(
                address = device.address,
                name = scanResult.scanRecord?.deviceName ?: device.name,
                rssi = scanResult.rssi,
                isConnectable = scanResult.isConnectable,
                advertisementData = advertisementData,
                deviceType = device.type,
                bondState = device.bondState
            )
        }
        
        /**
         * BluetoothDevice로부터 BleDevice 생성 (연결된 기기용)
         */
        fun fromBluetoothDevice(device: BluetoothDevice, rssi: Int = 0): BleDevice {
            return BleDevice(
                address = device.address,
                name = device.name,
                rssi = rssi,
                isConnectable = true,
                advertisementData = null,
                deviceType = device.type,
                bondState = device.bondState
            )
        }
    }
    
    override fun toString(): String {
        return "BleDevice(address='$address', name='$name', rssi=$rssi, quality=${getSignalQuality()})"
    }
}

/**
 * BLE 광고 데이터
 * BLE Advertisement Data
 */
@Parcelize
data class BleAdvertisementData(
    val localName: String?,
    val serviceUuids: List<String>,
    val serviceData: Map<String, ByteArray>,
    val manufacturerData: Map<Int, ByteArray>,
    val txPowerLevel: Int?,
    val isLimitedDiscoverable: Boolean,
    val isGeneralDiscoverable: Boolean,
    val rawData: ByteArray?
) : Parcelable {
    
    /**
     * 제조사 데이터에서 특정 회사 정보 추출
     */
    fun getManufacturerData(companyId: Int): ByteArray? {
        return manufacturerData[companyId]
    }
    
    /**
     * 서비스 데이터에서 특정 서비스 정보 추출
     */
    fun getServiceData(serviceUuid: String): ByteArray? {
        return serviceData[serviceUuid.uppercase()]
    }
    
    /**
     * Apple 기기 여부 확인 (회사 ID: 0x004C)
     */
    fun isAppleDevice(): Boolean {
        return manufacturerData.containsKey(0x004C)
    }
    
    /**
     * Samsung 기기 여부 확인 (회사 ID: 0x0075)
     */
    fun isSamsungDevice(): Boolean {
        return manufacturerData.containsKey(0x0075)
    }
    
    companion object {
        /**
         * ScanRecord로부터 광고 데이터 생성
         */
        fun fromScanRecord(scanRecord: android.bluetooth.le.ScanRecord?): BleAdvertisementData? {
            if (scanRecord == null) return null
            
            val serviceUuids = scanRecord.serviceUuids?.map { it.toString() } ?: emptyList()
            val serviceData = mutableMapOf<String, ByteArray>()
            val manufacturerData = mutableMapOf<Int, ByteArray>()
            
            // 서비스 데이터 변환
            scanRecord.serviceData?.forEach { (uuid, data) ->
                serviceData[uuid.toString().uppercase()] = data
            }
            
            // 제조사 데이터 변환 (MinSdk 28이므로 항상 사용 가능)
            scanRecord.manufacturerSpecificData?.let { sparseArray ->
                for (i in 0 until sparseArray.size()) {
                    val key = sparseArray.keyAt(i)
                    val value = sparseArray.valueAt(i)
                    manufacturerData[key] = value
                }
            }
            
            return BleAdvertisementData(
                localName = scanRecord.deviceName,
                serviceUuids = serviceUuids,
                serviceData = serviceData,
                manufacturerData = manufacturerData,
                txPowerLevel = scanRecord.txPowerLevel.takeIf { it != Int.MIN_VALUE },
                isLimitedDiscoverable = scanRecord.advertiseFlags and 0x01 != 0,
                isGeneralDiscoverable = scanRecord.advertiseFlags and 0x02 != 0,
                rawData = scanRecord.bytes
            )
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as BleAdvertisementData
        
        return localName == other.localName &&
               serviceUuids == other.serviceUuids &&
               serviceData == other.serviceData &&
               manufacturerData == other.manufacturerData &&
               txPowerLevel == other.txPowerLevel &&
               isLimitedDiscoverable == other.isLimitedDiscoverable &&
               isGeneralDiscoverable == other.isGeneralDiscoverable &&
               rawData?.contentEquals(other.rawData) == true
    }
    
    override fun hashCode(): Int {
        var result = localName?.hashCode() ?: 0
        result = 31 * result + serviceUuids.hashCode()
        result = 31 * result + serviceData.hashCode()
        result = 31 * result + manufacturerData.hashCode()
        result = 31 * result + (txPowerLevel ?: 0)
        result = 31 * result + isLimitedDiscoverable.hashCode()
        result = 31 * result + isGeneralDiscoverable.hashCode()
        result = 31 * result + (rawData?.contentHashCode() ?: 0)
        return result
    }
}