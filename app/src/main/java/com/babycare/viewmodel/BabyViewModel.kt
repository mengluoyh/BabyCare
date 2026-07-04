package com.babycare.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.babycare.data.*
import com.babycare.util.AgeCalculator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BabyViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val dao = db.babyDao()

    val profile = dao.getProfile().stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    private val todayBounds = MutableStateFlow(AgeCalculator.getStartAndEndOfDay())
    private val weekBounds = MutableStateFlow(Pair(todayBounds.value.first - (6 * 24 * 60 * 60 * 1000L), todayBounds.value.second))

    val todayFeedings = todayBounds.flatMapLatest { (start, end) ->
        dao.getFeedings(start, end)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val todayExcretions = todayBounds.flatMapLatest { (start, end) ->
        dao.getExcretions(start, end)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val weekFeedings = weekBounds.flatMapLatest { (start, end) ->
        dao.getFeedings(start, end)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun refreshDayBounds() {
        val newToday = AgeCalculator.getStartAndEndOfDay()
        if (todayBounds.value != newToday) {
            todayBounds.value = newToday
            weekBounds.value = Pair(newToday.first - (6 * 24 * 60 * 60 * 1000L), newToday.second)
        }
    }

    fun initProfile(name: String, birthDate: Long) {
        viewModelScope.launch {
            dao.upsertProfile(BabyProfile(name = name, birthDate = birthDate, isLocked = true))
        }
    }

    fun unlockProfile() {
        viewModelScope.launch {
            profile.value?.let { dao.upsertProfile(it.copy(isLocked = false)) }
        }
    }

    fun updateCustomTarget(target: Int) {
        viewModelScope.launch {
            profile.value?.let { dao.upsertProfile(it.copy(customFormulaTarget = target, isLocked = true)) }
        }
    }

    fun addFeeding(type: String, amount: Int, duration: Int, timestamp: Long) {
        viewModelScope.launch {
            dao.insertFeeding(FeedingRecord(timestamp = timestamp, type = type, amount = amount, duration = duration))
        }
    }

    fun addExcretion(type: String, note: String, timestamp: Long) {
        viewModelScope.launch {
            dao.insertExcretion(ExcretionRecord(timestamp = timestamp, type = type, note = note))
        }
    }
}