package com.example.droidrun

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

class OverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: OverlayView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val elementRects = mutableListOf<ElementInfo>()
    private var isOverlayVisible = false
    private var positionCorrectionOffset = 0 // Default correction offset
    private var elementIndexCounter = 0 // Counter to assign indexes to elements

    companion object {
        private const val TAG = "TOPVIEW_OVERLAY"
        private const val POSITION_OFFSET_Y = -128 // Shift rectangles up by a smaller amount
    }

    data class ElementInfo(
        val rect: Rect, 
        val type: String, 
        val text: String,
        val depth: Int = 0, // Added depth field to track hierarchy level
        val color: Int = Color.GREEN, // Add color field with default value
        val index: Int = 0 // Index number for identifying the element
    )

    fun showOverlay() {
        if (overlayView != null) {
            Log.d(TAG, "Overlay already exists, not creating a new one")
            return
        }
        
        try {
            Log.d(TAG, "Creating new overlay")
            overlayView = OverlayView(context)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            
            handler.post {
                try {
                    windowManager.addView(overlayView, params)
                    isOverlayVisible = true
                    Log.d(TAG, "Overlay added successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding overlay: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating overlay: ${e.message}", e)
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
                Log.d(TAG, "Overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay: ${e.message}", e)
            }
        }
    }

    fun clearElements() {
        elementRects.clear()
        elementIndexCounter = 0 // Reset the index counter when clearing elements
        Log.d(TAG, "Elements cleared")
        refreshOverlay()
    }

    fun addElement(rect: Rect, type: String, text: String, depth: Int = 0, color: Int = Color.GREEN) {
        // Apply position correction to the rectangle
        val correctedRect = correctRectPosition(rect)
        val index = elementIndexCounter++
        elementRects.add(ElementInfo(correctedRect, type, text, depth, color, index))
        Log.d(TAG, "Element added: $text (index $index) at $correctedRect (color: ${Integer.toHexString(color)})")
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
            Log.d(TAG, "Overlay refresh requested, element count: ${elementRects.size}")
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
                Log.d(TAG, "Updated element: $text (index $index) with new color: ${Integer.toHexString(color)}")
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
            Log.d(TAG, "Added new element during update: $text (index $index)")
        }
    }
    
    // Get the count of elements in the overlay
    fun getElementCount(): Int {
        return elementRects.size
    }

    // Get the index of an element
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
        
        // If no exact match, try to find the most similar rectangle by checking for intersection
        // and text match, which is more reliable in case of minor position differences
        val similarElement = elementRects.find { element ->
            val rectOverlaps = Rect.intersects(element.rect, correctedRect) 
            val textMatches = element.text == text
            
            // Calculate how much the rectangles overlap as a percentage of the smaller rectangle's area
            val overlapRect = Rect(element.rect)
            if (overlapRect.intersect(correctedRect)) {
                val overlapArea = overlapRect.width() * overlapRect.height()
                val elementArea = element.rect.width() * element.rect.height()
                val inputArea = correctedRect.width() * correctedRect.height()
                val minArea = minOf(elementArea, inputArea)
                
                // If rectangles have significant overlap (>70%) and the text matches, it's likely the same element
                val hasSignificantOverlap = minArea > 0 && overlapArea.toFloat() / minArea > 0.7f
                
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
            strokeWidth = 8f // Make lines thicker to be more visible
            isAntiAlias = true
        }
        
        private val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f // Smaller text for better fit
            isAntiAlias = true
        }
        
        private val textBackgroundPaint = Paint().apply {
            color = Color.argb(200, 0, 0, 0)
            style = Paint.Style.FILL
        }

        init {
            // Make the overlay transparent for proper positioning
            setWillNotDraw(false)
            setBackgroundColor(Color.TRANSPARENT) // Fully transparent background
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            Log.d(TAG, "onDraw called, drawing ${elementRects.size} elements")
            
            if (elementRects.isEmpty()) {
                // Only draw test rectangle if debugging
                if (isDebugging()) {
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
                }
                return
            }
            
            // Sort elements by depth for drawing order - draw deeper elements first
            val sortedElements = elementRects.sortedBy { it.depth }
            
            for (elementInfo in sortedElements) {
                try {
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
                    
                    Log.d(TAG, "Drawing element with color: #${Integer.toHexString(colorWithAlpha)}")
                    
                    // Draw the rectangle with the specified color
                    canvas.drawRect(elementInfo.rect, boxPaint)
                    
                    // Just display the element index
                    val displayText = "${elementInfo.index}"
                    val textWidth = textPaint.measureText(displayText)
                    val textHeight = 40f
                    
                    // Position the index in the center of the element if possible
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
                    
                    // Draw background circle
                    canvas.drawRect(backgroundRect, textBackgroundPaint)
                    
                    // Draw the index number centered
                    canvas.drawText(
                        displayText,
                        centerX - textWidth/2,
                        centerY + textHeight/3, // Adjusted to center the text vertically
                        textPaint
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error drawing element: ${e.message}", e)
                }
            }
        }
        
        private fun isDebugging(): Boolean {
            return false // Set to true to show test rectangle
        }
    }
} 