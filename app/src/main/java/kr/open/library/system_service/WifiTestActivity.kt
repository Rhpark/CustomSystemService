package kr.open.library.system_service

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.ACCESS_WIFI_STATE
import android.Manifest.permission.CHANGE_WIFI_STATE
import android.graphics.Color
import android.net.wifi.ScanResult
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import kotlinx.coroutines.*
import kr.open.library.logcat.Logx
import kr.open.library.permissions.PermissionManager
import kr.open.library.system_service.databinding.ActivityWifiTestBinding
import kr.open.library.systemmanager.controller.wifi.WifiController
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for testing the WifiController functionality.
 * Provides a comprehensive UI to test WiFi state information and control features.
 * 
 * WifiController 기능을 테스트하기 위한 액티비티입니다.
 * WiFi 상태 정보 조회 및 제어 기능을 테스트할 수 있는 포괄적인 UI를 제공합니다.
 */
class WifiTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWifiTestBinding
    private lateinit var wifiController: WifiController
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    private var currentRequestId: String? = null
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionManager.result(this, permissions, currentRequestId)
    }
    
    private val permissionManager: PermissionManager by lazy {
        PermissionManager.getInstance()
    }
    
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var scanResults: List<ScanResult> = emptyList()
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBinding()
        
        // Initialize WifiController
        wifiController = WifiController(this)
        
        // Initialize UI
        setupClickListeners()
        
        // Check permissions
        checkAndRequestPermissions()
        
        // Initial status update
        updateWifiStatus()
        
        // Log initial status
        addLog("WifiTestActivity initialized")
        addLog("WifiController ready for testing")
    }

    private fun setupBinding() {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_wifi_test)
    }

    private fun setupClickListeners() {
        // WiFi State Information Buttons
        binding.btnCheckWifiState.setOnClickListener {
            checkWifiState()
        }
        
        binding.btnGetConnectionInfo.setOnClickListener {
            getConnectionInfo()
        }
        
        binding.btnGetDhcpInfo.setOnClickListener {
            getDhcpInfo()
        }
        
        binding.btnCheckConnectivity.setOnClickListener {
            checkConnectivity()
        }
        
        // WiFi Control Buttons
        binding.btnEnableWifi.setOnClickListener {
            enableWifi()
        }
        
        binding.btnDisableWifi.setOnClickListener {
            disableWifi()
        }
        
        binding.btnStartScan.setOnClickListener {
            startWifiScan()
        }
        
        binding.btnGetScanResults.setOnClickListener {
            getScanResults()
        }
        
        binding.btnReconnect.setOnClickListener {
            reconnectWifi()
        }
        
        binding.btnDisconnect.setOnClickListener {
            disconnectWifi()
        }
        
        // Advanced Features
        binding.btnCheckAdvancedFeatures.setOnClickListener {
            checkAdvancedFeatures()
        }
        
        binding.btnGetConfiguredNetworks.setOnClickListener {
            getConfiguredNetworks()
        }
        
        binding.btnRefreshStatus.setOnClickListener {
            updateWifiStatus()
            addLog("Status refreshed manually")
        }
        
        binding.btnClearLogs.setOnClickListener {
            binding.tvStatus.text = "Logs cleared at ${dateFormat.format(Date())}"
        }
        
        // Safe Method Test Buttons
        binding.btnTestSafeMethods.setOnClickListener {
            testSafeMethods()
        }
    }

    private fun checkWifiState() {
        try {
            val isEnabled = wifiController.isWifiEnabled()
            val wifiState = wifiController.getWifiState()
            val stateString = getWifiStateString(wifiState)
            
            addLog("WiFi Enabled: $isEnabled")
            addLog("WiFi State: $stateString ($wifiState)")
            
            // Test safe method
            wifiController.isWifiEnabledSafe().fold(
                onSuccess = { result ->
                    addLog("✅ Safe method - WiFi Enabled: $result")
                },
                onFailure = { error ->
                    addLog("❌ Safe method error: ${error.message}")
                }
            )
            
        } catch (e: Exception) {
            addLog("❌ Error checking WiFi state: ${e.message}")
        }
    }

    private fun getConnectionInfo() {
        try {
            val connectionInfo = wifiController.getConnectionInfo()
            if (connectionInfo != null) {
                addLog("=== WiFi Connection Info ===")
                addLog("SSID: ${wifiController.getCurrentSsid()}")
                addLog("BSSID: ${wifiController.getCurrentBssid()}")
                addLog("RSSI: ${wifiController.getCurrentRssi()} dBm")
                addLog("Link Speed: ${wifiController.getCurrentLinkSpeed()} Mbps")
                addLog("Signal Level: ${wifiController.calculateSignalLevel(wifiController.getCurrentRssi(), 4)}/3")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    addLog("Frequency: ${connectionInfo.frequency} MHz")
                }
                
            } else {
                addLog("⚠️ No WiFi connection info available")
            }
            
        } catch (e: Exception) {
            addLog("❌ Error getting connection info: ${e.message}")
        }
    }

    private fun getDhcpInfo() {
        try {
            val dhcpInfo = wifiController.getDhcpInfo()
            if (dhcpInfo != null) {
                addLog("=== DHCP Info ===")
                addLog("IP Address: ${intToIpAddress(dhcpInfo.ipAddress)}")
                addLog("Gateway: ${intToIpAddress(dhcpInfo.gateway)}")
                addLog("Netmask: ${intToIpAddress(dhcpInfo.netmask)}")
                addLog("DNS1: ${intToIpAddress(dhcpInfo.dns1)}")
                addLog("DNS2: ${intToIpAddress(dhcpInfo.dns2)}")
                addLog("DHCP Server: ${intToIpAddress(dhcpInfo.serverAddress)}")
                addLog("Lease Duration: ${dhcpInfo.leaseDuration}s")
            } else {
                addLog("⚠️ No DHCP info available")
            }
            
        } catch (e: Exception) {
            addLog("❌ Error getting DHCP info: ${e.message}")
        }
    }

    private fun checkConnectivity() {
        try {
            val isConnected = wifiController.isConnectedWifi()
            addLog("WiFi Connected: $isConnected")
            
            // Test safe method
            wifiController.isConnectedWifiSafe().fold(
                onSuccess = { result ->
                    addLog("✅ Safe method - WiFi Connected: $result")
                },
                onFailure = { error ->
                    addLog("❌ Safe method error: ${error.message}")
                }
            )
            
        } catch (e: Exception) {
            addLog("❌ Error checking connectivity: ${e.message}")
        }
    }

    private fun enableWifi() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addLog("⚠️ WiFi control deprecated on API 29+")
                showToast("WiFi control is deprecated on Android 10+")
                return
            }
            
            addLog("Attempting to enable WiFi...")
            val success = wifiController.setWifiEnabled(true)
            
            if (success) {
                addLog("✅ WiFi enable command sent")
                // Wait a moment and check status
                activityScope.launch {
                    delay(2000)
                    updateWifiStatus()
                }
            } else {
                addLog("❌ Failed to enable WiFi")
            }
            
        } catch (e: Exception) {
            addLog("❌ Error enabling WiFi: ${e.message}")
        }
    }

    private fun disableWifi() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addLog("⚠️ WiFi control deprecated on API 29+")
                showToast("WiFi control is deprecated on Android 10+")
                return
            }
            
            addLog("Attempting to disable WiFi...")
            val success = wifiController.setWifiEnabled(false)
            
            if (success) {
                addLog("✅ WiFi disable command sent")
                // Wait a moment and check status
                activityScope.launch {
                    delay(2000)
                    updateWifiStatus()
                }
            } else {
                addLog("❌ Failed to disable WiFi")
            }
            
        } catch (e: Exception) {
            addLog("❌ Error disabling WiFi: ${e.message}")
        }
    }

    private fun startWifiScan() {
        try {
            addLog("Starting WiFi scan...")
            val success = wifiController.startScan()
            
            if (success) {
                addLog("✅ WiFi scan started")
                addLog("Wait a few seconds, then get scan results")
            } else {
                addLog("❌ Failed to start WiFi scan")
            }
            
        } catch (e: Exception) {
            addLog("❌ Error starting WiFi scan: ${e.message}")
        }
    }

    private fun getScanResults() {
        try {
            addLog("Getting WiFi scan results...")
            scanResults = wifiController.getScanResults()
            
            if (scanResults.isNotEmpty()) {
                addLog("✅ Found ${scanResults.size} WiFi networks:")
                scanResults.take(5).forEach { result ->
                    val signalLevel = wifiController.calculateSignalLevel(result.level, 4)
                    addLog("• ${result.SSID} (${result.level}dBm, Level:$signalLevel/3)")
                }
                
                if (scanResults.size > 5) {
                    addLog("... and ${scanResults.size - 5} more networks")
                }
            } else {
                addLog("⚠️ No scan results found")
            }
            
        } catch (e: Exception) {
            addLog("❌ Error getting scan results: ${e.message}")
        }
    }

    private fun reconnectWifi() {
        try {
            addLog("Attempting WiFi reconnect...")
            val success = wifiController.reconnect()
            
            if (success) {
                addLog("✅ WiFi reconnect command sent")
            } else {
                addLog("❌ Failed to reconnect WiFi")
            }
            
        } catch (e: Exception) {
            addLog("❌ Error reconnecting WiFi: ${e.message}")
        }
    }

    private fun disconnectWifi() {
        try {
            addLog("Attempting WiFi disconnect...")
            val success = wifiController.disconnect()
            
            if (success) {
                addLog("✅ WiFi disconnect command sent")
                activityScope.launch {
                    delay(1000)
                    updateWifiStatus()
                }
            } else {
                addLog("❌ Failed to disconnect WiFi")
            }
            
        } catch (e: Exception) {
            addLog("❌ Error disconnecting WiFi: ${e.message}")
        }
    }

    private fun checkAdvancedFeatures() {
        try {
            addLog("=== Advanced WiFi Features ===")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val supports5GHz = wifiController.is5GHzBandSupported()
                addLog("5GHz Band Supported: $supports5GHz")
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val supports6GHz = wifiController.is6GHzBandSupported()
                addLog("6GHz Band Supported (WiFi 6E): $supports6GHz")
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val supportsWpa3Sae = wifiController.isWpa3SaeSupported()
                val supportsEnhancedOpen = wifiController.isEnhancedOpenSupported()
                addLog("WPA3 SAE Supported: $supportsWpa3Sae")
                addLog("Enhanced Open Supported: $supportsEnhancedOpen")
            }
            
        } catch (e: Exception) {
            addLog("❌ Error checking advanced features: ${e.message}")
        }
    }

    private fun getConfiguredNetworks() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addLog("⚠️ getConfiguredNetworks deprecated on API 29+")
                return
            }
            
            addLog("Getting configured networks...")
            val networks = wifiController.getConfiguredNetworks()
            
            if (networks.isNotEmpty()) {
                addLog("✅ Found ${networks.size} configured networks:")
                networks.take(3).forEach { config ->
                    addLog("• ${config.SSID} (networkId: ${config.networkId})")
                }
                
                if (networks.size > 3) {
                    addLog("... and ${networks.size - 3} more networks")
                }
            } else {
                addLog("⚠️ No configured networks found")
            }
            
        } catch (e: Exception) {
            addLog("❌ Error getting configured networks: ${e.message}")
        }
    }

    private fun testSafeMethods() {
        try {
            addLog("=== Testing Safe Methods ===")
            
            // Test safe methods with Result pattern
            wifiController.setWifiEnabledSafe(true) { permission, settingsAction ->
                addLog("Permission required: $permission")
                if (settingsAction != null) {
                    addLog("Settings action: $settingsAction")
                }
            }.fold(
                onSuccess = { result ->
                    addLog("✅ setWifiEnabledSafe: $result")
                },
                onFailure = { error ->
                    addLog("❌ setWifiEnabledSafe error: ${error.message}")
                }
            )
            
            wifiController.startScanSafe { permission, settingsAction ->
                addLog("Scan permission required: $permission")
            }.fold(
                onSuccess = { result ->
                    addLog("✅ startScanSafe: $result")
                },
                onFailure = { error ->
                    addLog("❌ startScanSafe error: ${error.message}")
                }
            )
            
            wifiController.getScanResultsSafe().fold(
                onSuccess = { results ->
                    addLog("✅ getScanResultsSafe: ${results.size} networks")
                },
                onFailure = { error ->
                    addLog("❌ getScanResultsSafe error: ${error.message}")
                }
            )
            
        } catch (e: Exception) {
            addLog("❌ Error testing safe methods: ${e.message}")
        }
    }

    private fun updateWifiStatus() {
        try {
            val isEnabled = wifiController.isWifiEnabled()
            val isConnected = wifiController.isConnectedWifi()
            val wifiState = wifiController.getWifiState()
            val stateString = getWifiStateString(wifiState)
            
            // Update status text with color coding
            val statusText = "WiFi: ${if (isEnabled) "ON" else "OFF"} | " +
                    "Connected: ${if (isConnected) "YES" else "NO"} | " +
                    "State: $stateString"
            
            binding.tvWifiStatus.text = statusText
            binding.tvWifiStatus.setTextColor(
                when {
                    isEnabled && isConnected -> Color.GREEN
                    isEnabled && !isConnected -> Color.YELLOW
                    else -> Color.RED
                }
            )
            
            // Update connection details if connected
            if (isConnected) {
                val ssid = wifiController.getCurrentSsid()
                val rssi = wifiController.getCurrentRssi()
                val linkSpeed = wifiController.getCurrentLinkSpeed()
                
                binding.tvConnectionDetails.text = 
                    "Connected to: $ssid | Signal: ${rssi}dBm | Speed: ${linkSpeed}Mbps"
                binding.tvConnectionDetails.setTextColor(Color.GREEN)
            } else {
                binding.tvConnectionDetails.text = "Not connected to any WiFi network"
                binding.tvConnectionDetails.setTextColor(Color.GRAY)
            }
            
        } catch (e: Exception) {
            binding.tvWifiStatus.text = "Error getting WiFi status"
            binding.tvWifiStatus.setTextColor(Color.RED)
            addLog("❌ Error updating status: ${e.message}")
        }
    }

    private fun getWifiStateString(state: Int): String {
        return when (state) {
            android.net.wifi.WifiManager.WIFI_STATE_DISABLED -> "DISABLED"
            android.net.wifi.WifiManager.WIFI_STATE_DISABLING -> "DISABLING"
            android.net.wifi.WifiManager.WIFI_STATE_ENABLED -> "ENABLED"
            android.net.wifi.WifiManager.WIFI_STATE_ENABLING -> "ENABLING"
            android.net.wifi.WifiManager.WIFI_STATE_UNKNOWN -> "UNKNOWN"
            else -> "UNKNOWN($state)"
        }
    }

    private fun intToIpAddress(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }

    private fun addLog(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message"
        
        runOnUiThread {
            val currentText = binding.tvStatus.text.toString()
            val newText = if (currentText == "Ready to test WiFi functionality") {
                logEntry
            } else {
                "$currentText\n$logEntry"
            }
            binding.tvStatus.text = newText
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun checkAndRequestPermissions() {
        permissionManager.request(
            this, requestPermissionLauncher = requestPermissionLauncher,
            specialPermissionLaunchers = null,
            permissions = getPermissionList()
        ) { deniedPermissions ->
            Logx.d("deniedPermissions $deniedPermissions")
            if (deniedPermissions.isNotEmpty()) {
                addLog("❌ Some permissions denied: ${deniedPermissions.joinToString(", ")}")
            } else {
                addLog("✅ All permissions granted")
            }
        }
    }

    private fun getPermissionList(): List<String> {
        val list = mutableListOf<String>()
        list.add(ACCESS_WIFI_STATE)
        list.add(CHANGE_WIFI_STATE)
        
        // Location permission required for scan results on API 23+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            list.add(ACCESS_FINE_LOCATION)
        }
        
        return list.toList()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        wifiController.onDestroy()
        addLog("WifiTestActivity destroyed")
    }
}