package com.droidrun.portal.features.overlay

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.max
import kotlin.math.min

/**
 * A data class representing a single UI element captured by the accessibility service.
 *
 * This class serves as a rich data model that not only stores the properties of a UI element
 * (such as its `Rect`, `text`, and `className`) but also its relationships with other elements
 * (parent, children). It provides a set of helper functions for calculating the element's
 * weight, checking for overlaps, and traversing the element hierarchy.
 *
 * @property nodeInfo The raw `AccessibilityNodeInfo` object from the accessibility service.
 * @property rect The `Rect` that defines the element's boundaries on the screen.
 * @property text The text content of the element.
 * @property className The class name of the element (e.g., "android.widget.Button").
 * @property windowLayer The layer of the window that the element belongs to.
 * @property creationTime The time at which the element was created, used for calculating its weight.
 * @property id A unique ID for the element, generated from its properties.
 * @property parent A reference to the parent `ElementNode` in the hierarchy.
 * @property children A list of child `ElementNode` objects.
 * @property clickableIndex The index of the element in the list of clickable elements.
 * @property nestingLevel The depth of the element in the hierarchy.
 * @property semanticParentId The ID of the element's semantic parent.
 * @property overlayIndex The index of the element as shown in the overlay.
 */
data class ElementNode(
    val nodeInfo: AccessibilityNodeInfo,
    val rect: Rect,
    val text: String,
    val className: String,
    val windowLayer: Int,
    var creationTime: Long,
    val id: String,
    var parent: ElementNode? = null,
    val children: MutableList<ElementNode> = mutableListOf(),
    var clickableIndex: Int = -1,
    var nestingLevel: Int = 0,
    var semanticParentId: String? = null,
    var overlayIndex: Int = -1 // Store the exact index shown in the overlay
) {
    companion object {
        private const val FADE_DURATION_MS = 60000L // Time to fade from weight 1.0 to 0.0 (60 seconds)
        
        /**
         * Creates a unique ID for an element based on its properties
         */
        fun createId(rect: Rect, className: String, text: String): String {
            return "${rect.toShortString()}_${className}_${text.take(20)}"
        }
    }
    
    /**
     * Calculates the current weight of this element based on its age
     * Returns a value between 0.0 and 1.0
     */
    fun calculateWeight(): Float {
        val now = System.currentTimeMillis()
        val age = now - creationTime
        return max(0f, min(1f, 1f - (age.toFloat() / FADE_DURATION_MS.toFloat())))
    }
    
    /**
     * Checks if this element overlaps with another element
     */
    fun overlaps(other: ElementNode): Boolean {
        return Rect.intersects(this.rect, other.rect)
    }
    
    fun contains(other: ElementNode): Boolean {
        return this.rect.contains(other.rect)
    }
    
    fun isClickable(): Boolean {
        return nodeInfo.isClickable
    }
    
    fun isText(): Boolean {
        return text.isNotEmpty() && !nodeInfo.isClickable
    }
    
    // Calculate nesting level (depth in the hierarchy)
    fun calculateNestingLevel(): Int {
        if (nestingLevel > 0) {
            return nestingLevel
        }
        
        var current = this
        var level = 0
        
        while (current.parent != null) {
            level++
            current = current.parent!!
        }
        
        nestingLevel = level
        return level
    }
    
    // Get the root ancestor
    fun getRootAncestor(): ElementNode {
        var current = this
        while (current.parent != null) {
            current = current.parent!!
        }
        return current
    }
    
    // Add a child node
    fun addChild(child: ElementNode) {
        if (!children.contains(child)) {
            children.add(child)
            child.parent = this
        }
    }
    
    // Remove a child node
    fun removeChild(child: ElementNode) {
        children.remove(child)
        child.parent = null
    }
    
    // Get all descendants (children, grandchildren, etc.)
    fun getAllDescendants(): List<ElementNode> {
        val descendants = mutableListOf<ElementNode>()
        for (child in children) {
            descendants.add(child)
            descendants.addAll(child.getAllDescendants())
        }
        return descendants
    }
    
    // Get path from root to this node
    fun getPathFromRoot(): List<ElementNode> {
        val path = mutableListOf<ElementNode>()
        var current: ElementNode? = this
        while (current != null) {
            path.add(0, current)
            current = current.parent
        }
        return path
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