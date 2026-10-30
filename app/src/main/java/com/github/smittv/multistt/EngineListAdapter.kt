package com.github.smittv.multistt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.smittv.multistt.engines.Engine

class EngineListAdapter(
    private val items: MutableList<Pair<String, String>>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<EngineListAdapter.ViewHolder>() {
    var selectedItemPosition = if (items.size > 0) 0 else RecyclerView.NO_POSITION
    private set
    private var selectedView: View? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.engine_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.title.setText(items[position].first)

        holder.itemView.isSelected = selectedItemPosition == position
        if (selectedItemPosition == position) {
            selectedView = holder.itemView
        }

        holder.itemView.setOnClickListener {
            val oldPosition = selectedItemPosition
            selectedItemPosition = holder.bindingAdapterPosition

            if (selectedItemPosition == RecyclerView.NO_POSITION) {
                selectedView = null
                return@setOnClickListener
            }

            selectedView?.isSelected = false
            selectedView = it
            selectedView?.isSelected = true

            it.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_SELECT, null
            )

            onClick(items[position].second)
        }
    }

    override fun getItemCount(): Int = items.size

    fun addItem(item: Pair<String, String>) {
        items.add(item)
        notifyItemInserted(items.lastIndex)
    }

    fun remove(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    fun clear() {
        items.clear()
        selectedView = null
        selectedItemPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }

    fun setItems(newItems: List<Pair<String, String>>) {
        items.clear()
        selectedItemPosition = if (newItems.size > 0) 0 else RecyclerView.NO_POSITION
        selectedView = null
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getCurrentEngine(): String? {
        return items.getOrNull(selectedItemPosition)?.first
    }
}