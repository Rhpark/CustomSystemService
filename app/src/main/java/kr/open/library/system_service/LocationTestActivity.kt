package kr.open.library.system_service

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.open.library.system_service.R
import kr.open.library.system_service.databinding.ActivityLocationTestBinding
import kr.open.library.systemmanager.info.location.LocationStateInfo
import kr.open.library.systemmanager.info.location.LocationStateEvent
import android.location.LocationManager

/**
 * LocationTestActivity - LocationStateInfo 테스트 액티비티
 * LocationStateInfo Test Activity
 * 
 * 위치 서비스 기능을 종합적으로 테스트하는 액티비티입니다.
 * Activity for comprehensive testing of location service functionality.
 * 
 * 주요 테스트 기능 / Main Test Features:
 * - 위치 정보 실시간 모니터링 / Real-time location information monitoring  
 * - GPS/Network/Passive/Fused Provider 상태 확인 / GPS/Network/Passive/Fused provider status checking
 * - StateFlow 기반 반응형 업데이트 / StateFlow-based reactive updates
 * - 위치 업데이트 등록/해제 / Location update registration/unregistration
 * - 권한 관리 및 확인 / Permission management and verification
 * - Result 패턴 기반 안전한 작업 수행 / Result pattern-based safe operations
 * - SharedPreference 위치 저장/로드 / SharedPreference location save/load
 * - 거리/방향 계산 테스트 / Distance/bearing calculation testing
 */
class LocationTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocationTestBinding
    private lateinit var locationStateInfo: LocationStateInfo
    private var isMonitoring = false
    private var savedLocation: Location? = null
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupBinding()
        setupUI()
        initializeLocationController()
        
        if (hasLocationPermissions()) {
            updatePermissionStatus("권한 승인됨", Color.GREEN)
            enableLocationFeatures()
        } else {
            updatePermissionStatus("권한 필요", Color.RED)
            requestLocationPermissions()
        }
    }

    private fun setupBinding() {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_location_test)
    }

    private fun setupUI() {
        binding.btnRequestPermissions.setOnClickListener { requestLocationPermissions() }
        binding.btnToggleMonitoring.setOnClickListener { toggleLocationMonitoring() }
        binding.btnGetCurrentLocation.setOnClickListener { getCurrentLocation() }
        binding.btnStartLocationUpdates.setOnClickListener { startLocationUpdates() }
        binding.btnStopLocationUpdates.setOnClickListener { stopLocationUpdates() }
        binding.btnSaveLocation.setOnClickListener { saveCurrentLocation() }
        binding.btnLoadLocation.setOnClickListener { loadSavedLocation() }
        binding.btnClearSavedLocations.setOnClickListener { clearAllSavedLocations() }
        binding.btnCalculateDistance.setOnClickListener { calculateDistanceToSaved() }
        binding.btnProviderStatus.setOnClickListener { checkProviderStatus() }
        binding.btnClearLogs.setOnClickListener { clearStatusLogs() }
    }

    private fun initializeLocationController() {
        locationStateInfo = LocationStateInfo(this, lifecycleScope)
        
        lifecycleScope.launch {
            locationStateInfo.sfUpdate.collect { event ->
                when (event) {
                    is LocationStateEvent.OnLocationChanged -> {
                        val location = event.location
                        if (location != null) {
                            appendLog("📍 위치 업데이트: ${location.latitude}, ${location.longitude} (정확도: ${location.accuracy}m)")
                            updateLocationDisplay(location)
                        } else {
                            appendLog("❌ 위치 정보 없음")
                        }
                    }
                    is LocationStateEvent.OnGpsEnabled -> {
                        val status = if (event.isEnabled) "활성화" else "비활성화"
                        appendLog("🛰️ GPS $status")
                        updateProviderStatus()
                    }
                    is LocationStateEvent.OnNetworkEnabled -> {
                        val status = if (event.isEnabled) "활성화" else "비활성화" 
                        appendLog("📶 Network Provider $status")
                        updateProviderStatus()
                    }
                    is LocationStateEvent.OnPassiveEnabled -> {
                        val status = if (event.isEnabled) "활성화" else "비활성화"
                        appendLog("📱 Passive Provider $status")
                        updateProviderStatus()
                    }
                    is LocationStateEvent.OnFusedEnabled -> {
                        val status = if (event.isEnabled) "활성화" else "비활성화"
                        appendLog("🔗 Fused Provider $status")
                        updateProviderStatus()
                    }
                }
            }
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, LOCATION_PERMISSION_REQUEST_CODE)
    }

    private fun updatePermissionStatus(status: String, color: Int) {
        binding.tvPermissionStatus.text = status
        binding.tvPermissionStatus.setTextColor(color)
    }

    private fun enableLocationFeatures() {
        binding.btnToggleMonitoring.isEnabled = true
        binding.btnGetCurrentLocation.isEnabled = true
        binding.btnStartLocationUpdates.isEnabled = true
        binding.btnProviderStatus.isEnabled = true
        binding.btnSaveLocation.isEnabled = true
        binding.btnLoadLocation.isEnabled = true
        binding.btnClearSavedLocations.isEnabled = true
        
        updateProviderStatus()
        appendLog("✅ 위치 서비스 기능 활성화됨")
    }

    private fun toggleLocationMonitoring() {
        if (isMonitoring) {
            stopMonitoring()
        } else {
            startMonitoring()
        }
    }

    private fun startMonitoring() {
        locationStateInfo.registerLocation()
        isMonitoring = true
        binding.btnToggleMonitoring.text = "모니터링 중지"
        binding.btnToggleMonitoring.setBackgroundColor(Color.parseColor("#FF5722"))
        appendLog("🔍 위치 모니터링 시작됨")
    }

    private fun stopMonitoring() {
        locationStateInfo.unregisterGpsState()
        isMonitoring = false
        binding.btnToggleMonitoring.text = "모니터링 시작"
        binding.btnToggleMonitoring.setBackgroundColor(Color.parseColor("#4CAF50"))
        appendLog("⏹️ 위치 모니터링 중지됨")
    }

    private fun getCurrentLocation() {
        lifecycleScope.launch {
            locationStateInfo.getLocationResult()
                .onSuccess { location ->
                    if (location != null) {
                        updateLocationDisplay(location)
                        appendLog("📍 현재 위치 조회 성공: ${location.latitude}, ${location.longitude}")
                    } else {
                        appendLog("❌ 현재 위치를 찾을 수 없음")
                    }
                }
                .onFailure { throwable ->
                    appendLog("❌ 위치 조회 실패: ${throwable.message}")
                }
        }
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermissions()) {
            appendLog("❌ 위치 권한이 필요합니다")
            return
        }

        val provider = when {
            locationStateInfo.isGpsEnabled() -> LocationManager.GPS_PROVIDER
            locationStateInfo.isNetworkEnabled() -> LocationManager.NETWORK_PROVIDER
            else -> LocationManager.PASSIVE_PROVIDER
        }

        lifecycleScope.launch {
            locationStateInfo.registerLocationUpdateStartResult(provider, 1000L, 1.0f)
                .onSuccess {
                    appendLog("✅ 위치 업데이트 시작됨 (${provider})")
                    binding.btnStopLocationUpdates.isEnabled = true
                    binding.btnStartLocationUpdates.isEnabled = false
                }
                .onFailure { throwable ->
                    appendLog("❌ 위치 업데이트 시작 실패: ${throwable.message}")
                }
        }
    }

    private fun stopLocationUpdates() {
        lifecycleScope.launch {
            locationStateInfo.unregisterLocationUpdateListenerResult()
                .onSuccess {
                    appendLog("⏹️ 위치 업데이트 중지됨")
                    binding.btnStopLocationUpdates.isEnabled = false
                    binding.btnStartLocationUpdates.isEnabled = true
                }
                .onFailure { throwable ->
                    appendLog("❌ 위치 업데이트 중지 실패: ${throwable.message}")
                }
        }
    }

    private fun saveCurrentLocation() {
        lifecycleScope.launch {
            locationStateInfo.getLocationResult()
                .onSuccess { location ->
                    if (location != null) {
                        val key = "current_location_${System.currentTimeMillis()}"
                        locationStateInfo.saveApplyLocationResult(key, location)
                            .onSuccess {
                                appendLog("💾 위치 저장됨: $key")
                                savedLocation = location
                            }
                            .onFailure { throwable ->
                                appendLog("❌ 위치 저장 실패: ${throwable.message}")
                            }
                    } else {
                        appendLog("❌ 저장할 위치 정보가 없음")
                    }
                }
        }
    }

    private fun loadSavedLocation() {
        lifecycleScope.launch {
            locationStateInfo.getAllLocationKeysResult()
                .onSuccess { keys ->
                    if (keys.isNotEmpty()) {
                        val latestKey = keys.last()
                        locationStateInfo.loadLocationResult(latestKey)
                            .onSuccess { location ->
                                if (location != null) {
                                    savedLocation = location
                                    appendLog("📂 저장된 위치 로드됨: ${location.latitude}, ${location.longitude}")
                                    binding.tvSavedLocation.text = "저장된 위치: ${location.latitude}, ${location.longitude}"
                                } else {
                                    appendLog("❌ 저장된 위치가 없음")
                                }
                            }
                            .onFailure { throwable ->
                                appendLog("❌ 위치 로드 실패: ${throwable.message}")
                            }
                    } else {
                        appendLog("❌ 저장된 위치가 없음")
                    }
                }
        }
    }

    private fun clearAllSavedLocations() {
        lifecycleScope.launch {
            locationStateInfo.clearAllLocationsResult()
                .onSuccess {
                    appendLog("🗑️ 모든 저장된 위치 삭제됨")
                    binding.tvSavedLocation.text = "저장된 위치: 없음"
                    savedLocation = null
                }
                .onFailure { throwable ->
                    appendLog("❌ 위치 삭제 실패: ${throwable.message}")
                }
        }
    }

    private fun calculateDistanceToSaved() {
        val saved = savedLocation
        if (saved == null) {
            appendLog("❌ 저장된 위치가 없습니다")
            return
        }

        lifecycleScope.launch {
            locationStateInfo.getLocationResult()
                .onSuccess { currentLocation ->
                    if (currentLocation != null) {
                        locationStateInfo.calculateDistanceResult(currentLocation, saved)
                            .onSuccess { distance ->
                                locationStateInfo.calculateBearingResult(currentLocation, saved)
                                    .onSuccess { bearing ->
                                        appendLog("📏 현재 위치에서 저장된 위치까지:")
                                        appendLog("   거리: ${distance.toInt()}m")
                                        appendLog("   방향: ${bearing.toInt()}°")
                                        
                                        binding.tvDistanceInfo.text = "거리: ${distance.toInt()}m, 방향: ${bearing.toInt()}°"
                                    }
                            }
                            .onFailure { throwable ->
                                appendLog("❌ 계산 실패: ${throwable.message}")
                            }
                    } else {
                        appendLog("❌ 현재 위치를 찾을 수 없음")
                    }
                }
        }
    }

    private fun updateLocationDisplay(location: Location) {
        binding.tvCurrentLocation.text = "현재 위치: ${location.latitude}, ${location.longitude}"
        binding.tvAccuracy.text = "정확도: ${location.accuracy}m"
        binding.tvProvider.text = "제공자: ${location.provider}"
        binding.tvTime.text = "시간: ${android.text.format.DateFormat.format("HH:mm:ss", location.time)}"
    }

    private fun checkProviderStatus() {
        lifecycleScope.launch {
            val results = mutableListOf<String>()
            
            locationStateInfo.isGpsEnabledResult()
                .onSuccess { enabled -> results.add("GPS: ${if (enabled) "활성화" else "비활성화"}") }
                
            locationStateInfo.isNetworkEnabledResult()
                .onSuccess { enabled -> results.add("Network: ${if (enabled) "활성화" else "비활성화"}") }
                
            locationStateInfo.isPassiveEnabledResult()
                .onSuccess { enabled -> results.add("Passive: ${if (enabled) "활성화" else "비활성화"}") }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationStateInfo.isFusedEnabledResult()
                    .onSuccess { enabled -> results.add("Fused: ${if (enabled) "활성화" else "비활성화"}") }
            }

            appendLog("📊 Provider 상태:")
            results.forEach { appendLog("   $it") }
        }
    }

    private fun updateProviderStatus() {
        val gps = if (locationStateInfo.isGpsEnabled()) "✅" else "❌"
        val network = if (locationStateInfo.isNetworkEnabled()) "✅" else "❌"
        val passive = if (locationStateInfo.isPassiveEnabled()) "✅" else "❌"
        val fused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && locationStateInfo.isFusedEnabled()) "✅" else "❌"

        binding.tvProviderStatus.text = "GPS: $gps Network: $network Passive: $passive Fused: $fused"
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            val timestamp = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
            val logMessage = "[$timestamp] $message\n"
            binding.tvStatus.append(logMessage)
            
            // Auto-scroll to bottom
            binding.scrollView.post {
                binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun clearStatusLogs() {
        binding.tvStatus.text = ""
        appendLog("📝 로그 초기화됨")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    updatePermissionStatus("권한 승인됨", Color.GREEN)
                    enableLocationFeatures()
                    appendLog("✅ 위치 권한이 승인되었습니다")
                } else {
                    updatePermissionStatus("권한 거부됨", Color.RED)
                    appendLog("❌ 위치 권한이 거부되었습니다")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isMonitoring) {
            locationStateInfo.unregisterGpsState()
        }
        locationStateInfo.unregisterLocationUpdateListener()
        locationStateInfo.onDestroy()
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}