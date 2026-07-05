// BabyCare/app/src/main/java/com/babycare/ui/CustomRecordFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.babycare.BabyCareApp
import com.babycare.data.FeedingRecord
import com.babycare.databinding.FragmentCustomRecordBinding
import kotlinx.coroutines.launch

class CustomRecordFragment : Fragment() {
    private var _binding: FragmentCustomRecordBinding? = null
    private val binding get() = _binding!!
    private val feedingDao by lazy {
        (requireActivity().application as BabyCareApp).database.feedingDao()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCustomRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        binding.rbCustomBreast.setOnCheckedChangeListener { _, checked ->
            binding.etCustomVolume.isEnabled = !checked
            if (checked) binding.etCustomVolume.text?.clear()
        }
        binding.rbCustomFormula.setOnCheckedChangeListener { _, checked ->
            if (checked) binding.etCustomVolume.isEnabled = true
        }
        binding.btnSaveCustomRecord.setOnClickListener {
            val isBreast = binding.rbCustomBreast.isChecked
            val feedType = if (isBreast) "breast" else "formula"
            val volume = if (!isBreast) binding.etCustomVolume.text.toString().toIntOrNull() else null
            if (!isBreast && volume == null) {
                Toast.makeText(requireContext(), "请输入配方奶量", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveFeedingRecord(feedType, volume)
        }
    }

    private fun saveFeedingRecord(feedType: String, volume: Int?) {
        lifecycleScope.launch {
            val prev = feedingDao.getLatest()
            val diff = prev?.timestamp?.let { System.currentTimeMillis() - it }
            val record = FeedingRecord(
                type = "manual",
                feedType = feedType,
                volume = if (feedType == "formula") volume else null,
                timestamp = System.currentTimeMillis(),
                diff = diff
            )
            feedingDao.insert(record)
            Toast.makeText(requireContext(), "补录成功：${if (feedType == "breast") "母乳" else "配方奶 ${volume}ml"}", Toast.LENGTH_SHORT).show()
            binding.etCustomVolume.text?.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}