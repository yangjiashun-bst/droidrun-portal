package com.droidrun.portal

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri

class DroidrunContentProvider : ContentProvider() {
    companion object {
        private const val TAG = "DroidrunContentProvider"
        private const val AUTHORITY = "com.droidrun.portal"
        private const val COMMANDS = 1

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "/", COMMANDS)
        }
    }

    override fun onCreate(): Boolean {
        Log.d(TAG, "DroidrunContentProvider created")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            COMMANDS -> executeQuery(selection)
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return when (uriMatcher.match(uri)) {
            COMMANDS -> executeAction(values)
            else -> null
        }
    }

    private fun executeQuery(commandJson: String?): Cursor {
        val cursor = MatrixCursor(arrayOf("result"))

        if (commandJson.isNullOrEmpty()) {
            cursor.addRow(arrayOf(createErrorResponse("No command provided")))
            return cursor
        }

        try {
            val command = JSONObject(commandJson)
            val action = command.getString("action")

            val result = when (action) {
                "get_a11y_tree" -> getAccessibilityTree()
                //"screenshot" -> takeScreenshot()
                "ping" -> createSuccessResponse("pong")
                else -> createErrorResponse("Unknown query action: $action")
            }

            cursor.addRow(arrayOf(result))

        } catch (e: JSONException) {
            Log.e(TAG, "Invalid JSON command", e)
            cursor.addRow(arrayOf(createErrorResponse("Invalid JSON: ${e.message}")))
        } catch (e: Exception) {
            Log.e(TAG, "Query execution failed", e)
            cursor.addRow(arrayOf(createErrorResponse("Execution failed: ${e.message}")))
        }

        return cursor
    }

    private fun executeAction(values: ContentValues?): Uri? {
        if (values == null) {
            return "content://$AUTHORITY/result?status=error&message=No values provided".toUri()
        }

        try {
            val action = values.getAsString("action") ?: return "content://$AUTHORITY/result?status=error&message=No action specified".toUri()

            val result = when (action) {
                /*"click" -> performClick(values)
                "swipe" -> performSwipe(values)
                "key_press" -> performKeyPress(values)
                "start_app" -> startApp(values)*/
                else -> "error: Unknown action: $action"
            }

            // Encode result in URI
            return if (result.startsWith("success")) {
                "content://$AUTHORITY/result?status=success&message=${Uri.encode(result)}".toUri()
            } else {
                "content://$AUTHORITY/result?status=error&message=${Uri.encode(result)}".toUri()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Action execution failed", e)
            return "content://$AUTHORITY/result?status=error&message=${Uri.encode("Execution failed: ${e.message}")}".toUri()
        }
    }

    private fun getAccessibilityTree(): String {
        val accessibilityService = DroidrunAccessibilityService.getInstance()
            ?: return createErrorResponse("Accessibility service not available")

        return try {
            val rootNode = accessibilityService.rootInActiveWindow
                ?: return createErrorResponse("No active window")

            val treeJson = buildAccessibilityTree(rootNode)
            rootNode.recycle()

            createSuccessResponse(treeJson.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get accessibility tree", e)
            createErrorResponse("Failed to get accessibility tree: ${e.message}")
        }
    }

    private fun buildAccessibilityTree(node: AccessibilityNodeInfo): JSONObject {
        val nodeJson = JSONObject().apply {
            put("className", node.className?.toString() ?: "")
            put("text", node.text?.toString() ?: "")
            put("contentDescription", node.contentDescription?.toString() ?: "")
            put("resourceId", node.viewIdResourceName ?: "")
            put("packageName", node.packageName?.toString() ?: "")
            put("isClickable", node.isClickable)
            put("isScrollable", node.isScrollable)
            put("isEnabled", node.isEnabled)
            put("isSelected", node.isSelected)
            put("isFocused", node.isFocused)

            // Bounds
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            put("bounds", JSONObject().apply {
                put("left", bounds.left)
                put("top", bounds.top)
                put("right", bounds.right)
                put("bottom", bounds.bottom)
            })

            // Children
            val childrenArray = mutableListOf<JSONObject>()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    childrenArray.add(buildAccessibilityTree(child))
                    child.recycle()
                }
            }
            put("children", childrenArray)
        }

        return nodeJson
    }



    private fun createSuccessResponse(data: String): String {
        return JSONObject().apply {
            put("status", "success")
            put("data", data)
        }.toString()
    }

    private fun createErrorResponse(error: String): String {
        return JSONObject().apply {
            put("status", "error")
            put("error", error)
        }.toString()
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun getType(uri: Uri): String? = null
}