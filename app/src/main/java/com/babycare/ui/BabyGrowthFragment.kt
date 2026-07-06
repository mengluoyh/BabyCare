// BabyCare/app/src/main/java/com/babycare/ui/BabyGrowthFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.babycare.databinding.FragmentBabyGrowthBinding
import com.google.android.material.tabs.TabLayout

/**
 * 成长/护理页面 — 顶部两个Tab：宝宝成长 / 疫苗接种记录
 * - 宝宝成长 → BabyGrowthContentFragment（出生信息+体重）
 * - 疫苗接种记录 → BabyCareFragment（疫苗接种信息记录）
 */
class BabyGrowthFragment : Fragment() {
    private var _binding: FragmentBabyGrowthBinding? = null
    private val binding get() = _binding!!

    private val TAGS = arrayOf("growth", "care")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBabyGrowthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        switchFragment(0)
    }

    private fun setupTabs() {
        with(binding.tabLayout) {
            addTab(newTab().setText("👶 宝宝成长"))
            addTab(newTab().setText("💉 疫苗接种记录"))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) { switchFragment(tab?.position ?: 0) }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }
    }

    private fun switchFragment(position: Int) {
        val tag = TAGS[position]
        var fragment = childFragmentManager.findFragmentByTag(tag)
        if (fragment == null) {
            fragment = when (position) {
                0 -> BabyGrowthContentFragment()
                1 -> BabyCareFragment()
                else -> BabyGrowthContentFragment()
            }
            childFragmentManager.beginTransaction()
                .add(binding.contentContainer.id, fragment, tag)
                .commit()
        }
        val ft = childFragmentManager.beginTransaction()
        for (t in TAGS) {
            childFragmentManager.findFragmentByTag(t)?.let { ft.hide(it) }
        }
        ft.show(fragment)
        ft.commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}