package kr.open.library.systemmanager.controller.bluetooth.peripheral

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.base.BleComponent
import kr.open.library.systemmanager.controller.bluetooth.base.BleConstants
import kr.open.library.systemmanager.controller.bluetooth.base.BleServiceBuilder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BLE 광고자 - Peripheral 모드에서 서비스 제공
 * 단순화된 콜백 기반 1:1 전용 광고자
 */
class BleAdvertiser(context: Context) : BleComponent(context) {
    
    // 콜백 인터페이스
    interface AdvertiseListener {
        fun onAdvertiseStarted()
        fun onAdvertiseStopped()
        fun onConnectionReceived(deviceAddress: String)
        fun onAdvertiseError(error: String)
    }
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothManager: BluetoothManager? = null
    private var gattServer: BluetoothGattServer? = null
    private var currentListener: AdvertiseListener? = null
    private var deviceName: String? = null
    private var isConnectionAccepted = false
    private var currentConnectedDevice: String? = null
    
    // 단순한 상태 관리
    private val isAdvertising = AtomicBoolean(false)
    
    // 단순한 광고 콜백
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Logx.i(TAG, "🎉🎉🎉 ADVERTISING STARTED SUCCESSFULLY! 🎉🎉🎉")
            isAdvertising.set(true)
            currentListener?.onAdvertiseStarted()
        }

        override fun onStartFailure(errorCode: Int) {
            val errorMsg = getAdvertiseErrorString(errorCode)
            Logx.e(TAG, "❌❌❌ ADVERTISING FAILED: $errorMsg (code: $errorCode) ❌❌❌")
            isAdvertising.set(false)
            currentListener?.onAdvertiseError(errorMsg)
        }
    }
    
    // 단순한 GATT 서버 콜백
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (isConnectionAccepted) {
                        // 이미 연결된 디바이스가 있으면 새 연결 거부
                        Logx.i(TAG, "Rejecting additional connection from ${device.address}")
                        try {
                            gattServer?.cancelConnection(device)
                        } catch (e: Exception) {
                            Logx.w(TAG, "Error rejecting connection: ${e.message}")
                        }
                    } else {
                        // 첫 번째 연결 수락
                        Logx.i(TAG, "Accepting connection from ${device.address}")
                        isConnectionAccepted = true
                        currentConnectedDevice = device.address
                        
                        // 즉시 광고 중지
                        stopAdvertisingInternal()
                        
                        currentListener?.onConnectionReceived(device.address)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (device.address == currentConnectedDevice) {
                        Logx.i(TAG, "Device disconnected: ${device.address}")
                        isConnectionAccepted = false
                        currentConnectedDevice = null
                    }
                }
            }
        }
        
        override fun onServiceAdded(status: Int, service: android.bluetooth.BluetoothGattService) {
            if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                Logx.i(TAG, "Service added successfully: ${BleConstants.getShortUuidString(service.uuid)}")
            } else {
                Logx.e(TAG, "Failed to add service: ${BleConstants.getGattStatusString(status)}")
            }
        }
    }
    
    override suspend fun initialize(): Boolean {
        Logx.d(TAG, "Initializing BleAdvertiser...")
        
        if (!checkAllRequiredPermissions()) {
            val deniedPermissions = getDeniedPermissionList()
            Logx.e(TAG, "Required permissions not granted: $deniedPermissions")
            return false
        }
        Logx.d(TAG, "All BLE permissions granted")
        
        // Android 12+ 권한 재확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val advertisePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
            val connectPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            Logx.d(TAG, "BLUETOOTH_ADVERTISE permission: ${if (advertisePermission == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
            Logx.d(TAG, "BLUETOOTH_CONNECT permission: ${if (connectPermission == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
            
            if (advertisePermission != PackageManager.PERMISSION_GRANTED) {
                Logx.e(TAG, "BLUETOOTH_ADVERTISE permission required for Android 12+")
                return false
            }
            if (connectPermission != PackageManager.PERMISSION_GRANTED) {
                Logx.e(TAG, "BLUETOOTH_CONNECT permission required for Android 12+")
                return false
            }
        }
        
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager == null) {
            Logx.e(TAG, "BluetoothManager not available")
            return false
        }
        
        bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null) {
            Logx.e(TAG, "BluetoothAdapter not available")
            return false
        }
        
        if (bluetoothAdapter?.isEnabled != true) {
            Logx.e(TAG, "Bluetooth is disabled")
            return false
        }
        
        if (!bluetoothAdapter!!.isMultipleAdvertisementSupported) {
            Logx.e(TAG, "BLE advertising not supported")
            return false
        }
        
        bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Logx.e(TAG, "BluetoothLeAdvertiser not available")
            return false
        }
        
        // 기존 GATT 서버 완전 정리 (Too many register gatt interface 방지)
        cleanupGattResources()
        
        // 잠시 대기 (Android 시스템이 리소스 정리할 시간 제공)
        try {
            Thread.sleep(100)
        } catch (e: InterruptedException) {
            // 무시
        }
        
        Logx.d(TAG, "Creating new GATT server...")
        
        // GATT 서버 생성
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
        if (gattServer == null) {
            Logx.e(TAG, "Failed to create GATT server")
            return false
        }
        
        // Nordic UART Service 추가
        val nordicService = BleServiceBuilder.createNordicUartService()
        val serviceAdded = gattServer?.addService(nordicService) ?: false
        if (!serviceAdded) {
            Logx.e(TAG, "Failed to add Nordic UART service")
            return false
        }
        
        Logx.i(TAG, "BleAdvertiser initialized successfully")
        return true
    }
    
    /**
     * 컴포넌트 준비 상태 확인
     */
    private fun isComponentReady(): Boolean {
        return bluetoothAdapter?.isEnabled == true &&
               bluetoothLeAdvertiser != null &&
               gattServer != null &&
               checkAllRequiredPermissions()
    }

    // 🔧 호환성: getReadyState() 메서드 오버라이드
    override fun getReadyState(): Boolean = isComponentReady()
    
    internal override suspend fun cleanupGattResources() {
        Logx.d(TAG, "Cleaning up GATT server resources...")
        
        try {
            gattServer?.let { server ->
                // 모든 연결 해제
                if (currentConnectedDevice != null) {
                    val device = bluetoothAdapter?.getRemoteDevice(currentConnectedDevice)
                    device?.let { server.cancelConnection(it) }
                }
                
                // 서비스 제거
                server.clearServices()
                
                // 서버 종료
                server.close()
                
                Logx.d(TAG, "GATT server closed successfully")
            }
        } catch (e: SecurityException) {
            Logx.w(TAG, "SecurityException during GATT cleanup: ${e.message}")
        } catch (e: Exception) {
            Logx.w(TAG, "Exception during GATT cleanup: ${e.message}")
        } finally {
            gattServer = null
            isConnectionAccepted = false
            currentConnectedDevice = null
        }
    }
    
    override suspend fun cleanup() {
        Logx.d(TAG, "Cleaning up BleAdvertiser...")
        stopAdvertising()
        cleanupGattResources()
        currentListener = null
        super.cleanup()
    }
    
    /**
     * 광고 시작
     * @param deviceName 광고할 디바이스 이름
     * @param listener 광고 이벤트 리스너
     */
    fun startAdvertising(deviceName: String, listener: AdvertiseListener? = null) {
        Logx.d(TAG, "🚀🚀🚀 BleAdvertiser.startAdvertising() CALLED")
        Logx.d(TAG, "🚀 Device name: '$deviceName'")
        Logx.d(TAG, "🚀 Listener: ${if (listener != null) "SET" else "NULL"}")

        // 🔧 호환성 문제 해결을 위해 synchronized 대신 단순 체크
        if (isAdvertising.get()) {
            Logx.w(TAG, "🚀 Advertising already in progress")
            return
        }

        Logx.d(TAG, "🚀 Checking if component is ready...")
        if (!isComponentReady()) {
            val error = "Advertiser not ready"
            Logx.e(TAG, "🚀 ERROR: $error")
            listener?.onAdvertiseError(error)
            return
        }
        Logx.d(TAG, "🚀 All checks passed - starting advertising...")

        if (deviceName.length > BleConstants.MAX_DEVICE_NAME_LENGTH) {
            val error = "Device name too long: ${deviceName.length} > ${BleConstants.MAX_DEVICE_NAME_LENGTH}"
            Logx.e(TAG, error)
            listener?.onAdvertiseError(error)
            return
        }

        this.deviceName = deviceName
        this.currentListener = listener

        Logx.d(TAG, "🚀 Starting advertising as: '$deviceName'")

        // 🔧 디바이스 이름 설정 개선 (로그 강화)
        try {
            val oldName = bluetoothAdapter?.name
            bluetoothAdapter?.name = deviceName
            Logx.d(TAG, "📡 Adapter 이름: '$oldName' → '$deviceName'")
        } catch (e: SecurityException) {
            Logx.w(TAG, "🚀 어댑터 이름 설정 실패: ${e.message}")
        }
        Logx.d(TAG, "🚀 Creating advertise settings...")
        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(BleConstants.ADVERTISE_MODE)
            .setTxPowerLevel(BleConstants.ADVERTISE_TX_POWER)
            .setConnectable(true)
            .setTimeout(BleConstants.ADVERTISE_TIMEOUT)
            .build()

        Logx.d(TAG, "🚀 Creating advertise data...")
        // 🔧 광고 데이터 최적화 (31바이트 제한 해결)
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)  // 디바이스 이름만 포함 (8바이트)
            .setIncludeTxPowerLevel(true) // TX Power 포함 (3바이트)
            // Service UUID 제거 → 스캔 응답으로 이동
            .build()

        // 스캔 응답에 Service UUID 포함 (31바이트 별도 제한)
        val scanResponseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID)) // Service UUID는 여기에
            .build()

        Logx.d(TAG, "📡 광고 데이터 설정 완료")
        Logx.d(TAG, "🔧 Primary 광고 데이터: 이름 + TxPower (~11바이트)")
        Logx.d(TAG, "🔧 스캔 응답 데이터: Service UUID (~19바이트)")

        try {
            Logx.d(TAG, "🚀 Calling bluetoothLeAdvertiser.startAdvertising()...")
            bluetoothLeAdvertiser?.startAdvertising(
                advertiseSettings,
                advertiseData,
                scanResponseData,
                advertiseCallback
            )
            Logx.d(TAG, "🚀 startAdvertising() call completed - waiting for callback...")

        } catch (e: SecurityException) {
            val error = "Permission denied: ${e.message}"
            Logx.e(TAG, "🚀 SECURITY EXCEPTION: $error")
            listener?.onAdvertiseError(error)
        } catch (e: Exception) {
            val error = "Advertising failed: ${e.message}"
            Logx.e(TAG, "🚀 EXCEPTION in startAdvertising: $error")
            listener?.onAdvertiseError(error)
        }
    }
    
    /**
     * 광고 중지
     */
    fun stopAdvertising() {
        stopAdvertisingInternal()
    }
    
    /**
     * 내부 광고 중지
     */
    private fun stopAdvertisingInternal() {
        // 🔧 호환성 문제 해결을 위해 synchronized 대신 단순 체크
        if (!isAdvertising.get()) {
            return
        }

        Logx.d(TAG, "🚀 Stopping advertising...")

        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: SecurityException) {
            Logx.w(TAG, "🚀 Security exception during advertising stop: ${e.message}")
        } catch (e: Exception) {
            Logx.w(TAG, "🚀 Exception during advertising stop: ${e.message}")
        } finally {
            isAdvertising.set(false)
            currentListener?.onAdvertiseStopped()
            Logx.d(TAG, "🚀 Advertising stopped")
        }
    }
    
    /**
     * 광고 중 여부 확인
     */
    fun isAdvertising(): Boolean = isAdvertising.get()
    
    /**
     * 연결 수락 여부 확인
     */
    fun isConnectionAccepted(): Boolean = isConnectionAccepted
    
    /**
     * 현재 연결된 디바이스 주소
     */
    fun getConnectedDevice(): String? = currentConnectedDevice
    
    /**
     * 연결을 강제로 해제
     */
    fun disconnectDevice(deviceAddress: String) {
        Logx.d(TAG, "Disconnecting device: $deviceAddress")
        
        try {
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            device?.let { gattServer?.cancelConnection(it) }
        } catch (e: Exception) {
            Logx.w(TAG, "Error disconnecting device: ${e.message}")
        }
    }
    
    /**
     * 광고 에러 코드를 문자열로 변환
     */
    private fun getAdvertiseErrorString(errorCode: Int): String {
        return when (errorCode) {
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
            else -> "Unknown error ($errorCode)"
        }
    }
}