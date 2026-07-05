// BabyCare/app/src/main/java/com/babycare/ui/BabyCareFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.babycare.databinding.FragmentBabyCareBinding
import com.google.android.material.tabs.TabLayout

class BabyCareFragment : Fragment() {
    private var _binding: FragmentBabyCareBinding? = null
    private val binding get() = _binding!!

    private val TAGS = arrayOf("weight_trend", "vaccine")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBabyCareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
    }

    private fun setupTabs() {
        with(binding.tabLayout) {
            addTab(newTab().setText("⚖️ 体重趋势图"))
            addTab(newTab().setText("💉 疫苗接种信息记录"))
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
                0 -> WeightTrendFragment()
                1 -> VaccinationRecordsFragment()
                else -> WeightTrendFragment()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}