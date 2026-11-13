package com.ekomardiatno.emkeys

import android.view.KeyEvent as K
import com.ekomardiatno.emkeys.model.Evdev
import com.ekomardiatno.emkeys.model.KeyRole
import com.ekomardiatno.emkeys.model.LayoutGeometry
import com.ekomardiatno.emkeys.model.Mod
import com.ekomardiatno.emkeys.model.ThinkPadLayout
import org.junit.Assert.*
import org.junit.Test

class ThinkPadLayoutTest {
    private val layout = ThinkPadLayout.create()

    @Test fun `ANSI shell has 84 keys with complete alphabet and F row`() {
        assertEquals(84, layout.keys.size)
        val codes = layout.keys.filter { it.role == KeyRole.Base }.map { it.keyCode }
        assertTrue(codes.containsAll((K.KEYCODE_A..K.KEYCODE_Z).toList()))
        assertTrue(codes.containsAll((K.KEYCODE_F1..K.KEYCODE_F12).toList()))
        assertTrue(codes.containsAll(listOf(K.KEYCODE_SPACE, K.KEYCODE_ENTER, K.KEYCODE_DEL,
            K.KEYCODE_FORWARD_DEL, K.KEYCODE_INSERT, K.KEYCODE_MOVE_HOME, K.KEYCODE_MOVE_END)))
    }

    @Test fun `main rows span exactly 15 ThinkPad units`() {
        assertEquals(15f, layout.widthU, 0.001f)
        layout.rows.take(6).forEach { row ->
            assertEquals(15f, row.keys.sumOf { (it.widthU + it.gapBeforeU).toDouble() }.toFloat(), 0.001f)
        }
        assertEquals(5f, layout.keys.single { it.label == "Space" }.widthU, 0f)
        assertEquals(2.35f, layout.keys.single { it.label == "Enter" }.widthU, 0f)
        assertEquals(2.85f, layout.keys.last { it.label == "Shift" }.widthU, 0f)
    }

    @Test fun `every dispatched base and Fn key has an evdev scancode`() {
        (layout.keys + layout.keys.mapNotNull { it.fnKey }).filter { it.role == KeyRole.Base }.forEach {
            assertNotEquals("Missing scancode for ${it.label}", Evdev.UNKNOWN, it.scanCode)
        }
        assertEquals(99, Evdev[K.KEYCODE_SYSRQ])
        assertEquals(70, Evdev[K.KEYCODE_SCROLL_LOCK])
        assertEquals(119, Evdev[K.KEYCODE_BREAK])
    }

    @Test fun `swap changes the first two bottom caps and nothing else`() {
        val swapped = ThinkPadLayout.create(swapFnCtrl = true)
        assertEquals(KeyRole.Modifier(Mod.CTRL), swapped.rows[5].keys[0].role)
        assertEquals(KeyRole.Fn, swapped.rows[5].keys[1].role)
        assertEquals(layout.rows.take(5), swapped.rows.take(5))
        assertEquals(layout.rows[5].keys.drop(2), swapped.rows[5].keys.drop(2))
        assertEquals(layout.widthU, swapped.widthU, 0f)
    }

    @Test fun `geometry stays inside portrait and landscape viewports without overlap`() {
        listOf(1056f to 390f, 1691f to 450f, 600f to 260f).forEach { (w, h) ->
            val bounds = LayoutGeometry.measure(layout, w, h, 5f)
            assertEquals(84, bounds.size)
            bounds.forEach { cap ->
                assertTrue(cap.left >= 4.99f && cap.right <= w - 4.99f)
                assertTrue(cap.top >= 4.99f && cap.bottom <= h - 4.99f)
                assertTrue(cap.right > cap.left && cap.bottom > cap.top)
                val centerX = (cap.left + cap.right) / 2
                val centerY = (cap.top + cap.bottom) / 2
                assertEquals(cap, bounds.single { it.contains(centerX, centerY) })
            }
            bounds.forEachIndexed { i, a ->
                bounds.drop(i + 1).forEach { b ->
                    val x = minOf(a.right, b.right) - maxOf(a.left, b.left)
                    val y = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                    assertFalse("${a.key.label} overlaps ${b.key.label}", x > 0.01f && y > 0.01f)
                }
            }
        }
    }

    @Test fun `PgUp Up PgDn sit over the inverted T arrow island`() {
        val bounds = LayoutGeometry.measure(layout, 1500f, 554f, 0f)
        val upper = bounds.first { it.key.label == "↑" }
        val lower = bounds.first { it.key.label == "↓" }
        assertEquals(upper.left, lower.left, 0.001f)
        assertEquals(upper.bottom, lower.top, 0.001f)
        assertEquals(bounds.first { it.key.label == "PgUp" }.left,
            bounds.first { it.key.label == "←" }.left, 0.001f)
    }
}
