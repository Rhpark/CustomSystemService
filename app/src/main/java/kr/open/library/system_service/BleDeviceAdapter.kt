package kr.open.library.system_service

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kr.open.library.systemmanager.controller.bluetooth.data.BleDevice

/**
 * BLE 디바이스 목록을 표시하는 RecyclerView Adapter
 */
class BleDeviceAdapter(
    private val onDeviceClick: (BleDevice) -> Unit
) : ListAdapter<BleDevice, BleDeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return DeviceViewHolder(view, onDeviceClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DeviceViewHolder(
        itemView: View,
        private val onDeviceClick: (BleDevice) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvDeviceAddress: TextView = itemView.findViewById(R.id.tvDeviceAddress)
        private val tvRssi: TextView = itemView.findViewById(R.id.tvRssi)
        private val btnConnect: Button = itemView.findViewById(R.id.btnConnect)

        fun bind(device: BleDevice) {
            // 디바이스 이름
            tvDeviceName.text = device.displayName
            
            // MAC 주소
            tvDeviceAddress.text = device.address
            
            // RSSI 값과 색상
            tvRssi.text = device.rssi.toString()
            tvRssi.setTextColor(getRssiColor(device.rssi))
            
            // 연결 버튼 클릭
            btnConnect.setOnClickListener {
                onDeviceClick(device)
            }
            
            // 카드 전체 클릭도 가능
            itemView.setOnClickListener {
                onDeviceClick(device)
            }
        }
        
        private fun getRssiColor(rssi: Int): Int {
            return when {
                rssi > -50 -> ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark)
                rssi > -70 -> ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark)
                else -> ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark)
            }
        }
    }

    /**
     * DiffUtil.ItemCallback for efficient list updates
     */
    class DeviceDiffCallback : DiffUtil.ItemCallback<BleDevice>() {
        override fun areItemsTheSame(oldItem: BleDevice, newItem: BleDevice): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: BleDevice, newItem: BleDevice): Boolean {
            return oldItem == newItem
        }
    }
    
    /**
     * 디바이스 목록 업데이트 (RSSI 변경 등 반영)
     */
    fun updateDevice(device: BleDevice) {
        val currentList = currentList.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.address == device.address }
        
        if (existingIndex != -1) {
            // 기존 디바이스 업데이트 (RSSI 변경 등)
            currentList[existingIndex] = device
        } else {
            // 새 디바이스 추가
            currentList.add(device)
        }
        
        // 리스트를 RSSI 순으로 정렬 (강한 신호부터)
        currentList.sortByDescending { it.rssi }
        
        submitList(currentList)
    }
    
    /**
     * 디바이스 제거
     */
    fun removeDevice(deviceAddress: String) {
        val currentList = currentList.toMutableList()
        currentList.removeAll { it.address == deviceAddress }
        submitList(currentList)
    }
    
    /**
     * 모든 디바이스 제거
     */
    fun clearDevices() {
        submitList(emptyList())
    }
}