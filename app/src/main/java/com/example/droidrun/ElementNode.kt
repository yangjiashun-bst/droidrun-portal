package com.example.droidrun

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.*

/**
 * Represents a UI element detected by the accessibility service
 */
class ElementNode(
    val nodeInfo: AccessibilityNodeInfo,
    val rect: Rect,
    val text: String,
    val className: String,
    val windowLayer: Int,
    var creationTime: Long,
    val id: String
) {
    companion object {
        private const val FADE_DURATION_MS = 20000L // Time to fade from weight 1.0 to 0.0 (10 seconds)
        
        /**
         * Creates a unique ID for an element based on its properties
         */
        fun createId(rect: Rect, className: String, text: String): String {
            val baseString = "${rect.left},${rect.top},${rect.right},${rect.bottom}|$className|$text"
            return UUID.nameUUIDFromBytes(baseString.toByteArray()).toString()
        }
    }
    
    /**
     * Calculates the current weight of this element based on its age
     * Returns a value between 0.0 and 1.0
     */
    fun calculateWeight(): Float {
        val age = System.currentTimeMillis() - creationTime
        return when {
            age <= 0 -> 1.0f
            age >= FADE_DURATION_MS -> 0.0f
            else -> 1.0f - (age.toFloat() / FADE_DURATION_MS.toFloat())
        }
    }
    
    /**
     * Checks if this element overlaps with another element
     */
    fun overlaps(other: ElementNode): Boolean {
        return Rect.intersects(this.rect, other.rect)
    }
    
    override fun toString(): String {
        return "ElementNode(text='$text', className='$className', rect=$rect, id='$id')"
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ElementNode) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
} 