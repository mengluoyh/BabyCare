// BabyCare/app/src/main/java/com/babycare/ui/RecordsFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.babycare.databinding.FragmentRecordsBinding
import com.google.android.material.tabs.TabLayout

class RecordsFragment : Fragment() {
    private var _binding: FragmentRecordsBinding? = null
    private val binding get() = _binding!!

    private val TAGS = arrayOf("feeding", "excrete", "custom")
    private val fragments = listOf(FeedingRecordsFragment(), ExcreteRecordsFragment(), CustomRecordFragment())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
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
        val fragment = fragments[index]
        val tag = TAGS[index]
        val ft = childFragmentManager.beginTransaction()
        for (t in TAGS) {
            childFragmentManager.findFragmentByTag(t)?.let { ft.hide(it) }
        }
        if (childFragmentManager.findFragmentByTag(tag) == null) {
            ft.add(com.babycare.R.id.child_fragment_container, fragment, tag)
        } else {
            ft.show(fragment)
        }
        ft.commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}