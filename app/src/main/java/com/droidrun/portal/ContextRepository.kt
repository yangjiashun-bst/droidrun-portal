package com.droidrun.portal

import android.content.Context
import android.provider.Settings
import android.util.Log


class ContextRepository(private val context: Context) {
    // Check if the accessibility service is enabled
    fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityServiceName = context.packageName + "/" + DroidrunPortalService::class.java.canonicalName

        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            return enabledServices?.contains(accessibilityServiceName) == true
        } catch (e: Exception) {
            Log.e("DROIDRUN_MAIN", "Error checking accessibility status: ${e.message}")
            return false
        }
    }

}
