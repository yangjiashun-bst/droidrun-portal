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
        private const val REFRESH_INTERVAL_MS = 500L // Refresh every 0.5 seconds
        private const val MIN_ELEMENT_SIZE = 5 // Minimum size for an element to be considered
        
        // Time-based fade settings
        private const val FADE_DURATION_MS = 60000L // Time to fade from weight 1.0 to 0.0 (60 seconds)
        private const val VISUALIZATION_REFRESH_MS = 250L // How often to refresh visualization (250ms = 4 times per second)
        private const val MIN_DISPLAY_WEIGHT = 0.2f // Minimum weight to display elements
        private const val SAME_TIME_THRESHOLD_MS = 300L // Elements appearing within this time window are considered "same time"
        
        // Color for heatmap (we'll use a gradient from RED to BLUE based on weight)
        private val NEW_ELEMENT_COLOR = Color.RED         // Newest elements
        private val OLD_ELEMENT_COLOR = Color.BLUE        // Oldest elements
        
        // Intent actions for ADB communication
        const val ACTION_GET_ELEMENTS = "com.example.droidrun.GET_ELEMENTS"
        const val ACTION_GET_INTERACTIVE_ELEMENTS = "com.example.droidrun.GET_INTERACTIVE_ELEMENTS"
        const val ACTION_ELEMENTS_RESPONSE = "com.example.droidrun.ELEMENTS_RESPONSE"
        const val ACTION_TOGGLE_OVERLAY = "com.example.droidrun.TOGGLE_OVERLAY"
        const val ACTION_TOGGLE_INTERACTIVE_ONLY = "com.example.droidrun.TOGGLE_INTERACTIVE_ONLY"
        const val ACTION_RETRIGGER_ELEMENTS = "com.example.droidrun.RETRIGGER_ELEMENTS"
        const val EXTRA_ELEMENTS_DATA = "elements_data"
        const val EXTRA_OVERLAY_VISIBLE = "overlay_visible"
        const val EXTRA_INTERACTIVE_ONLY = "interactive_only"
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
    private var showInteractiveOnly = false // Track if we should only show interactive elements
    
    // Track currently displayed elements (after filtering)
    private val displayedElements = mutableListOf<Pair<ElementNode, Float>>()
    
    // Broadcast receiver for ADB commands
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_GET_ELEMENTS -> {
                    broadcastElementData(false)
                }
                ACTION_GET_INTERACTIVE_ELEMENTS -> {
                    broadcastElementData(true)
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
                ACTION_TOGGLE_INTERACTIVE_ONLY -> {
                    showInteractiveOnly = intent.getBooleanExtra(EXTRA_INTERACTIVE_ONLY, !showInteractiveOnly)
                    if (overlayVisible) {
                        updateVisualization()
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
                addAction(ACTION_GET_INTERACTIVE_ELEMENTS)
                addAction(ACTION_TOGGLE_OVERLAY)
                addAction(ACTION_TOGGLE_INTERACTIVE_ONLY)
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
            stopVisualizationUpdates()
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
            processActiveWindow()
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }
    
    private val visualizationRunnable = object : Runnable {
        override fun run() {
            if (isInitialized) {
                updateVisualization()
            }
            mainHandler.postDelayed(this, VISUALIZATION_REFRESH_MS)
        }
    }

    private fun startPeriodicUpdates() {
        Log.d(TAG, "Starting periodic updates")
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
    
    private fun updateVisualization() {
        if (!isInitialized || visibleElements.isEmpty()) return
        
        try {
            // Get the elements to display based on the current mode
            val elementsToProcess = if (showInteractiveOnly) {
                getInteractiveElements()
            } else {
                visibleElements.map { element -> 
                    Pair(element, element.calculateWeight())
                }
            }
            
            // Filter by weight and sort
            val weightSortedElements = elementsToProcess
                .filter { (_, weight) -> weight > MIN_DISPLAY_WEIGHT }
                .sortedByDescending { (_, weight) -> weight }
            
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
                compareBy<Pair<ElementNode, Float>> { it.first.windowLayer }
                    .thenByDescending { it.second }
            )
            
            // Update UI if visible
            if (overlayVisible) {
                overlayManager.clearElements()
                
                for ((element, weight) in sortedElements) {
                    val heatmapColor = calculateHeatmapColor(weight)
                    val weightStr = String.format("%.2f", weight)
                    val ageSeconds = ((System.currentTimeMillis() - element.creationTime) / 1000)
                    
                    overlayManager.addElement(
                        rect = element.rect,
                        type = "${element.className}", 
                        text = "${element.text} (w:${weightStr}, age:${ageSeconds}s)",
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
            
            Log.d(TAG, "Updated visualization with ${sortedElements.size} elements (interactive only: $showInteractiveOnly)")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating visualization: ${e.message}", e)
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
                processActiveWindow()
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
            Log.d(TAG, "Added $elementType: ${displayText} [${className.substringAfterLast('.')}]")
        }
        
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i) ?: continue
            try {
                findAllVisibleElements(childNode, windowLayer)
            } finally {
                childNode.recycle()
            }
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
        val elementsWithWeights = visibleElements.map { element ->
            val weight = element.calculateWeight()
            Pair(element, weight)
        }
        
        val visibleWeightElements = elementsWithWeights
            .filter { (_, weight) -> weight > MIN_DISPLAY_WEIGHT }
            
        val weightSortedElements = visibleWeightElements
            .sortedByDescending { (_, weight) -> weight }
        
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
                    Log.d(TAG, "Including overlapping element ${element.text} that appeared at same time")
                } else {
                    Log.d(TAG, "Skipping overlapped element: ${element.text} (weight: $weight)")
                }
            }
        }
        
        val sortedElements = elementsToDisplay.sortedWith(
            compareBy<Pair<ElementNode, Float>> { it.first.windowLayer }
                .thenByDescending { it.second }
        )
        
        synchronized(displayedElements) {
            displayedElements.clear()
            displayedElements.addAll(sortedElements)
        }
        
        Log.d(TAG, "Collected ${sortedElements.size} elements out of ${visibleWeightElements.size} visible elements")
    }
    
    private fun updateOverlayWithTimeBasedWeights() {
        try {
            if (!overlayVisible) return
            
            overlayManager.clearElements()
            
            Log.d(TAG, "Updating overlay UI with ${displayedElements.size} elements")
            
            for ((element, weight) in displayedElements) {
                try {
                    val heatmapColor = calculateHeatmapColor(weight)
                    
                    val weightStr = String.format("%.2f", weight)
                    val ageSeconds = ((System.currentTimeMillis() - element.creationTime) / 1000)
                    
                    overlayManager.addElement(
                        rect = element.rect,
                        type = "${element.className}", 
                        text = "${element.text} (w:${weightStr}, age:${ageSeconds}s)",
                        depth = element.windowLayer,
                        color = heatmapColor
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding element: ${e.message}", e)
                }
            }
            
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
                    startVisualizationUpdates()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing overlay: ${e.message}", e)
            }
        }, 1000)
    }

    private fun broadcastElementData(onlyInteractive: Boolean) {
        try {
            val elementsToProcess = if (onlyInteractive) {
                getInteractiveElements()
            } else {
                visibleElements.map { element -> 
                    Pair(element, element.calculateWeight())
                }
            }

            val significantElements = elementsToProcess.filter { (_, weight) -> 
                weight > MIN_DISPLAY_WEIGHT 
            }

            val elementsArray = JSONArray()
            
            // First, print just the text content of each element for easy reading
            Log.e("DROIDRUN_TEXT", "======= ELEMENT TEXT LIST START =======")
            significantElements.forEachIndexed { index, (element, _) ->
                Log.e("DROIDRUN_TEXT", "${index}: ${element.text}")
            }
            Log.e("DROIDRUN_TEXT", "======= ELEMENT TEXT LIST END =======")
            
            for ((element, weight) in significantElements) {
                val elementJson = JSONObject().apply {
                    // Always include essential text property
                    put("text", element.text)
                    put("className", element.className)
                    put("bounds", "${element.rect.left},${element.rect.top},${element.rect.right},${element.rect.bottom}")
                    put("weight", weight)
                    
                    // Interactive properties are always important
                    if (element.nodeInfo.isClickable) {
                        put("isClickable", true)
                    }
                    if (element.nodeInfo.isCheckable) {
                        put("isCheckable", true)
                    }
                    if (element.nodeInfo.isEditable) {
                        put("isEditable", true)
                    }
                    
                    // Include window layer only if not zero
                    if (element.windowLayer > 0) {
                        put("windowLayer", element.windowLayer)
                    }
                }
                elementsArray.put(elementJson)
            }
            
            val jsonData = elementsArray.toString()
            
            // Add option to get the compact or full version
            val compactJsonData = compactifyJsonElements(jsonData)
            
            // Save both versions to files in external storage for ADB access
            try {
                val outputDir = getExternalFilesDir(null)
                val fullJsonFile = java.io.File(outputDir, "element_data.json")
                val compactJsonFile = java.io.File(outputDir, "element_data_compact.json")
                
                fullJsonFile.writeText(jsonData)
                compactJsonFile.writeText(compactJsonData)
                
                Log.e("DROIDRUN_FILE", "JSON data written to: ${fullJsonFile.absolutePath}")
                Log.e("DROIDRUN_FILE", "Compact JSON written to: ${compactJsonFile.absolutePath}")
                
                // Also dump the compact data to logcat for direct access
                Log.e("DROIDRUN_ADB_DATA", compactJsonData)
            } catch (e: Exception) {
                Log.e("DROIDRUN_FILE", "Error writing to file: ${e.message}")
            }
            
            Log.e("DROIDRUN_ADB_RESPONSE", "======= ELEMENTS DATA RESPONSE START =======")
            Log.e("DROIDRUN_ADB_RESPONSE", "Broadcasting data for ${significantElements.size} elements (${if (onlyInteractive) "interactive only" else "all elements"})")
            Log.e("DROIDRUN_ADB_RESPONSE", "Original size: ${jsonData.length} bytes, Compact: ${compactJsonData.length} bytes (${
                String.format("%.1f%%", 100 * (1 - compactJsonData.length.toFloat() / jsonData.length.toFloat()))
            } reduction)")
            Log.e("DROIDRUN_ADB_RESPONSE", "======= ELEMENTS DATA RESPONSE END =======")
            
            // Broadcast a response intent with the data
            val responseIntent = Intent(ACTION_ELEMENTS_RESPONSE).apply {
                putExtra(EXTRA_ELEMENTS_DATA, compactJsonData)
            }
            sendBroadcast(responseIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting element data: ${e.message}", e)
            Log.e("DROIDRUN_ADB_RESPONSE", "ERROR: ${e.message}")
        }
    }
    
    /**
     * Further compactifies the JSON by using shorter property names and removing whitespace
     */
    private fun compactifyJsonElements(jsonData: String): String {
        try {
            val jsonArray = JSONArray(jsonData)
            val compactArray = JSONArray()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val compactObj = JSONObject()
                
                // Map full property names to shorter ones
                val propertyMap = mapOf(
                    "text" to "t",
                    "className" to "c",
                    "bounds" to "b", 
                    "weight" to "w",
                    "age" to "a",
                    "windowLayer" to "l",
                    "isClickable" to "ic",
                    "isCheckable" to "ik",
                    "isEditable" to "ie",
                    "additionalContext" to "ac"
                )
                
                // Transfer properties with shorter names
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val shortKey = propertyMap[key] ?: key
                    compactObj.put(shortKey, obj.get(key))
                }
                
                compactArray.put(compactObj)
            }
            
            return compactArray.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error compactifying JSON: ${e.message}")
            return jsonData // Return original on error
        }
    }
    
    /**
     * Filters the elements to only include those that provide meaningful context for an LLM,
     * reducing token count while preserving important information.
     */
    private fun getSignificantElements(elements: List<Pair<ElementNode, Float>>): List<Pair<ElementNode, Float>> {
        // These class names typically don't add contextual value and can be filtered out
        val lowValueClassNames = setOf(
            "FrameLayout", "LinearLayout", "ViewGroup", "View", "GridView", 
            "HorizontalScrollView", "ConstraintLayout"
        )
        
        // Filter out container views without meaningful text
        val filteredElements = elements.filter { (element, _) ->
            // Keep elements with meaningful text
            if (element.text.isNotEmpty() && 
                !element.text.startsWith("ub__") && 
                element.text != element.className) {
                return@filter true
            }
            
            // Keep clickable elements regardless of text
            if (element.nodeInfo.isClickable || element.nodeInfo.isCheckable || element.nodeInfo.isEditable) {
                return@filter true
            }
            
            // Filter out generic container classes without meaningful text
            if (lowValueClassNames.contains(element.className)) {
                return@filter false
            }
            
            // Default to keeping elements we're unsure about
            return@filter true
        }
        
        return filteredElements
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

    /**
     * Returns a list of only clickable and input elements with their calculated weights.
     * This includes buttons, text inputs, checkboxes, and other interactive elements.
     */
    private fun getInteractiveElements(): List<Pair<ElementNode, Float>> {
        val interactiveElements = visibleElements.filter { element ->
            // Check if the element is interactive through properties
            element.nodeInfo.isClickable || 
            element.nodeInfo.isCheckable || 
            element.nodeInfo.isEditable ||
            // Check if it's focusable (often true for input fields)
            element.nodeInfo.isFocusable ||
            // Check if it accepts text input
            element.nodeInfo.isTextEntryKey ||
            // Also include elements that are likely interactive based on their class name
            element.className.matches(Regex(".*(Button|EditText|TextView|CheckBox|Switch|RadioButton|Spinner|SearchView|AutoCompleteTextView)$")) ||
            // Check if it's an input field by class name pattern
            element.className.contains("Input") ||
            element.className.contains("Edit") ||
            // Check if it's a text field
            element.className.contains("Text") && element.nodeInfo.isFocusable
        }
        
        // Calculate weights for the filtered elements
        val elementsWithWeights = interactiveElements.map { element ->
            Pair(element, element.calculateWeight())
        }
        
        // Sort by weight descending to prioritize more recently visible elements
        return elementsWithWeights.sortedByDescending { it.second }
    }
} 