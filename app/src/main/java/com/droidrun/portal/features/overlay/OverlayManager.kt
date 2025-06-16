package com.droidrun.portal.features.overlay

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
import android.view.View
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the creation, display, and content of the screen overlay.
 *
 * This class is the heart of the overlay feature. It is responsible for creating, showing,
 * hiding, and managing the overlay `View` itself. It maintains a list of `ElementInfo`
 * objects and handles all the drawing logic on the `Canvas`. It completely abstracts the
 * complexities of the Android `WindowManager` and the `Canvas` drawing APIs, providing a
 * simple interface for adding, clearing, and refreshing elements on the screen.
 *
 * @param context The application context, used for accessing system services.
 */
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
    
    private var positionOffsetY = -128 // Default offset value

    companion object {
        private const val TAG = "TOPVIEW_OVERLAY"
        private const val OVERLAP_THRESHOLD = 0.5f // Lower overlap threshold for matching
        
        // Define a color scheme with 8 visually distinct colors
        private val COLOR_SCHEME = arrayOf(
            Color.rgb(0, 122, 255),    // Blue
            Color.rgb(255, 45, 85),    // Red
            Color.rgb(52, 199, 89),    // Green
            Color.rgb(255, 149, 0),    // Orange
            Color.rgb(175, 82, 222),   // Purple
            Color.rgb(255, 204, 0),    // Yellow
            Color.rgb(90, 200, 250),   // Light Blue
            Color.rgb(88, 86, 214)     // Indigo
        )
    }

    data class ElementInfo(
        val rect: Rect, 
        val type: String, 
        val text: String,
        val depth: Int = 0, // Added depth field to track hierarchy level
        val color: Int = Color.GREEN, // Add color field with default value
        val index: Int = 0 // Index number for identifying the element
    )
    
    // Add method to adjust the vertical offset
    fun setPositionOffsetY(offsetY: Int) {
        val diff = offsetY - this.positionOffsetY
        this.positionOffsetY = offsetY
        
        // Update the position of each element directly
        elementRects.forEach { it.rect.offset(0, diff) }
        
        refreshOverlay()
    }
    
    // Add getter for the current offset value
    fun getPositionOffsetY(): Int {
        return positionOffsetY
    }

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
        // Assign a color from the color scheme based on the index
        val colorFromScheme = COLOR_SCHEME[index % COLOR_SCHEME.size]
        elementRects.add(ElementInfo(correctedRect, type, text, depth, colorFromScheme, index))
        // Don't refresh on each add to avoid excessive redraws with many elements
    }
    
    // Correct the rectangle position to better match the actual UI element
    private fun correctRectPosition(rect: Rect): Rect {
        val correctedRect = Rect(rect)
        
        // Apply the vertical offset to shift the rectangle upward
        correctedRect.offset(0, positionOffsetY)
        
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
        val existingElement = findElement(correctedRect, text)

        if (existingElement != null) {
            val index = elementRects.indexOf(existingElement)
            if (index != -1) {
                elementRects[index] = existingElement.copy(rect = correctedRect, text = text)
            }
        } else {
            addElement(rect, "UpdatedElement", text, 0, color)
        }
    }
    
    private fun findElement(rect: Rect, text: String): ElementInfo? {
        return elementRects.find { element ->
            val textMatches = element.text.trim() == text.trim()
            if (!textMatches) return@find false

            val overlapRect = Rect(element.rect)
            if (overlapRect.intersect(rect)) {
                val overlapArea = overlapRect.width() * overlapRect.height()
                val elementArea = element.rect.width() * element.rect.height()
                val inputArea = rect.width() * rect.height()
                val minArea = minOf(elementArea, inputArea)
                
                minArea > 0 && overlapArea.toFloat() / minArea > OVERLAP_THRESHOLD
            } else {
                false
            }
        }
    }
    
    // Get the count of elements in the overlay
    fun getElementCount(): Int {
        return elementRects.size
    }

    // Modified getElementIndex with more lenient matching
    fun getElementIndex(rect: Rect, text: String): Int {
        val correctedRect = correctRectPosition(rect)
        return findElement(correctedRect, text)?.index ?: -1
    }

    inner class OverlayView(context: Context) : FrameLayout(context) {
        private val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f  // Thinner border as requested
            isAntiAlias = true
            // Enable hardware acceleration features
            flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
        }
        
        private val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 32f  // Increased text size for better visibility
            isAntiAlias = true
            // Enable hardware acceleration features
            flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
        }
        
        private val textBackgroundPaint = Paint().apply {
            // Color will be set dynamically to match the border color
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
            super.onDraw(canvas)

            if (!isOverlayVisible) return

            if (elementRects.isEmpty()) {
                if (isDebugging()) drawDebugRect(canvas)
                return
            }
            
            // Create a local copy to prevent concurrent modification issues
            val elementsToDraw = ArrayList(elementRects)
            
            // Sort elements by depth to ensure correct drawing order
            elementsToDraw.sortBy { it.depth }
            
            for (elementInfo in elementsToDraw) {
                try {
                    drawElement(canvas, elementInfo)
                } catch (e: Exception) {
                    Log.e(TAG, "Error drawing element ${elementInfo.index}: ${e.message}", e)
                }
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
                
                // Set the background color to match the border color with some transparency
                textBackgroundPaint.color = Color.argb(
                    200, // Semi-transparent
                    Color.red(elementColor),
                    Color.green(elementColor),
                    Color.blue(elementColor)
                )
                
                // Draw the rectangle with the specified color
                canvas.drawRect(elementInfo.rect, boxPaint)
                
                // Draw the index number in the top-right corner
                val displayText = "${elementInfo.index}"
                val textWidth = textPaint.measureText(displayText)
                val textHeight = 36f  // Larger text height to match increased text size
                
                // Position for top-right corner with small padding
                val textX = elementInfo.rect.right - textWidth - 4f  // 4px padding from right edge
                val textY = elementInfo.rect.top + textHeight  // Position text at top with some padding
                
                // Calculate background rectangle for the text
                val backgroundPadding = 4f
                val backgroundRect = Rect(
                    (textX - backgroundPadding).toInt(),
                    (textY - textHeight).toInt(),
                    (textX + textWidth + backgroundPadding).toInt(),
                    (textY + backgroundPadding).toInt()
                )
                
                // Draw background and text
                canvas.drawRect(backgroundRect, textBackgroundPaint)
                canvas.drawText(
                    displayText,
                    textX,
                    textY - backgroundPadding,
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