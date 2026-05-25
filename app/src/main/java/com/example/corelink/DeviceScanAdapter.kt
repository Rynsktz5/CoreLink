package com.example.corelink

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

data class DeviceItem(
    val name: String,
    val address: String,
    val state: String,
    val isPaired: Boolean
)

class DeviceScanAdapter(
    private val devices: MutableList<DeviceItem>,
    private val onPairClick: (DeviceItem) -> Unit,
    private val onTap: (DeviceItem) -> Unit
) : RecyclerView.Adapter<DeviceScanAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.deviceName)
        val address: TextView = view.findViewById(R.id.deviceAddress)
        val state: TextView = view.findViewById(R.id.deviceState)
        val pairBtn: MaterialButton = view.findViewById(R.id.pairDeviceBtn)
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
        
        if (item.isPaired) {
            holder.pairBtn.visibility = View.GONE
            holder.state.visibility = View.VISIBLE
        } else {
            holder.pairBtn.visibility = View.VISIBLE
            holder.state.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onTap(item) }
        holder.pairBtn.setOnClickListener { onPairClick(item) }
    }

    fun replace(newItems: List<DeviceItem>) {
        devices.clear()
        devices.addAll(newItems)
        notifyDataSetChanged()
    }
}
