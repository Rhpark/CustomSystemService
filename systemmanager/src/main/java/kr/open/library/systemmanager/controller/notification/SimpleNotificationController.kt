package kr.open.library.systemmanager.controller.notification

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_LOW
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.base.BaseSystemService
import kr.open.library.systemmanager.controller.notification.dto.SimpleNotificationOption
import kr.open.library.systemmanager.controller.notification.dto.SimplePendingIntentOption
import kr.open.library.systemmanager.controller.notification.dto.SimpleProgressNotificationOption
import kr.open.library.systemmanager.controller.notification.vo.SimpleNotificationType
import kr.open.library.systemmanager.extenstions.getNotificationManager
import kr.open.library.systemmanager.extenstions.safeCatch


/**
 * <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
 *
 * NotificationManager.IMPORTANCE_HIGH	긴급 상황이며 알림음이 울리며 헤드업으로 표시
 * NotificationManager.IMPORTANCE_DEFAULT	높은 중요도이며 알림음이 울림
 * NotificationManager.IMPORTANCE_LOW	중간 중요도이며 알림음이 울리지 않음
 * NotificationManager.IMPORTANCE_MIN   낮은 중요도이며 알림음이 없고 상태표시줄에 표시되지 않음
 *
 * @param context 컨텍스트
 * @param showType 알림 클릭 시 동작 유형 (Activity, Service, Broadcast)
 */
public open class SimpleNotificationController(context: Context, private val showType: SimpleNotificationType)
    : BaseSystemService(context, listOf(POST_NOTIFICATIONS)) {

    public val notificationManager: NotificationManager by lazy { context.getNotificationManager() }

    // 진행률 알림 빌더들을 저장하는 맵 (ID별 관리)
    private val progressBuilders = mutableMapOf<Int, NotificationCompat.Builder>()

    // 현재 설정된 알림 채널
    private var currentChannel: NotificationChannel? = null

    /**
     * NotificationChannel을 생성 및 등록하고 현재 채널로 설정.
     */
    public fun createChannel(notificationChannel: NotificationChannel) {
        currentChannel = notificationChannel
        notificationManager.createNotificationChannel(notificationChannel)
    }

    /**
     * 알림 채널을 생성하여 등록합니다.
     * @param channelId 채널 ID
     * @param channelName 채널 이름
     * @param importance 중요도 (IMPORTANCE_HIGH, DEFAULT, LOW, MIN)
     * @param description 채널 설명 (선택사항)
     */
    public fun createChannel(channelId: String, channelName: String, importance: Int, description: String? = null) {
        createChannel(NotificationChannel(channelId, channelName, importance).apply {
            description?.let { this.description = it }
        })
    }

    /**
     * 기본 알림을 표시합니다.
     * @param notificationOption 알림 옵션
     * @return 성공 여부
     */
    public fun showNotification(notificationOption: SimpleNotificationOption): Boolean =
        safeCatch("Failed to show notification", false) {
            showNotification(notificationOption.notificationId, getBuilder(notificationOption))
            true
        }

    /**
     * 알림 빌더를 생성합니다.
     * @param notificationOption 알림 옵션
     * @return NotificationCompat.Builder
     */
    public fun getBuilder(notificationOption: SimpleNotificationOption):NotificationCompat.Builder {
        return with(notificationOption) {
            currentChannel?.let { channel ->
                val builder = NotificationCompat.Builder(context, channel.id).apply {
                    title?.let { setContentTitle(it) }
                    content?.let { setContentText(it) }
                    setAutoCancel(isAutoCancel)
                    smallIcon?.let { setSmallIcon(it) }
                    largeIcon?.let { setLargeIcon(it) }
                    setOngoing(onGoing)
                    clickIntent?.let {
                        setContentIntent(getPendingIntentType(SimplePendingIntentOption(notificationId, it)))
                    }
                    actions?.forEach { addAction(it) }
                }
                builder
            }
        }?: throw IllegalStateException("Notification channel not created. Call createChannel() first.")
    }

    /**
     * Progress 알림 빌더를 생성합니다.
     * @param notificationOption 진행률 알림 옵션
     * @return NotificationCompat.Builder (진행률 바 포함)
     */
    public fun getProgressBuilder(notificationOption: SimpleProgressNotificationOption):NotificationCompat.Builder {
        return with(notificationOption) {
            currentChannel?.let { channel ->
                val builder = NotificationCompat.Builder(context, channel.id).apply {
                    title?.let { setContentTitle(it) }
                    content?.let { setContentText(it) }
                    setAutoCancel(isAutoCancel)
                    smallIcon?.let { setSmallIcon(it) }
                    setOngoing(onGoing)
                    clickIntent?.let {
                        setContentIntent(getPendingIntentType(SimplePendingIntentOption(notificationId, it)))
                    }
                    actions?.forEach { addAction(it) }
                    setPriority(PRIORITY_LOW)
                    setProgress(100, progressPercent, false)
                }

                progressBuilders[notificationId] = builder // 진행률 업데이트를 위해 빌더 저장
                builder
            }
        }?: throw IllegalStateException("Notification channel not created. Call createChannel() first.")
    }

    /**
     * 알림을 실제로 표시하는 내부 메서드
     */
    private fun showNotification(notificationId: Int, builder: NotificationCompat.Builder) {
        notificationManager.notify(notificationId, builder.build())
    }

    private fun getPendingIntentType(pendingIntentOption: SimplePendingIntentOption) = when(showType) {
        SimpleNotificationType.ACTIVITY -> getClickShowActivityPendingIntent(pendingIntentOption)
        SimpleNotificationType.SERVICE -> getClickShowServicePendingIntent(pendingIntentOption)
        SimpleNotificationType.BROADCAST -> getClickShowBroadcastPendingIntent(pendingIntentOption)
    }

    public fun getClickShowActivityPendingIntent(pendingIntentOption: SimplePendingIntentOption): PendingIntent =
        with(pendingIntentOption) { PendingIntent.getActivity(context, actionId, clickIntent, flags) }

    public fun getClickShowServicePendingIntent(pendingIntentOption: SimplePendingIntentOption): PendingIntent =
        with(pendingIntentOption) { PendingIntent.getService(context, actionId, clickIntent, flags) }

    public fun getClickShowBroadcastPendingIntent(pendingIntentOption: SimplePendingIntentOption): PendingIntent =
        with(pendingIntentOption) { PendingIntent.getBroadcast(context, actionId, clickIntent, flags) }

    /**
     * 직접 알림을 표시합니다.
     * @param notificationId 알림 ID
     * @param build 빌드된 알림 객체
     */
    public fun notify(notificationId: Int, build: Notification) {
        notificationManager.notify(notificationId, build)
    }

    /**
     * 큰 이미지 스타일 알림을 표시합니다.
     * @param notificationOption 알림 옵션
     * @return 성공 여부
     */
    public fun showNotificationBigImage(notificationOption: SimpleNotificationOption): Boolean =
        safeCatch("Failed to show notification", false) {
            with(notificationOption) {
                val builder = getBuilder(notificationOption).apply {
                    setStyle(NotificationCompat.BigPictureStyle().bigPicture(largeIcon))
                }
                showNotification(notificationId, builder)
            }
            true
        }

    /**
     * 긴 텍스트 스타일 알림을 표시합니다.
     * @param notificationOption 알림 옵션
     * @return 성공 여부
     */
    public fun showNotificationBigText(notificationOption: SimpleNotificationOption): Boolean =
        safeCatch("Failed to show notification", false) {
            with(notificationOption) {
                val builder = getBuilder(notificationOption).apply {
                    setStyle(NotificationCompat.BigTextStyle().bigText(snippet))
                }
                showNotification(notificationId, builder)
            }
            true
        }

    /**
     * 진행률 알림을 생성합니다.
     * @param simpleProgressNotificationOption 진행률 알림 옵션
     * @return 생성 성공 여부
     */
    public fun showProgressNotification(
        simpleProgressNotificationOption: SimpleProgressNotificationOption
    ): Boolean = safeCatch("Failed to create progress notification", false) {
        with(simpleProgressNotificationOption) {
            val builder = getProgressBuilder(simpleProgressNotificationOption)
            showNotification(notificationId, builder)
        }
        true
    }

    /**
     * 진행률을 업데이트합니다.
     * @param notificationId 알림 ID
     * @param progressPercent 진행률 (0~100)
     * @return 업데이트 성공 여부
     */
    public fun updateProgress(notificationId: Int, progressPercent: Int): Boolean {
        // 진행률 범위 검증
        if (progressPercent !in 0..100) {
            Logx.w("Invalid progress: $progressPercent (must be 0 ~ 100)")
            return false
        }
        return safeCatch("Failed to update progress", false) {
            progressBuilders[notificationId]?.let { builder ->
                builder.setProgress(100, progressPercent, false)
                showNotification(notificationId, builder)
                true
            } ?: run {
                Logx.w("Progress notification not found for ID: $notificationId")
                false
            }
        }
    }

    /**
     * 진행률 알림을 완료 상태로 변경합니다.
     * @param notificationId 알림 ID
     * @param completedContent 완료 메시지
     * @return 완료 처리 성공 여부
     */
    public fun completeProgress(notificationId: Int, completedContent: String? = null): Boolean =
        safeCatch("Failed to complete progress", false) {
            progressBuilders[notificationId]?.let { builder ->
                builder.setProgress(0, 0, false) // 진행률 바 제거
                completedContent?.let { builder.setContentText(it) }
                showNotification(notificationId, builder)
                progressBuilders.remove(notificationId) // 빌더 제거로 메모리 정리
                true
            } ?: run {
                Logx.w("Progress notification not found for ID: $notificationId")
                false
            }
        }

    /**
     * 특정 알림을 취소합니다.
     * @param tag 알림 태그 (선택사항)
     * @param notificationId 알림 ID
     * @return 취소 성공 여부
     */
    public fun cancelNotification(tag: String?, notificationId: Int): Boolean =
        safeCatch("Failed to cancel notification", false) {
            tag?.let { notificationManager.cancel(tag, notificationId) }
                ?: notificationManager.cancel(notificationId)
            progressBuilders.remove(notificationId) // 진행률 빌더도 함께 제거
            true
        }

    /**
     * 모든 알림을 취소합니다.
     */
    public fun cancelAll(): Unit {
        notificationManager.cancelAll()
        progressBuilders.clear()
    }
}
