package kr.open.library.systemmanager.controller.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import kr.open.library.systemmanager.base.BaseSystemService
import kr.open.library.systemmanager.controller.alarm.dto.AlarmDTO
import kr.open.library.systemmanager.controller.alarm.receiver.BaseAlarmReceiver
import kr.open.library.systemmanager.extenstions.getAlarmManager


/**
 *
 */
public open class AlarmController(context: Context) : BaseSystemService(context) {

    public val alarmManager: AlarmManager by lazy { context.getAlarmManager() }

    public fun registerAlarmClock(receiver: Class<*>, alarmDto: AlarmDTO) {
        val calendar = getCalendar(alarmDto)
        val pendingIntent = getAlarmPendingIntent(receiver, alarmDto.key)
        val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }

    public fun registerAlarmExactAndAllowWhileIdle(receiver: Class<*>, alarmDto: AlarmDTO) {
        val calendar = getCalendar(alarmDto)
        val pendingIntent = getAlarmPendingIntent(receiver, alarmDto.key)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
    }

    public fun registerAlarmAndAllowWhileIdle(receiver: Class<*>, alarmDto: AlarmDTO) {
        val calendar = getCalendar(alarmDto)
        val pendingIntent = getAlarmPendingIntent(receiver, alarmDto.key)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
    }

    private fun getAlarmPendingIntent(receiver: Class<*>, key: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            key,
            Intent(context, receiver).apply { putExtra("AlarmKey", key) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun getCalendar(alarmDto: AlarmDTO): Calendar = Calendar.getInstance().apply {
//        timeInMillis = System.currentTimeMillis()
        set(Calendar.HOUR_OF_DAY, alarmDto.hour)
        set(Calendar.MINUTE, alarmDto.minute)
        set(Calendar.SECOND, alarmDto.second)
//        set(Calendar.SECOND, 10)
        if (before(this@apply)) {
            add(Calendar.DATE, 1)
        }
    }

    public fun remove(key: Int) {
        val intent = Intent(context, BaseAlarmReceiver::class.java)
        val pendingIntent =
            PendingIntent.getBroadcast(context, key, intent, PendingIntent.FLAG_NO_CREATE)

        pendingIntent?.let { alarmManager.cancel(it) }
    }
}