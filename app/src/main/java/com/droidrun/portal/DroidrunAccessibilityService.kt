package com.droidrun.portal

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.droidrun.portal.model.PhoneState

class DroidrunAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DroidrunA11yService"
        private var instance: DroidrunAccessibilityService? = null
        private const val MIN_ELEMENT_SIZE = 5

        fun getInstance(): DroidrunAccessibilityService? = instance
    }

    private lateinit var overlayManager: OverlayManager
    private val screenBounds = Rect()


    override fun onCreate() {
        super.onCreate()
        overlayManager = OverlayManager(this)
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val windowMetrics = windowManager.currentWindowMetrics
        val bounds = windowMetrics.bounds
        screenBounds.set(0, 0, bounds.width(), bounds.height())
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager.showOverlay()
        instance = this

        // Configure accessibility service
        serviceInfo = AccessibilityServiceInfo().apply {
            // Listen to all events
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK

            // Monitor all packages
            packageNames = null

            // Set feedback type
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            // Set flags for better access
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE

            // Enable screenshot capability (API 34+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH
            }
        }

        Log.d(TAG, "Accessibility service connected and configured")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle events for automation
        // This is just required by AccessibilityService
    }

    fun getVisibleElements(): MutableList<ElementNode> {
        val windows = windows
        val visibleElements = mutableListOf<ElementNode>()
        val indexCounter = IndexCounter(1) // Start indexing from 1

        /*if (windows != null && windows.isNotEmpty()) {
            val sortedWindows = windows.sortedByDescending { it.layer }

            for ((windowLayer, window) in sortedWindows.withIndex()) {
                val rootNode = window.root ?: continue
                findAllVisibleElements(rootNode, windowLayer, visibleElements)
            }

            windows.forEach { it.recycle() }
        } else {*/
            val rootNode = rootInActiveWindow ?: return visibleElements
            val rootElement = findAllVisibleElements(rootNode, 0, null, indexCounter)
            rootElement?.let {
                collectRootElements(it, visibleElements)
            }
        //}

        overlayManager.clearElements()

        visibleElements.forEach { rootElement ->
            addElementAndChildrenToOverlay(rootElement, 0)
        }

        overlayManager.refreshOverlay()
        return visibleElements
    }

    private fun collectRootElements(element: ElementNode, rootElements: MutableList<ElementNode>) {
        rootElements.add(element)
    }

    private fun findAllVisibleElements(
        node: AccessibilityNodeInfo,
        windowLayer: Int,
        parent: ElementNode?,
        indexCounter: IndexCounter
    ): ElementNode? {
        try {
            if (!node.isVisibleToUser) {
                return null
            }

            val rect = Rect()
            node.getBoundsInScreen(rect)

            val isInScreen = Rect.intersects(rect, screenBounds)
            val hasSize = rect.width() > MIN_ELEMENT_SIZE && rect.height() > MIN_ELEMENT_SIZE

            var currentElement: ElementNode? = null

            if (isInScreen && hasSize) {
                val text = node.text?.toString() ?: ""
                val contentDesc = node.contentDescription?.toString() ?: ""
                val className = node.className?.toString() ?: ""
                val viewId = node.viewIdResourceName ?: ""

                val displayText = when {
                    text.isNotEmpty() -> text
                    contentDesc.isNotEmpty() -> contentDesc
                    viewId.isNotEmpty() -> viewId.substringAfterLast('/')
                    else -> className.substringAfterLast('.')
                }

                val elementType = if (node.isClickable) {
                    "Clickable"
                } else if (node.isCheckable) {
                    "Checkable"
                } else if (node.isEditable) {
                    "Input"
                } else if (text.isNotEmpty()) {
                    "Text"
                } else if (node.isScrollable) {
                    "Container"
                } else {
                    "View"
                }

                val id = ElementNode.createId(rect, className.substringAfterLast('.'), displayText)

                currentElement = ElementNode(
                    AccessibilityNodeInfo(node),
                    Rect(rect),
                    displayText,
                    className.substringAfterLast('.'),
                    windowLayer,
                    System.currentTimeMillis(),
                    id
                )

                // Assign unique index
                currentElement.overlayIndex = indexCounter.getNext()

                // Set parent-child relationship
                parent?.addChild(currentElement)
            }

            // Recursively process children
            for (i in 0 until node.childCount) {
                val childNode = node.getChild(i) ?: continue
                val childElement = findAllVisibleElements(childNode, windowLayer, currentElement, indexCounter)
                // Children are already added to currentElement via parent?.addChild() call above
            }

            return currentElement

        } catch (e: Exception) {
            Log.e(TAG, "Error in findAllVisibleElements: ${e.message}", e)
            return null
        }
    }

     fun getPhoneState(): PhoneState {
        val focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val keyboardVisible = detectKeyboardVisibility()
        val currentPackage = rootInActiveWindow?.packageName?.toString()

        return PhoneState(focusedNode, keyboardVisible, currentPackage)
    }

    private fun detectKeyboardVisibility(): Boolean {
        try {
            val windows = windows
            if (windows != null) {
                val hasInputMethodWindow = windows.any { window -> window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                windows.forEach { it.recycle() }
                return hasInputMethodWindow
            } else { return false }
        } catch (e: Exception) { return false}
    }


    // Helper class to maintain global index counter
    private class IndexCounter(private var current: Int = 1) {
        fun getNext(): Int = current++
    }



    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
    }

    private fun addElementAndChildrenToOverlay(element: ElementNode, depth: Int) {
        overlayManager.addElement(
            text = element.text,
            rect = element.rect,
            type = element.className,
            index = element.overlayIndex
        )

        for (child in element.children) {
            addElementAndChildrenToOverlay(child, depth + 1)
        }
    }
}