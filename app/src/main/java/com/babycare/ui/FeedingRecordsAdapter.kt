// BabyCare/app/src/main/java/com/babycare/ui/FeedingRecordAdapter.kt
package com.babycare.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.babycare.data.FeedingRecord
import com.babycare.databinding.ItemRecordBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class FeedingRecordAdapter(private val onDelete: (FeedingRecord) -> Unit) :
    ListAdapter<FeedingRecord, FeedingRecordAdapter.ViewHolder>(DiffCallback()) {

    companion object {
        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        private class DiffCallback : DiffUtil.ItemCallback<FeedingRecord>() {
            override fun areItemsTheSame(old: FeedingRecord, new: FeedingRecord) = old.id == new.id
            override fun areContentsTheSame(old: FeedingRecord, new: FeedingRecord) = old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRecordBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: FeedingRecord) {
            binding.tvTime.text = DATE_FMT.format(Date(record.timestamp))
            binding.tvDetail.text = "${if (record.feedType == "breast") "🤱 母乳" else "🍼 配方奶"}" +
                    if (record.volume != null) " | ${record.volume} ml" else ""
            val diffStr = record.diff?.let {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(it)
                val hours = minutes / 60
                if (hours > 0) "距上次: ${hours}小时${minutes % 60}分钟" else "距上次: $minutes 分钟"
            } ?: ""
            binding.tvInterval.text = diffStr

            binding.btnDelete.setOnClickListener { onDelete(record) }
        }
    }
}