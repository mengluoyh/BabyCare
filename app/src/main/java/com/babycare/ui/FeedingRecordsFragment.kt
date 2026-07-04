// BabyCare/app/src/main/java/com/example/babycare/ui/FeedingRecordsFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.babycare.BabyCareApp
import com.babycare.databinding.FragmentFeedingRecordsBinding
import com.babycare.util.ExportUtil
import kotlinx.coroutines.launch

class FeedingRecordsFragment : Fragment() {
    private var _binding: FragmentFeedingRecordsBinding? = null
    private val binding get() = _binding!!
    private val feedingDao by lazy {
        (requireActivity().application as BabyCareApp).database.feedingDao()
    }
    private val adapter = FeedingRecordAdapter { record -> deleteRecord(record) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedingRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnExport.setOnClickListener {
            exportRecords()
        }

        lifecycleScope.launch {
            feedingDao.getAll().collect { records ->
                adapter.submitList(records)
                binding.emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun deleteRecord(record: com.babycare.data.FeedingRecord) {
        lifecycleScope.launch {
            feedingDao.delete(record)
        }
    }

    private fun exportRecords() {
        lifecycleScope.launch {
            val records = feedingDao.getAll().let {
                var list = emptyList<com.babycare.data.FeedingRecord>()
                it.collect { list = it }
                list
            }
            val file = ExportUtil.exportToJson(
                requireContext(),
                records,
                "feeding_${System.currentTimeMillis()}.json"
            )
            if (file != null) {
                // Toast success
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}