package com.droidrun.portal.features.overlay

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.droidrun.portal.DroidrunPortalService
import com.droidrun.portal.DroidrunPortalService.Companion.ACTION_ELEMENTS_RESPONSE
import com.droidrun.portal.DroidrunPortalService.Companion.ACTION_GET_ELEMENTS
import com.droidrun.portal.DroidrunPortalService.Companion.ACTION_RETRIGGER_ELEMENTS
import com.droidrun.portal.DroidrunPortalService.Companion.EXTRA_ELEMENTS_DATA
import com.droidrun.portal.DroidrunPortalService.Companion.EXTRA_OVERLAY_VISIBLE
import com.droidrun.portal.MainActivity.Companion.ACTION_UPDATE_OVERLAY_OFFSET
import com.droidrun.portal.MainActivity.Companion.EXTRA_OVERLAY_OFFSET

/**
 * A repository that acts as a bridge between the `DroidrunPortalService` and the rest of the
 * application. It is responsible for sending commands to the service (e.g., to fetch elements)
 * and listening for responses via a `BroadcastReceiver`.
 *
 * This class abstracts the data-fetching logic from the UI and the `ViewModel`, providing a
 * clean API for the `ViewModel` to interact with the overlay feature without needing to know
 * the details of how the data is fetched or how the overlay is managed.
 *
 * @param context The application context, used for sending and receiving broadcasts.
 */
class OverlayRepository(private val context: Context) {

    interface Listener {
        fun onStatusChanged(status: String)
        fun onResponse(json: String)
        fun onToast(message: String)
        fun onOverlaySwitchChanged(isChecked: Boolean)
        fun onOffsetChanged(newOffset: Int)
    }

    private var listener: Listener? = null
    fun setListener(l: Listener) { listener = l }
    fun clearListener() { listener = null }

    // Manage all service communication, BroadcastReceivers, etc. here
    private val elementDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_ELEMENTS_RESPONSE) {
                val data = intent.getStringExtra(EXTRA_ELEMENTS_DATA)
                if (data != null) {
                    listener?.onStatusChanged("Received data: ${data.length} characters")
                    listener?.onResponse(data)
                    listener?.onToast("Data received successfully!")
                }

                val retriggerStatus = intent.getStringExtra("retrigger_status")
                if (retriggerStatus != null) {
                    val count = intent.getIntExtra("elements_count", 0)
                    listener?.onStatusChanged("Elements refreshed: $count UI elements restored")
                    listener?.onToast("Refresh successful: $count elements")
                }

                if (intent.hasExtra(EXTRA_OVERLAY_VISIBLE)) {
                    listener?.onOverlaySwitchChanged(
                        intent.getBooleanExtra(EXTRA_OVERLAY_VISIBLE, true)
                    )
                }

                if (intent.hasExtra("current_offset")) {
                    val currentOffset = intent.getIntExtra("current_offset", -128)
                    listener?.onOffsetChanged(currentOffset)
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_ELEMENTS_RESPONSE)
        }
        context.registerReceiver(elementDataReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    fun fetchElementData() {
        val intent = Intent(ACTION_GET_ELEMENTS)
        context.sendBroadcast(intent)
    }

    fun retriggerElements() {
        val intent = Intent(ACTION_RETRIGGER_ELEMENTS)
        context.sendBroadcast(intent)
    }

    fun setOverlayEnabled(visible: Boolean) {
        val intent = Intent(DroidrunPortalService.ACTION_TOGGLE_OVERLAY).apply {
            putExtra(DroidrunPortalService.EXTRA_OVERLAY_VISIBLE, visible)
        }
        context.sendBroadcast(intent)
    }

    fun setOverlayOffset(offset: Int) {
        val intent = Intent(ACTION_UPDATE_OVERLAY_OFFSET).apply {
            putExtra(EXTRA_OVERLAY_OFFSET, offset)
        }
        context.sendBroadcast(intent)
    }

    fun clear() {
        context.unregisterReceiver(elementDataReceiver)
        listener = null
    }
}
