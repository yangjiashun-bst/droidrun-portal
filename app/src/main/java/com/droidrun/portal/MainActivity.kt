package com.droidrun.portal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {
    
    private lateinit var statusText: TextView
    private lateinit var responseText: TextView
    private lateinit var toggleOverlay: SwitchMaterial
    private lateinit var fetchButton: MaterialButton
    private lateinit var retriggerButton: MaterialButton
    
    // Broadcast receiver to get element data response
    private val elementDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.e("DROIDRUN_MAIN", "Received broadcast: ${intent.action}")
            if (intent.action == DroidrunPortalService.ACTION_ELEMENTS_RESPONSE) {
                // Handle element data response
                val data = intent.getStringExtra(DroidrunPortalService.EXTRA_ELEMENTS_DATA)
                if (data != null) {
                    Log.e("DROIDRUN_MAIN", "Received element data: ${data.substring(0, Math.min(100, data.length))}...")
                    
                    // Update UI with the data
                    statusText.text = "Received data: ${data.length} characters"
                    responseText.text = data // Display the full JSON string
                    Toast.makeText(context, "Data received successfully!", Toast.LENGTH_SHORT).show()
                }
                
                // Handle retrigger response
                val retriggerStatus = intent.getStringExtra("retrigger_status")
                if (retriggerStatus != null) {
                    val count = intent.getIntExtra("elements_count", 0)
                    statusText.text = "Elements refreshed: $count UI elements restored"
                    Toast.makeText(context, "Refresh successful: $count elements", Toast.LENGTH_SHORT).show()
                }
                
                // Handle overlay toggle status
                if (intent.hasExtra("overlay_status")) {
                    val overlayVisible = intent.getBooleanExtra("overlay_status", true)
                    toggleOverlay.isChecked = overlayVisible
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize UI elements
        statusText = findViewById(R.id.status_text)
        responseText = findViewById(R.id.response_text)
        fetchButton = findViewById(R.id.fetch_button)
        retriggerButton = findViewById(R.id.retrigger_button)
        toggleOverlay = findViewById(R.id.toggle_overlay)
        
        // Register for responses
        val filter = IntentFilter(DroidrunPortalService.ACTION_ELEMENTS_RESPONSE)
        registerReceiver(elementDataReceiver, filter)
        
        fetchButton.setOnClickListener {
            fetchElementData()
        }
        
        retriggerButton.setOnClickListener {
            retriggerElements()
        }
        
        toggleOverlay.setOnCheckedChangeListener { _, isChecked ->
            toggleOverlayVisibility(isChecked)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(elementDataReceiver)
        } catch (e: Exception) {
            Log.e("DROIDRUN_MAIN", "Error unregistering receiver: ${e.message}")
        }
    }
    
    private fun fetchElementData() {
        try {
            // Send broadcast to request elements
            val intent = Intent(DroidrunPortalService.ACTION_GET_ELEMENTS)
            sendBroadcast(intent)
            
            statusText.text = "Request sent, awaiting response..."
            Log.e("DROIDRUN_MAIN", "Broadcast sent with action: ${DroidrunPortalService.ACTION_GET_ELEMENTS}")
        } catch (e: Exception) {
            statusText.text = "Error sending request: ${e.message}"
            Log.e("DROIDRUN_MAIN", "Error sending broadcast: ${e.message}")
        }
    }
    
    private fun toggleOverlayVisibility(visible: Boolean) {
        try {
            val intent = Intent(DroidrunPortalService.ACTION_TOGGLE_OVERLAY).apply {
                putExtra(DroidrunPortalService.EXTRA_OVERLAY_VISIBLE, visible)
            }
            sendBroadcast(intent)
            
            statusText.text = "Visualization overlays ${if (visible) "enabled" else "disabled"}"
            Log.e("DROIDRUN_MAIN", "Toggled overlay visibility to: $visible")
        } catch (e: Exception) {
            statusText.text = "Error changing visibility: ${e.message}"
            Log.e("DROIDRUN_MAIN", "Error toggling overlay: ${e.message}")
        }
    }
    
    private fun retriggerElements() {
        try {
            // Send broadcast to request element retrigger
            val intent = Intent(DroidrunPortalService.ACTION_RETRIGGER_ELEMENTS)
            sendBroadcast(intent)
            
            statusText.text = "Refreshing UI elements..."
            Log.e("DROIDRUN_MAIN", "Broadcast sent with action: ${DroidrunPortalService.ACTION_RETRIGGER_ELEMENTS}")
            Toast.makeText(this, "Refreshing elements...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            statusText.text = "Error refreshing elements: ${e.message}"
            Log.e("DROIDRUN_MAIN", "Error sending retrigger broadcast: ${e.message}")
        }
    }
} 