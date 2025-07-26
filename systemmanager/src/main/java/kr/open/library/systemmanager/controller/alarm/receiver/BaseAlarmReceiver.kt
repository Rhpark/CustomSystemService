package kr.open.library.systemmanager.controller.alarm.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.alarm.AlarmController
import kr.open.library.systemmanager.controller.alarm.dto.AlarmDTO
import kr.open.library.systemmanager.controller.alarm.vo.AlarmVO.ALARM_KEY
import kr.open.library.systemmanager.controller.alarm.vo.AlarmVO.ALARM_KEY_DEFAULT_VALUE
import kr.open.library.systemmanager.controller.notification.SimpleNotificationController
import kr.open.library.systemmanager.extenstions.getAlarmController
import kr.open.library.systemmanager.extenstions.getPowerManager


public abstract class BaseAlarmReceiver() : BroadcastReceiver() {

    protected lateinit var notificationController: SimpleNotificationController

    protected abstract val registerType: RegisterType
    protected abstract val classType: Class<*>

    protected abstract fun createNotificationChannel(context: Context, alarmDTO: AlarmDTO)

    protected abstract fun showNotification(context: Context, alarmDTO: AlarmDTO)

    protected abstract fun loadAllAlarmDtoList(context: Context): List<AlarmDTO>
    protected abstract fun loadAlarmDtoList(context:Context, intent: Intent, key:Int): AlarmDTO?

    protected abstract val powerManagerAcquireTime: Long

    @SuppressLint("InvalidWakeLockTag")
    override fun onReceive(context: Context?, intent: Intent?) {
        Logx.d()
        if(context == null || intent == null) return

        Logx.d("BaseAlarmReceiver onReceive")
        val pm = context.getPowerManager()
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE, "AlarmReceiver").apply {
            acquire(powerManagerAcquireTime)
        }
        val alarmController = context.getAlarmController()

        Logx.d(" intent.action ${intent.action}")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            //data load
            loadAllAlarmDtoList(context).forEach { registerAlarm(alarmController, it) }
        } else {
            // alarmDto Load
            val key = intent.getIntExtra(ALARM_KEY, ALARM_KEY_DEFAULT_VALUE)
            loadAlarmDtoList(context, intent, key)?.let {
                createNotificationChannel(context, it)
                showNotification(context, it)
            } ?: Logx.e("Failed to load AlarmDTO for key: $key")
        }
        wl.release()
    }

    private fun registerAlarm(alarmController: AlarmController, alarmDto: AlarmDTO): Unit =
        when (registerType) {
            RegisterType.ALARM_AND_ALLOW_WHILE_IDLE -> {
                alarmController.registerAlarmAndAllowWhileIdle(classType, alarmDto)
            }

            RegisterType.ALARM_CLOCK -> {
                alarmController.registerAlarmClock(classType, alarmDto)
            }

            RegisterType.ALARM_EXACT_AND_ALLOW_WHILE_IDLE -> {
                alarmController.registerAlarmExactAndAllowWhileIdle(classType, alarmDto)
            }
        }

    public enum class RegisterType {
        ALARM_CLOCK,
        ALARM_AND_ALLOW_WHILE_IDLE,
        ALARM_EXACT_AND_ALLOW_WHILE_IDLE
    }
}
