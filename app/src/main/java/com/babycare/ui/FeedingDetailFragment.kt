// BabyCare/app/src/main/java/com/babycare/ui/FeedingDetailFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.babycare.BabyCareApp
import com.babycare.data.FeedingRecord
import com.babycare.databinding.FragmentFeedingBreastBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/** 喂养详情 — 展示所有喂养记录（母乳+配方奶合并按时间倒排） */
class FeedingDetailFragment : Fragment() {
    private var _binding: FragmentFeedingBreastBinding? = null
    private val binding get() = _binding!!
    private val feedingDao by lazy { (requireActivity().application as BabyCareApp).database.feedingDao() }
    private val adapter = FeedingDetailAdapter { record -> deleteRecord(record) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedingBreastBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.emptyView.text = "暂无喂养记录"

        lifecycleScope.launch {
            feedingDao.getAll().collect { records ->
                adapter.submitList(records) // 全部记录
                binding.emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun deleteRecord(record: FeedingRecord) {
        lifecycleScope.launch { feedingDao.delete(record) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class FeedingDetailAdapter(
        private val onDelete: (FeedingRecord) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<FeedingDetailAdapter.VH>() {
        private var records = emptyList<FeedingRecord>()
        fun submitList(list: List<FeedingRecord>) { records = list; notifyDataSetChanged() }
        override fun getItemCount() = records.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val itemBinding = com.babycare.databinding.ItemRecordBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(itemBinding)
        }
        override fun onBindViewHolder(holder: VH, position: Int) { holder.bind(records[position]) }
        inner class VH(private val itemBinding: com.babycare.databinding.ItemRecordBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(itemBinding.root) {
            fun bind(r: FeedingRecord) {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                itemBinding.tvTime.text = sdf.format(Date(r.timestamp))
                val typeLabel = if (r.feedType == "breast") "🤱 母乳" else "🍼 配方奶 ${r.volume ?: 0} ml"
                itemBinding.tvDetail.text = typeLabel
                val diffStr = r.diff?.let {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(it)
                    val hours = minutes / 60
                    if (hours > 0) "距上次: ${hours}小时${minutes % 60}分钟" else "距上次: $minutes 分钟"
                } ?: ""
                itemBinding.tvInterval.text = diffStr
                itemBinding.btnDelete.setOnClickListener { onDelete(r) }
            }
        }
    }
}