package kr.open.library.system_service

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.open.library.system_service.databinding.ActivityFloatingViewTestBinding
import kr.open.library.systemmanager.controller.window.FloatingViewController
import kr.open.library.systemmanager.controller.window.floating.drag.FloatingDragView
import kr.open.library.systemmanager.controller.window.floating.fixed.FloatingFixedView
import kr.open.library.systemmanager.controller.window.floating.vo.FloatingViewCollisionsType
import kr.open.library.systemmanager.controller.window.floating.vo.FloatingViewTouchType

/**
 * FloatingViewTestActivity - FloatingViewController 테스트 액티비티
 * FloatingViewController Test Activity
 * 
 * 플로팅 뷰 관리 기능을 종합적으로 테스트하는 액티비티입니다.
 * Activity for comprehensive testing of floating view management functionality.
 * 
 * 주요 테스트 기능 / Main Test Features:
 * - 고정 플로팅 뷰 관리 / Fixed floating view management
 * - 드래그 가능한 플로팅 뷰 관리 / Draggable floating view management
 * - 충돌 감지 및 상태 모니터링 / Collision detection and state monitoring
 * - 권한 관리 및 확인 / Permission management and verification
 * - 실시간 상태 업데이트 / Real-time status updates
 */
class FloatingViewTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFloatingViewTestBinding
    private lateinit var floatingViewController: FloatingViewController
    
    // 테스트용 뷰 관리 / Test View Management
    private var currentFixedView: FloatingFixedView? = null
    private val dragViews = mutableListOf<FloatingDragView>()
    private var dragViewCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupBinding()
        initializeFloatingController()
        setupEventListeners()
        updatePermissionStatus()
        
        addLog("FloatingViewController 테스트 시작 / FloatingViewController test started")
    }

    /**
     * FloatingViewController 초기화
     * Initialize FloatingViewController
     */
    private fun initializeFloatingController() {
        floatingViewController = FloatingViewController(this)
        addLog("FloatingViewController 초기화 완료 / FloatingViewController initialized")
    }

    private fun setupBinding() {
        binding = ActivityFloatingViewTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    /**
     * 이벤트 리스너 설정
     * Setup event listeners
     */
    private fun setupEventListeners() {
        binding.btnCheckPermission.setOnClickListener {
            updatePermissionStatus()
            addLog("권한 상태 확인 / Permission status checked")
        }

        binding.btnRequestPermission.setOnClickListener {
            requestOverlayPermission()
        }

        binding.btnAddFixedView.setOnClickListener {
            addFixedFloatingView()
        }

        binding.btnRemoveFixedView.setOnClickListener {
            removeFixedFloatingView()
        }

        binding.btnAddDragView.setOnClickListener {
            addDragFloatingView()
        }

        binding.btnRemoveDragView.setOnClickListener {
            removeDragFloatingView()
        }

        binding.btnRemoveAllViews.setOnClickListener {
            removeAllFloatingViews()
        }
    }

    /**
     * 권한 상태 업데이트
     * Update permission status
     */
    private fun updatePermissionStatus() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        val statusText = if (hasPermission) {
            "권한 승인됨 ✅ / Permission Granted ✅"
        } else {
            "권한 필요 ❌ / Permission Required ❌"
        }

        binding.tvPermissionStatus.text = statusText
        binding.tvPermissionStatus.setTextColor(if (hasPermission) Color.GREEN else Color.RED)

        // 버튼 활성화 상태 조정 / Adjust button enabled state
        val isEnabled = hasPermission
        binding.btnAddFixedView.isEnabled = isEnabled
        binding.btnAddDragView.isEnabled = isEnabled
        binding.btnRemoveFixedView.isEnabled = isEnabled
        binding.btnRemoveDragView.isEnabled = isEnabled
        binding.btnRemoveAllViews.isEnabled = isEnabled
    }

    /**
     * 오버레이 권한 요청
     * Request overlay permission
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
                addLog("오버레이 권한 요청 / Overlay permission requested")
            } else {
                addLog("이미 권한이 승인되었습니다 / Permission already granted")
            }
        } else {
            addLog("API 23 미만에서는 권한이 자동으로 승인됩니다 / Auto-granted on API < 23")
        }
    }

    /**
     * 고정 플로팅 뷰 추가
     * Add fixed floating view
     */
    private fun addFixedFloatingView() {
        if (currentFixedView != null) {
            addLog("이미 고정 뷰가 존재합니다 / Fixed view already exists")
            return
        }

        // 고정 뷰용 TextView 생성 / Create TextView for fixed view
        val fixedTextView = TextView(this).apply {
            text = "고정 뷰\nFixed View"
            setBackgroundColor(Color.BLUE)
            setTextColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
            gravity = Gravity.CENTER
        }

        currentFixedView = FloatingFixedView(fixedTextView, 100, 200)
        
        val success = floatingViewController.setFloatingFixedView(currentFixedView)
        if (success) {
            addLog("고정 플로팅 뷰 추가 성공 / Fixed floating view added successfully")
        } else {
            addLog("고정 플로팅 뷰 추가 실패 / Failed to add fixed floating view")
            currentFixedView = null
        }
    }

    /**
     * 고정 플로팅 뷰 제거
     * Remove fixed floating view
     */
    private fun removeFixedFloatingView() {
        if (currentFixedView == null) {
            addLog("제거할 고정 뷰가 없습니다 / No fixed view to remove")
            return
        }

        val success = floatingViewController.removeFloatingFixedView()
        if (success) {
            currentFixedView = null
            addLog("고정 플로팅 뷰 제거 성공 / Fixed floating view removed successfully")
        } else {
            addLog("고정 플로팅 뷰 제거 실패 / Failed to remove fixed floating view")
        }
    }

    /**
     * 드래그 플로팅 뷰 추가
     * Add drag floating view
     */
    private fun addDragFloatingView() {
        dragViewCounter++
        
        // 드래그 뷰용 TextView 생성 / Create TextView for drag view
        val dragTextView = TextView(this).apply {
            text = "드래그 뷰 #$dragViewCounter\nDrag View #$dragViewCounter"
            setBackgroundColor(Color.GREEN)
            setTextColor(Color.WHITE)
            setPadding(20, 20, 20, 20)
            gravity = Gravity.CENTER
        }

        val dragView = FloatingDragView(
            view = dragTextView,
            startX = 300 + (dragViewCounter * 50),
            startY = 300 + (dragViewCounter * 100),
            collisionsWhileTouchDown = { view, type ->
                updateCollisionStatus("터치 다운", type)
            },
            collisionsWhileDrag = { view, type ->
                updateCollisionStatus("드래그 중", type)
            },
            collisionsWhileTouchUp = { view, type ->
                updateCollisionStatus("터치 업", type)
            }
        )

        // StateFlow 관찰 / Observe StateFlow
        lifecycleScope.launch {
            dragView.sfCollisionStateFlow.collect { (touchType, collisionType) ->
                runOnUiThread {
                    updateCollisionStatusFromFlow(touchType, collisionType)
                }
            }
        }

        val success = floatingViewController.addFloatingDragView(dragView)
        if (success) {
            dragViews.add(dragView)
            addLog("드래그 플로팅 뷰 #$dragViewCounter 추가 성공 / Drag floating view #$dragViewCounter added successfully")
        } else {
            addLog("드래그 플로팅 뷰 #$dragViewCounter 추가 실패 / Failed to add drag floating view #$dragViewCounter")
        }
    }

    /**
     * 드래그 플로팅 뷰 제거
     * Remove drag floating view
     */
    private fun removeDragFloatingView() {
        if (dragViews.isEmpty()) {
            addLog("제거할 드래그 뷰가 없습니다 / No drag view to remove")
            return
        }

        val dragView = dragViews.removeLastOrNull()
        if (dragView != null) {
            val success = floatingViewController.removeFloatingDragView(dragView)
            if (success) {
                addLog("드래그 플로팅 뷰 제거 성공 / Drag floating view removed successfully")
            } else {
                addLog("드래그 플로팅 뷰 제거 실패 / Failed to remove drag floating view")
                dragViews.add(dragView) // 실패 시 다시 추가 / Re-add on failure
            }
        }
    }

    /**
     * 모든 플로팅 뷰 제거
     * Remove all floating views
     */
    private fun removeAllFloatingViews() {
        val success = floatingViewController.removeAllFloatingView()
        if (success) {
            currentFixedView = null
            dragViews.clear()
            dragViewCounter = 0
            addLog("모든 플로팅 뷰 제거 완료 / All floating views removed")
            binding.tvCollisionStatus.text = "충돌 상태: 없음 / Collision: None"
            binding.tvCollisionStatus.setTextColor(Color.GREEN)
        } else {
            addLog("플로팅 뷰 제거 실패 / Failed to remove floating views")
        }
    }

    /**
     * 충돌 상태 업데이트 (콜백)
     * Update collision status (callback)
     */
    private fun updateCollisionStatus(phase: String, type: FloatingViewCollisionsType) {
        val typeText = when (type) {
            FloatingViewCollisionsType.OCCURING -> "충돌 발생 / Collision Occurring"
            FloatingViewCollisionsType.UNCOLLISIONS -> "충돌 없음 / No Collision"
        }
        
        runOnUiThread {
            binding.tvCollisionStatus.text = "[$phase] $typeText"
            binding.tvCollisionStatus.setTextColor(
                when (type) {
                    FloatingViewCollisionsType.OCCURING -> Color.RED
                    FloatingViewCollisionsType.UNCOLLISIONS -> Color.GREEN
                }
            )
        }
        
        addLog("충돌 상태 업데이트: [$phase] $typeText / Collision status updated: [$phase] $typeText")
    }

    /**
     * StateFlow로부터 충돌 상태 업데이트
     * Update collision status from StateFlow
     */
    private fun updateCollisionStatusFromFlow(touchType: FloatingViewTouchType, collisionType: FloatingViewCollisionsType) {
        val touchText = when (touchType) {
            FloatingViewTouchType.TOUCH_DOWN -> "터치 다운 / Touch Down"
            FloatingViewTouchType.TOUCH_MOVE -> "터치 이동 / Touch Move"
            FloatingViewTouchType.TOUCH_UP -> "터치 업 / Touch Up"
        }
        
        val collisionText = when (collisionType) {
            FloatingViewCollisionsType.OCCURING -> "충돌 발생 / Collision"
            FloatingViewCollisionsType.UNCOLLISIONS -> "충돌 없음 / No Collision"
        }
        
        binding.tvCollisionStatus.text = "StateFlow: [$touchText] $collisionText"
        binding.tvCollisionStatus.setTextColor(
            when (collisionType) {
                FloatingViewCollisionsType.OCCURING -> Color.RED
                FloatingViewCollisionsType.UNCOLLISIONS -> Color.GREEN
            }
        )
    }

    /**
     * 로그 추가
     * Add log entry
     */
    private fun addLog(message: String) {
        val timestamp = System.currentTimeMillis()
        val logEntry = "[$timestamp] $message\n"
        runOnUiThread {
            binding.tvLog.append(logEntry)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            updatePermissionStatus()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                addLog("오버레이 권한 승인됨 / Overlay permission granted")
            } else {
                addLog("오버레이 권한 거부됨 / Overlay permission denied")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 액티비티 종료 시 모든 플로팅 뷰 정리 / Clean up all floating views on activity destroy
        floatingViewController.removeAllFloatingView()
        addLog("액티비티 종료 - 모든 리소스 정리 완료 / Activity destroyed - All resources cleaned up")
    }

    companion object {
        private const val OVERLAY_PERMISSION_REQ_CODE = 1000
    }
}