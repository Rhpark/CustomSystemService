package kr.open.library.system_service

import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.RECEIVE_BOOT_COMPLETED
import android.Manifest.permission.SCHEDULE_EXACT_ALARM
import android.Manifest.permission.USE_EXACT_ALARM
import android.Manifest.permission.WAKE_LOCK
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import kotlinx.coroutines.*
import kr.open.library.logcat.Logx
import kr.open.library.permissions.PermissionManager
import kr.open.library.system_service.R
import kr.open.library.system_service.databinding.ActivityAlarmTestBinding
import kr.open.library.systemmanager.controller.alarm.AlarmController
import kr.open.library.systemmanager.controller.alarm.dto.AlarmDTO
import kr.open.library.systemmanager.extenstions.checkSdkVersion
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for testing the AlarmController functionality.
 * Provides a comprehensive UI to test different alarm types and configurations.
 * 
 * AlarmController 기능을 테스트하기 위한 액티비티입니다.
 * 다양한 알람 유형과 구성을 테스트할 수 있는 포괄적인 UI를 제공합니다.
 */
class AlarmTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmTestBinding
    private lateinit var alarmController: AlarmController
    private val testAlarmId = 12345
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var currentRequestId: String? = null
    // 일반 권한 요청 런처
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionManager.result(this, permissions, currentRequestId)
    }

    private val permissionManager:PermissionManager by lazy {
        PermissionManager.getInstance()
    }

    
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(
            WAKE_LOCK,
            RECEIVE_BOOT_COMPLETED
        ).let { basePermissions ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                basePermissions + SCHEDULE_EXACT_ALARM
            } else {
                basePermissions
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBinding()
        
        // Initialize AlarmController
        alarmController = AlarmController(this)
        
        // Initialize UI
        setupClickListeners()
        initializeDefaultValues()
        
        // Check permissions
        checkAndRequestPermissions()
        
        // Log initial status
        logMessage("AlarmTestActivity initialized")
        logMessage("AlarmController ready for testing")
    }

    private fun setupBinding() {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_alarm_test)
    }
    
    private fun initializeDefaultValues() {
        // Set default values
        binding.etTitle.setText("Test Alarm")
        binding.etMessage.setText("This is a test alarm notification")
        
        // Set current time + 1 minute as default
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, 1)
        binding.etHour.setText(String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY)))
        binding.etMinute.setText(String.format("%02d", calendar.get(Calendar.MINUTE)))
        binding.etSecond.setText("00")
    }

    private fun setupClickListeners() {
        binding.btnSetAlarm.setOnClickListener {
            if (validateInputs()) {
                setAlarm()
            }
        }
        
        binding.btnCancelAlarm.setOnClickListener {
            cancelAlarm()
        }
        
        binding.btnClearLogs.setOnClickListener {
            binding.tvStatus.text = "Logs cleared at ${dateFormat.format(Date())}"
        }
        
        binding.btnTest30sec.setOnClickListener {
            setQuickTestAlarm(30)
        }
        
        binding.btnTest1min.setOnClickListener {
            setQuickTestAlarm(60)
        }
        
        binding.btnTest5min.setOnClickListener {
            setQuickTestAlarm(300)
        }
    }

    private fun validateInputs(): Boolean {
        try {
            val hour = binding.etHour.text.toString().toIntOrNull()
            val minute = binding.etMinute.text.toString().toIntOrNull()
            val second = binding.etSecond.text.toString().toIntOrNull()
            val title = binding.etTitle.text.toString()
            val message = binding.etMessage.text.toString()
            
            when {
                hour == null || hour !in 0..23 -> {
                    showError("Hour must be between 0-23")
                    return false
                }
                minute == null || minute !in 0..59 -> {
                    showError("Minute must be between 0-59")
                    return false
                }
                second == null || second !in 0..59 -> {
                    showError("Second must be between 0-59")
                    return false
                }
                title.isBlank() -> {
                    showError("Title cannot be empty")
                    return false
                }
                message.isBlank() -> {
                    showError("Message cannot be empty")
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            showError("Invalid input: ${e.message}")
            return false
        }
    }

    private fun setAlarm() {
        try {
            val alarmDto = createAlarmDTO()
            val alarmType = getSelectedAlarmType()
            
            logMessage("Setting alarm: ${alarmDto.getDescription()}")
            logMessage("Alarm type: $alarmType")
            
            val success = when (alarmType) {
                "ALARM_CLOCK" -> alarmController.registerAlarmClock(TestAlarmReceiver::class.java, alarmDto).isSuccess
                "EXACT_IDLE" -> alarmController.registerAlarmExactAndAllowWhileIdle(TestAlarmReceiver::class.java, alarmDto).isSuccess
                "ALLOW_IDLE" -> alarmController.registerAlarmAndAllowWhileIdle(TestAlarmReceiver::class.java, alarmDto).isSuccess
                else -> false
            }
            
            if (success) {
                logMessage("✅ Alarm set successfully!")
                logMessage("Scheduled for: ${alarmDto.getFormattedTime()}")
                
                // Check if alarm exists
                val exists = alarmController.exists(testAlarmId, TestAlarmReceiver::class.java).getOrDefault(false)
                logMessage("Alarm exists check: $exists")
            } else {
                logMessage("❌ Failed to set alarm")
            }
            
        } catch (e: Exception) {
            logMessage("❌ Error setting alarm: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun cancelAlarm() {
        try {
            logMessage("Cancelling alarm with ID: $testAlarmId")
            
            val success = alarmController.remove(testAlarmId, TestAlarmReceiver::class.java).getOrDefault(false)
            
            if (success) {
                logMessage("✅ Alarm cancelled successfully!")
            } else {
                logMessage("⚠️ No alarm found with ID: $testAlarmId")
            }
            
        } catch (e: Exception) {
            logMessage("❌ Error cancelling alarm: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun setQuickTestAlarm(delaySeconds: Int) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, delaySeconds)
        
        binding.etHour.setText(String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY)))
        binding.etMinute.setText(String.format("%02d", calendar.get(Calendar.MINUTE)))
        binding.etSecond.setText(String.format("%02d", calendar.get(Calendar.SECOND)))
        binding.etTitle.setText("Quick Test Alarm")
        binding.etMessage.setText("Test alarm triggered in ${delaySeconds}s")
        
        logMessage("Quick test alarm set for ${delaySeconds}s from now")
        
        if (validateInputs()) {
            setAlarm()
        }
    }

    private fun createAlarmDTO(): AlarmDTO {
        return AlarmDTO(
            key = testAlarmId,
            title = binding.etTitle.text.toString(),
            message = binding.etMessage.text.toString(),
            isActive = true,
            isAllowIdle = binding.cbAllowIdle.isChecked,
            hour = binding.etHour.text.toString().toInt(),
            minute = binding.etMinute.text.toString().toInt(),
            second = binding.etSecond.text.toString().toInt()
        )
    }

    private fun getSelectedAlarmType(): String {
        return when (binding.rgAlarmType.checkedRadioButtonId) {
            R.id.rbAlarmClock -> "ALARM_CLOCK"
            R.id.rbExactIdle -> "EXACT_IDLE"
            R.id.rbAllowIdle -> "ALLOW_IDLE"
            else -> "ALARM_CLOCK"
        }
    }

    private fun logMessage(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message"
        
        runOnUiThread {
            val currentText = binding.tvStatus.text.toString()
            val newText = if (currentText == "Ready to set alarm") {
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
        permissionManager.request(
            this, requestPermissionLauncher = requestPermissionLauncher,
            specialPermissionLaunchers = null,
            permissions = getPermissionList()
        ) { deniedPermissions ->
            Logx.d("deniedPermissions $deniedPermissions")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                logMessage("✅ All permissions granted")
            } else {
                logMessage("❌ Some permissions denied - alarm functionality may be limited")
                
                permissions.forEachIndexed { index, permission ->
                    val result = if (grantResults[index] == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
                    logMessage("$permission: $result")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        logMessage("AlarmTestActivity destroyed")
    }

    private fun getPermissionList(): List<String> {
        val list = mutableListOf<String>()
        list.add(RECEIVE_BOOT_COMPLETED)
        list.add(WAKE_LOCK)
        checkSdkVersion(Build.VERSION_CODES.TIRAMISU) {
            list.add(USE_EXACT_ALARM)
            list.add(POST_NOTIFICATIONS)
        }
        checkSdkVersion(Build.VERSION_CODES.S) {
            list.add(SCHEDULE_EXACT_ALARM)
        }
        return list.toList()
    }
}