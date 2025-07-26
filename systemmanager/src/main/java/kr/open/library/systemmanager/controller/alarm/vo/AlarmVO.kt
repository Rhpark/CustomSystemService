package kr.open.library.systemmanager.controller.alarm.vo

/**
 * Constants for alarm functionality throughout the system
 * 시스템 전체에서 사용되는 알람 관련 상수들
 */
public object AlarmConstants {
    
    /**
     * Intent extras
     */
    public const val ALARM_KEY: String = "AlarmKey"
    public const val ALARM_KEY_DEFAULT_VALUE: Int = -1
    
    /**
     * WakeLock settings
     */
    public const val WAKELOCK_TAG: String = "SystemManager:AlarmReceiver"
    public const val WAKELOCK_TIMEOUT_MS: Long = 10 * 60 * 1000L // 10 minutes
    public const val DEFAULT_ACQUIRE_TIME_MS: Long = 3000L // 3 seconds
    
    /**
     * Calendar settings
     */
    public const val MILLISECONDS_IN_SECOND: Long = 1000L
    public const val SECONDS_IN_MINUTE: Long = 60L
    public const val MINUTES_IN_HOUR: Long = 60L
    
    /**
     * Alarm type identifiers
     */
    public const val ALARM_TYPE_CLOCK: String = "ALARM_CLOCK"
    public const val ALARM_TYPE_IDLE: String = "ALLOW_WHILE_IDLE"
    public const val ALARM_TYPE_EXACT_IDLE: String = "EXACT_AND_ALLOW_WHILE_IDLE"
    
    /**
     * Error codes
     */
    public const val ERROR_INVALID_TIME: Int = -1001
    public const val ERROR_PENDING_INTENT_FAILED: Int = -1002
    public const val ERROR_ALARM_REGISTRATION_FAILED: Int = -1003
}