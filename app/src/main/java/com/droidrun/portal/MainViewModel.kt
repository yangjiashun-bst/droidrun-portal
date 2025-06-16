package com.droidrun.portal

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import com.droidrun.portal.features.overlay.OverlayRepository

class MainViewModel(application: Application) : AndroidViewModel(application),
    OverlayRepository.Listener {

    private val repository = OverlayRepository(application)
    private val contextRepository = ContextRepository(application)

    // UI State
    var status by mutableStateOf("Ready to fetch element data")
    var accessibilityStatus by mutableStateOf("DISABLED")
    var accessibilityIndicatorColor by mutableStateOf(Color.Red)
    var response by mutableStateOf("{\n  \"example\": true\n}")
    var overlayVisible by mutableStateOf(true)
    var offset by mutableIntStateOf(-128) // Default offset

    init {
        repository.setListener(this)
        updateAccessibilityStatusIndicator()
    }

    // region UI Events
    fun onFetchButtonClicked() = repository.fetchElementData()
    fun onRetriggerButtonClicked() = repository.retriggerElements()
    fun onOverlaySwitchToggled(isChecked: Boolean) {
        overlayVisible = isChecked
        repository.setOverlayEnabled(isChecked)
    }
    fun onOffsetChanged(value: String) {
        value.toIntOrNull()?.let(repository::setOverlayOffset)
    }
    // endregion

    // region Accessibility
    // Update the accessibility status indicator based on service status
    fun updateAccessibilityStatusIndicator() {
        val isEnabled = contextRepository.isAccessibilityServiceEnabled()

        if (isEnabled) {
            accessibilityIndicatorColor = Color.Green
            accessibilityStatus = "ENABLED"
        } else {
            accessibilityIndicatorColor = Color.Red
            accessibilityStatus = "DISABLED"
        }
    }

    override fun onCleared() {
        repository.clearListener()
        super.onCleared()
    }

    // Listener implementations
    override fun onStatusChanged(status: String) {
        this.status = status
    }

    override fun onResponse(json: String) {
        this.response = json
    }

    override fun onToast(message: String) {
        // TODO: Implement a toast mechanism
    }

    override fun onOverlaySwitchChanged(isChecked: Boolean) {
        this.overlayVisible = isChecked
    }

    override fun onOffsetChanged(newOffset: Int) {
        this.offset = newOffset
    }

    override fun onAccessibilityServiceStateChanged(isEnabled: Boolean) {
        if (isEnabled) {
            accessibilityIndicatorColor = Color.Green
            accessibilityStatus = "ENABLED"
        } else {
            accessibilityIndicatorColor = Color.Red
            accessibilityStatus = "DISABLED"
            overlayVisible = false
        }
    }
}
