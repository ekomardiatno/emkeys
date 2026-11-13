package com.ekomardiatno.emkeys.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.ekomardiatno.emkeys.R
import com.ekomardiatno.emkeys.ime.KeyboardController
import com.ekomardiatno.emkeys.ime.ModifierState.Phase
import com.ekomardiatno.emkeys.model.Key
import com.ekomardiatno.emkeys.model.KeyBounds
import com.ekomardiatno.emkeys.model.KeyRole
import com.ekomardiatno.emkeys.model.KeyboardAction
import com.ekomardiatno.emkeys.model.Layout
import com.ekomardiatno.emkeys.model.LayoutGeometry

/** One geometry model drives drawing, generous full-cell hit targets and virtual keys. */
class KeyboardView(context: Context) : View(context) {
    private val theme = Theme(context)
    private val density = resources.displayMetrics.density
    private val inset = 2f * density
    private val deckPadding = 5f * density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val radius = 3f * density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val legend = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = resources.getFont(R.font.jetbrains_mono_regular)
    }
    private val rect = RectF()
    private var layout = Layout(emptyList())
    private var controller: KeyboardController? = null
    private var caps: List<KeyBounds> = emptyList()
    private val touched = mutableMapOf<Int, Int>()
    private val accessibility = KeyAccessibility()

    init {
        isFocusable = true
        ViewCompat.setAccessibilityDelegate(this, accessibility)
    }

    fun bind(layout: Layout, controller: KeyboardController) {
        this.layout = layout
        this.controller = controller
        touched.clear()
        layOutCaps()
        requestLayout()
        refreshState()
    }

    fun resetTouches() {
        touched.clear()
        refreshState()
    }

    fun refreshState() {
        invalidate()
        accessibility.invalidateRoot()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val natural = if (layout.widthU > 0f) {
            // At least 48dp for the 0.6u F/arrow tiers, when the screen budget allows it.
            val unitY = ((width - deckPadding * 2) / layout.widthU).coerceAtLeast(80f * density)
            deckPadding * 2 + unitY * layout.heightU
        } else 0f
        // Keep room for the IDE in landscape; the 48dp toolbar shares this height budget.
        val budget = (resources.displayMetrics.heightPixels * 0.48f - 48f * density)
            .coerceAtLeast(120f * density)
        val height = resolveSize(natural.coerceAtMost(budget).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layOutCaps()
        accessibility.invalidateRoot()
    }

    private fun layOutCaps() {
        caps = LayoutGeometry.measure(layout, width.toFloat(), height.toFloat(), deckPadding)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(theme.deck)
        val controller = controller ?: return
        caps.forEachIndexed { index, cap ->
            val key = cap.key
            val effective = controller.effectiveKey(key)
            val phase = (key.role as? KeyRole.Modifier)?.let {
                controller.modifierState().phase(it.mod)
            } ?: Phase.OFF
            val selected = isSelected(key)
            val held = phase == Phase.HELD || (key.role == KeyRole.Fn && controller.fn.isHeld)
            val amberFill = selected && !held
            val modifier = key.role != KeyRole.Base
            val disabled = key.role == KeyRole.Disabled
            fill.color = when {
                phase == Phase.LOCKED -> theme.locked
                amberFill -> theme.latched
                index in touched.values -> theme.capPressed
                modifier -> theme.capModifier
                else -> theme.cap
            }
            stroke.color = when {
                held -> theme.latched
                modifier -> theme.borderStrong
                else -> theme.border
            }
            legend.color = when {
                disabled -> theme.legendMuted
                amberFill -> theme.onAmber
                else -> theme.legend
            }
            rect.set(cap.left + inset, cap.top + inset, cap.right - inset, cap.bottom - inset)
            canvas.drawRoundRect(rect, radius, radius, fill)
            canvas.drawRoundRect(rect, radius, radius, stroke)

            val shifted = effective.shiftedLabel != null && controller.isUppercase(effective)
            val label = if (shifted) effective.shiftedLabel!! else effective.label
            val secondary = when {
                effective !== key -> key.label
                shifted -> key.label
                else -> key.shiftedLabel ?: key.fnKey?.label
            }
            val textPadding = (rect.width() * 0.1f).coerceIn(2f * density, 7f * density)
            val primarySize = (rect.height() * 0.32f).coerceIn(11f * density, 19f * density)
            legend.textSize = primarySize
            val available = (rect.width() - textPadding * 2).coerceAtLeast(1f)
            if (legend.measureText(label) > available) {
                legend.textSize *= available / legend.measureText(label)
            }
            canvas.drawText(label, rect.left + textPadding,
                rect.top + textPadding - legend.ascent(), legend)
            if (secondary != null && rect.height() > 27f * density) {
                legend.textSize = 9f * density
                legend.color = if (amberFill) theme.onAmber else theme.legendMuted
                if (legend.measureText(secondary) > available) {
                    legend.textSize *= available / legend.measureText(secondary)
                }
                canvas.drawText(secondary, rect.right - textPadding - legend.measureText(secondary),
                    rect.bottom - textPadding - legend.descent(), legend)
            }
            if ((key.fnKey?.role as? KeyRole.Action)?.action == KeyboardAction.FN_LOCK && controller.fn.locked) {
                fill.color = theme.latched
                canvas.drawCircle(rect.right - 5f * density, rect.top + 5f * density, 2f * density, fill)
            }
        }
    }

    private fun isSelected(key: Key): Boolean {
        val controller = controller ?: return false
        return when (val role = key.role) {
            is KeyRole.Modifier -> controller.modifierState().phase(role.mod) != Phase.OFF
            KeyRole.Fn -> controller.fn.active
            is KeyRole.Action -> role.action == KeyboardAction.CAPS_LOCK && controller.capsLock
            else -> false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val controller = controller ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val i = event.actionIndex
                val id = event.getPointerId(i)
                val index = capAt(event.getX(i), event.getY(i))
                if (index >= 0 && caps[index].key.role != KeyRole.Disabled) {
                    touched[id] = index
                    controller.press(id, caps[index].key)
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
            MotionEvent.ACTION_MOVE -> touched.toMap().forEach { (id, index) ->
                val i = event.findPointerIndex(id)
                if (i < 0 || !insideWithSlop(caps[index], event.getX(i), event.getY(i))) {
                    touched.remove(id)
                    controller.cancel(id)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                val id = event.getPointerId(i)
                val index = touched.remove(id)
                if (index != null && !insideWithSlop(caps[index], event.getX(i), event.getY(i))) {
                    controller.cancel(id)
                } else {
                    controller.release(id)
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                touched.clear()
                controller.cancelAll(sendUps = true)
            }
        }
        invalidate()
        return true
    }

    // Touches already dispatch on DOWN. This supplies the standard accessibility hook only.
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun insideWithSlop(cap: KeyBounds, x: Float, y: Float): Boolean =
        x >= cap.left - slop && x < cap.right + slop && y >= cap.top - slop && y < cap.bottom + slop

    private fun capAt(x: Float, y: Float): Int = caps.indexOfFirst { it.contains(x, y) }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        accessibility.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        accessibility.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    private inner class KeyAccessibility : ExploreByTouchHelper(this@KeyboardView) {
        override fun getVirtualViewAt(x: Float, y: Float): Int =
            capAt(x, y).let { if (it < 0) INVALID_ID else it }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            virtualViewIds.addAll(caps.indices)
        }

        override fun onPopulateNodeForVirtualView(id: Int, node: AccessibilityNodeInfoCompat) {
            val cap = caps[id]
            val effective = controller?.effectiveKey(cap.key) ?: cap.key
            node.contentDescription = if (effective.shiftedLabel != null && controller?.isUppercase(effective) == true) {
                effective.shiftedLabel
            } else effective.description
            node.className = Button::class.java.name
            setBoundsInScreenFromBoundsInParent(node,
                Rect(cap.left.toInt(), cap.top.toInt(), cap.right.toInt(), cap.bottom.toInt()))
            node.isEnabled = cap.key.role != KeyRole.Disabled
            node.isClickable = node.isEnabled
            node.isCheckable = cap.key.role is KeyRole.Modifier || cap.key.role == KeyRole.Fn ||
                cap.key.role == KeyRole.Action(KeyboardAction.CAPS_LOCK)
            node.isChecked = isSelected(cap.key)
            node.stateDescription = controller?.let { controller ->
                KeyAccessibilityState.description(cap.key, controller)?.let { context.getString(it) }
            }
            if (node.isEnabled) node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }

        override fun onPerformActionForVirtualView(id: Int, action: Int, arguments: Bundle?): Boolean {
            val cap = caps.getOrNull(id) ?: return false
            val controller = controller ?: return false
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || cap.key.role == KeyRole.Disabled) return false
            // Virtual ids use negative pointer ids so they cannot collide with real touches.
            controller.press(-id - 1, cap.key)
            controller.release(-id - 1)
            sendEventForVirtualView(id, AccessibilityEvent.TYPE_VIEW_CLICKED)
            return true
        }
    }
}
