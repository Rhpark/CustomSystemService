package kr.open.library.system_service

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kr.open.library.systemmanager.controller.alarm.AlarmController
import kr.open.library.systemmanager.controller.alarm.dto.AlarmDTO
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

    private lateinit var alarmController: AlarmController
    private val testAlarmId = 12345
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    // UI Components
    private lateinit var etHour: EditText
    private lateinit var etMinute: EditText
    private lateinit var etSecond: EditText
    private lateinit var etTitle: EditText
    private lateinit var etMessage: EditText
    private lateinit var cbAllowIdle: CheckBox
    private lateinit var rgAlarmType: RadioGroup
    private lateinit var rbAlarmClock: RadioButton
    private lateinit var rbExactIdle: RadioButton
    private lateinit var rbAllowIdle: RadioButton
    private lateinit var btnSetAlarm: Button
    private lateinit var btnCancelAlarm: Button
    private lateinit var btnClearLogs: Button
    private lateinit var btnTest30sec: Button
    private lateinit var btnTest1min: Button
    private lateinit var btnTest5min: Button
    private lateinit var tvStatus: TextView
    
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.RECEIVE_BOOT_COMPLETED
        ).let { basePermissions ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                basePermissions + Manifest.permission.SCHEDULE_EXACT_ALARM
            } else {
                basePermissions
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_test)
        
        // Initialize AlarmController
        alarmController = AlarmController(this)
        
        // Initialize UI
        initializeViews()
        setupClickListeners()
        
        // Check permissions
        checkAndRequestPermissions()
        
        // Log initial status
        logMessage("AlarmTestActivity initialized")
        logMessage("AlarmController ready for testing")
    }

    private fun initializeViews() {
        etHour = findViewById(R.id.etHour)
        etMinute = findViewById(R.id.etMinute)
        etSecond = findViewById(R.id.etSecond)
        etTitle = findViewById(R.id.etTitle)
        etMessage = findViewById(R.id.etMessage)
        cbAllowIdle = findViewById(R.id.cbAllowIdle)
        rgAlarmType = findViewById(R.id.rgAlarmType)
        rbAlarmClock = findViewById(R.id.rbAlarmClock)
        rbExactIdle = findViewById(R.id.rbExactIdle)
        rbAllowIdle = findViewById(R.id.rbAllowIdle)
        btnSetAlarm = findViewById(R.id.btnSetAlarm)
        btnCancelAlarm = findViewById(R.id.btnCancelAlarm)
        btnClearLogs = findViewById(R.id.btnClearLogs)
        btnTest30sec = findViewById(R.id.btnTest30sec)
        btnTest1min = findViewById(R.id.btnTest1min)
        btnTest5min = findViewById(R.id.btnTest5min)
        tvStatus = findViewById(R.id.tvStatus)
        
        // Set default values
        etTitle.setText("Test Alarm")
        etMessage.setText("This is a test alarm notification")
        
        // Set current time + 1 minute as default
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, 1)
        etHour.setText(String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY)))
        etMinute.setText(String.format("%02d", calendar.get(Calendar.MINUTE)))
        etSecond.setText("00")
    }

    private fun setupClickListeners() {
        btnSetAlarm.setOnClickListener {
            if (validateInputs()) {
                setAlarm()
            }
        }
        
        btnCancelAlarm.setOnClickListener {
            cancelAlarm()
        }
        
        btnClearLogs.setOnClickListener {
            tvStatus.text = "Logs cleared at ${dateFormat.format(Date())}"
        }
        
        btnTest30sec.setOnClickListener {
            setQuickTestAlarm(30)
        }
        
        btnTest1min.setOnClickListener {
            setQuickTestAlarm(60)
        }
        
        btnTest5min.setOnClickListener {
            setQuickTestAlarm(300)
        }
    }

    private fun validateInputs(): Boolean {
        try {
            val hour = etHour.text.toString().toIntOrNull()
            val minute = etMinute.text.toString().toIntOrNull()
            val second = etSecond.text.toString().toIntOrNull()
            val title = etTitle.text.toString()
            val message = etMessage.text.toString()
            
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
                "ALARM_CLOCK" -> alarmController.registerAlarmClock(TestAlarmReceiver::class.java, alarmDto)
                "EXACT_IDLE" -> alarmController.registerAlarmExactAndAllowWhileIdle(TestAlarmReceiver::class.java, alarmDto)
                "ALLOW_IDLE" -> alarmController.registerAlarmAndAllowWhileIdle(TestAlarmReceiver::class.java, alarmDto)
                else -> false
            }
            
            if (success) {
                logMessage("✅ Alarm set successfully!")
                logMessage("Scheduled for: ${alarmDto.getFormattedTime()}")
                
                // Check if alarm exists
                val exists = alarmController.exists(testAlarmId, TestAlarmReceiver::class.java)
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
            
            val success = alarmController.remove(testAlarmId, TestAlarmReceiver::class.java)
            
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
        
        etHour.setText(String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY)))
        etMinute.setText(String.format("%02d", calendar.get(Calendar.MINUTE)))
        etSecond.setText(String.format("%02d", calendar.get(Calendar.SECOND)))
        etTitle.setText("Quick Test Alarm")
        etMessage.setText("Test alarm triggered in ${delaySeconds}s")
        
        logMessage("Quick test alarm set for ${delaySeconds}s from now")
        
        if (validateInputs()) {
            setAlarm()
        }
    }

    private fun createAlarmDTO(): AlarmDTO {
        return AlarmDTO(
            key = testAlarmId,
            title = etTitle.text.toString(),
            message = etMessage.text.toString(),
            isActive = true,
            isAllowIdle = cbAllowIdle.isChecked,
            hour = etHour.text.toString().toInt(),
            minute = etMinute.text.toString().toInt(),
            second = etSecond.text.toString().toInt()
        )
    }

    private fun getSelectedAlarmType(): String {
        return when (rgAlarmType.checkedRadioButtonId) {
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
            val currentText = tvStatus.text.toString()
            val newText = if (currentText == "Ready to set alarm") {
                logEntry
            } else {
                "$currentText\n$logEntry"
            }
            tvStatus.text = newText
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        logMessage("❌ Error: $message")
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            logMessage("Requesting permissions: ${missingPermissions.joinToString()}")
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            logMessage("All required permissions granted")
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
}