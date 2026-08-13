package com.primaloptima.scribe.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeShellViewModel : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Tracks whether the DataStore-backed starting tab has been applied yet.
    // Without this guard, HomeScreen's LaunchedEffect(Unit) would reset the tab
    // to the default page every time the user navigates back to Home, losing
    // whatever tab they were on when they left.
    private var initialTabApplied = false

    fun selectTab(index: Int) {
        _selectedTab.value = index.coerceIn(0, 3)
    }

    /** Called once on first composition with the DataStore home-start preference.
     *  Subsequent calls (on back-navigation re-entry) are ignored. */
    fun setInitialTab(index: Int) {
        if (!initialTabApplied) {
            initialTabApplied = true
            _selectedTab.value = index.coerceIn(0, 3)
        }
    }
}