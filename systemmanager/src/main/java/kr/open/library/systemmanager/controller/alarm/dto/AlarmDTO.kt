package kr.open.library.systemmanager.controller.alarm.dto

import android.net.Uri

/**
 *
 */
public class AlarmDTO(
    public val key: Int,
    public val title: String,
    public val msg: String,
    public val isActive: Boolean = false,
    public val isAllowIdle:Boolean= false,
    public val vibrationEffect: LongArray?= null,
    public val sound: Uri? = null, //RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    public val hour: Int,
    public val minute: Int,
    public val second: Int,
    public val acquireTime: Long = 3000
) {
}