package kr.open.library.systemmanager.controller.bluetooth.base

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import kr.open.library.logcat.Logx

/**
 * GATT 서비스를 빌드하는 유틸리티 클래스
 */
object BleServiceBuilder {
    
    private const val TAG = "BleServiceBuilder"
    
    /**
     * Nordic UART Service 생성
     * @return 생성된 BluetoothGattService
     */
    fun createNordicUartService(): BluetoothGattService {
        Logx.d(TAG, "Creating Nordic UART Service...")
        
        val service = BluetoothGattService(
            BleConstants.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        
        // TX 특성 생성 (Peripheral → Central, NOTIFY)
        val txCharacteristic = createTxCharacteristic()
        service.addCharacteristic(txCharacteristic)
        
        // RX 특성 생성 (Central → Peripheral, WRITE)
        val rxCharacteristic = createRxCharacteristic()
        service.addCharacteristic(rxCharacteristic)
        
        Logx.i(TAG, "Nordic UART Service created successfully")
        Logx.d(TAG, "Service contains ${service.characteristics.size} characteristics")
        
        return service
    }
    
    /**
     * TX 특성 생성 (Peripheral → Central)
     * NOTIFY 속성으로 데이터를 Central에 전송하는 용도
     */
    private fun createTxCharacteristic(): BluetoothGattCharacteristic {
        val characteristic = BluetoothGattCharacteristic(
            BleConstants.TX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0 // 권한: 읽기 불가 (NOTIFY만 사용)
        )
        
        // CCCD 디스크립터 추가 (알림 활성화용)
        val cccdDescriptor = BluetoothGattDescriptor(
            BleConstants.CLIENT_CHARACTERISTIC_CONFIG,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        
        characteristic.addDescriptor(cccdDescriptor)
        
        Logx.d(TAG, "TX Characteristic created with NOTIFY property")
        return characteristic
    }
    
    /**
     * RX 특성 생성 (Central → Peripheral)
     * WRITE 속성으로 Central에서 데이터를 받는 용도
     */
    private fun createRxCharacteristic(): BluetoothGattCharacteristic {
        val characteristic = BluetoothGattCharacteristic(
            BleConstants.RX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        Logx.d(TAG, "RX Characteristic created with WRITE properties")
        return characteristic
    }
    
    /**
     * 서비스의 특성 정보를 로그로 출력
     * @param service 정보를 출력할 서비스
     */
    fun logServiceInfo(service: BluetoothGattService) {
        Logx.d(TAG, "=== GATT Service Information ===")
        Logx.d(TAG, "Service UUID: ${BleConstants.getShortUuidString(service.uuid)}")
        Logx.d(TAG, "Service Type: ${if (service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY) "PRIMARY" else "SECONDARY"}")
        Logx.d(TAG, "Characteristics Count: ${service.characteristics.size}")
        
        service.characteristics.forEachIndexed { index, characteristic ->
            Logx.d(TAG, "  Characteristic ${index + 1}:")
            Logx.d(TAG, "    UUID: ${BleConstants.getShortUuidString(characteristic.uuid)}")
            Logx.d(TAG, "    Properties: ${getCharacteristicPropertiesString(characteristic.properties)}")
            Logx.d(TAG, "    Permissions: ${getCharacteristicPermissionsString(characteristic.permissions)}")
            Logx.d(TAG, "    Descriptors: ${characteristic.descriptors.size}")
            
            characteristic.descriptors.forEachIndexed { descIndex, descriptor ->
                Logx.d(TAG, "      Descriptor ${descIndex + 1}: ${BleConstants.getShortUuidString(descriptor.uuid)}")
            }
        }
        Logx.d(TAG, "================================")
    }
    
    /**
     * 특성의 속성을 문자열로 변환
     */
    private fun getCharacteristicPropertiesString(properties: Int): String {
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
    
    /**
     * 특성의 권한을 문자열로 변환
     */
    private fun getCharacteristicPermissionsString(permissions: Int): String {
        val perms = mutableListOf<String>()
        
        if (permissions and BluetoothGattCharacteristic.PERMISSION_READ != 0) perms.add("READ")
        if (permissions and BluetoothGattCharacteristic.PERMISSION_WRITE != 0) perms.add("WRITE")
        if (permissions and BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED != 0) perms.add("READ_ENCRYPTED")
        if (permissions and BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED != 0) perms.add("WRITE_ENCRYPTED")
        if (permissions and BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM != 0) perms.add("READ_ENCRYPTED_MITM")
        if (permissions and BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM != 0) perms.add("WRITE_ENCRYPTED_MITM")
        if (permissions and BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED != 0) perms.add("WRITE_SIGNED")
        if (permissions and BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM != 0) perms.add("WRITE_SIGNED_MITM")
        
        return if (perms.isNotEmpty()) perms.joinToString(", ") else "NONE"
    }
    
    /**
     * 특성이 쓰기 가능한지 확인
     */
    fun isWritableCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        val properties = characteristic.properties
        return (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
               (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
    }
    
    /**
     * 특성이 읽기 가능한지 확인
     */
    fun isReadableCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
    }
    
    /**
     * 특성이 알림 가능한지 확인
     */
    fun isNotifiableCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean {
        val properties = characteristic.properties
        return (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) ||
               (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
    }
    
    /**
     * 서비스 검증
     * @param service 검증할 서비스
     * @return 검증 성공 여부
     */
    fun validateService(service: BluetoothGattService): Boolean {
        // 1. 서비스 UUID 확인
        if (service.uuid != BleConstants.SERVICE_UUID) {
            Logx.e(TAG, "Invalid service UUID: ${service.uuid}")
            return false
        }
        
        // 2. 필수 특성 존재 확인
        val txChar = service.getCharacteristic(BleConstants.TX_CHAR_UUID)
        if (txChar == null) {
            Logx.e(TAG, "TX characteristic not found")
            return false
        }
        
        val rxChar = service.getCharacteristic(BleConstants.RX_CHAR_UUID)
        if (rxChar == null) {
            Logx.e(TAG, "RX characteristic not found")
            return false
        }
        
        // 3. TX 특성 속성 확인
        if (!isNotifiableCharacteristic(txChar)) {
            Logx.e(TAG, "TX characteristic is not notifiable")
            return false
        }
        
        // 4. RX 특성 속성 확인
        if (!isWritableCharacteristic(rxChar)) {
            Logx.e(TAG, "RX characteristic is not writable")
            return false
        }
        
        // 5. CCCD 디스크립터 확인
        val cccd = txChar.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG)
        if (cccd == null) {
            Logx.e(TAG, "CCCD descriptor not found on TX characteristic")
            return false
        }
        
        Logx.i(TAG, "Service validation passed")
        return true
    }
}