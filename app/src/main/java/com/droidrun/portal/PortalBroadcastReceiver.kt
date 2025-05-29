package com.droidrun.portal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log

class PortalBroadcastReceiver : BroadcastReceiver() {
    private val TAG = "PortalBroadcastReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        // Verify that the intent is from our own package for additional security
        if (intent.component?.packageName != null && intent.component?.packageName != context.packageName) {
            Log.w(TAG, "Received intent from unauthorized package: ${intent.component?.packageName}")
            return
        }
        
        when (intent.action) {
            "com.droidrun.portal.DROIDRUN_INPUT_B64" -> {
                Log.d(TAG, "Received DROIDRUN_INPUT_B64 broadcast")
                
                // Extract and validate the message from the external broadcast
                val message = intent.getStringExtra("msg")
                if (message != null && isValidBase64(message)) {
                    // Create a new intent to forward to the keyboard service
                    // Use a different action name to avoid infinite loop
                    val forwardIntent = Intent("com.droidrun.portal.INTERNAL_INPUT_B64").apply {
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
                    Log.w(TAG, "Received DROIDRUN_INPUT_B64 broadcast with invalid or no message")
                }
            }
            else -> {
                Log.w(TAG, "Received unexpected action: ${intent.action}")
            }
        }
    }
    
    private fun isValidBase64(input: String): Boolean {
        return try {
            // Attempt to decode the base64 string to validate it
            Base64.decode(input, Base64.DEFAULT)
            true
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid base64 input received")
            false
        }
    }
} 