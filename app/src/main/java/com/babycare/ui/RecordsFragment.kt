// BabyCare/app/src/main/java/com/babycare/ui/RecordsFragment.kt
package com.babycare.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.babycare.databinding.FragmentRecordsBinding

class RecordsFragment : Fragment() {
    private var _binding: FragmentRecordsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        binding.cardFeeding.setOnClickListener {
            openChildFragment(FeedingRecordsFragment())
        }
        binding.cardExcrete.setOnClickListener {
            openChildFragment(ExcreteRecordsFragment())
        }
    }

    private fun openChildFragment(fragment: Fragment) {
        // 显示子容器
        binding.childFragmentContainer.visibility = View.VISIBLE
        // 隐藏入口卡片
        binding.cardFeeding.visibility = View.GONE
        binding.cardExcrete.visibility = View.GONE

        childFragmentManager.beginTransaction()
            .replace(com.babycare.R.id.child_fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}