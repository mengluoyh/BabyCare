// BabyCare/app/src/main/java/com/babycare/ui/FeedingFormulaFragment.kt
package com.babycare.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.babycare.BabyCareApp
import com.babycare.data.FeedingRecord
import com.babycare.databinding.FragmentFeedingFormulaBinding
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class FeedingFormulaFragment : Fragment() {
    private var _binding: FragmentFeedingFormulaBinding? = null
    private val binding get() = _binding!!
    private val feedingDao by lazy { (requireActivity().application as BabyCareApp).database.feedingDao() }
    private val adapter = FormulaAdapter { record -> deleteRecord(record) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedingFormulaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            feedingDao.getAll().collect { records ->
                val formula = records.filter { it.feedType == "formula" }
                adapter.submitList(formula)
                binding.emptyView.visibility = if (formula.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun deleteRecord(record: FeedingRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除确认")
            .setMessage("确定删除此条配方奶记录？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch { feedingDao.softDelete(record.id, System.currentTimeMillis()) }
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class FormulaAdapter(
        private val onDelete: (FeedingRecord) -> Unit
    ) : ListAdapter<FeedingRecord, FormulaAdapter.VH>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val itemBinding = com.babycare.databinding.ItemRecordBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(itemBinding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        inner class VH(private val itemBinding: com.babycare.databinding.ItemRecordBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(itemBinding.root) {
            fun bind(r: FeedingRecord) {
                itemBinding.tvTime.text = DATE_FMT.format(Date(r.timestamp))
                itemBinding.tvDetail.text = "🍼 配方奶 ${r.volume ?: 0} ml"
                val diffStr = r.diff?.let {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(it)
                    val hours = minutes / 60
                    if (hours > 0) "距上次: ${hours}小时${minutes % 60}分钟" else "距上次: $minutes 分钟"
                } ?: ""
                itemBinding.tvInterval.text = diffStr
                itemBinding.btnDelete.setOnClickListener { onDelete(r) }
            }
        }

        companion object {
            private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<FeedingRecord>() {
        override fun areItemsTheSame(old: FeedingRecord, new: FeedingRecord) = old.id == new.id
        override fun areContentsTheSame(old: FeedingRecord, new: FeedingRecord) = old == new
    }
}
