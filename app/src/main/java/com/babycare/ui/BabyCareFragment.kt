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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBabyCareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!isAdded) return
        // 直接显示疫苗接种记录，移除体重趋势图
        childFragmentManager.beginTransaction()
            .replace(com.babycare.R.id.childContainer, VaccinationRecordsFragment())
            .commitNow()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}