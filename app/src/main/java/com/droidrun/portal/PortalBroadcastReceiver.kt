package com.droidrun.portal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PortalBroadcastReceiver : BroadcastReceiver() {
    private val TAG = "PortalBroadcastReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.droidrun.portal.DROIDRUN_INPUT_B64" -> {
                Log.d(TAG, "Received DROIDRUN_INPUT_B64 broadcast")
                
                // Extract the message from the external broadcast
                val message = intent.getStringExtra("msg")
                if (message != null) {
                    // Create a new intent to forward to the keyboard service
                    // Use an explicit package-scoped broadcast to avoid security issues
                    val forwardIntent = Intent("DROIDRUN_INPUT_B64").apply {
                        putExtra("msg", message)
                        setPackage(context.packageName) // Restrict to our own package
                    }
                    
                    try {
                        context.sendBroadcast(forwardIntent)
                        Log.d(TAG, "Forwarded message to keyboard service: ${message.substring(0, minOf(50, message.length))}...")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error forwarding broadcast to keyboard service", e)
                    }
                } else {
                    Log.w(TAG, "Received DROIDRUN_INPUT_B64 broadcast with no message")
                }
            }
            else -> {
                Log.w(TAG, "Received unexpected action: ${intent.action}")
            }
        }
    }
} 