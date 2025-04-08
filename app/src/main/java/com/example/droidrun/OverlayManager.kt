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
import android.widget.ImageButton
import android.content.Intent
import android.view.View

class OverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: OverlayView? = null
    private var toggleButton: ImageButton? = null
    private val handler = Handler(Looper.getMainLooper())
    private val elementRects = mutableListOf<ElementInfo>()
    private var isOverlayVisible = false
    private var isInteractiveOnly = false
    private var positionCorrectionOffset = 0 // Default correction offset

    companion object {
        private const val TAG = "TOPVIEW_OVERLAY"
        private const val POSITION_OFFSET_Y = -128 // Shift rectangles up by a smaller amount
    }

    data class ElementInfo(
        val rect: Rect, 
        val type: String, 
        val text: String,
        val depth: Int = 0, // Added depth field to track hierarchy level
        val color: Int = Color.GREEN // Add color field with default value
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
            
            // Create toggle button
            toggleButton = ImageButton(context).apply {
                setBackgroundColor(Color.parseColor("#CC000000")) // More opaque black background
                setImageResource(android.R.drawable.ic_menu_manage)
                alpha = 0.8f
                // Set explicit size
                minimumWidth = 150
                minimumHeight = 150
                setPadding(20, 20, 20, 20)
            }
            
            val buttonParams = WindowManager.LayoutParams(
                150, // Fixed width
                150, // Fixed height
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            buttonParams.gravity = Gravity.TOP or Gravity.END
            buttonParams.x = 50 // Distance from right edge
            buttonParams.y = 200 // Distance from top
            
            toggleButton?.setOnClickListener {
                isInteractiveOnly = !isInteractiveOnly
                toggleButton?.apply {
                    alpha = if (isInteractiveOnly) 1.0f else 0.8f
                    setBackgroundColor(if (isInteractiveOnly) 
                        Color.parseColor("#FF2196F3") // Material Blue when active
                    else 
                        Color.parseColor("#CC000000") // Semi-transparent black when inactive
                    )
                }
                
                // Broadcast the change
                val intent = Intent(DroidrunPortalService.ACTION_TOGGLE_INTERACTIVE_ONLY).apply {
                    putExtra(DroidrunPortalService.EXTRA_INTERACTIVE_ONLY, isInteractiveOnly)
                }
                context.sendBroadcast(intent)
            }
            
            handler.post {
                try {
                    windowManager.addView(overlayView, params)
                    windowManager.addView(toggleButton, buttonParams)
                    isOverlayVisible = true
                    Log.d(TAG, "Overlay and toggle button added successfully")
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
                toggleButton?.let {
                    windowManager.removeView(it)
                    toggleButton = null
                }
                isOverlayVisible = false
                Log.d(TAG, "Overlay and toggle button removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay: ${e.message}", e)
            }
        }
    }

    fun clearElements() {
        elementRects.clear()
        Log.d(TAG, "Elements cleared")
        refreshOverlay()
    }

    fun addElement(rect: Rect, type: String, text: String, depth: Int = 0, color: Int = Color.GREEN) {
        // Apply position correction to the rectangle
        val correctedRect = correctRectPosition(rect)
        elementRects.add(ElementInfo(correctedRect, type, text, depth, color))
        Log.d(TAG, "Element added: $text at $correctedRect (color: ${Integer.toHexString(color)})")
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
                    
                    // Position the text label above the element
                    val colorHex = String.format("%06X", 0xFFFFFF and elementColor)
                    val displayText = "${elementInfo.text.take(12)} (#$colorHex)"
                    val textWidth = textPaint.measureText(displayText)
                    val textHeight = 36f
                    
                    // Draw text background
                    val textY = maxOf(textHeight + 5, elementInfo.rect.top.toFloat() - 5)
                    val textRect = Rect(
                        elementInfo.rect.left,
                        (textY - textHeight).toInt(),
                        (elementInfo.rect.left + textWidth + 10).toInt(),
                        textY.toInt()
                    )
                    
                    // Make sure the text background is fully on screen
                    if (textRect.top >= 0) {
                        canvas.drawRect(textRect, textBackgroundPaint)
                        
                        // Draw the text
                        canvas.drawText(
                            displayText,
                            elementInfo.rect.left + 5f,
                            textY - 8f, // Position text inside background
                            textPaint
                        )
                    } else {
                        // If text would be off-screen, draw it below the element instead
                        val belowTextRect = Rect(
                            elementInfo.rect.left,
                            elementInfo.rect.bottom,
                            (elementInfo.rect.left + textWidth + 10).toInt(),
                            elementInfo.rect.bottom + textHeight.toInt()
                        )
                        canvas.drawRect(belowTextRect, textBackgroundPaint)
                        
                        // Draw text below
                        canvas.drawText(
                            displayText,
                            elementInfo.rect.left + 5f,
                            elementInfo.rect.bottom + textHeight - 8f,
                            textPaint
                        )
                    }
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