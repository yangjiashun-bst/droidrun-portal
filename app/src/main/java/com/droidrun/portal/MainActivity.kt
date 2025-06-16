package com.droidrun.portal

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel

    companion object {
        // Intent action for updating overlay offset
        const val ACTION_UPDATE_OVERLAY_OFFSET = "com.droidrun.portal.UPDATE_OVERLAY_OFFSET"
        const val EXTRA_OVERLAY_OFFSET = "overlay_offset"
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.updateAccessibilityStatusIndicator()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = droidrunPrimary,
                    secondary = droidrunSecondary,
                    background = Color.Transparent,
                )
            ) {
                viewModel = viewModel()
                MainScreen(
                    viewModel = viewModel,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings
                )
            }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "Please enable Droidrun Portal in Accessibility Services",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e("DROIDRUN_MAIN", "Error opening accessibility settings: ${e.message}")
            Toast.makeText(
                this,
                "Error opening accessibility settings",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}