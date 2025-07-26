package kr.open.library.systemmanager.controller.notification.vo

/**
 * 알림 스타일을 정의하는 enum 클래스
 * Enum class that defines notification styles
 */
public enum class NotificationStyle {
    /**
     * 기본 알림 스타일
     * Default notification style
     */
    DEFAULT,
    
    /**
     * 큰 이미지를 포함하는 알림 스타일
     * Notification style with big picture
     */
    BIG_PICTURE,
    
    /**
     * 긴 텍스트를 포함하는 알림 스타일
     * Notification style with expanded text
     */
    BIG_TEXT,
    
    /**
     * 진행률 바를 포함하는 알림 스타일
     * Notification style with progress bar
     */
    PROGRESS
}