package kr.open.library.system_service

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kr.open.library.systemmanager.controller.bluetooth.BleMasterController
import kr.open.library.systemmanager.controller.bluetooth.data.BleDevice

/**
 * BLE 디바이스 목록을 위한 RecyclerView 어댑터
 * RecyclerView adapter for BLE device list
 */
class BleDeviceAdapter : RecyclerView.Adapter<BleDeviceAdapter.BleDeviceViewHolder>() {

    private val devices = mutableListOf<BleDevice>()
    private val connectionStates = mutableMapOf<String, BleMasterController.ConnectionState>()
    private var onDeviceClickListener: ((BleDevice) -> Unit)? = null
    private var onConnectClickListener: ((BleDevice) -> Unit)? = null
    private var onDisconnectClickListener: ((BleDevice) -> Unit)? = null

    /**
     * 디바이스 클릭 리스너 설정
     */
    fun setOnDeviceClickListener(listener: (BleDevice) -> Unit) {
        onDeviceClickListener = listener
    }
    
    /**
     * 연결 버튼 클릭 리스너 설정
     */
    fun setOnConnectClickListener(listener: (BleDevice) -> Unit) {
        onConnectClickListener = listener
    }
    
    /**
     * 연결 해제 버튼 클릭 리스너 설정
     */
    fun setOnDisconnectClickListener(listener: (BleDevice) -> Unit) {
        onDisconnectClickListener = listener
    }
    
    /**
     * 연결 상태 업데이트
     */
    fun updateConnectionState(address: String, state: BleMasterController.ConnectionState) {
        connectionStates[address] = state
        // 해당 디바이스의 위치를 찾아서 업데이트
        val position = devices.indexOfFirst { it.address == address }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }

    /**
     * 디바이스 목록 업데이트
     */
    fun updateDevices(newDevices: List<BleDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    /**
     * 단일 디바이스 추가 또는 업데이트
     */
    fun addOrUpdateDevice(device: BleDevice) {
        val existingIndex = devices.indexOfFirst { it.address == device.address }
        if (existingIndex != -1) {
            // 기존 디바이스 업데이트
            devices[existingIndex] = device
            notifyItemChanged(existingIndex)
        } else {
            // 새 디바이스 추가
            devices.add(device)
            // RSSI에 따라 정렬 (신호가 강한 순)
            devices.sortByDescending { it.rssi }
            notifyDataSetChanged()
        }
    }

    /**
     * 디바이스 목록 초기화
     */
    fun clearDevices() {
        devices.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BleDeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return BleDeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: BleDeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    /**
     * ViewHolder 클래스
     */
    inner class BleDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        private val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvDeviceAddress: TextView = itemView.findViewById(R.id.tvDeviceAddress)
        private val tvRssi: TextView = itemView.findViewById(R.id.tvRssi)
        private val tvConnectable: TextView = itemView.findViewById(R.id.tvConnectable)
        private val tvLastSeen: TextView = itemView.findViewById(R.id.tvLastSeen)
        private val tvServiceUuids: TextView = itemView.findViewById(R.id.tvServiceUuids)
        private val tvConnectionState: TextView = itemView.findViewById(R.id.tvConnectionState)
        private val btnConnect: Button = itemView.findViewById(R.id.btnConnect)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeviceClickListener?.invoke(devices[position])
                }
            }
            
            btnConnect.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val device = devices[position]
                    val connectionState = connectionStates[device.address] ?: BleMasterController.ConnectionState.DISCONNECTED
                    
                    when (connectionState) {
                        BleMasterController.ConnectionState.DISCONNECTED -> {
                            onConnectClickListener?.invoke(device)
                        }
                        BleMasterController.ConnectionState.CONNECTED -> {
                            onDisconnectClickListener?.invoke(device)
                        }
                        else -> {
                            // 연결 중이거나 연결 해제 중일 때는 버튼 비활성화
                        }
                    }
                }
            }
        }

        fun bind(device: BleDevice) {
            tvDeviceName.text = device.displayName
            tvDeviceAddress.text = device.address
            tvRssi.text = "${device.rssi}dBm"
            tvConnectable.text = device.connectableText
            tvServiceUuids.text = "Services: ${device.serviceUuidsText}"
            
            // 마지막으로 본 시간
            val minutesAgo = device.lastSeenMinutesAgo
            tvLastSeen.text = when {
                minutesAgo == 0L -> "방금 전"
                minutesAgo < 60L -> "${minutesAgo}분 전"
                else -> "${minutesAgo / 60}시간 전"
            }
            
            // RSSI에 따른 색상 변경
            val rssiColor = when {
                device.rssi >= -50 -> android.R.color.holo_green_dark
                device.rssi >= -65 -> android.R.color.holo_blue_dark
                device.rssi >= -75 -> android.R.color.holo_orange_dark
                else -> android.R.color.holo_red_dark
            }
            tvRssi.setTextColor(itemView.context.getColor(rssiColor))
            
            // 연결 가능 여부에 따른 색상
            val connectableColor = if (device.isConnectable) {
                android.R.color.holo_green_dark
            } else {
                android.R.color.darker_gray
            }
            tvConnectable.setTextColor(itemView.context.getColor(connectableColor))
            
            // 연결 상태 및 버튼 업데이트
            val connectionState = connectionStates[device.address] ?: BleMasterController.ConnectionState.DISCONNECTED
            updateConnectionUI(connectionState)
        }
        
        private fun updateConnectionUI(state: BleMasterController.ConnectionState) {
            when (state) {
                BleMasterController.ConnectionState.DISCONNECTED -> {
                    tvConnectionState.text = "연결 해제됨"
                    tvConnectionState.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                    btnConnect.text = "연결"
                    btnConnect.backgroundTintList = itemView.context.getColorStateList(android.R.color.holo_blue_light)
                    btnConnect.isEnabled = true
                }
                BleMasterController.ConnectionState.CONNECTING -> {
                    tvConnectionState.text = "연결 중..."
                    tvConnectionState.setTextColor(itemView.context.getColor(android.R.color.holo_orange_dark))
                    btnConnect.text = "연결 중"
                    btnConnect.backgroundTintList = itemView.context.getColorStateList(android.R.color.darker_gray)
                    btnConnect.isEnabled = false
                }
                BleMasterController.ConnectionState.CONNECTED -> {
                    tvConnectionState.text = "연결됨"
                    tvConnectionState.setTextColor(itemView.context.getColor(android.R.color.holo_green_dark))
                    btnConnect.text = "연결해제"
                    btnConnect.backgroundTintList = itemView.context.getColorStateList(android.R.color.holo_red_light)
                    btnConnect.isEnabled = true
                }
                BleMasterController.ConnectionState.DISCONNECTING -> {
                    tvConnectionState.text = "연결 해제 중..."
                    tvConnectionState.setTextColor(itemView.context.getColor(android.R.color.holo_orange_dark))
                    btnConnect.text = "해제 중"
                    btnConnect.backgroundTintList = itemView.context.getColorStateList(android.R.color.darker_gray)
                    btnConnect.isEnabled = false
                }
                BleMasterController.ConnectionState.ERROR -> {
                    tvConnectionState.text = "연결 오류"
                    tvConnectionState.setTextColor(itemView.context.getColor(android.R.color.holo_red_dark))
                    btnConnect.text = "재연결"
                    btnConnect.backgroundTintList = itemView.context.getColorStateList(android.R.color.holo_blue_light)
                    btnConnect.isEnabled = true
                }
            }
        }
    }
}