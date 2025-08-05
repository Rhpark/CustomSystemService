package kr.open.library.system_service

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kr.open.library.logcat.Logx
import kr.open.library.permissions.PermissionManager
import kr.open.library.system_service.databinding.ActivityNotificationTestBinding
import kr.open.library.systemmanager.controller.notification.SimpleNotificationController
import kr.open.library.systemmanager.controller.notification.dto.SimpleNotificationOption
import kr.open.library.systemmanager.controller.notification.dto.SimpleProgressNotificationOption
import kr.open.library.systemmanager.controller.notification.vo.NotificationStyle
import kr.open.library.systemmanager.controller.notification.vo.SimpleNotificationType
import kr.open.library.systemmanager.extenstions.checkSdkVersion
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for testing the SimpleNotificationController functionality.
 * Provides comprehensive UI to test different notification types, styles, and configurations.
 * 
 * SimpleNotificationController 기능을 테스트하기 위한 액티비티입니다.
 * 다양한 알림 유형, 스타일 및 구성을 테스트할 수 있는 포괄적인 UI를 제공합니다.
 */
class NotificationTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationTestBinding
    private lateinit var notificationController: SimpleNotificationController
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var notificationIdCounter = 1000

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
    private var currentProgressNotificationId: Int? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBinding()
        
        // Initialize NotificationController
        notificationController = SimpleNotificationController(this, SimpleNotificationType.ACTIVITY)
        
        // Initialize UI
        setupSpinners()
        setupClickListeners()
        initializeDefaultValues()
        
        // Check permissions
        checkAndRequestPermissions()
        
        // Log initial status
        logMessage("NotificationTestActivity initialized")
        logMessage("SimpleNotificationController ready for testing")
    }

    private fun setupBinding() {
        binding = ActivityNotificationTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
    
    private fun initializeDefaultValues() {
        // Set default values
        binding.etTitle.setText("Test Notification")
        binding.etContent.setText("This is a test notification from SimpleNotificationController")
        binding.etSnippet.setText("This is a longer text that will be shown in BigText style notification. It can contain much more information than regular notifications.")
        
        // Set progress seekbar
        binding.seekProgress.max = 100
        binding.seekProgress.progress = 50
        updateProgressDisplay(50)
        
        binding.seekProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateProgressDisplay(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupSpinners() {
        // Notification Style Spinner
        val styleAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            NotificationStyle.values().map { it.name }
        )
        styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spNotificationStyle.adapter = styleAdapter
        
        // Notification Type Spinner
        val typeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            SimpleNotificationType.values().map { it.name }
        )
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spNotificationType.adapter = typeAdapter
        
        // Channel Importance Spinner
        val importanceOptions = listOf(
            "IMPORTANCE_HIGH" to NotificationManager.IMPORTANCE_HIGH,
            "IMPORTANCE_DEFAULT" to NotificationManager.IMPORTANCE_DEFAULT,
            "IMPORTANCE_LOW" to NotificationManager.IMPORTANCE_LOW,
            "IMPORTANCE_MIN" to NotificationManager.IMPORTANCE_MIN
        )
        val importanceAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            importanceOptions.map { it.first }
        )
        importanceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spChannelImportance.adapter = importanceAdapter
        binding.spChannelImportance.setSelection(1) // Default to IMPORTANCE_DEFAULT
    }

    private fun setupClickListeners() {
        binding.btnCreateChannel.setOnClickListener {
            createNotificationChannel()
        }
        
        binding.btnShowNotification.setOnClickListener {
            showNotification()
        }
        
        binding.btnShowProgressNotification.setOnClickListener {
            showProgressNotification()
        }
        
        binding.btnUpdateProgress.setOnClickListener {
            updateProgress()
        }
        
        binding.btnCompleteProgress.setOnClickListener {
            completeProgress()
        }
        
        binding.btnCancelNotification.setOnClickListener {
            cancelCurrentNotification()
        }
        
        binding.btnCancelAll.setOnClickListener {
            cancelAllNotifications()
        }
        
        binding.btnClearLogs.setOnClickListener {
            binding.tvStatus.text = "Logs cleared at ${dateFormat.format(Date())}"
        }
    }

    private fun createNotificationChannel() {
        try {
            val channelId = "test_channel_${System.currentTimeMillis()}"
            val channelName = "Test Channel"
            val importance = getSelectedImportance()
            val description = "Test notification channel created at ${Date()}"
            
            notificationController.createChannel(channelId, channelName, importance, description)
            
            logMessage("✅ Channel created successfully")
            logMessage("Channel ID: $channelId")
            logMessage("Importance: ${getImportanceName(importance)}")
            
        } catch (e: Exception) {
            logMessage("❌ Error creating channel: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun showNotification() {
        try {
            val notificationId = notificationIdCounter++
            val style = getSelectedNotificationStyle()
            
            val notificationOption = SimpleNotificationOption(
                notificationId = notificationId,
                title = binding.etTitle.text.toString().takeIf { it.isNotBlank() },
                content = binding.etContent.text.toString().takeIf { it.isNotBlank() },
                snippet = binding.etSnippet.text.toString().takeIf { it.isNotBlank() },
                isAutoCancel = binding.cbAutoCancel.isChecked,
                onGoing = binding.cbOngoing.isChecked,
                smallIcon = R.drawable.ic_launcher_foreground,
                largeIcon = if (style == NotificationStyle.BIG_PICTURE) {
                    BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_background)
                } else null,
                clickIntent = Intent(this, MainActivity::class.java),
                style = style
            )
            
            val success = notificationController.showNotification(notificationOption)
            
            if (success) {
                logMessage("✅ Notification shown successfully!")
                logMessage("ID: $notificationId, Style: ${style.name}")
            } else {
                logMessage("❌ Failed to show notification")
            }
            
        } catch (e: Exception) {
            logMessage("❌ Error showing notification: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun showProgressNotification() {
        try {
            val notificationId = notificationIdCounter++
            currentProgressNotificationId = notificationId
            
            val progressOption = SimpleProgressNotificationOption(
                notificationId = notificationId,
                title = binding.etTitle.text.toString().takeIf { it.isNotBlank() } ?: "Progress Notification",
                content = "Starting progress...",
                isAutoCancel = binding.cbAutoCancel.isChecked,
                onGoing = true, // Progress notifications should be ongoing
                smallIcon = R.drawable.ic_launcher_foreground,
                clickIntent = Intent(this, MainActivity::class.java),
                progressPercent = binding.seekProgress.progress
            )
            
            val success = notificationController.showProgressNotification(progressOption)
            
            if (success) {
                logMessage("✅ Progress notification created!")
                logMessage("ID: $notificationId, Progress: ${binding.seekProgress.progress}%")
                
                // Enable progress control buttons
                binding.btnUpdateProgress.isEnabled = true
                binding.btnCompleteProgress.isEnabled = true
            } else {
                logMessage("❌ Failed to create progress notification")
                currentProgressNotificationId = null
            }
            
        } catch (e: Exception) {
            logMessage("❌ Error creating progress notification: ${e.message}")
            currentProgressNotificationId = null
            e.printStackTrace()
        }
    }

    private fun updateProgress() {
        currentProgressNotificationId?.let { notificationId ->
            try {
                val progress = binding.seekProgress.progress
                val success = notificationController.updateProgress(notificationId, progress)
                
                if (success) {
                    logMessage("✅ Progress updated to $progress%")
                } else {
                    logMessage("❌ Failed to update progress")
                }
                
            } catch (e: Exception) {
                logMessage("❌ Error updating progress: ${e.message}")
                e.printStackTrace()
            }
        } ?: run {
            logMessage("⚠️ No active progress notification to update")
        }
    }

    private fun completeProgress() {
        currentProgressNotificationId?.let { notificationId ->
            try {
                val success = notificationController.completeProgress(
                    notificationId,
                    "Task completed successfully!"
                )
                
                if (success) {
                    logMessage("✅ Progress notification completed")
                    currentProgressNotificationId = null
                    binding.btnUpdateProgress.isEnabled = false
                    binding.btnCompleteProgress.isEnabled = false
                } else {
                    logMessage("❌ Failed to complete progress notification")
                }
                
            } catch (e: Exception) {
                logMessage("❌ Error completing progress: ${e.message}")
                e.printStackTrace()
            }
        } ?: run {
            logMessage("⚠️ No active progress notification to complete")
        }
    }

    private fun cancelCurrentNotification() {
        currentProgressNotificationId?.let { notificationId ->
            try {
                val success = notificationController.cancelNotification(null, notificationId)
                
                if (success) {
                    logMessage("✅ Current notification cancelled")
                    currentProgressNotificationId = null
                    binding.btnUpdateProgress.isEnabled = false
                    binding.btnCompleteProgress.isEnabled = false
                } else {
                    logMessage("❌ Failed to cancel notification")
                }
                
            } catch (e: Exception) {
                logMessage("❌ Error cancelling notification: ${e.message}")
                e.printStackTrace()
            }
        } ?: run {
            logMessage("⚠️ No active notification to cancel")
        }
    }

    private fun cancelAllNotifications() {
        try {
            notificationController.cancelAll()
            logMessage("✅ All notifications cancelled")
            
            currentProgressNotificationId = null
            binding.btnUpdateProgress.isEnabled = false
            binding.btnCompleteProgress.isEnabled = false
            
        } catch (e: Exception) {
            logMessage("❌ Error cancelling all notifications: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun getSelectedNotificationStyle(): NotificationStyle {
        val selectedName = binding.spNotificationStyle.selectedItem.toString()
        return NotificationStyle.valueOf(selectedName)
    }

    private fun getSelectedImportance(): Int {
        return when (binding.spChannelImportance.selectedItemPosition) {
            0 -> NotificationManager.IMPORTANCE_HIGH
            1 -> NotificationManager.IMPORTANCE_DEFAULT
            2 -> NotificationManager.IMPORTANCE_LOW
            3 -> NotificationManager.IMPORTANCE_MIN
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }
    }

    private fun getImportanceName(importance: Int): String {
        return when (importance) {
            NotificationManager.IMPORTANCE_HIGH -> "HIGH"
            NotificationManager.IMPORTANCE_DEFAULT -> "DEFAULT"
            NotificationManager.IMPORTANCE_LOW -> "LOW"
            NotificationManager.IMPORTANCE_MIN -> "MIN"
            else -> "UNKNOWN"
        }
    }

    private fun updateProgressDisplay(progress: Int) {
        binding.tvProgressValue.text = "$progress%"
    }

    private fun logMessage(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message"
        
        runOnUiThread {
            val currentText = binding.tvStatus.text.toString()
            val newText = if (currentText == "Ready to test notifications") {
                logEntry
            } else {
                "$currentText\n$logEntry"
            }
            binding.tvStatus.text = newText
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        logMessage("❌ Error: $message")
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        checkSdkVersion(Build.VERSION_CODES.TIRAMISU) {
            permissions.add(POST_NOTIFICATIONS)
        }
        
        if (permissions.isNotEmpty()) {
            permissionManager.request(
                this,
                requestPermissionLauncher = requestPermissionLauncher,
                specialPermissionLaunchers = null,
                permissions = permissions
            ) { deniedPermissions ->
                if (deniedPermissions.isNotEmpty()) {
                    logMessage("❌ Some permissions denied: $deniedPermissions")
                    showError("Notification permission required for full functionality")
                } else {
                    logMessage("✅ All permissions granted")
                }
            }
        } else {
            logMessage("✅ No additional permissions required")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        
        // Cleanup notification controller resources
        try {
            notificationController.cleanup()
            logMessage("NotificationController resources cleaned up")
        } catch (e: Exception) {
            Logx.e("Error cleaning up NotificationController: ${e.message}")
        }
    }
}