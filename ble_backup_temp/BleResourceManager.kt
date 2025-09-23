package kr.open.library.systemmanager.controller.bluetooth.base

import android.os.Handler
import android.os.Looper
import kr.open.library.logcat.Logx
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * BLE 리소스 관리자
 * BLE Resource Manager
 * 
 * BLE 작업과 관련된 모든 리소스의 생명주기를 관리하여 메모리 누수를 방지합니다.
 * Manages the lifecycle of all BLE-related resources to prevent memory leaks.
 * 
 * 관리 대상:
 * Managed resources:
 * - BluetoothGatt 연결
 * - Scan callbacks
 * - Advertise callbacks
 * - GATT server instances
 * - Handler 및 Runnable 작업들
 */
class BleResourceManager {
    
    private val TAG = "BleResourceManager"
    
    /**
     * 리소스 타입 정의
     */
    enum class ResourceType {
        GATT_CONNECTION,    // GATT 연결
        SCAN_CALLBACK,      // 스캔 콜백
        ADVERTISE_CALLBACK, // 광고 콜백
        GATT_SERVER,        // GATT 서버
        HANDLER_TASK,       // Handler 작업
        SYSTEM_CALLBACK     // 시스템 콜백
    }
    
    /**
     * 리소스 상태
     */
    enum class ResourceState {
        CREATED,    // 생성됨
        ACTIVE,     // 활성
        PAUSED,     // 일시 중지
        DISPOSING,  // 해제 중
        DISPOSED    // 해제됨
    }
    
    /**
     * 관리되는 리소스 정보
     */
    data class ManagedResource(
        val id: String,
        val type: ResourceType,
        val resourceRef: WeakReference<BluetoothBaseController.BleResource>,
        var state: ResourceState,
        val createdTime: Long,
        var lastAccessTime: Long,
        val metadata: MutableMap<String, Any> = mutableMapOf()
    ) {
        fun isAlive(): Boolean = resourceRef.get() != null
        fun updateAccess() { lastAccessTime = System.currentTimeMillis() }
        fun getAge(): Long = System.currentTimeMillis() - createdTime
        fun getIdleDuration(): Long = System.currentTimeMillis() - lastAccessTime
    }
    
    private val resources = ConcurrentHashMap<String, ManagedResource>()
    private val resourceIdCounter = AtomicLong(0)
    private val cleanupHandler = Handler(Looper.getMainLooper())
    private var isCleanupScheduled = false
    
    // 설정값들
    private var maxResourceAge = 5 * 60 * 1000L      // 5분
    private var maxIdleDuration = 2 * 60 * 1000L     // 2분  
    private var cleanupInterval = 30 * 1000L         // 30초
    private var maxResourceCount = 50                 // 최대 리소스 수
    
    /**
     * 리소스 등록
     * Register a resource for management
     */
    fun <T : BluetoothBaseController.BleResource> registerResource(
        resource: T,
        type: ResourceType,
        customId: String? = null
    ): String {
        val resourceId = customId ?: "${type.name}_${resourceIdCounter.incrementAndGet()}"
        val now = System.currentTimeMillis()
        
        val managedResource = ManagedResource(
            id = resourceId,
            type = type,
            resourceRef = WeakReference(resource),
            state = ResourceState.CREATED,
            createdTime = now,
            lastAccessTime = now
        )
        
        resources[resourceId] = managedResource
        
        Logx.d(TAG, "Registered resource: $resourceId (type: $type)")
        
        // 정리 작업 예약
        scheduleCleanup()
        
        return resourceId
    }
    
    /**
     * 리소스 상태 변경
     * Change resource state
     */
    fun updateResourceState(resourceId: String, newState: ResourceState): Boolean {
        val resource = resources[resourceId] ?: return false
        
        val oldState = resource.state
        resource.state = newState
        resource.updateAccess()
        
        Logx.d(TAG, "Resource state changed: $resourceId ($oldState -> $newState)")
        
        // DISPOSING 상태가 되면 실제 정리 수행
        if (newState == ResourceState.DISPOSING) {
            disposeResourceInternal(resource)
        }
        
        return true
    }
    
    /**
     * 리소스 접근 시간 업데이트
     * Update resource access time
     */
    fun touchResource(resourceId: String): Boolean {
        val resource = resources[resourceId] ?: return false
        resource.updateAccess()
        return true
    }
    
    /**
     * 리소스 메타데이터 설정
     * Set resource metadata
     */
    fun setResourceMetadata(resourceId: String, key: String, value: Any): Boolean {
        val resource = resources[resourceId] ?: return false
        resource.metadata[key] = value
        return true
    }
    
    /**
     * 리소스 메타데이터 조회
     * Get resource metadata
     */
    fun getResourceMetadata(resourceId: String, key: String): Any? {
        return resources[resourceId]?.metadata?.get(key)
    }
    
    /**
     * 특정 리소스 해제
     * Dispose specific resource
     */
    fun disposeResource(resourceId: String): Boolean {
        val resource = resources[resourceId] ?: return false
        
        if (resource.state == ResourceState.DISPOSED || resource.state == ResourceState.DISPOSING) {
            return true
        }
        
        updateResourceState(resourceId, ResourceState.DISPOSING)
        return true
    }
    
    /**
     * 특정 타입의 모든 리소스 해제
     * Dispose all resources of specific type
     */
    fun disposeResourcesByType(type: ResourceType) {
        val targetResources = resources.values.filter { it.type == type && it.state != ResourceState.DISPOSED }
        
        Logx.i(TAG, "Disposing ${targetResources.size} resources of type: $type")
        
        targetResources.forEach { resource ->
            updateResourceState(resource.id, ResourceState.DISPOSING)
        }
    }
    
    /**
     * 모든 리소스 해제
     * Dispose all resources
     */
    fun disposeAllResources() {
        val activeResources = resources.values.filter { it.state != ResourceState.DISPOSED }
        
        Logx.i(TAG, "Disposing all ${activeResources.size} resources")
        
        activeResources.forEach { resource ->
            disposeResourceInternal(resource)
        }
        
        resources.clear()
        cancelScheduledCleanup()
    }
    
    /**
     * 리소스 통계 정보 반환
     * Get resource statistics
     */
    fun getResourceStats(): ResourceStats {
        val byType = mutableMapOf<ResourceType, Int>()
        val byState = mutableMapOf<ResourceState, Int>()
        var aliveCount = 0
        var deadCount = 0
        
        resources.values.forEach { resource ->
            byType[resource.type] = (byType[resource.type] ?: 0) + 1
            byState[resource.state] = (byState[resource.state] ?: 0) + 1
            
            if (resource.isAlive()) {
                aliveCount++
            } else {
                deadCount++
            }
        }
        
        return ResourceStats(
            totalResources = resources.size,
            aliveResources = aliveCount,
            deadResources = deadCount,
            byType = byType,
            byState = byState
        )
    }
    
    /**
     * 리소스 통계 데이터 클래스
     */
    data class ResourceStats(
        val totalResources: Int,
        val aliveResources: Int,
        val deadResources: Int,
        val byType: Map<ResourceType, Int>,
        val byState: Map<ResourceState, Int>
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("=== BLE Resource Statistics ===")
                appendLine("Total: $totalResources")
                appendLine("Alive: $aliveResources")
                appendLine("Dead: $deadResources")
                appendLine("By Type:")
                byType.forEach { (type, count) ->
                    appendLine("  $type: $count")
                }
                appendLine("By State:")
                byState.forEach { (state, count) ->
                    appendLine("  $state: $count")
                }
            }
        }
    }
    
    /**
     * 실제 리소스 해제 수행
     */
    private fun disposeResourceInternal(managedResource: ManagedResource) {
        try {
            managedResource.resourceRef.get()?.let { resource ->
                resource.cleanup()
                Logx.d(TAG, "Disposed resource: ${managedResource.id}")
            }
        } catch (e: Exception) {
            Logx.w(TAG, "Error disposing resource: ${managedResource.id}", e)
        } finally {
            managedResource.state = ResourceState.DISPOSED
            managedResource.resourceRef.clear()
        }
    }
    
    /**
     * 자동 정리 작업 예약
     */
    private fun scheduleCleanup() {
        if (isCleanupScheduled) return
        
        isCleanupScheduled = true
        cleanupHandler.postDelayed({
            performCleanup()
            isCleanupScheduled = false
            
            // 아직 리소스가 있다면 다음 정리 예약
            if (resources.isNotEmpty()) {
                scheduleCleanup()
            }
        }, cleanupInterval)
    }
    
    /**
     * 정리 작업 수행
     */
    private fun performCleanup() {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()
        var disposedCount = 0
        var removedCount = 0
        
        resources.values.forEach { resource ->
            when {
                // 이미 해제된 리소스는 제거
                resource.state == ResourceState.DISPOSED -> {
                    toRemove.add(resource.id)
                    removedCount++
                }
                
                // 죽은 참조는 해제
                !resource.isAlive() -> {
                    disposeResourceInternal(resource)
                    toRemove.add(resource.id)
                    disposedCount++
                }
                
                // 오래된 리소스는 해제
                resource.getAge() > maxResourceAge -> {
                    Logx.w(TAG, "Disposing aged resource: ${resource.id} (age: ${resource.getAge()}ms)")
                    disposeResourceInternal(resource)
                    toRemove.add(resource.id)
                    disposedCount++
                }
                
                // 오래 사용되지 않은 리소스는 해제
                resource.getIdleDuration() > maxIdleDuration && resource.state == ResourceState.CREATED -> {
                    Logx.w(TAG, "Disposing idle resource: ${resource.id} (idle: ${resource.getIdleDuration()}ms)")
                    disposeResourceInternal(resource)
                    toRemove.add(resource.id)
                    disposedCount++
                }
            }
        }
        
        // 리소스 수 제한 확인
        if (resources.size > maxResourceCount) {
            val excess = resources.size - maxResourceCount
            val oldestResources = resources.values
                .filter { it.state != ResourceState.ACTIVE } // 활성 리소스는 보호
                .sortedBy { it.lastAccessTime }
                .take(excess)
            
            oldestResources.forEach { resource ->
                Logx.w(TAG, "Disposing excess resource: ${resource.id}")
                disposeResourceInternal(resource)
                toRemove.add(resource.id)
                disposedCount++
            }
        }
        
        // 제거할 리소스들을 맵에서 제거
        toRemove.forEach { resourceId ->
            resources.remove(resourceId)
        }
        
        if (disposedCount > 0 || removedCount > 0) {
            Logx.i(TAG, "Cleanup completed: disposed $disposedCount, removed $removedCount resources")
        }
    }
    
    /**
     * 예약된 정리 작업 취소
     */
    private fun cancelScheduledCleanup() {
        cleanupHandler.removeCallbacksAndMessages(null)
        isCleanupScheduled = false
    }
    
    /**
     * 설정값 변경
     */
    fun configure(
        maxResourceAge: Long? = null,
        maxIdleDuration: Long? = null,
        cleanupInterval: Long? = null,
        maxResourceCount: Int? = null
    ) {
        maxResourceAge?.let { this.maxResourceAge = it }
        maxIdleDuration?.let { this.maxIdleDuration = it }
        cleanupInterval?.let { this.cleanupInterval = it }
        maxResourceCount?.let { this.maxResourceCount = it }
        
        Logx.i(TAG, "Resource manager configured: maxAge=${this.maxResourceAge}ms, maxIdle=${this.maxIdleDuration}ms, interval=${this.cleanupInterval}ms, maxCount=${this.maxResourceCount}")
    }
}