// BabyCare/app/src/main/java/com/babycare/ui/ExcreteRecordsFragment.kt
package com.babycare.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.babycare.BabyCareApp
import com.babycare.data.ExcreteRecord
import com.babycare.databinding.FragmentExcreteRecordsBinding
import com.babycare.util.ExportUtil
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class ExcreteRecordsFragment : Fragment() {
    private var _binding: FragmentExcreteRecordsBinding? = null
    private val binding get() = _binding!!
    private val excreteDao by lazy { (requireActivity().application as BabyCareApp).database.excreteDao() }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importRecords(uri)
    }

    private val TAGS = arrayOf("bowel", "pee", "chart")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExcreteRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        binding.btnExport.setOnClickListener { exportRecords() }
        binding.btnImport.setOnClickListener { importLauncher.launch("application/json") }
    }

    private fun setupTabs() {
        with(binding.tabLayout) {
            addTab(newTab().setText("💩 排便"))
            addTab(newTab().setText("💧 排泄详情"))
            addTab(newTab().setText("📊 趋势图"))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) { switchFragment(tab?.position ?: 0) }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }
        switchFragment(0)
    }

    private fun switchFragment(position: Int) {
        val tag = TAGS[position]
        var fragment = childFragmentManager.findFragmentByTag(tag)
        if (fragment == null) {
            fragment = when (position) {
                0 -> ExcreteBowelFragment()
                1 -> ExcretePeeFragment()
                2 -> ExcreteChartFragment()
                else -> ExcreteBowelFragment()
            }
            childFragmentManager.beginTransaction()
                .add(binding.childContainer.id, fragment, tag)
                .commit()
        }
        val ft = childFragmentManager.beginTransaction()
        for (t in TAGS) {
            childFragmentManager.findFragmentByTag(t)?.let { ft.hide(it) }
        }
        ft.show(fragment)
        ft.commit()
    }

    private fun exportRecords() {
        lifecycleScope.launch {
            val records = excreteDao.getAllSnapshot()
            val file = ExportUtil.exportExcreteRecordsText(requireContext(), records)
            if (file != null) {
                Toast.makeText(requireContext(), "导出成功：${file.absolutePath}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importRecords(uri: Uri) {
        lifecycleScope.launch {
            val records = ExportUtil.importFromJson<ExcreteRecord>(requireContext(), uri)
            if (records.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "导入失败：文件为空或格式错误", Toast.LENGTH_SHORT).show()
                return@launch
            }
            excreteDao.insertAll(records)
            Toast.makeText(requireContext(), "导入成功：${records.size} 条记录", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}