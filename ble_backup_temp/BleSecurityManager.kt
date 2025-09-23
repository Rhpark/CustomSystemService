package kr.open.library.systemmanager.controller.bluetooth.base

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.error.BleServiceError
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import kotlin.random.Random

/**
 * BLE 보안 관리자
 * BLE Security Manager
 * 
 * BLE 통신의 보안을 관리하며 페어링, 본딩, 암호화 등의 기능을 제공합니다.
 * Manages BLE communication security including pairing, bonding, and encryption.
 * 
 * 주요 기능:
 * Key features:
 * - 기기 페어링/본딩 관리
 * - 데이터 암호화/복호화
 * - 보안 수준 검증
 * - 신뢰할 수 있는 기기 관리
 */
class BleSecurityManager(private val context: Context) {
    
    private val TAG = "BleSecurityManager"
    
    /**
     * 보안 수준
     */
    enum class SecurityLevel {
        NONE,           // 보안 없음
        AUTHENTICATION, // 인증만
        ENCRYPTION,     // 암호화
        HIGH           // 높은 수준 (인증 + 암호화)
    }
    
    /**
     * 페어링 상태
     */
    enum class PairingState {
        NOT_PAIRED,     // 페어링되지 않음
        PAIRING,        // 페어링 중
        PAIRED,         // 페어링됨
        BONDED         // 본딩됨
    }
    
    /**
     * 보안 이벤트
     */
    enum class SecurityEvent {
        PAIRING_REQUEST,        // 페어링 요청
        PAIRING_SUCCESS,        // 페어링 성공
        PAIRING_FAILED,         // 페어링 실패
        BOND_STATE_CHANGED,     // 본딩 상태 변경
        SECURITY_VIOLATION      // 보안 위반
    }
    
    /**
     * 보안 이벤트 리스너
     */
    interface SecurityEventListener {
        fun onSecurityEvent(event: SecurityEvent, deviceAddress: String, data: Any? = null)
    }
    
    /**
     * 신뢰할 수 있는 기기 정보
     */
    data class TrustedDevice(
        val address: String,
        val name: String?,
        val securityLevel: SecurityLevel,
        val bondState: PairingState,
        val firstPairedTime: Long,
        val lastConnectedTime: Long,
        val connectionCount: Int = 0,
        val encryptionKey: SecretKey? = null
    )
    
    private val listeners = CopyOnWriteArrayList<SecurityEventListener>()
    private val trustedDevices = ConcurrentHashMap<String, TrustedDevice>()
    private val deviceSecretKeys = ConcurrentHashMap<String, SecretKey>()
    
    /**
     * 페어링 상태 변화 수신기
     */
    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    
                    val previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    
                    device?.let { handleBondStateChange(it, previousBondState, bondState) }
                }
                
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    
                    device?.let { handlePairingRequest(it, intent) }
                }
            }
        }
    }
    
    /**
     * 초기화
     */
    fun initialize() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
        }
        
        context.registerReceiver(pairingReceiver, filter)
        
        // 이미 페어링된 기기들 로드
        loadBondedDevices()
        
        Logx.i(TAG, "BleSecurityManager initialized")
    }
    
    /**
     * 보안 이벤트 리스너 등록
     */
    fun addSecurityEventListener(listener: SecurityEventListener) {
        listeners.addIfAbsent(listener)
    }
    
    /**
     * 보안 이벤트 리스너 해제
     */
    fun removeSecurityEventListener(listener: SecurityEventListener) {
        listeners.remove(listener)
    }
    
    /**
     * 기기와의 페어링 요청
     */
    fun requestPairing(device: BluetoothDevice, requiredSecurityLevel: SecurityLevel = SecurityLevel.ENCRYPTION): Result<Boolean> {
        return try {
            // 이미 본딩된 기기인지 확인
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                updateTrustedDevice(device, PairingState.BONDED, requiredSecurityLevel)
                Result.success(true)
            } else {
                // 페어링 시작
                val success = device.createBond()
                if (success) {
                    updateTrustedDevice(device, PairingState.PAIRING, requiredSecurityLevel)
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to start pairing with ${device.address}"))
                }
            }
        } catch (e: SecurityException) {
            val error = BleServiceError.Permission.NotGranted(listOf("BLUETOOTH_CONNECT"))
            Result.failure(Exception("Pairing permission denied", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 기기와의 페어링 해제
     */
    fun unpairDevice(deviceAddress: String): Result<Boolean> {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            val device = adapter?.getRemoteDevice(deviceAddress)
                ?: return Result.failure(Exception("Device not found: $deviceAddress"))
            
            // 본딩 해제를 위한 리플렉션 사용 (공식 API 없음)
            val removeBondMethod = device.javaClass.getMethod("removeBond")
            val success = removeBondMethod.invoke(device) as Boolean
            
            if (success) {
                trustedDevices.remove(deviceAddress)
                deviceSecretKeys.remove(deviceAddress)
                
                notifySecurityEvent(SecurityEvent.BOND_STATE_CHANGED, deviceAddress, PairingState.NOT_PAIRED)
                Logx.i(TAG, "Device unpaired: $deviceAddress")
            }
            
            Result.success(success)
        } catch (e: Exception) {
            Logx.e(TAG, "Failed to unpair device: $deviceAddress", e)
            Result.failure(e)
        }
    }
    
    /**
     * 데이터 암호화
     */
    fun encryptData(deviceAddress: String, data: ByteArray): Result<ByteArray> {
        return try {
            val secretKey = getOrCreateSecretKey(deviceAddress)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            
            // IV 생성
            val iv = ByteArray(16)
            Random.Default.nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            val encryptedData = cipher.doFinal(data)
            
            // IV와 암호화된 데이터 결합
            val result = iv + encryptedData
            
            Logx.d(TAG, "Data encrypted for device: $deviceAddress (${data.size} -> ${result.size} bytes)")
            Result.success(result)
        } catch (e: Exception) {
            Logx.e(TAG, "Failed to encrypt data for device: $deviceAddress", e)
            Result.failure(e)
        }
    }
    
    /**
     * 데이터 복호화
     */
    fun decryptData(deviceAddress: String, encryptedData: ByteArray): Result<ByteArray> {
        return try {
            if (encryptedData.size < 16) {
                return Result.failure(Exception("Invalid encrypted data size"))
            }
            
            val secretKey = deviceSecretKeys[deviceAddress]
                ?: return Result.failure(Exception("No encryption key found for device: $deviceAddress"))
            
            // IV와 암호화된 데이터 분리
            val iv = encryptedData.sliceArray(0..15)
            val actualEncryptedData = encryptedData.sliceArray(16 until encryptedData.size)
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            
            val decryptedData = cipher.doFinal(actualEncryptedData)
            
            Logx.d(TAG, "Data decrypted for device: $deviceAddress (${encryptedData.size} -> ${decryptedData.size} bytes)")
            Result.success(decryptedData)
        } catch (e: Exception) {
            Logx.e(TAG, "Failed to decrypt data for device: $deviceAddress", e)
            Result.failure(e)
        }
    }
    
    /**
     * 기기의 보안 수준 확인
     */
    fun getDeviceSecurityLevel(deviceAddress: String): SecurityLevel {
        val trustedDevice = trustedDevices[deviceAddress] ?: return SecurityLevel.NONE
        return trustedDevice.securityLevel
    }
    
    /**
     * 기기가 신뢰할 수 있는지 확인
     */
    fun isTrustedDevice(deviceAddress: String): Boolean {
        val trustedDevice = trustedDevices[deviceAddress] ?: return false
        return trustedDevice.bondState == PairingState.BONDED || trustedDevice.bondState == PairingState.PAIRED
    }
    
    /**
     * 신뢰할 수 있는 기기 목록 반환
     */
    fun getTrustedDevices(): List<TrustedDevice> {
        return trustedDevices.values.toList()
    }
    
    /**
     * 기기 연결 기록 업데이트
     */
    fun updateConnectionRecord(deviceAddress: String) {
        trustedDevices[deviceAddress]?.let { device ->
            val updatedDevice = device.copy(
                lastConnectedTime = System.currentTimeMillis(),
                connectionCount = device.connectionCount + 1
            )
            trustedDevices[deviceAddress] = updatedDevice
        }
    }
    
    /**
     * 보안 통계 정보 반환
     */
    fun getSecurityStats(): SecurityStats {
        val devicesBySecurityLevel = trustedDevices.values.groupBy { it.securityLevel }
        val devicesByPairingState = trustedDevices.values.groupBy { it.bondState }
        
        return SecurityStats(
            totalTrustedDevices = trustedDevices.size,
            encryptedConnections = deviceSecretKeys.size,
            devicesBySecurityLevel = devicesBySecurityLevel.mapValues { it.value.size },
            devicesByPairingState = devicesByPairingState.mapValues { it.value.size }
        )
    }
    
    data class SecurityStats(
        val totalTrustedDevices: Int,
        val encryptedConnections: Int,
        val devicesBySecurityLevel: Map<SecurityLevel, Int>,
        val devicesByPairingState: Map<PairingState, Int>
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("=== BLE Security Statistics ===")
                appendLine("Trusted Devices: $totalTrustedDevices")
                appendLine("Encrypted Connections: $encryptedConnections")
                appendLine("By Security Level:")
                devicesBySecurityLevel.forEach { (level, count) ->
                    appendLine("  $level: $count")
                }
                appendLine("By Pairing State:")
                devicesByPairingState.forEach { (state, count) ->
                    appendLine("  $state: $count")
                }
            }
        }
    }
    
    /**
     * 본딩 상태 변화 처리
     */
    private fun handleBondStateChange(device: BluetoothDevice, previousState: Int, newState: Int) {
        val deviceAddress = device.address
        val pairingState = when (newState) {
            BluetoothDevice.BOND_NONE -> PairingState.NOT_PAIRED
            BluetoothDevice.BOND_BONDING -> PairingState.PAIRING
            BluetoothDevice.BOND_BONDED -> PairingState.BONDED
            else -> PairingState.NOT_PAIRED
        }
        
        when (newState) {
            BluetoothDevice.BOND_BONDED -> {
                updateTrustedDevice(device, PairingState.BONDED, SecurityLevel.ENCRYPTION)
                notifySecurityEvent(SecurityEvent.PAIRING_SUCCESS, deviceAddress, pairingState)
                Logx.i(TAG, "Device bonded successfully: $deviceAddress")
            }
            
            BluetoothDevice.BOND_NONE -> {
                if (previousState == BluetoothDevice.BOND_BONDING) {
                    notifySecurityEvent(SecurityEvent.PAIRING_FAILED, deviceAddress, "Bond failed")
                    Logx.w(TAG, "Device bonding failed: $deviceAddress")
                } else {
                    trustedDevices.remove(deviceAddress)
                    deviceSecretKeys.remove(deviceAddress)
                    notifySecurityEvent(SecurityEvent.BOND_STATE_CHANGED, deviceAddress, pairingState)
                    Logx.i(TAG, "Device bond removed: $deviceAddress")
                }
            }
        }
    }
    
    /**
     * 페어링 요청 처리
     */
    private fun handlePairingRequest(device: BluetoothDevice, intent: Intent) {
        val deviceAddress = device.address
        
        // 페어링 유형 확인
        val pairingVariant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
        
        notifySecurityEvent(SecurityEvent.PAIRING_REQUEST, deviceAddress, pairingVariant)
        
        Logx.i(TAG, "Pairing request from device: $deviceAddress (variant: $pairingVariant)")
    }
    
    /**
     * 신뢰할 수 있는 기기 정보 업데이트
     */
    private fun updateTrustedDevice(device: BluetoothDevice, pairingState: PairingState, securityLevel: SecurityLevel) {
        val deviceAddress = device.address
        val currentTime = System.currentTimeMillis()
        
        val existingDevice = trustedDevices[deviceAddress]
        val updatedDevice = if (existingDevice != null) {
            existingDevice.copy(
                name = device.name ?: existingDevice.name,
                securityLevel = securityLevel,
                bondState = pairingState,
                lastConnectedTime = currentTime
            )
        } else {
            TrustedDevice(
                address = deviceAddress,
                name = device.name,
                securityLevel = securityLevel,
                bondState = pairingState,
                firstPairedTime = currentTime,
                lastConnectedTime = currentTime
            )
        }
        
        trustedDevices[deviceAddress] = updatedDevice
        
        // 암호화가 필요한 경우 키 생성
        if (securityLevel == SecurityLevel.ENCRYPTION || securityLevel == SecurityLevel.HIGH) {
            getOrCreateSecretKey(deviceAddress)
        }
    }
    
    /**
     * 이미 본딩된 기기들 로드
     */
    private fun loadBondedDevices() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            adapter?.bondedDevices?.forEach { device ->
                updateTrustedDevice(device, PairingState.BONDED, SecurityLevel.ENCRYPTION)
            }
            
            Logx.i(TAG, "Loaded ${trustedDevices.size} bonded devices")
        } catch (e: SecurityException) {
            Logx.w(TAG, "No permission to access bonded devices", e)
        } catch (e: Exception) {
            Logx.e(TAG, "Failed to load bonded devices", e)
        }
    }
    
    /**
     * 기기용 암호화 키 생성 또는 조회
     */
    private fun getOrCreateSecretKey(deviceAddress: String): SecretKey {
        return deviceSecretKeys[deviceAddress] ?: run {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            val secretKey = keyGenerator.generateKey()
            deviceSecretKeys[deviceAddress] = secretKey
            
            Logx.d(TAG, "Generated encryption key for device: $deviceAddress")
            secretKey
        }
    }
    
    /**
     * 보안 이벤트 알림
     */
    private fun notifySecurityEvent(event: SecurityEvent, deviceAddress: String, data: Any? = null) {
        listeners.forEach { listener ->
            try {
                listener.onSecurityEvent(event, deviceAddress, data)
            } catch (e: Exception) {
                Logx.w(TAG, "Error notifying security event listener", e)
            }
        }
    }
    
    /**
     * 정리
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(pairingReceiver)
            listeners.clear()
            trustedDevices.clear()
            deviceSecretKeys.clear()
            
            Logx.i(TAG, "BleSecurityManager cleaned up")
        } catch (e: Exception) {
            Logx.e(TAG, "Error during cleanup", e)
        }
    }
}