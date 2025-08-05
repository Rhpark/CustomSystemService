package kr.open.library.system_service

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kr.open.library.system_service.databinding.ActivitySoftKeyboardTestBinding
import kr.open.library.systemmanager.controller.softkeyboard.SoftKeyboardController

/**
 * Test activity for demonstrating SoftKeyboardController functionality.
 * Provides comprehensive examples of keyboard show/hide operations with various parameters.
 * 
 * SoftKeyboardController 기능을 시연하기 위한 테스트 액티비티입니다.
 * 다양한 매개변수로 키보드 표시/숨김 작업의 포괄적인 예제를 제공합니다.
 */
class SoftKeyboardTestActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySoftKeyboardTestBinding
    private lateinit var softKeyboardController: SoftKeyboardController
    
    
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
        setupStylusSupport()
        updateStatus("SoftKeyboard Controller initialized")
    }
    
    /**
     * Initializes the SoftKeyboardController instance.
     * SoftKeyboardController 인스턴스를 초기화합니다.
     */
    private fun initializeController() {
        softKeyboardController = SoftKeyboardController(this)
    }
    
    private fun setupBinding() {
        binding = ActivitySoftKeyboardTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
    
    private fun setupStylusSupport() {
        // Enable stylus handwriting buttons only on supported devices
        val isStylusSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        binding.btnStartStylusHandwriting.isEnabled = isStylusSupported
        binding.btnStartStylusDelayed.isEnabled = isStylusSupported
        binding.etStylusTest.isEnabled = isStylusSupported
        
        if (!isStylusSupported) {
            binding.btnStartStylusHandwriting.text = "Stylus (Requires API 33+)"
            binding.btnStartStylusDelayed.text = "Stylus Delayed (Requires API 33+)"
            binding.etStylusTest.hint = "Stylus handwriting not supported"
        }
    }
    
    /**
     * Sets up click listeners for all buttons.
     * 모든 버튼에 대한 클릭 리스너를 설정합니다.
     */
    private fun setupClickListeners() {
        binding.btnShowKeyboard.setOnClickListener {
            demonstrateShowKeyboard()
        }
        
        binding.btnHideKeyboard.setOnClickListener {
            demonstrateHideKeyboard()
        }
        
        binding.btnShowWithDelay.setOnClickListener {
            demonstrateShowWithDelay()
        }
        
        binding.btnHideWithDelay.setOnClickListener {
            demonstrateHideWithDelay()
        }
        
        binding.btnShowCoroutineDelay.setOnClickListener {
            demonstrateShowCoroutineDelay()
        }
        
        binding.btnHideCoroutineDelay.setOnClickListener {
            demonstrateHideCoroutineDelay()
        }
        
        binding.btnSetAdjustPan.setOnClickListener {
            demonstrateSetAdjustPan()
        }
        
        binding.btnSetAdjustResize.setOnClickListener {
            demonstrateSetAdjustResize()
        }
        
        binding.btnStartStylusHandwriting.setOnClickListener {
            demonstrateStartStylusHandwriting()
        }
        
        binding.btnStartStylusDelayed.setOnClickListener {
            demonstrateStartStylusDelayed()
        }
    }
    
    /**
     * Demonstrates basic keyboard show functionality.
     * 기본 키보드 표시 기능을 시연합니다.
     */
    private fun demonstrateShowKeyboard() {
        val success = softKeyboardController.show(binding.etTest1)
        val message = if (success) "Keyboard shown successfully" else "Failed to show keyboard"
        updateStatus(message)
        showToast(message)
    }
    
    /**
     * Demonstrates basic keyboard hide functionality.
     * 기본 키보드 숨김 기능을 시연합니다.
     */
    private fun demonstrateHideKeyboard() {
        val success = softKeyboardController.hide(binding.etTest1)
        val message = if (success) "Keyboard hidden successfully" else "Failed to hide keyboard"
        updateStatus(message)
        showToast(message)
    }
    
    /**
     * Demonstrates keyboard show with delay functionality.
     * 지연이 있는 키보드 표시 기능을 시연합니다.
     */
    private fun demonstrateShowWithDelay() {
        val success = softKeyboardController.showDelay(binding.etTest2, 2000L)
        val message = if (success) "Keyboard will show in 2 seconds..." else "Failed to schedule keyboard show"
        updateStatus(message)
        showToast(message)
    }
    
    /**
     * Demonstrates keyboard hide with delay functionality.
     * 지연이 있는 키보드 숨김 기능을 시연합니다.
     */
    private fun demonstrateHideWithDelay() {
        val success = softKeyboardController.hideDelay(binding.etTest2, 2000L)
        val message = if (success) "Keyboard will hide in 2 seconds..." else "Failed to schedule keyboard hide"
        updateStatus(message)
        showToast(message)
    }
    
    /**
     * Demonstrates keyboard show with coroutine-based delay.
     * 코루틴 기반 지연이 있는 키보드 표시를 시연합니다.
     */
    private fun demonstrateShowCoroutineDelay() {
        softKeyboardController.showDelay(binding.etTest1, 1500L, coroutineScope = lifecycleScope)
        val message = "Keyboard will show in 1.5 seconds (coroutine)..."
        updateStatus(message)
        showToast(message)
    }
    
    /**
     * Demonstrates keyboard hide with coroutine-based delay.
     * 코루틴 기반 지연이 있는 키보드 숨김을 시연합니다.
     */
    private fun demonstrateHideCoroutineDelay() {
        softKeyboardController.hideDelay(binding.etTest1, 1500L, coroutineScope = lifecycleScope)
        val message = "Keyboard will hide in 1.5 seconds (coroutine)..."
        updateStatus(message)
        showToast(message)
    }
    
    /**
     * Demonstrates setting window soft input mode to adjust pan.
     * 윈도우 소프트 입력 모드를 adjust pan으로 설정하는 것을 시연합니다.
     */
    private fun demonstrateSetAdjustPan() {
        val success = softKeyboardController.setAdjustPan(window)
        val message = if (success) "Window mode set to ADJUST_PAN" else "Failed to set ADJUST_PAN"
        updateStatus(message)
        showToast(message)
    }
    
    /**
     * Demonstrates setting window soft input mode to adjust resize.
     * 윈도우 소프트 입력 모드를 adjust resize로 설정하는 것을 시연합니다.
     */
    private fun demonstrateSetAdjustResize() {
        val success = softKeyboardController.setSoftInputMode(
            window, 
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        val message = if (success) "Window mode set to ADJUST_RESIZE" else "Failed to set ADJUST_RESIZE"
        updateStatus(message)
        showToast(message)
    }
    
    /**
     * Demonstrates stylus handwriting functionality (API 33+).
     * 스타일러스 필기 기능을 시연합니다 (API 33+).
     */
    private fun demonstrateStartStylusHandwriting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val success = softKeyboardController.startStylusHandwriting(binding.etStylusTest)
            val message = if (success) "Stylus handwriting started" else "Failed to start stylus handwriting"
            updateStatus(message)
            showToast(message)
        } else {
            val message = "Stylus handwriting requires Android 13+ (API 33)"
            updateStatus(message)
            showToast(message)
        }
    }
    
    /**
     * Demonstrates stylus handwriting with delay (API 33+).
     * 지연이 있는 스타일러스 필기를 시연합니다 (API 33+).
     */
    private fun demonstrateStartStylusDelayed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val success = softKeyboardController.startStylusHandwriting(binding.etStylusTest, 2000L)
            val message = if (success) "Stylus handwriting will start in 2 seconds..." else "Failed to schedule stylus handwriting"
            updateStatus(message)
            showToast(message)
        } else {
            val message = "Stylus handwriting requires Android 13+ (API 33)"
            updateStatus(message)
            showToast(message)
        }
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
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up any remaining keyboard operations if needed
        // 필요한 경우 남은 키보드 작업을 정리합니다
    }
}