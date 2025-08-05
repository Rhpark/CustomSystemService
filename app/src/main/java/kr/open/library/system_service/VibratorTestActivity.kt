package kr.open.library.system_service

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kr.open.library.system_service.databinding.ActivityVibratorTestBinding
import kr.open.library.systemmanager.controller.vibrator.VibratorController

/**
 * Test activity for demonstrating VibratorController functionality.
 * Provides comprehensive examples of device vibration operations with various patterns and intensities.
 * 
 * VibratorController 기능을 시연하기 위한 테스트 액티비티입니다.
 * 다양한 패턴과 강도로 기기 진동 작업의 포괄적인 예제를 제공합니다.
 */
class VibratorTestActivity : AppCompatActivity() {
    
    companion object {
        private const val VIBRATION_PERMISSION_REQUEST_CODE = 1001
    }
    
    private lateinit var binding: ActivityVibratorTestBinding
    private lateinit var vibratorController: VibratorController
    
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupBinding()
        
        // Setup window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Initialize controller and views
        initializeController()
        setupClickListeners()
        setupSeekBar()
        checkVibrationPermission()
        updateApiLevelInfo()
        enableButtonsBasedOnApiLevel()
        updateStatus("Vibrator Controller initialized")
    }
    
    /**
     * Initializes the VibratorController instance.
     * VibratorController 인스턴스를 초기화합니다.
     */
    private fun initializeController() {
        vibratorController = VibratorController(this)
    }
    
    private fun setupBinding() {
        binding = ActivityVibratorTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
    
    /**
     * Enables or disables buttons based on current API level.
     * 현재 API 레벨에 따라 버튼을 활성화하거나 비활성화합니다.
     */
    private fun enableButtonsBasedOnApiLevel() {
        val isApi29Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val isApi31Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        
        // Predefined effects require API 29+
        binding.btnEffectClick.isEnabled = isApi29Plus
        binding.btnEffectDoubleClick.isEnabled = isApi29Plus
        binding.btnEffectTick.isEnabled = isApi29Plus
        binding.btnEffectHeavyClick.isEnabled = isApi29Plus
        
        // Waveform patterns require API 31+
        binding.btnWaveformPulse.isEnabled = isApi31Plus
        binding.btnWaveformHeartbeat.isEnabled = isApi31Plus
        binding.btnWaveformSOS.isEnabled = isApi31Plus
        binding.btnWaveformCustom.isEnabled = isApi31Plus
        
        // Update button text for unsupported features
        if (!isApi29Plus) {
            binding.btnEffectClick.text = "Click (API 29+)"
            binding.btnEffectDoubleClick.text = "Double Click (API 29+)"
            binding.btnEffectTick.text = "Tick (API 29+)"
            binding.btnEffectHeavyClick.text = "Heavy Click (API 29+)"
        }
        
        if (!isApi31Plus) {
            binding.btnWaveformPulse.text = "Pulse (API 31+)"
            binding.btnWaveformHeartbeat.text = "Heartbeat (API 31+)"
            binding.btnWaveformSOS.text = "SOS (API 31+)"
            binding.btnWaveformCustom.text = "Custom (API 31+)"
        }
    }
    
    /**
     * Sets up click listeners for all buttons.
     * 모든 버튼에 대한 클릭 리스너를 설정합니다.
     */
    private fun setupClickListeners() {
        // Basic vibration
        binding.btnBasicVibration.setOnClickListener { demonstrateBasicVibration() }
        binding.btnCustomDuration.setOnClickListener { demonstrateCustomDuration() }
        binding.btnCustomAmplitude.setOnClickListener { demonstrateCustomAmplitude() }
        
        // Predefined effects
        binding.btnEffectClick.setOnClickListener { demonstratePredefinedEffect(VibrationEffect.EFFECT_CLICK, "Click") }
        binding.btnEffectDoubleClick.setOnClickListener { demonstratePredefinedEffect(VibrationEffect.EFFECT_DOUBLE_CLICK, "Double Click") }
        binding.btnEffectTick.setOnClickListener { demonstratePredefinedEffect(VibrationEffect.EFFECT_TICK, "Tick") }
        binding.btnEffectHeavyClick.setOnClickListener { demonstratePredefinedEffect(VibrationEffect.EFFECT_HEAVY_CLICK, "Heavy Click") }
        
        // Waveform patterns
        binding.btnWaveformPulse.setOnClickListener { demonstrateWaveformPulse() }
        binding.btnWaveformHeartbeat.setOnClickListener { demonstrateWaveformHeartbeat() }
        binding.btnWaveformSOS.setOnClickListener { demonstrateWaveformSOS() }
        binding.btnWaveformCustom.setOnClickListener { demonstrateWaveformCustom() }
        
        // Control buttons
        binding.btnCancelVibration.setOnClickListener { demonstrateCancelVibration() }
        binding.btnPermissionCheck.setOnClickListener { checkVibrationPermission() }
    }
    
    /**
     * Sets up the amplitude SeekBar.
     * 진폭 SeekBar를 설정합니다.
     */
    private fun setupSeekBar() {
        binding.seekAmplitude.max = 255
        binding.seekAmplitude.progress = 128
        binding.tvAmplitudeValue.text = "128"
        
        binding.seekAmplitude.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvAmplitudeValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    /**
     * Demonstrates basic vibration functionality.
     * 기본 진동 기능을 시연합니다.
     */
    private fun demonstrateBasicVibration() {
        val success = vibratorController.createOneShot(500L)
        val message = if (success) "Basic vibration (500ms) executed" else "Failed to execute basic vibration"
        updateStatus(message)
        showToast(message)
    }
    
    /**
     * Demonstrates custom duration vibration.
     * 사용자 정의 지속 시간 진동을 시연합니다.
     */
    private fun demonstrateCustomDuration() {
        val durationText = binding.etDuration.text.toString()
        val duration = durationText.toLongOrNull() ?: 1000L
        
        val success = vibratorController.createOneShot(duration)
        val message = if (success) "Custom vibration (${duration}ms) executed" else "Failed to execute custom duration vibration"
        updateStatus(message)
        showToast(message)
    }
    
    /**
     * Demonstrates custom amplitude vibration.
     * 사용자 정의 진폭 진동을 시연합니다.
     */
    private fun demonstrateCustomAmplitude() {
        val amplitude = binding.seekAmplitude.progress
        val duration = binding.etDuration.text.toString().toLongOrNull() ?: 800L
        
        val success = vibratorController.createOneShot(duration, amplitude)
        val message = if (success) "Custom amplitude vibration (${duration}ms, amplitude: $amplitude) executed" 
                     else "Failed to execute custom amplitude vibration"
        updateStatus(message)
        showToast(message)
    }
    
    /**
     * Demonstrates predefined vibration effects.
     * 미리 정의된 진동 효과를 시연합니다.
     */
    private fun demonstratePredefinedEffect(effect: Int, effectName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val success = vibratorController.createPredefined(effect)
            val message = if (success) "$effectName effect executed" else "Failed to execute $effectName effect"
            updateStatus(message)
            showToast(message)
        } else {
            val message = "Predefined effects require Android 10+ (API 29)"
            updateStatus(message)
            showToast(message)
        }
    }
    
    /**
     * Demonstrates pulse waveform pattern.
     * 펄스 웨이브폼 패턴을 시연합니다.
     */
    private fun demonstrateWaveformPulse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val timings = longArrayOf(0, 100, 100, 100, 100, 100)
            val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
            
            val success = vibratorController.createWaveform(timings, amplitudes)
            val message = if (success) "Pulse waveform executed" else "Failed to execute pulse waveform"
            updateStatus(message)
            showToast(message)
        } else {
            val message = "Waveform patterns require Android 12+ (API 31)"
            updateStatus(message)
            showToast(message)
        }
    }
    
    /**
     * Demonstrates heartbeat waveform pattern.
     * 심장 박동 웨이브폼 패턴을 시연합니다.
     */
    private fun demonstrateWaveformHeartbeat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val timings = longArrayOf(0, 100, 200, 150, 400, 100, 200, 150)
            val amplitudes = intArrayOf(0, 200, 0, 255, 0, 180, 0, 220)
            
            val success = vibratorController.createWaveform(timings, amplitudes)
            val message = if (success) "Heartbeat waveform executed" else "Failed to execute heartbeat waveform"
            updateStatus(message)
            showToast(message)
        } else {
            val message = "Waveform patterns require Android 12+ (API 31)"
            updateStatus(message)
            showToast(message)
        }
    }
    
    /**
     * Demonstrates SOS waveform pattern (... --- ...).
     * SOS 웨이브폼 패턴을 시연합니다 (... --- ...).
     */
    private fun demonstrateWaveformSOS() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // SOS: Short-Short-Short Long-Long-Long Short-Short-Short
            val timings = longArrayOf(
                0, 150, 150, 150, 150, 150, 300,    // ... (3 short)
                300, 300, 300, 300, 300, 300,       // --- (3 long)  
                150, 150, 150, 150, 150, 150        // ... (3 short)
            )
            val amplitudes = intArrayOf(
                0, 255, 0, 255, 0, 255, 0,          // ... 
                255, 0, 255, 0, 255, 0,             // ---
                255, 0, 255, 0, 255, 0              // ...
            )
            
            val success = vibratorController.createWaveform(timings, amplitudes)
            val message = if (success) "SOS waveform executed" else "Failed to execute SOS waveform"
            updateStatus(message)
            showToast(message)
        } else {
            val message = "Waveform patterns require Android 12+ (API 31)"
            updateStatus(message)
            showToast(message)
        }
    }
    
    /**
     * Demonstrates custom repeating waveform pattern.
     * 사용자 정의 반복 웨이브폼 패턴을 시연합니다.
     */
    private fun demonstrateWaveformCustom() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val timings = longArrayOf(0, 200, 300, 100, 500, 200)
            val amplitudes = intArrayOf(0, 100, 0, 200, 0, 255)
            val repeatIndex = 1 // Repeat from index 1
            
            val success = vibratorController.createWaveform(timings, amplitudes, repeatIndex)
            val message = if (success) "Custom repeating waveform started (tap Cancel to stop)" 
                         else "Failed to execute custom waveform"
            updateStatus(message)
            showToast(message)
        } else {
            val message = "Waveform patterns require Android 12+ (API 31)"
            updateStatus(message)
            showToast(message)
        }
    }
    
    /**
     * Demonstrates vibration cancellation.
     * 진동 취소를 시연합니다.
     */
    private fun demonstrateCancelVibration() {
        val success = vibratorController.cancel()
        val message = if (success) "All vibrations cancelled" else "Failed to cancel vibrations"
        updateStatus(message)
        showToast(message)
    }
    
    /**
     * Checks and requests vibration permission if needed.
     * 필요한 경우 진동 권한을 확인하고 요청합니다.
     */
    private fun checkVibrationPermission() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.VIBRATE
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            val message = "✅ VIBRATE permission granted"
            updateStatus(message)
            showToast(message)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.VIBRATE),
                VIBRATION_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    /**
     * Updates API level information display.
     * API 레벨 정보 표시를 업데이트합니다.
     */
    private fun updateApiLevelInfo() {
        val currentApi = Build.VERSION.SDK_INT
        val apiInfo = when {
            currentApi >= Build.VERSION_CODES.S -> "API $currentApi - Full features available"
            currentApi >= Build.VERSION_CODES.Q -> "API $currentApi - Predefined effects available"
            else -> "API $currentApi - Basic vibration only"
        }
        binding.tvApiLevel.text = apiInfo
    }
    
    /**
     * Updates the status TextView with the given message.
     * 주어진 메시지로 상태 TextView를 업데이트합니다.
     */
    private fun updateStatus(message: String) {
        binding.tvStatus.text = "Status: $message"
    }
    
    /**
     * Shows a toast message to the user.
     * 사용자에게 토스트 메시지를 표시합니다.
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            VIBRATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    val message = "✅ VIBRATE permission granted"
                    updateStatus(message)
                    showToast(message)
                } else {
                    val message = "❌ VIBRATE permission denied"
                    updateStatus(message)
                    showToast(message)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cancel any ongoing vibrations when activity is destroyed
        vibratorController.cancel()
    }
}