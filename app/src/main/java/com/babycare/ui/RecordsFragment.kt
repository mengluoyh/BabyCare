// BabyCare/app/src/main/java/com/babycare/ui/RecordsFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.babycare.databinding.FragmentRecordsBinding
import com.babycare.util.SyncEngine
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class RecordsFragment : Fragment() {
    private var _binding: FragmentRecordsBinding? = null
    private val binding get() = _binding!!

    private val TAGS = arrayOf("feeding", "excrete", "custom")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        setupRefresh()
    }

    private fun setupRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                try {
                    val result = SyncEngine.sync(requireContext())
                    result.onSuccess {
                        val parts = mutableListOf<String>()
                        if (it.pushed > 0) parts.add("上传 ${it.pushed} 条")
                        if (it.pulled > 0) parts.add("下载 ${it.pulled} 条")
                        val msg = if (parts.isEmpty()) "✅ 同步完成，无新数据" else "✅ 同步成功：${parts.joinToString("、")}"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Toast.makeText(requireContext(), "同步失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun setupTabs() {
        with(binding.tabLayout) {
            addTab(newTab().setText("🍼 喂养详情"))
            addTab(newTab().setText("💩 排泄详情"))
            addTab(newTab().setText("✏️ 补录记录"))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) { switchFragment(tab?.position ?: 0) }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) { switchFragment(tab?.position ?: 0) }
            })
        }
        switchFragment(0)
    }

    private fun switchFragment(index: Int) {
        if (!isAdded) return
        val tag = TAGS[index]
        var fragment = childFragmentManager.findFragmentByTag(tag)
        if (fragment == null) {
            fragment = when (index) {
                0 -> FeedingRecordsFragment()
                1 -> ExcreteRecordsFragment()
                2 -> CustomRecordFragment()
                else -> FeedingRecordsFragment()
            }
            childFragmentManager.beginTransaction()
                .add(com.babycare.R.id.child_fragment_container, fragment, tag)
                .commitNow()
        }
        val ft = childFragmentManager.beginTransaction()
        for (t in TAGS) {
            childFragmentManager.findFragmentByTag(t)?.let { ft.hide(it) }
        }
        ft.show(fragment)
        ft.commitNow()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}