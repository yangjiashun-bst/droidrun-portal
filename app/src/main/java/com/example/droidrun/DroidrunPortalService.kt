package com.example.droidrun

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context
import android.graphics.Point
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.atomic.AtomicBoolean
import android.graphics.Color
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import org.json.JSONArray
import org.json.JSONObject

class DroidrunPortalService : AccessibilityService() {
    
    companion object {
        private const val TAG = "DROIDRUN_PORTAL"
        private const val REFRESH_INTERVAL_MS = 250L // Single refresh interval for all updates
        private const val MIN_ELEMENT_SIZE = 5 // Minimum size for an element to be considered
        private const val MIN_FRAME_TIME_MS = 16L // Minimum time between frames (roughly 60 FPS)
        
        // Time-based fade settings
        private const val FADE_DURATION_MS = 60000L // Time to fade from weight 1.0 to 0.0 (60 seconds)
        private const val VISUALIZATION_REFRESH_MS = 250L // How often to refresh visualization (250ms = 4 times per second)
        private const val MIN_DISPLAY_WEIGHT = 0.05f // Minimum weight to display elements
        private const val SAME_TIME_THRESHOLD_MS = 500L // Elements appearing within this time window are considered "same time"
        
        // Color for heatmap (we'll use a gradient from RED to BLUE based on weight)
        private val NEW_ELEMENT_COLOR = Color.RED         // Newest elements
        private val OLD_ELEMENT_COLOR = Color.BLUE        // Oldest elements
        
        // Intent actions for ADB communication
        const val ACTION_GET_ELEMENTS = "com.example.droidrun.GET_ELEMENTS"
        const val ACTION_ELEMENTS_RESPONSE = "com.example.droidrun.ELEMENTS_RESPONSE"
        const val ACTION_TOGGLE_OVERLAY = "com.example.droidrun.TOGGLE_OVERLAY"
        const val ACTION_RETRIGGER_ELEMENTS = "com.example.droidrun.RETRIGGER_ELEMENTS"
        const val EXTRA_ELEMENTS_DATA = "elements_data"
        const val EXTRA_OVERLAY_VISIBLE = "overlay_visible"
    }
    
    private lateinit var overlayManager: OverlayManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isInitialized = false
    private val screenBounds = Rect()
    private val visibleElements = mutableListOf<ElementNode>()
    private val previousElements = mutableListOf<ElementNode>() // Track previous elements
    private val isProcessing = AtomicBoolean(false)
    private var currentPackageName: String = "" // Track current app package
    private var overlayVisible = true // Track if overlay is visible
    
    // Track currently displayed elements (after filtering)
    private val displayedElements = mutableListOf<Pair<ElementNode, Float>>()
    
    private var lastDrawTime = 0L
    private var pendingVisualizationUpdate = false
    
    // Broadcast receiver for ADB commands
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_GET_ELEMENTS -> {
                    broadcastElementData()
                }
                ACTION_TOGGLE_OVERLAY -> {
                    val shouldShow = intent.getBooleanExtra(EXTRA_OVERLAY_VISIBLE, !overlayVisible)
                    if (shouldShow && !overlayVisible) {
                        overlayManager.showOverlay()
                        overlayVisible = true
                    } else if (!shouldShow && overlayVisible) {
                        overlayManager.hideOverlay()
                        overlayVisible = false
                    }
                }
                ACTION_RETRIGGER_ELEMENTS -> {
                    retriggerElements()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        try {
            // Register broadcast receiver for commands
            val intentFilter = IntentFilter().apply {
                addAction(ACTION_GET_ELEMENTS)
                addAction(ACTION_TOGGLE_OVERLAY)
                addAction(ACTION_RETRIGGER_ELEMENTS)
            }
            registerReceiver(broadcastReceiver, intentFilter)
            Log.e("DROIDRUN_RECEIVER", "Registered receiver for commands")
            
            overlayManager = OverlayManager(this)
            isInitialized = true
            
            // Get screen dimensions
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val size = Point()
            display.getSize(size)
            screenBounds.set(0, 0, size.x, size.y)
            
            Log.d(TAG, "Screen bounds: $screenBounds")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OverlayManager: ${e.message}", e)
            Log.e("DROIDRUN_RECEIVER", "Error in onCreate: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        try {
            // Unregister broadcast receiver
            try {
                unregisterReceiver(broadcastReceiver)
                Log.e("DROIDRUN_RECEIVER", "Unregistered command receiver")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver: ${e.message}")
            }
            
            stopPeriodicUpdates()
            mainHandler.removeCallbacks(visualizationRunnable)
            resetOverlayState()
            
            if (isInitialized) {
                overlayManager.hideOverlay()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}", e)
        }
        super.onDestroy()
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized) {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastDraw = currentTime - lastDrawTime
                
                if (timeSinceLastDraw >= MIN_FRAME_TIME_MS) {
                    processActiveWindow()
                    updateVisualizationIfNeeded()
                    lastDrawTime = currentTime
                } else if (pendingVisualizationUpdate) {
                    // Schedule next update to maintain frame rate
                    mainHandler.postDelayed(this, MIN_FRAME_TIME_MS - timeSinceLastDraw)
                    return
                }
            }
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }
    
    private val visualizationRunnable = object : Runnable {
        override fun run() {
            if (isInitialized) {
                updateVisualizationIfNeeded()
            }
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private fun startPeriodicUpdates() {
        Log.d(TAG, "Starting periodic updates")
        lastDrawTime = System.currentTimeMillis()
        mainHandler.postDelayed(updateRunnable, REFRESH_INTERVAL_MS)
    }
    
    private fun stopPeriodicUpdates() {
        Log.d(TAG, "Stopping periodic updates")
        mainHandler.removeCallbacks(updateRunnable)
    }
    
    private fun startVisualizationUpdates() {
        Log.d(TAG, "Starting visualization updates")
        mainHandler.postDelayed(visualizationRunnable, VISUALIZATION_REFRESH_MS)
    }
    
    private fun stopVisualizationUpdates() {
        Log.d(TAG, "Stopping visualization updates")
        mainHandler.removeCallbacks(visualizationRunnable)
    }
    
    private fun updateVisualizationIfNeeded() {
        if (!isInitialized || visibleElements.isEmpty()) {
            pendingVisualizationUpdate = false
            return
        }
        
        try {
            // Process all visible elements
            val elementsToProcess = visibleElements.map { element -> 
                Pair(element, element.calculateWeight())
            }
            
            // Filter by weight and sort
            val weightSortedElements = elementsToProcess
                .filter { (_, weight) -> weight > MIN_DISPLAY_WEIGHT }
                .sortedByDescending { (_, weight) -> weight }
            
            if (!overlayVisible) {
                pendingVisualizationUpdate = false
                return
            }
            
            // Check if we actually need to update
            val needsUpdate = synchronized(displayedElements) {
                if (displayedElements.size != weightSortedElements.size) {
                    true
                } else {
                    // Check if weights have changed significantly
                    displayedElements.zip(weightSortedElements).any { (current, new) ->
                        Math.abs(current.second - new.second) > 0.05f
                    }
                }
            }
            
            if (!needsUpdate) {
                pendingVisualizationUpdate = false
                return
            }
            
            // Process overlapping elements
            val elementsToDisplay = mutableListOf<Pair<ElementNode, Float>>()
            
            for (currentElement in weightSortedElements) {
                val (element, weight) = currentElement
                
                val overlappingElements = elementsToDisplay.filter { (existingElement, _) ->
                    element.overlaps(existingElement)
                }
                
                if (overlappingElements.isEmpty()) {
                    elementsToDisplay.add(currentElement)
                } else {
                    val showDespiteOverlap = overlappingElements.any { (existingElement, _) ->
                        val timeDiff = Math.abs(existingElement.creationTime - element.creationTime)
                        timeDiff <= SAME_TIME_THRESHOLD_MS
                    }
                    
                    if (showDespiteOverlap) {
                        elementsToDisplay.add(currentElement)
                    }
                }
            }
            
            // Sort by layer and weight
            val sortedElements = elementsToDisplay.sortedWith(
                compareBy<Pair<ElementNode, Float>> { (element, _) -> element.windowLayer }
                    .thenByDescending { (_, weight) -> weight }
            )
            
            // Update UI if visible
            if (overlayVisible) {
                overlayManager.clearElements()
                
                for ((element, weight) in sortedElements) {
                    val heatmapColor = calculateHeatmapColor(weight)
                    
                    overlayManager.addElement(
                        rect = element.rect,
                        type = "${element.className}", 
                        text = element.text,
                        depth = element.windowLayer,
                        color = heatmapColor
                    )
                }
                
                overlayManager.refreshOverlay()
            }
            
            // Update displayed elements list
            synchronized(displayedElements) {
                displayedElements.clear()
                displayedElements.addAll(sortedElements)
            }
            
            pendingVisualizationUpdate = false
            Log.d(TAG, "Updated visualization with ${sortedElements.size} elements")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating visualization: ${e.message}", e)
            pendingVisualizationUpdate = true  // Try again next frame
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventPackage = event.packageName?.toString() ?: ""
        
        if (eventPackage.isNotEmpty() && eventPackage != currentPackageName && currentPackageName.isNotEmpty()) {
            Log.d(TAG, "App changed from $currentPackageName to $eventPackage, clearing overlay")
            resetOverlayState()
        }
        
        if (eventPackage.isNotEmpty()) {
            currentPackageName = eventPackage
        }
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                pendingVisualizationUpdate = true  // Mark for update on next frame
            }
        }
    }
    
    private fun resetOverlayState() {
        try {
            overlayManager.clearElements()
            overlayManager.refreshOverlay()
            
            clearElementList()
            
            for (element in previousElements) {
                try {
                    element.nodeInfo.recycle()
                } catch (e: Exception) {
                    Log.e(TAG, "Error recycling previous node: ${e.message}")
                }
            }
            previousElements.clear()
            
            Log.d(TAG, "Overlay state reset due to app change")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting overlay state: ${e.message}", e)
        }
    }
    
    private fun processActiveWindow() {
        if (!isProcessing.compareAndSet(false, true)) {
            return
        }
        
        try {
            if (currentPackageName.isEmpty()) {
                if (overlayVisible) {
                    overlayManager.clearElements()
                    overlayManager.refreshOverlay()
                }
                isProcessing.set(false)
                return
            }
            
            saveCurrentElementsAsPrevious()
            
            clearElementList()
            
            val windows = windows
            
            if (windows != null && windows.isNotEmpty()) {
                val sortedWindows = windows.sortedByDescending { it.layer }
                
                for ((windowLayer, window) in sortedWindows.withIndex()) {
                    val rootNode = window.root ?: continue
                    findAllVisibleElements(rootNode, windowLayer)
                }
                
                windows.forEach { it.recycle() }
            } else {
                val rootNode = rootInActiveWindow ?: return
                findAllVisibleElements(rootNode, 0)
            }
            
            if (visibleElements.isEmpty()) {
                if (overlayVisible) {
                    overlayManager.clearElements()
                    overlayManager.refreshOverlay()
                }
                isProcessing.set(false)
                return
            }
            
            preserveCreationTimes()
            
            // Always process elements for data collection, but only update UI if overlay is visible
            updateElementsForDataCollection()
            
            // Only update the overlay UI if it's visible
            if (overlayVisible) {
                updateOverlayWithTimeBasedWeights()
            }
            
            Log.d(TAG, "Processed ${visibleElements.size} elements for package $currentPackageName")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing active window: ${e.message}", e)
            if (overlayVisible) {
                overlayManager.clearElements()
                overlayManager.refreshOverlay()
            }
        } finally {
            isProcessing.set(false)
        }
    }
    
    private fun saveCurrentElementsAsPrevious() {
        for (element in previousElements) {
            try {
                element.nodeInfo.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error recycling previous node: ${e.message}")
            }
        }
        previousElements.clear()
        
        for (element in visibleElements) {
            val copy = ElementNode(
                AccessibilityNodeInfo.obtain(element.nodeInfo),
                Rect(element.rect),
                element.text,
                element.className,
                element.windowLayer,
                element.creationTime,
                element.id
            )
            previousElements.add(copy)
        }
    }
    
    private fun preserveCreationTimes() {
        for (element in visibleElements) {
            val previousElement = previousElements.find { it.id == element.id }
            
            if (previousElement != null) {
                element.creationTime = previousElement.creationTime
                Log.d(TAG, "Element persists: ${element.text}, age: ${System.currentTimeMillis() - element.creationTime}ms")
            } else {
                Log.d(TAG, "New element: ${element.text}")
            }
        }
    }
    
    private fun findAllVisibleElements(node: AccessibilityNodeInfo, windowLayer: Int) {
        try {
            if (!node.isVisibleToUser) {
                return
            }
            
            val rect = Rect()
            node.getBoundsInScreen(rect)
            
            val isInScreen = Rect.intersects(rect, screenBounds)
            val hasSize = rect.width() > MIN_ELEMENT_SIZE && rect.height() > MIN_ELEMENT_SIZE
            
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
                
                val elementNode = ElementNode(
                    AccessibilityNodeInfo.obtain(node),
                    Rect(rect),
                    displayText.take(30),
                    className.substringAfterLast('.'),
                    windowLayer,
                    System.currentTimeMillis(),
                    id
                )
                visibleElements.add(elementNode)
                Log.d(TAG, "Found $elementType: ${displayText} [${className.substringAfterLast('.')}] at $rect")
            }
            
            for (i in 0 until node.childCount) {
                val childNode = node.getChild(i) ?: continue
                try {
                    findAllVisibleElements(childNode, windowLayer)
                } finally {
                    childNode.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in findAllVisibleElements: ${e.message}", e)
        }
    }
    
    private fun calculateHeatmapColor(weight: Float): Int {
        val normalizedWeight = weight
        
        val startRed = Color.red(NEW_ELEMENT_COLOR)
        val startGreen = Color.green(NEW_ELEMENT_COLOR)
        val startBlue = Color.blue(NEW_ELEMENT_COLOR)
        
        val endRed = Color.red(OLD_ELEMENT_COLOR)
        val endGreen = Color.green(OLD_ELEMENT_COLOR)
        val endBlue = Color.blue(OLD_ELEMENT_COLOR)
        
        val red = (startRed * normalizedWeight + endRed * (1 - normalizedWeight)).toInt()
        val green = (startGreen * normalizedWeight + endGreen * (1 - normalizedWeight)).toInt()
        val blue = (startBlue * normalizedWeight + endBlue * (1 - normalizedWeight)).toInt()
        
        val alpha = (255 * weight).toInt()
        
        return Color.argb(alpha, red, green, blue)
    }
    
    // This method processes elements for data collection without affecting the UI
    private fun updateElementsForDataCollection() {
        try {
            Log.d(TAG, "Starting element processing with ${visibleElements.size} elements")
            
            // First, sort all elements by creation time (oldest first)
            val sortedElements = visibleElements.sortedBy { it.creationTime }
            Log.d(TAG, "Sorted elements by creation time")
            
            // Clear existing relationships
            for (element in visibleElements) {
                element.parent = null
                element.children.clear()
            }
            Log.d(TAG, "Cleared existing relationships")
            
            // Build the hierarchy - but limit nesting depth to avoid cycles
            val processedElements = mutableSetOf<String>() // Track processed elements by ID
            
            for (element in sortedElements) {
                if (processedElements.contains(element.id)) {
                    Log.w(TAG, "Element ${element.text} already processed, skipping")
                    continue
                }
                
                // Find potential containers (elements that fully contain this one)
                val containers = sortedElements.filter { potential ->
                    potential != element && 
                    potential.contains(element) &&
                    !processedElements.contains(potential.id)
                }
                
                // Find the smallest container
                val bestContainer = containers.minByOrNull { it.rect.width() * it.rect.height() }
                
                if (bestContainer != null) {
                    bestContainer.addChild(element)
                    Log.d(TAG, "Added ${element.text} as child of ${bestContainer.text}")
                } else {
                    element.parent = null
                    Log.d(TAG, "Kept ${element.text} as root element")
                }
                
                processedElements.add(element.id)
            }
            
            // Now assign indices to clickable elements in order of their creation time
            var clickableIndex = 0
            
            // First reset all indices
            for (element in visibleElements) {
                element.clickableIndex = -1
            }
            
            // Then assign new indices to qualifying elements
            val clickableElements = sortedElements.filter { it.isClickable() || it.isText() }
            Log.d(TAG, "Found ${clickableElements.size} clickable/text elements")
            
            for (element in clickableElements) {
                element.clickableIndex = clickableIndex++
                Log.d(TAG, "Assigned index ${element.clickableIndex} to ${element.text}")
            }
            
            // Update the displayed elements list with the hierarchy information
            synchronized(displayedElements) {
                displayedElements.clear()
                
                // Add all elements with their weights, but only if they're visible
                val visibleWeightElements = sortedElements.mapNotNull { element ->
                    val weight = element.calculateWeight()
                    if (weight > MIN_DISPLAY_WEIGHT) {
                        Pair(element, weight)
                    } else null
                }
                
                displayedElements.addAll(visibleWeightElements)
                Log.d(TAG, "Updated displayed elements, now showing ${displayedElements.size} elements")
            }
            
            Log.d(TAG, "Completed hierarchy build with ${sortedElements.size} elements, ${clickableIndex} clickable/text elements")
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateElementsForDataCollection: ${e.message}", e)
        }
    }
    
    private fun updateOverlayWithTimeBasedWeights() {
        try {
            if (!overlayVisible) {
                Log.d(TAG, "Overlay not visible, skipping update")
                return
            }
            
            Log.d(TAG, "Starting overlay update with ${displayedElements.size} elements")
            
            overlayManager.clearElements()
            
            // Process elements in order of nesting (parents before children)
            val elementsByNesting = displayedElements.sortedBy { (element, _) -> 
                element.getNestingLevel() 
            }
            
            var addedCount = 0
            for ((element, weight) in elementsByNesting) {
                try {
                    val heatmapColor = calculateHeatmapColor(weight)
                    
                    // Create label based on element type and index
                    val label = when {
                        element.isClickable() -> "C${element.clickableIndex}"
                        element.isText() -> "T${element.clickableIndex}"
                        else -> ""
                    }
                    
                    overlayManager.addElement(
                        rect = element.rect,
                        type = "${element.className}",
                        text = label,
                        depth = element.getNestingLevel(),
                        color = heatmapColor
                    )
                    addedCount++
                    
                    Log.d(TAG, "Added element to overlay: ${element.text} with label $label")
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding element ${element.text}: ${e.message}", e)
                }
            }
            
            Log.d(TAG, "Added $addedCount elements to overlay, refreshing display")
            overlayManager.refreshOverlay()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateOverlayWithTimeBasedWeights: ${e.message}", e)
        }
    }
    
    private fun clearElementList() {
        for (element in visibleElements) {
            try {
                element.nodeInfo.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error recycling node: ${e.message}")
            }
        }
        visibleElements.clear()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
        // Clear overlay when service is interrupted
        if (isInitialized) {
            resetOverlayState()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected, initializing overlay")
        
        // Start the overlay with a short delay
        mainHandler.postDelayed({
            try {
                if (isInitialized) {
                    overlayManager.showOverlay()
                    startPeriodicUpdates()
                    mainHandler.post(visualizationRunnable)
                    
                    // Send initial state to the MainActivity
                    val responseIntent = Intent(ACTION_ELEMENTS_RESPONSE).apply {
                        putExtra(EXTRA_OVERLAY_VISIBLE, overlayVisible)
                    }
                    sendBroadcast(responseIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing overlay: ${e.message}", e)
            }
        }, 1000)
    }

    private fun broadcastElementData() {
        try {
            // Check if overlay is ready
            if (!isInitialized) {
                Log.e("DROIDRUN_DEBUG", "Service not initialized yet")
                return
            }

            // First ensure the overlay is updated with current elements
            updateVisualizationIfNeeded()
            
            // Small delay to ensure overlay is updated
            mainHandler.postDelayed({
                try {
                    val elementsArray = JSONArray()
                    
                    // Get all elements and filter for only clickable ones
                    val clickableElements = visibleElements.filter { element ->
                        element.nodeInfo.isClickable || 
                        element.nodeInfo.isCheckable ||
                        element.nodeInfo.isEditable ||
                        element.nodeInfo.isScrollable
                    }.sortedBy { it.clickableIndex }
                    
                    // Log clickable elements for debugging
                    Log.e("DROIDRUN_TEXT", "======= CLICKABLE ELEMENTS START =======")
                    clickableElements.forEach { element ->
                        val type = when {
                            element.nodeInfo.isClickable -> "CLICK"
                            element.nodeInfo.isCheckable -> "CHECK"
                            element.nodeInfo.isEditable -> "INPUT"
                            element.nodeInfo.isScrollable -> "SCROLL"
                            else -> "OTHER"
                        }
                        Log.e("DROIDRUN_TEXT", "${element.clickableIndex}: $type - ${element.text}")
                    }
                    Log.e("DROIDRUN_TEXT", "======= CLICKABLE ELEMENTS END =======")
                    
                    // Create JSON entries only for clickable elements
                    for (element in clickableElements) {
                        val elementJson = JSONObject().apply {
                            put("text", element.text)
                            put("className", element.className)
                            put("index", element.clickableIndex)
                            put("bounds", "${element.rect.left},${element.rect.top},${element.rect.right},${element.rect.bottom}")
                            // Add type information
                            put("type", when {
                                element.nodeInfo.isClickable -> "clickable"
                                element.nodeInfo.isCheckable -> "checkable"
                                element.nodeInfo.isEditable -> "input"
                                element.nodeInfo.isScrollable -> "scrollable"
                                else -> "other"
                            })
                        }
                        elementsArray.put(elementJson)
                    }
                    
                    val jsonData = elementsArray.toString()
                    
                    // Save to file
                    try {
                        val outputDir = getExternalFilesDir(null)
                        val jsonFile = java.io.File(outputDir, "element_data.json")
                        jsonFile.writeText(jsonData)
                        Log.e("DROIDRUN_FILE", "JSON data written to: ${jsonFile.absolutePath}")
                        Log.e("DROIDRUN_ADB_DATA", jsonData)
                    } catch (e: Exception) {
                        Log.e("DROIDRUN_FILE", "Error writing to file: ${e.message}")
                    }
                    
                    // Broadcast response
                    val responseIntent = Intent(ACTION_ELEMENTS_RESPONSE).apply {
                        putExtra(EXTRA_ELEMENTS_DATA, jsonData)
                    }
                    sendBroadcast(responseIntent)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing elements: ${e.message}", e)
                    Log.e("DROIDRUN_ADB_RESPONSE", "ERROR: ${e.message}")
                }
            }, 100)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting element data: ${e.message}", e)
            Log.e("DROIDRUN_ADB_RESPONSE", "ERROR: ${e.message}")
        }
    }
    
    /**
     * Simplified JSON output - no longer needed
     */
    private fun compactifyJsonElements(jsonData: String): String {
        return jsonData
    }
    
    private fun retriggerElements() {
        if (!isInitialized || visibleElements.isEmpty()) {
            Log.e("DROIDRUN_RECEIVER", "Cannot retrigger - service not initialized or no elements")
            return
        }
        
        try {
            val now = System.currentTimeMillis()
            
            // Reset all visible elements to have creation time of "now"
            for (element in visibleElements) {
                element.creationTime = now
                Log.d(TAG, "Reset element age: ${element.text}")
            }
            
            // Update the data collection with fresh weights
            updateElementsForDataCollection()
            
            // Update the visual display if visible
            if (overlayVisible) {
                updateOverlayWithTimeBasedWeights()
            }
            
            // Broadcast a confirmation response
            val responseIntent = Intent(ACTION_ELEMENTS_RESPONSE).apply {
                putExtra("retrigger_status", "success")
                putExtra("elements_count", visibleElements.size)
            }
            sendBroadcast(responseIntent)
            
            Log.e("DROIDRUN_RETRIGGER", "Successfully reset weights for ${visibleElements.size} elements")
        } catch (e: Exception) {
            Log.e(TAG, "Error retriggering elements: ${e.message}", e)
            Log.e("DROIDRUN_RETRIGGER", "ERROR: ${e.message}")
        }
    }
} 