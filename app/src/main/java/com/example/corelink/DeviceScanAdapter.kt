package com.example.corelink

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class DeviceItem(
    val name: String,
    val address: String,
    val state: String
)

class DeviceScanAdapter(
    private val devices: MutableList<DeviceItem>,
    private val onTap: (DeviceItem) -> Unit
) : RecyclerView.Adapter<DeviceScanAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.deviceName)
        val address: TextView = view.findViewById(R.id.deviceAddress)
        val state: TextView = view.findViewById(R.id.deviceState)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun getItemCount(): Int = devices.size

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val item = devices[position]
        holder.name.text = item.name
        holder.address.text = item.address
        holder.state.text = item.state
        holder.itemView.setOnClickListener { onTap(item) }
    }

    fun replace(newItems: List<DeviceItem>) {
        devices.clear()
        devices.addAll(newItems)
        notifyDataSetChanged()
    }
}
