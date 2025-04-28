package com.droidrun.portal

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Context.RECEIVER_EXPORTED
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
import kotlin.math.abs

class DroidrunPortalService : AccessibilityService() {
    
    companion object {
        private const val TAG = "DROIDRUN_PORTAL"
        private const val REFRESH_INTERVAL_MS = 250L // Single refresh interval for all updates
        private const val MIN_ELEMENT_SIZE = 5 // Minimum size for an element to be considered
        private const val MIN_FRAME_TIME_MS = 16L // Minimum time between frames (roughly 60 FPS)
        
        // Time-based fade settings
        private const val FADE_DURATION_MS = 300000L // Time to fade from weight 1.0 to 0.0 (300 seconds = 5 minutes)
        private const val VISUALIZATION_REFRESH_MS = 250L // How often to refresh visualization (250ms = 4 times per second)
        private const val MIN_DISPLAY_WEIGHT = 0.05f // Minimum weight to display elements
        private const val SAME_TIME_THRESHOLD_MS = 500L // Elements appearing within this time window are considered "same time"
        
        // Color for heatmap (we'll use a gradient from RED to BLUE based on weight)
        private val NEW_ELEMENT_COLOR = Color.RED         // Newest elements
        private val OLD_ELEMENT_COLOR = Color.BLUE        // Oldest elements
        
        // Intent actions for ADB communication
        const val ACTION_GET_ELEMENTS = "com.droidrun.portal.GET_ELEMENTS"
        const val ACTION_ELEMENTS_RESPONSE = "com.droidrun.portal.ELEMENTS_RESPONSE"
        const val ACTION_TOGGLE_OVERLAY = "com.droidrun.portal.TOGGLE_OVERLAY"
        const val ACTION_RETRIGGER_ELEMENTS = "com.droidrun.portal.RETRIGGER_ELEMENTS"
        const val ACTION_GET_ALL_ELEMENTS = "com.droidrun.portal.GET_ALL_ELEMENTS"
        const val ACTION_GET_INTERACTIVE_ELEMENTS = "com.droidrun.portal.GET_INTERACTIVE_ELEMENTS"
        const val ACTION_FORCE_HIDE_OVERLAY = "com.droidrun.portal.FORCE_HIDE_OVERLAY"
        const val EXTRA_ELEMENTS_DATA = "elements_data"
        const val EXTRA_ALL_ELEMENTS_DATA = "all_elements_data"
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
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_GET_ELEMENTS -> {
                    Log.e("DROIDRUN_RECEIVER", "Received GET_ELEMENTS command")
                    broadcastElementData()
                }
                ACTION_GET_INTERACTIVE_ELEMENTS -> {
                    Log.e("DROIDRUN_RECEIVER", "Received GET_INTERACTIVE_ELEMENTS command")
                    broadcastElementData() // Use the same implementation as GET_ELEMENTS
                }
                ACTION_GET_ALL_ELEMENTS -> {
                    Log.e("DROIDRUN_RECEIVER", "Received GET_ALL_ELEMENTS command")
                    broadcastAllElementsData()
                }
                ACTION_TOGGLE_OVERLAY -> {
                    if (!isOverlayManagerAvailable()) {
                        Log.e("DROIDRUN_RECEIVER", "Cannot toggle overlay: OverlayManager not initialized")
                        return
                    }
                    
                    val shouldShow = intent.getBooleanExtra(EXTRA_OVERLAY_VISIBLE, !overlayVisible)
                    Log.e("DROIDRUN_RECEIVER", "Received TOGGLE_OVERLAY command: $shouldShow")
                    if (shouldShow) {
                        overlayManager.showOverlay()
                        overlayVisible = true
                    } else {
                        overlayManager.hideOverlay()
                        overlayVisible = false
                    }
                    val responseIntent = Intent(ACTION_ELEMENTS_RESPONSE).apply {
                        putExtra(EXTRA_OVERLAY_VISIBLE, overlayVisible)
                    }
                    sendBroadcast(responseIntent)
                }
                ACTION_RETRIGGER_ELEMENTS -> {
                    Log.e("DROIDRUN_RECEIVER", "Received RETRIGGER_ELEMENTS command")
                    retriggerElements()
                }
                ACTION_FORCE_HIDE_OVERLAY -> {
                    Log.e("DROIDRUN_RECEIVER", "Received FORCE_HIDE_OVERLAY command")
                    if (isOverlayManagerAvailable()) {
                        overlayManager.hideOverlay()
                        overlayVisible = false
                        overlayManager.clearElements()
                        overlayManager.refreshOverlay()
                        
                        // Send confirmation
                        val responseIntent = Intent(ACTION_ELEMENTS_RESPONSE).apply {
                            putExtra(EXTRA_OVERLAY_VISIBLE, false)
                            putExtra("force_hide_successful", true)
                        }
                        sendBroadcast(responseIntent)
                        Log.e("DROIDRUN_RECEIVER", "Overlay forcibly hidden")
                    } else {
                        Log.e("DROIDRUN_RECEIVER", "Cannot hide overlay: OverlayManager not initialized")
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        try {
            val intentFilter = IntentFilter().apply {
                addAction(ACTION_GET_ELEMENTS)
                addAction(ACTION_GET_INTERACTIVE_ELEMENTS)
                addAction(ACTION_GET_ALL_ELEMENTS)
                addAction(ACTION_TOGGLE_OVERLAY)
                addAction(ACTION_RETRIGGER_ELEMENTS)
                addAction(ACTION_FORCE_HIDE_OVERLAY)
            }
            registerReceiver(broadcastReceiver, intentFilter, RECEIVER_EXPORTED)
            Log.e("DROIDRUN_RECEIVER", "Registered receiver for commands with EXPORTED flag")
            
            overlayManager = OverlayManager(this)
            isInitialized = true
            
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val size = Point()
            display.getSize(size)
            screenBounds.set(0, 0, size.x, size.y)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OverlayManager: ${e.message}", e)
            Log.e("DROIDRUN_RECEIVER", "Error in onCreate: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        try {
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
        lastDrawTime = System.currentTimeMillis()
        mainHandler.postDelayed(updateRunnable, REFRESH_INTERVAL_MS)
    }
    
    private fun stopPeriodicUpdates() {
        mainHandler.removeCallbacks(updateRunnable)
    }
    
    private fun startVisualizationUpdates() {
        mainHandler.postDelayed(visualizationRunnable, VISUALIZATION_REFRESH_MS)
    }
    
    private fun stopVisualizationUpdates() {
        mainHandler.removeCallbacks(visualizationRunnable)
    }
    
    private fun updateVisualizationIfNeeded() {
        if (!isOverlayManagerAvailable() || visibleElements.isEmpty()) {
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
                        abs(current.second - new.second) > 0.05f
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
                        val timeDiff = abs(existingElement.creationTime - element.creationTime)
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
        } catch (e: Exception) {
            Log.e(TAG, "Error updating visualization: ${e.message}", e)
            pendingVisualizationUpdate = true  // Try again next frame
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventPackage = event.packageName?.toString() ?: ""
        
        if (eventPackage.isNotEmpty() && eventPackage != currentPackageName && currentPackageName.isNotEmpty()) {
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
            if (!isOverlayManagerAvailable()) {
                Log.d(TAG, "OverlayManager not yet initialized, skipping reset")
                return
            }
            
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
            
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting overlay state: ${e.message}", e)
        }
    }
    
    private fun processActiveWindow() {
        if (!isProcessing.compareAndSet(false, true)) {
            return
        }
        
        try {
            if (!isOverlayManagerAvailable()) {
                Log.d(TAG, "OverlayManager not yet initialized, skipping process")
                isProcessing.set(false)
                return
            }
            
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
            } else {
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
                    displayText,
                    className.substringAfterLast('.'),
                    windowLayer,
                    System.currentTimeMillis(),
                    id
                )
                visibleElements.add(elementNode)
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
            
            // First, sort all elements by creation time (oldest first)
            val sortedElements = visibleElements.sortedBy { it.creationTime }
            
            // Clear existing relationships
            for (element in visibleElements) {
                element.parent = null
                element.children.clear()
            }
            
            // Build the hierarchy - but limit nesting depth to avoid cycles
            val processedElements = mutableSetOf<String>() // Track processed elements by ID
            
            for (element in sortedElements) {
                if (processedElements.contains(element.id)) {
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
                } else {
                    element.parent = null
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
            
            for (element in clickableElements) {
                element.clickableIndex = clickableIndex++
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
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateElementsForDataCollection: ${e.message}", e)
        }
    }
    
    private fun updateOverlayWithTimeBasedWeights() {
        try {
            if (!isOverlayManagerAvailable() || !overlayVisible) {
                return
            }
            
            overlayManager.clearElements()
            
            // Process elements in order of nesting (parents before children)
            val elementsByNesting = displayedElements.sortedBy { (element, _) -> 
                element.calculateNestingLevel() 
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
                    
                    // Get the index this element will have in the overlay
                    val overlayIndex = overlayManager.getElementCount()
                    
                    // Directly assign the overlay index to the element
                    element.overlayIndex = overlayIndex
                    
                    overlayManager.addElement(
                        rect = element.rect,
                        type = "${element.className}",
                        text = label,
                        depth = element.calculateNestingLevel(),
                        color = heatmapColor
                    )
                    
                    addedCount++
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding element ${element.text}: ${e.message}", e)
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
        if (isOverlayManagerAvailable()) {
            resetOverlayState()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected, initializing overlay")
        
        mainHandler.postDelayed({
            try {
                if (isOverlayManagerAvailable()) {
                    overlayManager.showOverlay()
                    startPeriodicUpdates()
                    mainHandler.post(visualizationRunnable)
                    
                    val responseIntent = Intent(ACTION_ELEMENTS_RESPONSE).apply {
                        putExtra(EXTRA_OVERLAY_VISIBLE, overlayVisible)
                    }
                    sendBroadcast(responseIntent)
                } else {
                    Log.e(TAG, "OverlayManager not initialized in onServiceConnected")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing overlay: ${e.message}", e)
            }
        }, 1000)
    }

    private fun sanitizeText(text: String?): String {
        if (text == null) return ""
        // Remove control characters (0-31) that can break JSON
        return text.replace(Regex("[\u0000-\u001F]"), "")
    }

    private fun broadcastElementData() {
        try {
            if (!isInitialized) {
                Log.e("DROIDRUN_DEBUG", "Service not initialized yet")
                return
            }

            updateVisualizationIfNeeded()
            
            mainHandler.postDelayed({
                try {
                    // Create a list to track all interactive elements
                    val interactiveElements = mutableListOf<ElementNode>()
                    val rootElements = mutableListOf<ElementNode>()
                    val childrenMap = mutableMapOf<String, MutableList<ElementNode>>()
                    val processedAsChild = mutableSetOf<String>()
                    
                    // Identify all interactive elements (clickable, checkable, editable, etc.)
                    val clickableContainers = visibleElements.filter { element ->
                        element.nodeInfo.isClickable || 
                        element.nodeInfo.isCheckable ||
                        element.nodeInfo.isEditable ||
                        element.nodeInfo.isScrollable ||
                        element.nodeInfo.isFocusable
                    }
                    
                    // First pass: Build containment hierarchy
                    // Find all potential parent-child relationships
                    for (container in clickableContainers) {
                        // Find all elements that could be children of this container
                        val possibleChildren = visibleElements.filter { element ->
                            element != container &&  // Not the container itself
                            isWithinBounds(element.rect, container.rect) && // Within container bounds
                            !processedAsChild.contains(element.id) // Not already processed as a child
                        }
                        
                        // Only consider direct children (not contained in other children)
                        val directChildren = possibleChildren.filter { child ->
                            // Child is not contained by any other possible child
                            possibleChildren.none { otherChild -> 
                                otherChild != child && isWithinBounds(child.rect, otherChild.rect)
                            }
                        }
                        
                        if (directChildren.isNotEmpty()) {
                            // This element has children, store them for later processing
                            childrenMap[container.id] = directChildren.toMutableList()
                            
                            // Mark all these children as processed so they don't appear at root level
                            for (child in directChildren) {
                                processedAsChild.add(child.id)
                            }
                        }
                    }
                    
                    // Second pass: Find true root elements (not children of any other element)
                    for (container in clickableContainers) {
                        if (!processedAsChild.contains(container.id)) {
                            rootElements.add(container)
                        }
                    }
                    
                    // Add all elements to our tracking list
                    for (element in rootElements) {
                        interactiveElements.add(element)
                    }
                    
                    // Process all children recursively to add them to our tracking list
                    val processedIndices = mutableSetOf<String>()
                    
                    fun addAllChildren(parentId: String) {
                        if (processedIndices.contains(parentId)) return
                        processedIndices.add(parentId)
                        
                        val children = childrenMap[parentId] ?: return
                        
                        for (child in children) {
                            interactiveElements.add(child)
                            
                            // Recursively process any grandchildren
                            if (childrenMap.containsKey(child.id)) {
                                addAllChildren(child.id)
                            }
                        }
                    }
                    
                    // Process all root elements to add the whole tree to our tracking list
                    for (root in rootElements) {
                        if (childrenMap.containsKey(root.id)) {
                            addAllChildren(root.id)
                        }
                    }
                    
                    // Assign sequential indices to elements without an overlay index
                    val highestIndex = visibleElements.maxOfOrNull { it.overlayIndex } ?: -1
                    var nextIndex = highestIndex + 1
                    
                    for (element in interactiveElements) {
                        if (element.overlayIndex == -1) {
                            element.overlayIndex = nextIndex++
                        }
                    }
                    
                    // Log statistics
                    Log.e("DROIDRUN_TEXT", "======= INTERACTIVE ELEMENTS START =======")
                    Log.e("DROIDRUN_TEXT", "Found ${interactiveElements.size} interactive elements, ${rootElements.size} root elements")
                    
                    // Create the HTML-like structure as a JSON array - ONLY include root elements at top level
                    val elementsArray = JSONArray()
                    
                    // Helper function to recursively build the element tree with children
                    fun buildElementJson(element: ElementNode): JSONObject {
                        return JSONObject().apply {
                            // Find all text from this element and its children
                            val allText = mutableListOf<String>()
                            
                            // First add this element's own text if meaningful
                            if (element.text.isNotEmpty() && 
                                !element.text.startsWith("android:") && 
                                !element.text.contains("_") && 
                                element.text.length > 1 &&
                                !element.text.equals("LinearLayout", ignoreCase = true) &&
                                !element.text.equals("FrameLayout", ignoreCase = true)) {
                                allText.add(element.text)
                            }

                            // Add resource ID if available
                            val resourceId = element.nodeInfo.viewIdResourceName
                            if (resourceId != null && resourceId.isNotEmpty()) {
                                val shortId = resourceId.substringAfterLast('/')
                                if (!shortId.startsWith("android:") && shortId.isNotEmpty()) {
                                    allText.add(shortId)
                                }
                            }
                            
                            // Get this element's direct children from the childrenMap
                            val directChildren = childrenMap[element.id] ?: emptyList()
                            
                            // Find text elements that are within this element's bounds but NOT within any child element's bounds
                            val nestedTexts = visibleElements.filter { child ->
                                child != element && // Not the element itself
                                isWithinBounds(child.rect, element.rect) && // Within element bounds
                                child.text.isNotEmpty() && // Has text
                                !child.text.startsWith("android:") && // Not a resource ID
                                !child.text.contains("_") && // Not an internal ID
                                child.text.length > 1 && // Not a single character
                                !child.text.equals("LinearLayout", ignoreCase = true) &&
                                !child.text.equals("FrameLayout", ignoreCase = true) &&
                                // Most importantly: check that this text is not within ANY child element
                                directChildren.none { directChild -> 
                                    isWithinBounds(child.rect, directChild.rect)
                                }
                            }.sortedBy { it.rect.width() * it.rect.height() } // Sort by size, smallest first
                            .map { it.text }
                            .distinct() // Remove any duplicate texts
                            
                            allText.addAll(nestedTexts)
                            
                            // Basic properties
                            put("text", when {
                                allText.isNotEmpty() -> sanitizeText(allText.distinct().joinToString(" "))
                                else -> ""
                            })
                            put("className", sanitizeText(element.className))
                            put("index", element.overlayIndex) // Use the overlay index directly
                            put("bounds", "${element.rect.left},${element.rect.top},${element.rect.right},${element.rect.bottom}")
                            
                            // Add resource ID as a separate field
                            put("resourceId", when {
                                resourceId != null && resourceId.isNotEmpty() -> resourceId
                                else -> ""
                            })
                            
                            put("type", when {
                                element.nodeInfo.isClickable -> "clickable"
                                element.nodeInfo.isCheckable -> "checkable"
                                element.nodeInfo.isEditable -> "input"
                                element.nodeInfo.isScrollable -> "scrollable"
                                element.nodeInfo.isFocusable -> "focusable"
                                element.text.isNotEmpty() -> "text"
                                else -> "view"
                            })
                            
                            // Add children if this element has any
                            if (childrenMap.containsKey(element.id)) {
                                val children = childrenMap[element.id] ?: emptyList()
                                if (children.isNotEmpty()) {
                                    val childrenArray = JSONArray()
                                    for (child in children) {
                                        val childJson = buildElementJson(child)
                                        childrenArray.put(childJson)
                                    }
                                    put("children", childrenArray)
                                }
                            }
                        }
                    }
                    
                    // Process only root elements for the top-level array
                    // Sort by overlay indices
                    val sortedRoots = rootElements.sortedBy { it.overlayIndex }
                    
                    for (element in sortedRoots) {
                        val elementJson = buildElementJson(element)
                        elementsArray.put(elementJson)
                    }
                    
                    // Log each element with its index for debugging purposes only
                    for (element in interactiveElements.sortedBy { it.overlayIndex }) {
                        val type = when {
                            element.nodeInfo.isClickable -> "CLICK"
                            element.nodeInfo.isCheckable -> "CHECK"
                            element.nodeInfo.isEditable -> "INPUT"
                            element.nodeInfo.isScrollable -> "SCROLL"
                            element.nodeInfo.isFocusable -> "FOCUS"
                            else -> "TEXT"
                        }
                        
                        // Mark if this is a root element or a child
                        val role = if (rootElements.contains(element)) "ROOT" else "CHILD"
                        val parentInfo = if (role == "CHILD") {
                            val parent = visibleElements.find { container -> 
                                childrenMap.containsKey(container.id) && 
                                childrenMap[container.id]?.contains(element) == true 
                            }
                            " (parent: ${parent?.text?.take(20) ?: "unknown"})"
                        } else ""
                        
                        Log.e("DROIDRUN_TEXT", "Index: ${element.overlayIndex} - $type - $role$parentInfo - ${sanitizeText(element.text ?: "")} (${element.className})")
                    }
                    Log.e("DROIDRUN_TEXT", "======= INTERACTIVE ELEMENTS END =======")
                    
                    val jsonData = elementsArray.toString()
                    
                    try {
                        val outputDir = getExternalFilesDir(null)
                        val jsonFile = java.io.File(outputDir, "element_data.json")
                        jsonFile.writeText(jsonData)
                        Log.e("DROIDRUN_FILE", "JSON data written to: ${jsonFile.absolutePath}")
                        
                        // Split the JSON data into chunks to avoid logcat truncation
                        val maxChunkSize = 4000
                        val chunks = jsonData.chunked(maxChunkSize)
                        val totalChunks = chunks.size
                        
                        // Output each chunk with metadata for reassembly
                        chunks.forEachIndexed { index, chunk ->
                            Log.e("DROIDRUN_ADB_DATA", "CHUNK|$index|$totalChunks|$chunk")
                        }
                    } catch (e: Exception) {
                        Log.e("DROIDRUN_FILE", "Error writing to file: ${e.message}")
                    }
                    
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
    
    // Helper function to check if one rectangle is within another
    private fun isWithinBounds(inner: Rect, outer: Rect): Boolean {
        return inner.left >= outer.left && inner.right <= outer.right &&
               inner.top >= outer.top && inner.bottom <= outer.bottom
    }
    
    private fun retriggerElements() {
        if (!isInitialized || visibleElements.isEmpty()) {
            Log.e("DROIDRUN_RECEIVER", "Cannot retrigger - service not initialized or no elements")
            return
        }
        
        try {
            val now = System.currentTimeMillis()
            
            for (element in visibleElements) {
                element.creationTime = now
            }
            
            updateElementsForDataCollection()
            
            if (overlayVisible) {
                updateOverlayWithTimeBasedWeights()
            }
            
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

    private fun broadcastAllElementsData() {
        try {
            if (!isInitialized) {
                Log.e("DROIDRUN_DEBUG", "Service not initialized yet")
                return
            }

            updateVisualizationIfNeeded()
            
            mainHandler.postDelayed({
                try {
                    // Create tracking structures for all elements
                    val allElementsList = mutableListOf<ElementNode>()
                    val rootElements = mutableListOf<ElementNode>()
                    val childrenMap = mutableMapOf<String, MutableList<ElementNode>>()
                    val processedAsChild = mutableSetOf<String>()
                    
                    // First pass: Find all potential containers (elements that can contain others)
                    val potentialContainers = visibleElements.filter { element ->
                        // Consider all elements as potential containers, not just interactive ones
                        element.rect.width() > 0 && element.rect.height() > 0
                    }
                    
                    // Build the containment hierarchy
                    for (container in potentialContainers) {
                        // Find all elements contained within this container
                        val possibleChildren = visibleElements.filter { element ->
                            element != container &&  // Not the container itself
                            isWithinBounds(element.rect, container.rect) && // Within container bounds
                            !processedAsChild.contains(element.id) // Not already processed as a child
                        }
                        
                        // Only consider direct children (not contained in other children)
                        val directChildren = possibleChildren.filter { child ->
                            // A direct child is not contained by any other child of this container
                            possibleChildren.none { otherChild -> 
                                otherChild != child && isWithinBounds(child.rect, otherChild.rect)
                            }
                        }
                        
                        if (directChildren.isNotEmpty()) {
                            // This element has children, store them for later processing
                            childrenMap[container.id] = directChildren.toMutableList()
                            
                            // Mark all these children as processed so they don't appear at root level
                            for (child in directChildren) {
                                processedAsChild.add(child.id)
                            }
                        }
                    }
                    
                    // Second pass: Find root elements (not contained within any other element)
                    for (element in visibleElements) {
                        if (!processedAsChild.contains(element.id)) {
                            rootElements.add(element)
                        }
                    }
                    
                    // Add all elements to our list
                    for (element in rootElements) {
                        allElementsList.add(element)
                    }
                    
                    // Process all children recursively
                    val processedIndices = mutableSetOf<String>()
                    
                    fun addAllChildren(parentId: String) {
                        if (processedIndices.contains(parentId)) return
                        processedIndices.add(parentId)
                        
                        val children = childrenMap[parentId] ?: return
                        
                        for (child in children) {
                            allElementsList.add(child)
                            
                            // Recursively process any grandchildren
                            if (childrenMap.containsKey(child.id)) {
                                addAllChildren(child.id)
                            }
                        }
                    }
                    
                    // Process all root elements
                    for (root in rootElements) {
                        if (childrenMap.containsKey(root.id)) {
                            addAllChildren(root.id)
                        }
                    }
                    
                    // Assign sequential indices to elements without an overlay index
                    val highestIndex = visibleElements.maxOfOrNull { it.overlayIndex } ?: -1
                    var nextIndex = highestIndex + 1
                    
                    for (element in allElementsList) {
                        if (element.overlayIndex == -1) {
                            element.overlayIndex = nextIndex++
                        }
                    }
                    
                    // Log statistics
                    Log.e("DROIDRUN_TEXT", "======= ALL ELEMENTS START =======")
                    Log.e("DROIDRUN_TEXT", "Found ${allElementsList.size} total elements, ${rootElements.size} root elements")
                    
                    // Create the HTML-like structure as a JSON array
                    val elementsArray = JSONArray()
                    
                    // Helper function to recursively build the element tree with children
                    fun buildElementJson(element: ElementNode): JSONObject {
                        return JSONObject().apply {
                            // Find all text from this element and its children
                            val allText = mutableListOf<String>()
                            
                            // First add this element's own text if meaningful
                            if (element.text.isNotEmpty() && 
                                !element.text.startsWith("android:") && 
                                !element.text.contains("_") && 
                                element.text.length > 1 &&
                                !element.text.equals("LinearLayout", ignoreCase = true) &&
                                !element.text.equals("FrameLayout", ignoreCase = true)) {
                                allText.add(element.text)
                            }

                            // Add resource ID if available
                            val resourceId = element.nodeInfo.viewIdResourceName
                            if (resourceId != null && resourceId.isNotEmpty()) {
                                val shortId = resourceId.substringAfterLast('/')
                                if (!shortId.startsWith("android:") && shortId.isNotEmpty()) {
                                    allText.add(shortId)
                                }
                            }
                            
                            // Get this element's direct children from the childrenMap
                            val directChildren = childrenMap[element.id] ?: emptyList()
                            
                            // Find text elements that are within this element's bounds but NOT within any child element's bounds
                            val nestedTexts = visibleElements.filter { child ->
                                child != element && // Not the element itself
                                isWithinBounds(child.rect, element.rect) && // Within element bounds
                                child.text.isNotEmpty() && // Has text
                                !child.text.startsWith("android:") && // Not a resource ID
                                !child.text.contains("_") && // Not an internal ID
                                child.text.length > 1 && // Not a single character
                                !child.text.equals("LinearLayout", ignoreCase = true) &&
                                !child.text.equals("FrameLayout", ignoreCase = true) &&
                                // Most importantly: check that this text is not within ANY child element
                                directChildren.none { directChild -> 
                                    isWithinBounds(child.rect, directChild.rect)
                                }
                            }.sortedBy { it.rect.width() * it.rect.height() } // Sort by size, smallest first
                            .map { it.text }
                            .distinct() // Remove any duplicate texts
                            
                            allText.addAll(nestedTexts)
                            
                            // Basic properties
                            put("text", when {
                                allText.isNotEmpty() -> sanitizeText(allText.distinct().joinToString(" "))
                                else -> ""
                            })
                            put("className", sanitizeText(element.className))
                            put("index", element.overlayIndex) // Use the overlay index directly
                            put("bounds", "${element.rect.left},${element.rect.top},${element.rect.right},${element.rect.bottom}")
                            
                            // Add resource ID as a separate field
                            put("resourceId", when {
                                resourceId != null && resourceId.isNotEmpty() -> resourceId
                                else -> ""
                            })
                            
                            put("type", when {
                                element.nodeInfo.isClickable -> "clickable"
                                element.nodeInfo.isCheckable -> "checkable"
                                element.nodeInfo.isEditable -> "input"
                                element.nodeInfo.isScrollable -> "scrollable"
                                element.nodeInfo.isFocusable -> "focusable"
                                element.text.isNotEmpty() -> "text"
                                else -> "view"
                            })
                            
                            // Add children if this element has any
                            if (childrenMap.containsKey(element.id)) {
                                val children = childrenMap[element.id] ?: emptyList()
                                if (children.isNotEmpty()) {
                                    val childrenArray = JSONArray()
                                    for (child in children) {
                                        val childJson = buildElementJson(child)
                                        childrenArray.put(childJson)
                                    }
                                    put("children", childrenArray)
                                }
                            }
                        }
                    }
                    
                    // Process only root elements for the top-level array, sorted by the overlay index
                    val sortedRoots = rootElements.sortedBy { it.overlayIndex }
                    
                    for (element in sortedRoots) {
                        val elementJson = buildElementJson(element)
                        elementsArray.put(elementJson)
                    }
                    
                    // Log element counts for debugging
                    Log.e("DROIDRUN_TEXT", "Total root elements: ${rootElements.size}")
                    Log.e("DROIDRUN_TEXT", "Total elements in hierarchy: ${allElementsList.size}")
                    Log.e("DROIDRUN_TEXT", "======= ALL ELEMENTS END =======")
                    
                    val jsonData = elementsArray.toString()
                    
                    try {
                        val outputDir = getExternalFilesDir(null)
                        val jsonFile = java.io.File(outputDir, "all_elements_data.json")
                        jsonFile.writeText(jsonData)
                        Log.e("DROIDRUN_FILE", "All elements JSON data written to: ${jsonFile.absolutePath}")
                        
                        // Split the JSON data into chunks to avoid logcat truncation
                        val maxChunkSize = 4000
                        val chunks = jsonData.chunked(maxChunkSize)
                        val totalChunks = chunks.size
                        
                        // Output each chunk with metadata for reassembly
                        chunks.forEachIndexed { index, chunk ->
                            Log.e("DROIDRUN_ADB_ALL_DATA", "CHUNK|$index|$totalChunks|$chunk")
                        }
                    } catch (e: Exception) {
                        Log.e("DROIDRUN_FILE", "Error writing all elements to file: ${e.message}")
                    }
                    
                    val responseIntent = Intent(ACTION_ELEMENTS_RESPONSE).apply {
                        putExtra(EXTRA_ALL_ELEMENTS_DATA, "Size: ${jsonData.length} bytes, Elements: ${allElementsList.size}")
                    }
                    sendBroadcast(responseIntent)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing all elements: ${e.message}", e)
                    Log.e("DROIDRUN_ADB_RESPONSE", "ERROR: ${e.message}")
                }
            }, 100)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting all element data: ${e.message}", e)
            Log.e("DROIDRUN_ADB_RESPONSE", "ERROR: ${e.message}")
        }
    }

    // Helper method to check if overlayManager is available
    private fun isOverlayManagerAvailable(): Boolean {
        return this::overlayManager.isInitialized && isInitialized
    }

    // Helper function to find meaningful text in children
    private fun findMeaningfulTextInChildren(element: ElementNode): String {
        val meaningfulText = mutableListOf<String>()
        
        // Find all elements that are contained within this element's bounds
        val childElements = visibleElements.filter { child ->
            child != element && // Not the element itself
            isWithinBounds(child.rect, element.rect) // Within element bounds
        }
        
        // Process each child for meaningful text
        for (child in childElements) {
            if (child.text.isNotEmpty() && 
                !child.text.startsWith("android:") && 
                !child.text.contains("_") && // Skip IDs and resource names
                child.text.length > 1) {  // Skip single characters
                meaningfulText.add(child.text)
            }
        }
        
        return meaningfulText.joinToString(" ")
    }
} 