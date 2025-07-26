package kr.open.library.system_service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.alarm.dto.AlarmDTO
import kr.open.library.systemmanager.controller.alarm.receiver.BaseAlarmReceiver
import kr.open.library.systemmanager.controller.alarm.vo.AlarmConstants

/**
 * Test implementation of BaseAlarmReceiver for demonstrating alarm functionality.
 * Creates notifications when alarms are triggered and handles boot completion.
 * 
 * 알람 기능을 시연하기 위한 BaseAlarmReceiver의 테스트 구현입니다.
 * 알람이 트리거될 때 알림을 생성하고 부팅 완료를 처리합니다.
 */
class TestAlarmReceiver : BaseAlarmReceiver() {

    companion object {
        private const val CHANNEL_ID = "test_alarm_channel"
        private const val CHANNEL_NAME = "Test Alarm Notifications"
        private const val CHANNEL_DESCRIPTION = "Notifications for testing alarm functionality"
        private const val NOTIFICATION_ID_BASE = 2000
    }

    override val registerType: RegisterType = RegisterType.ALARM_CLOCK
    override val classType: Class<*> = TestAlarmReceiver::class.java
    override val powerManagerAcquireTime: Long = AlarmConstants.DEFAULT_ACQUIRE_TIME_MS

    override fun createNotificationChannel(context: Context, alarmDTO: AlarmDTO) {
        // Only create notification channels on API 26+ (Android O and higher)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Logx.d("Notification channel created: $CHANNEL_ID")
        }
    }

    override fun showNotification(context: Context, alarmDTO: AlarmDTO) {
        try {
            // Create the notification
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert) // Using system icon for compatibility
                .setContentTitle(alarmDTO.title)
                .setContentText(alarmDTO.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "${alarmDTO.message}\n\n" +
                    "Alarm ID: ${alarmDTO.key}\n" +
                    "Scheduled Time: ${alarmDTO.getFormattedTime()}\n" +
                    "Allow Idle: ${alarmDTO.isAllowIdle}"
                ))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            // Show the notification
            val notificationManager = NotificationManagerCompat.from(context)
            val notificationId = NOTIFICATION_ID_BASE + alarmDTO.key
            
            try {
                notificationManager.notify(notificationId, notification)
                Logx.d("Test alarm notification shown: ID=${alarmDTO.key}, Title='${alarmDTO.title}'")
            } catch (e: SecurityException) {
                Logx.e("Permission denied for showing notification: ${e.message}")
                // In case notification permission is missing on API 33+
            }

        } catch (e: Exception) {
            Logx.e("Error showing test alarm notification: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun loadAllAlarmDtoList(context: Context): List<AlarmDTO> {
        // For testing purposes, we don't persist alarms
        // In a real implementation, you would load from SharedPreferences, Database, etc.
        Logx.d("TestAlarmReceiver.loadAllAlarmDtoList called - returning empty list for test")
        return emptyList()
    }

    override fun loadAlarmDtoList(context: Context, intent: Intent, key: Int): AlarmDTO? {
        // For testing purposes, create a simple AlarmDTO from the intent data
        // In a real implementation, you would load the full AlarmDTO from storage
        try {
            val currentTime = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = currentTime
            }
            
            val testAlarmDto = AlarmDTO.createSimple(
                key = key,
                title = "Test Alarm Triggered",
                message = "This test alarm was triggered successfully at ${
                    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                }",
                hour = calendar.get(java.util.Calendar.HOUR_OF_DAY),
                minute = calendar.get(java.util.Calendar.MINUTE)
            )
            
            Logx.d("TestAlarmReceiver.loadAlarmDtoList: Created test AlarmDTO for key=$key")
            return testAlarmDto
            
        } catch (e: Exception) {
            Logx.e("Error creating test AlarmDTO: ${e.message}")
            return null
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Logx.d("TestAlarmReceiver.onReceive called with action: ${intent?.action}")
        
        // Log the alarm trigger for debugging
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            val alarmKey = intent?.getIntExtra(AlarmConstants.ALARM_KEY, AlarmConstants.ALARM_KEY_DEFAULT_VALUE)
            Logx.d("🔔 Test alarm triggered! Key: $alarmKey")
        }
        
        // Call parent implementation to handle the alarm
        super.onReceive(context, intent)
    }
}