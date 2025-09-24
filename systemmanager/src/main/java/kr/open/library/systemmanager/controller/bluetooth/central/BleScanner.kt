package kr.open.library.systemmanager.controller.bluetooth.central

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.base.BleComponent
import kr.open.library.systemmanager.controller.bluetooth.base.BleConstants
import kr.open.library.systemmanager.controller.bluetooth.data.BleDevice
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BLE 스캐너 - Central 모드에서 디바이스 검색
 * 단순화된 콜백 기반 1:1 전용 스캐너
 */
class BleScanner(context: Context) : BleComponent(context) {
    
    // 콜백 인터페이스
    interface ScanListener {
        fun onDeviceFound(device: BleDevice)
        fun onScanStarted()
        fun onScanStopped()
        fun onScanError(error: String)
    }
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var currentListener: ScanListener? = null
    private var targetDeviceName: String? = null
    private var isScanOnlyMode = false
    
    // 단순한 상태 관리
    private val isScanning = AtomicBoolean(false)
    
    // 단순한 스캔 콜백 - 🔧 강화된 디버깅 로그
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = BleDevice.fromScanResult(result)

            // 🔍 모든 발견된 디바이스 로그 출력
            Logx.d(TAG, "📱 DEVICE DISCOVERED:")
            Logx.d(TAG, "   이름: '${device.name}' (표시명: '${device.displayName}')")
            Logx.d(TAG, "   주소: ${device.address}")
            Logx.d(TAG, "   RSSI: ${device.rssi}dBm (${device.signalStrengthText})")
            Logx.d(TAG, "   연결가능: ${device.isConnectable}")
            Logx.d(TAG, "   타겟: '$targetDeviceName'")
            Logx.d(TAG, "   모드: ${if (isScanOnlyMode) "SCAN_ONLY" else "AUTO_CONNECT"}")

            try {
                if (isScanOnlyMode) {
                    Logx.d(TAG, "📡 Scan-only mode: 모든 디바이스 리포트")
                    currentListener?.onDeviceFound(device)
                } else {
                    // 자동 연결 모드: 필터링 적용 + 상세 로그
                    if (shouldAcceptDevice(device)) {
                        Logx.i(TAG, "✅ TARGET FOUND: ${device.displayName} - stopping scan immediately")
                        stopScanInternal()
                        currentListener?.onDeviceFound(device)
                    } else {
                        Logx.d(TAG, "❌ 타겟 아님: 계속 스캔")
                    }
                }
            } catch (e: Exception) {
                Logx.e(TAG, "Error processing scan result: ${e.message}")
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            val errorMsg = getScanErrorString(errorCode)
            Logx.e(TAG, "❌ SCAN FAILED: $errorMsg (code: $errorCode)")

            isScanning.set(false)
            currentListener?.onScanError(errorMsg)
        }
    }
    
    override suspend fun initialize(): Boolean {
        Logx.d(TAG, "Initializing BleScanner...")
        
        if (!checkAllRequiredPermissions()) {
            val deniedPermissions = getDeniedPermissionList()
            Logx.e(TAG, "Required permissions not granted: $deniedPermissions")
            return false
        }
        Logx.d(TAG, "All BLE permissions granted")
        
        // Android 12+ 권한 재확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            Logx.d(TAG, "BLUETOOTH_CONNECT permission: ${if (connectPermission == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"}")
            
            if (connectPermission != PackageManager.PERMISSION_GRANTED) {
                Logx.e(TAG, "BLUETOOTH_CONNECT permission required for Android 12+")
                return false
            }
        }
        
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager == null) {
            Logx.e(TAG, "BluetoothManager not available")
            return false
        }
        
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Logx.e(TAG, "BluetoothAdapter not available")
            return false
        }
        
        // 자세한 블루투스 상태 디버깅
        try {
            val isEnabled = bluetoothAdapter?.isEnabled
            Logx.d(TAG, "BluetoothAdapter.isEnabled = $isEnabled")
            
            if (isEnabled != true) {
                Logx.e(TAG, "Bluetooth is disabled (isEnabled = $isEnabled)")
                return false
            }
        } catch (e: SecurityException) {
            Logx.e(TAG, "SecurityException checking bluetooth state: ${e.message}")
            return false
        } catch (e: Exception) {
            Logx.e(TAG, "Exception checking bluetooth state: ${e.message}")
            return false
        }
        
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bluetoothLeScanner == null) {
            Logx.e(TAG, "BluetoothLeScanner not available")
            return false
        }
        
        Logx.i(TAG, "BleScanner initialized successfully")
        return true
    }
    
    internal override suspend fun cleanupGattResources() {
        Logx.d(TAG, "Cleaning up scanner GATT resources...")
        
        // 진행중인 스캔이 있으면 강제 중지
        try {
            if (isScanning.get()) {
                bluetoothLeScanner?.stopScan(scanCallback)
                isScanning.set(false)
                Logx.d(TAG, "Forced scan stop during cleanup")
            }
        } catch (e: Exception) {
            Logx.w(TAG, "Error stopping scan during cleanup: ${e.message}")
        }
        
        // 리스너, 타겟 초기화
        currentListener = null
        targetDeviceName = null
        
        // 시스템에 정리 시간 제공
        delay(50)
        
        Logx.d(TAG, "Scanner GATT resources cleaned")
    }
    
    override suspend fun cleanup() {
        Logx.d(TAG, "Cleaning up BleScanner...")
        stopScan()
        currentListener = null
        super.cleanup()
    }
    
    /**
     * 스캔 시작
     * @param targetDeviceName 목표 디바이스 이름 (null이면 모든 디바이스)
     * @param listener 스캔 이벤트 리스너
     */
    fun startScan(targetDeviceName: String? = null, listener: ScanListener? = null) {
        Logx.d(TAG, "🔍 startScan() called with target: '$targetDeviceName'")
        isScanOnlyMode = false
        // 🔧 서비스 필터 강제 비활성화 - 모든 디바이스 발견하도록
        startScanInternal(targetDeviceName, listener, useServiceFilter = false)
        Logx.d(TAG, "🔧 FIXED: Service filter forced to FALSE")
    }
    
    /**
     * 스캔 전용 시작 (서비스 필터 없이 모든 디바이스 스캔)
     * @param listener 스캔 이벤트 리스너
     */
    fun startScanOnly(listener: ScanListener? = null) {
        Logx.d(TAG, "🎯 startScanOnly() called")
        Logx.d(TAG, "🔧 Setting scan-only mode: no filters, all devices")
        isScanOnlyMode = true
        startScanInternal(targetDeviceName = null, listener = listener, useServiceFilter = false)
    }
    
    /**
     * 내부 스캔 시작 메서드
     */
    private fun startScanInternal(targetDeviceName: String?, listener: ScanListener?, useServiceFilter: Boolean) {
        synchronized(stateLock) {
            Logx.d(TAG, "📡 startScanInternal() called:")
            Logx.d(TAG, "   Target: '$targetDeviceName'")
            Logx.d(TAG, "   UseServiceFilter: $useServiceFilter")
            Logx.d(TAG, "   ScanOnlyMode: $isScanOnlyMode")

            if (isScanning.get()) {
                Logx.w(TAG, "Scan already in progress")
                return
            }

            if (!isComponentReady()) {
                val error = "Scanner not ready"
                Logx.e(TAG, error)
                listener?.onScanError(error)
                return
            }

            this@BleScanner.targetDeviceName = targetDeviceName
            this@BleScanner.currentListener = listener
            Logx.d(TAG, "⚙️ Listener set: ${if (listener != null) "SET" else "NULL"}")

            Logx.d(TAG, "Starting scan for device: ${targetDeviceName ?: "any"} (service filter: $useServiceFilter)")
            
            val scanSettings = ScanSettings.Builder()
                .setScanMode(BleConstants.SCAN_MODE)
                .setCallbackType(BleConstants.SCAN_CALLBACK_TYPE)
                .setReportDelay(0)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                        setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                    }
                }
                .build()
            
            // 🔧 실제 필터 적용 로직 확실하게 수정
            val scanFilters = if (useServiceFilter) {
                Logx.d(TAG, "📡 Using service filter: ${BleConstants.SERVICE_UUID}")
                listOf(
                    ScanFilter.Builder()
                        .setServiceUuid(android.os.ParcelUuid(BleConstants.SERVICE_UUID))
                        .build()
                )
            } else {
                Logx.d(TAG, "📡 No service filter - scanning ALL devices")
                emptyList()  // 🔧 빈 리스트 = 모든 디바이스 스캔
            }

            Logx.d(TAG, "📡 Final filter list size: ${scanFilters.size}")
            
            try {
                bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
                isScanning.set(true)
                
                Logx.d(TAG, "Scan started successfully")
                listener?.onScanStarted()
                
            } catch (e: SecurityException) {
                val error = "Permission denied: ${e.message}"
                Logx.e(TAG, "Security exception during scan start: ${e.message}")
                listener?.onScanError(error)
            } catch (e: Exception) {
                val error = "Scan failed: ${e.message}"
                Logx.e(TAG, "Unexpected error during scan start: ${e.message}")
                listener?.onScanError(error)
            }
        }
    }
    
    /**
     * 스캔 중지
     */
    fun stopScan() {
        stopScanInternal()
    }
    
    /**
     * 내부 스캔 중지
     */
    private fun stopScanInternal() {
        synchronized(stateLock) {
            if (!isScanning.get()) {
                return
            }
            
            Logx.d(TAG, "Stopping scan...")
            
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
            } catch (e: SecurityException) {
                Logx.w(TAG, "Security exception during scan stop: ${e.message}")
            } catch (e: Exception) {
                Logx.w(TAG, "Exception during scan stop: ${e.message}")
            } finally {
                isScanning.set(false)
                currentListener?.onScanStopped()
                Logx.d(TAG, "Scan stopped")
            }
        }
    }
    
    /**
     * 컴포넌트 준비 상태 확인
     */
    private fun isComponentReady(): Boolean {
        return bluetoothAdapter != null &&
               bluetoothLeScanner != null &&
               bluetoothAdapter?.isEnabled == true
    }

    // 🔧 호환성: getReadyState() 메서드 오버라이드
    override fun getReadyState(): Boolean = isComponentReady()
    
    /**
     * 스캔 중인지 확인
     */
    fun isScanning(): Boolean = isScanning.get()
    
    /**
     * 디바이스가 타겟에 해당하는지 확인 - 🔧 필터링 단계별 상세 로그
     */
    private fun shouldAcceptDevice(device: BleDevice): Boolean {
        Logx.d(TAG, "🔍 shouldAcceptDevice() 체크 시작: ${device.displayName}")

        // 1. 유효한 주소 확인
        if (!device.isValidAddress) {
            Logx.d(TAG, "❌ 거부됨: 잘못된 주소 (${device.address})")
            return false
        }
        Logx.d(TAG, "✅ 주소 유효: ${device.address}")

        // 2. 연결 가능한 디바이스만
        if (!device.isConnectable) {
            Logx.d(TAG, "❌ 거부됨: 연결 불가능 (${device.displayName})")
            return false
        }
        Logx.d(TAG, "✅ 연결 가능: ${device.displayName}")

        // 3. 신호 강도 확인 (최소 -90dBm로 완화)
        if (device.rssi < -90) {
            Logx.d(TAG, "❌ 거부됨: 신호 약함 (${device.rssi}dBm < -90dBm)")
            return false
        }
        Logx.d(TAG, "✅ 신호 강도 양호: ${device.rssi}dBm (${device.signalStrengthText})")

        // 4. 타겟 디바이스 이름 매칭
        val target = targetDeviceName
        if (target != null) {
            val deviceName = device.name ?: ""
            val matches = deviceName.contains(target, ignoreCase = true)
            Logx.d(TAG, "🔍 이름 매칭: '$deviceName' contains '$target' = $matches")
            if (!matches) {
                Logx.d(TAG, "❌ 거부됨: 이름 불일치 ('$deviceName' != '$target')")
                return false
            }
        }
        Logx.d(TAG, "✅ 이름 매칭 성공")

        Logx.d(TAG, "✅ 디바이스 수락됨: ${device.displayName} (${device.signalStrengthText})")
        return true
    }
    
    /**
     * 스캔 에러 코드를 문자열로 변환
     */
    private fun getScanErrorString(errorCode: Int): String {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
            else -> "Unknown error ($errorCode)"
        }
    }
}