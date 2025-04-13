package com.droidrun.portal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.content.Intent
import android.view.View
import java.util.concurrent.atomic.AtomicBoolean

class OverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: OverlayView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val elementRects = mutableListOf<ElementInfo>()
    private var isOverlayVisible = false
    private var positionCorrectionOffset = 0 // Default correction offset
    private var elementIndexCounter = 0 // Counter to assign indexes to elements
    private val isOverlayReady = AtomicBoolean(false)
    private var onReadyCallback: (() -> Unit)? = null

    companion object {
        private const val TAG = "TOPVIEW_OVERLAY"
        private const val POSITION_OFFSET_Y = -128 // Shift rectangles up by a smaller amount
        private const val OVERLAP_THRESHOLD = 0.5f // Lower overlap threshold for matching
    }

    data class ElementInfo(
        val rect: Rect, 
        val type: String, 
        val text: String,
        val depth: Int = 0, // Added depth field to track hierarchy level
        val color: Int = Color.GREEN, // Add color field with default value
        val index: Int = 0 // Index number for identifying the element
    )

    fun setOnReadyCallback(callback: () -> Unit) {
        onReadyCallback = callback
        // If already ready, call immediately
        if (isOverlayReady.get()) {
            handler.post(callback)
        }
    }

    fun showOverlay() {
        if (overlayView != null) {
            Log.d(TAG, "Overlay already exists, checking if it's attached")
            try {
                // Check if the view is actually attached
                overlayView?.parent ?: run {
                    Log.w(TAG, "Overlay exists but not attached, recreating")
                    overlayView = null
                    createAndAddOverlay()
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking overlay state: ${e.message}", e)
                overlayView = null
                createAndAddOverlay()
                return
            }
            isOverlayReady.set(true)
            onReadyCallback?.let { handler.post(it) }
            return
        }
        createAndAddOverlay()
    }

    private fun createAndAddOverlay() {
        try {
            Log.d(TAG, "Creating new overlay")
            overlayView = OverlayView(context).apply {
                // Set hardware acceleration
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            
            handler.post {
                try {
                    windowManager.addView(overlayView, params)
                    isOverlayVisible = true
                    
                    // Set ready state and notify callback after a short delay to ensure view is laid out
                    handler.postDelayed({
                        if (overlayView?.parent != null) {
                            isOverlayReady.set(true)
                            onReadyCallback?.let { it() }
                        } else {
                            Log.e(TAG, "Overlay not properly attached after delay")
                            // Try to recreate if not attached
                            hideOverlay()
                            showOverlay()
                        }
                    }, 500)
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding overlay: ${e.message}", e)
                    // Clean up on failure
                    overlayView = null
                    isOverlayVisible = false
                    isOverlayReady.set(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating overlay: ${e.message}", e)
            // Clean up on failure
            overlayView = null
            isOverlayVisible = false
            isOverlayReady.set(false)
        }
    }

    fun hideOverlay() {
        handler.post {
            try {
                overlayView?.let {
                    windowManager.removeView(it)
                    overlayView = null
                }
                isOverlayVisible = false
                isOverlayReady.set(false)
                Log.d(TAG, "Overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay: ${e.message}", e)
            }
        }
    }

    fun clearElements() {
        elementRects.clear()
        elementIndexCounter = 0 // Reset the index counter when clearing elements
        refreshOverlay()
    }

    fun addElement(rect: Rect, type: String, text: String, depth: Int = 0, color: Int = Color.GREEN) {
        // Apply position correction to the rectangle
        val correctedRect = correctRectPosition(rect)
        val index = elementIndexCounter++
        elementRects.add(ElementInfo(correctedRect, type, text, depth, color, index))
        // Don't refresh on each add to avoid excessive redraws with many elements
    }
    
    // Correct the rectangle position to better match the actual UI element
    private fun correctRectPosition(rect: Rect): Rect {
        val correctedRect = Rect(rect)
        
        // Apply a vertical offset to shift the rectangle upward
        correctedRect.offset(0, POSITION_OFFSET_Y)
        
        return correctedRect
    }

    fun refreshOverlay() {
        handler.post {
            if (overlayView == null) {
                Log.e(TAG, "Cannot refresh overlay - view is null")
                showOverlay()
            }
            overlayView?.invalidate()
        }
    }

    // Update an existing element without changing its index
    fun updateElement(rect: Rect, text: String, color: Int = Color.GREEN) {
        val correctedRect = correctRectPosition(rect)
        
        // Try to find the existing element first
        val existingElement = elementRects.find { element ->
            // Check if this is the same element by matching text and checking for significant overlap
            if (element.text == text) {
                val overlapRect = Rect(element.rect)
                if (overlapRect.intersect(correctedRect)) {
                    val overlapArea = overlapRect.width() * overlapRect.height()
                    val elementArea = element.rect.width() * element.rect.height()
                    val inputArea = correctedRect.width() * correctedRect.height()
                    val minArea = minOf(elementArea, inputArea)
                    
                    // If rectangles have significant overlap (>50%), it's likely the same element
                    minArea > 0 && overlapArea.toFloat() / minArea > 0.5f
                } else {
                    false
                }
            } else {
                false
            }
        }
        
        if (existingElement != null) {
            // Update the existing element's properties but keep its index
            val index = existingElement.index
            val elementIndex = elementRects.indexOf(existingElement)
            if (elementIndex >= 0) {
                elementRects[elementIndex] = ElementInfo(
                    rect = correctedRect,
                    type = existingElement.type,
                    text = text,
                    depth = existingElement.depth,
                    color = color,
                    index = index
                )
            }
        } else {
            // If element doesn't exist, add it as a new element
            val index = elementIndexCounter++
            elementRects.add(ElementInfo(
                rect = correctedRect,
                type = "UpdatedElement",
                text = text,
                depth = 0,
                color = color,
                index = index
            ))
        }
    }
    
    // Get the count of elements in the overlay
    fun getElementCount(): Int {
        return elementRects.size
    }

    // Modified getElementIndex with more lenient matching
    fun getElementIndex(rect: Rect, text: String): Int {
        // Apply the same position correction that was applied when adding the element
        val correctedRect = correctRectPosition(rect)
        
        // First try to find an exact match with the corrected rectangle
        val exactMatch = elementRects.find { 
            it.rect == correctedRect && it.text == text 
        }
        
        if (exactMatch != null) {
            return exactMatch.index
        }
        
        // Try looser matching with lower overlap threshold
        val similarElement = elementRects.find { element ->
            val rectOverlaps = Rect.intersects(element.rect, correctedRect)
            
            // More lenient text matching
            val textMatches = element.text.trim() == text.trim()
            
            if (rectOverlaps) {
                val overlapRect = Rect(element.rect)
                overlapRect.intersect(correctedRect)
                val overlapArea = overlapRect.width() * overlapRect.height()
                val elementArea = element.rect.width() * element.rect.height()
                val inputArea = correctedRect.width() * correctedRect.height()
                val minArea = minOf(elementArea, inputArea)
                
                val hasSignificantOverlap = minArea > 0 && 
                    overlapArea.toFloat() / minArea > OVERLAP_THRESHOLD
                
                hasSignificantOverlap && textMatches
            } else {
                false
            }
        }
        
        return similarElement?.index ?: -1
    }

    inner class OverlayView(context: Context) : FrameLayout(context) {
        private val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            isAntiAlias = true
            // Enable hardware acceleration features
            flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
        }
        
        private val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isAntiAlias = true
            // Enable hardware acceleration features
            flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
        }
        
        private val textBackgroundPaint = Paint().apply {
            color = Color.argb(200, 0, 0, 0)
            style = Paint.Style.FILL
            // Enable hardware acceleration features
            flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
        }

        init {
            setWillNotDraw(false)
            setBackgroundColor(Color.TRANSPARENT)
            // Enable hardware acceleration
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            try {
                if (canvas == null) {
                    Log.e(TAG, "Canvas is null in onDraw")
                    return
                }

                if (!isOverlayVisible) {
                    Log.d(TAG, "Overlay not visible, skipping draw")
                    return
                }

                super.onDraw(canvas)
                
                val startTime = System.currentTimeMillis()

                if (elementRects.isEmpty()) {
                    if (isDebugging()) {
                        drawDebugRect(canvas)
                    }
                    return
                }
                
                // Create a local copy to prevent concurrent modification
                val elementsToDraw = ArrayList(elementRects)
                
                // Sort elements by depth for drawing order
                val sortedElements = elementsToDraw.sortedBy { it.depth }
                
                for (elementInfo in sortedElements) {
                    drawElement(canvas, elementInfo)
                }

                val drawTime = System.currentTimeMillis() - startTime
            } catch (e: Exception) {
                Log.e(TAG, "Error in onDraw: ${e.message}", e)
            }
        }

        private fun drawElement(canvas: Canvas, elementInfo: ElementInfo) {
            try {
                // Ensure the rectangle is valid
                if (elementInfo.rect.width() <= 0 || elementInfo.rect.height() <= 0) {
                    Log.w(TAG, "Invalid rectangle dimensions for element ${elementInfo.index}")
                    return
                }

                // IMPORTANT: Set the color for this specific element 
                val elementColor = elementInfo.color
                
                // Ensure color has full alpha for visibility
                val colorWithAlpha = Color.argb(
                    255,
                    Color.red(elementColor),
                    Color.green(elementColor),
                    Color.blue(elementColor)
                )
                
                boxPaint.color = colorWithAlpha
                
                // Draw the rectangle with the specified color
                canvas.drawRect(elementInfo.rect, boxPaint)
                
                // Draw the index number
                val displayText = "${elementInfo.index}"
                val textWidth = textPaint.measureText(displayText)
                val textHeight = 40f
                
                val centerX = elementInfo.rect.centerX()
                val centerY = elementInfo.rect.centerY()
                
                // Calculate background rectangle
                val textBackgroundSize = textHeight * 1.2f
                val backgroundRect = Rect(
                    (centerX - textBackgroundSize/2).toInt(),
                    (centerY - textBackgroundSize/2).toInt(),
                    (centerX + textBackgroundSize/2).toInt(),
                    (centerY + textBackgroundSize/2).toInt()
                )
                
                // Draw background and text
                canvas.drawRect(backgroundRect, textBackgroundPaint)
                canvas.drawText(
                    displayText,
                    centerX - textWidth/2,
                    centerY + textHeight/3,
                    textPaint
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing element ${elementInfo.index}: ${e.message}", e)
            }
        }

        private fun drawDebugRect(canvas: Canvas) {
            try {
                val screenWidth = width
                val screenHeight = height
                val testRect = Rect(
                    screenWidth / 4,
                    screenHeight / 4,
                    (screenWidth * 3) / 4,
                    (screenHeight * 3) / 4
                )
                boxPaint.color = Color.GREEN
                canvas.drawRect(testRect, boxPaint)
                Log.d(TAG, "Drew test rectangle at $testRect")
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing debug rectangle: ${e.message}", e)
            }
        }
        
        private fun isDebugging(): Boolean {
            return false // Set to true to show test rectangle
        }
    }
} 