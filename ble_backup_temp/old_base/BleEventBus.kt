package kr.open.library.systemmanager.controller.bluetooth.base

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class BleEventBus {
    
    companion object {
        private const val TAG = "BleEventBus"
    }
    
    // 이벤트 타입별 리스너 관리
    private val eventListeners = ConcurrentHashMap<Class<*>, CopyOnWriteArraySet<Any>>()
    private var isInitialized = false
    
    fun initialize() {
        Log.d(TAG, "Initializing event bus...")
        isInitialized = true
        Log.d(TAG, "Event bus initialized")
    }
    
    fun cleanup() {
        Log.d(TAG, "Cleaning up event bus...")
        eventListeners.clear()
        isInitialized = false
        Log.d(TAG, "Event bus cleaned up")
    }
    
    // 리스너 등록
    inline fun <reified T> subscribe(listener: (T) -> Unit) {
        val eventType = T::class.java
        val listeners = eventListeners.getOrPut(eventType) { CopyOnWriteArraySet() }
        listeners.add(listener)
        
        Log.d(TAG, "Subscribed to ${eventType.simpleName}, total listeners: ${listeners.size}")
    }
    
    // 리스너 해제
    inline fun <reified T> unsubscribe(listener: (T) -> Unit) {
        val eventType = T::class.java
        eventListeners[eventType]?.remove(listener)
        
        Log.d(TAG, "Unsubscribed from ${eventType.simpleName}")
    }
    
    // 이벤트 발송
    fun <T> post(event: T) {
        if (!isInitialized) {
            Log.w(TAG, "Event bus not initialized, ignoring event: ${event?.javaClass?.simpleName}")
            return
        }
        
        val eventType = event?.javaClass ?: return
        val listeners = eventListeners[eventType] ?: return
        
        Log.d(TAG, "Posting ${eventType.simpleName} to ${listeners.size} listeners")
        
        listeners.forEach { listener ->
            try {
                @Suppress("UNCHECKED_CAST")
                (listener as (T) -> Unit)(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error in event listener for ${eventType.simpleName}", e)
            }
        }
    }
    
    // 상태 조회
    fun getSubscriberCount(eventType: Class<*>): Int {
        return eventListeners[eventType]?.size ?: 0
    }
    
    fun getTotalSubscriberCount(): Int {
        return eventListeners.values.sumOf { it.size }
    }
    
    fun getEventTypes(): Set<String> {
        return eventListeners.keys.map { it.simpleName }.toSet()
    }
    
    // 미리 정의된 이벤트 타입들
    data class ScanStartedEvent(val timestamp: Long = System.currentTimeMillis())
    data class ScanStoppedEvent(val timestamp: Long = System.currentTimeMillis())
    data class DeviceFoundEvent(val deviceAddress: String, val deviceName: String?, val rssi: Int)
    data class ConnectionStateEvent(val deviceAddress: String, val isConnected: Boolean)
    data class AdvertisingStartedEvent(val timestamp: Long = System.currentTimeMillis())
    data class AdvertisingStoppedEvent(val timestamp: Long = System.currentTimeMillis())
    data class MessageReceivedEvent(val deviceAddress: String, val message: ByteArray)
    data class MessageSentEvent(val deviceAddress: String, val success: Boolean)
    data class ErrorEvent(val error: String, val exception: Throwable? = null)
}