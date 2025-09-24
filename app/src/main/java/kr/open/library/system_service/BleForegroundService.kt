package kr.open.library.system_service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.controller.bluetooth.SimpleBleController
import kotlinx.coroutines.*

/**
 * BLE 작업을 위한 Foreground Service
 * Android 15에서 GATT 서버 안정성을 위해 필요
 */
class BleForegroundService : Service() {
    
    companion object {
        private const val TAG = "BleForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "BLE_SERVICE_CHANNEL"
        private const val CHANNEL_NAME = "BLE Service"
    }
    
    private val binder = BleBinder()
    private var bleController: SimpleBleController? = null
    private var isServiceRunning = false
    
    // Service에 CoroutineScope 추가
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Service Binder
    inner class BleBinder : Binder() {
        fun getService(): BleForegroundService = this@BleForegroundService
    }
    
    override fun onCreate() {
        super.onCreate()
        Logx.d(TAG, "BleForegroundService created")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        isServiceRunning = true
        
        // BLE 컨트롤러 초기화
        initializeBleController()
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logx.d(TAG, "Service started with command")
        return START_STICKY // 서비스가 종료되면 자동 재시작
    }
    
    override fun onDestroy() {
        Logx.d(TAG, "BleForegroundService destroyed")
        isServiceRunning = false
        
        // BLE 리소스 정리 - suspend 함수를 runBlocking으로 호출
        bleController?.let { controller ->
            runBlocking {
                try {
                    controller.cleanup()
                    Logx.d(TAG, "BLE controller cleanup completed")
                } catch (e: Exception) {
                    Logx.e(TAG, "Error during BLE cleanup: ${e.message}")
                }
            }
        }
        bleController = null
        
        // CoroutineScope 정리
        serviceScope.cancel()
        
        super.onDestroy()
    }
    
    /**
     * BLE 컨트롤러 초기화 - suspend 함수를 serviceScope로 호출
     */
    private fun initializeBleController() {
        try {
            bleController = SimpleBleController(this)
            
            // suspend 함수를 coroutine으로 호출
            serviceScope.launch {
                try {
                    val initialized = bleController!!.initialize()
                    if (initialized) {
                        Logx.i(TAG, "BLE controller initialized in foreground service")
                        updateNotification("BLE 서비스 실행 중")
                    } else {
                        Logx.e(TAG, "Failed to initialize BLE controller in foreground service")
                        updateNotification("BLE 초기화 실패")
                    }
                } catch (e: Exception) {
                    Logx.e(TAG, "Exception during BLE initialization: ${e.message}")
                    updateNotification("BLE 초기화 오류")
                }
            }
        } catch (e: Exception) {
            Logx.e(TAG, "Exception creating BLE controller: ${e.message}")
            updateNotification("BLE 서비스 오류")
        }
    }
    
    /**
     * 알림 채널 생성 (Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "BLE 서비스 상태 알림"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 포그라운드 서비스 알림 생성
     */
    private fun createNotification(message: String = "BLE 서비스 시작 중..."): Notification {
        val notificationIntent = Intent(this, BleTestActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE 서비스")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    /**
     * 알림 업데이트
     */
    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    // Public API
    fun getBleController(): SimpleBleController? = bleController
    
    fun isRunning(): Boolean = isServiceRunning
    
    fun restartBleController() {
        Logx.d(TAG, "Restarting BLE controller...")
        
        // suspend 함수를 serviceScope로 호출
        serviceScope.launch {
            try {
                // 기존 컨트롤러 정리
                bleController?.cleanup()
                bleController = null
                
                // 잠시 대기 (non-blocking delay 사용)
                delay(500)
                
                // 새 컨트롤러 초기화
                initializeBleController()
            } catch (e: Exception) {
                Logx.e(TAG, "Exception during BLE controller restart: ${e.message}")
                updateNotification("BLE 재시작 실패")
            }
        }
    }
}