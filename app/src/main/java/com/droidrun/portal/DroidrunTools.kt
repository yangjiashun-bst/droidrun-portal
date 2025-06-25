package com.droidrun.portal

import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DroidrunTools {
    /**
     * Perform swipe gesture
     * @param startX Starting X coordinate
     * @param startY Starting Y coordinate
     * @param endX Ending X coordinate
     * @param endY Ending Y coordinate
     * @param durationMs Duration in milliseconds
     * @return Map with swipe details
     * @throws Exception if swipe fails
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 500L) {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        val latch = CountDownLatch(1)
        var success = false

        dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    success = true
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    success = false
                    latch.countDown()
                }
            },
            null
        )

        if (!latch.await(durationMs + 2000, TimeUnit.MILLISECONDS) || !success) {
            throw Exception("Swipe gesture failed or timed out")
        }
    }

    /**
     * Input text into focused element
     * @param text Text to input
     * @return Input method used ("direct" or "clipboard")
     * @throws Exception if no focused element or input fails
     */
    fun inputText(text: String): String {
        val focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: throw Exception("No focused input element found")

        try {
            // Try to set text directly
            val arguments = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }

            val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            return if (success) {
                "direct"
            } else {
                // Fallback: try to use clipboard
                inputTextViaClipboard(text)
                "clipboard"
            }
        } finally {
            focusedNode.recycle()
        }
    }

    private fun inputTextViaClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("droidrun_input", text)
        clipboard.setPrimaryClip(clip)

        // Simulate Ctrl+V
        val success = performGlobalAction(GLOBAL_ACTION_PASTE)

        if (!success) {
            throw Exception("Clipboard paste failed")
        }
    }

    /**
     * Start an application
     * @param packageName Package name to start
     * @param activityName Optional activity name
     * @return Package name that was started
     * @throws Exception if app not found or start fails
     */
    fun startApp(packageName: String, activityName: String = "") {
        val intent = if (activityName.isNotEmpty()) {
            Intent().apply {
                setClassName(packageName, activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: throw Exception("App not found: $packageName")
        }

        startActivity(intent)
    }

    /**
     * List installed packages
     * @param includeSystemApps Whether to include system apps
     * @return List of package names
     */
    fun listPackages(includeSystemApps: Boolean = false): List<String> {
        val pm = packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)

        return if (includeSystemApps) {
            packages.map { it.packageName }
        } else {
            packages.filter { (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { it.packageName }
        }
    }
}