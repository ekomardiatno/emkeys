package com.ekomardiatno.emkeys

import android.view.KeyEvent
import com.ekomardiatno.emkeys.model.Evdev
import com.ekomardiatno.emkeys.model.Key
import com.ekomardiatno.emkeys.model.KeyRole
import com.ekomardiatno.emkeys.model.Mod
import com.ekomardiatno.emkeys.model.SkeletonLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards [Evdev] against the single most plausible silent bug in emkeys: treating evdev
 * scancodes as alphabetical. They are positional. A wrong scancode corrupts `event.code`
 * only, and since VS Code dispatches on `keyCode`, nothing visibly breaks until much later.
 */
class EvdevTableTest {

    @Test
    fun `alphabet is positional, not alphabetical`() {
        // Top row, left to right.
        assertEquals(16, Evdev[KeyEvent.KEYCODE_Q])
        assertEquals(17, Evdev[KeyEvent.KEYCODE_W])
        assertEquals(25, Evdev[KeyEvent.KEYCODE_P])
        // Home row.
        assertEquals(30, Evdev[KeyEvent.KEYCODE_A])
        assertEquals(31, Evdev[KeyEvent.KEYCODE_S])
        assertEquals(38, Evdev[KeyEvent.KEYCODE_L])
        // Bottom row.
        assertEquals(44, Evdev[KeyEvent.KEYCODE_Z])
        assertEquals(45, Evdev[KeyEvent.KEYCODE_X])
        assertEquals(50, Evdev[KeyEvent.KEYCODE_M])
    }

    @Test
    fun `the alphabetical formula would have produced these wrong values`() {
        // A=30 + (letter index) gives P=45 (which is KeyX) and W=52 (which is KeyComma).
        assertNotEquals(45, Evdev[KeyEvent.KEYCODE_P])
        assertNotEquals(52, Evdev[KeyEvent.KEYCODE_W])
        assertEquals(45, Evdev[KeyEvent.KEYCODE_X])
        assertEquals(52, Evdev[KeyEvent.KEYCODE_PERIOD])
    }

    @Test
    fun `function row is not contiguous`() {
        assertEquals(59, Evdev[KeyEvent.KEYCODE_F1])
        assertEquals(68, Evdev[KeyEvent.KEYCODE_F10])
        // NumLock/ScrollLock/keypad sit at 69..83, so F11 jumps.
        assertEquals(87, Evdev[KeyEvent.KEYCODE_F11])
        assertEquals(88, Evdev[KeyEvent.KEYCODE_F12])
    }

    @Test
    fun `modifiers, navigation and editing keys`() {
        assertEquals(1, Evdev[KeyEvent.KEYCODE_ESCAPE])
        assertEquals(15, Evdev[KeyEvent.KEYCODE_TAB])
        assertEquals(14, Evdev[KeyEvent.KEYCODE_DEL]) // Backspace
        assertEquals(111, Evdev[KeyEvent.KEYCODE_FORWARD_DEL]) // Delete
        assertEquals(28, Evdev[KeyEvent.KEYCODE_ENTER])
        assertEquals(57, Evdev[KeyEvent.KEYCODE_SPACE])
        assertEquals(29, Evdev[KeyEvent.KEYCODE_CTRL_LEFT])
        assertEquals(42, Evdev[KeyEvent.KEYCODE_SHIFT_LEFT])
        assertEquals(56, Evdev[KeyEvent.KEYCODE_ALT_LEFT])
        assertEquals(102, Evdev[KeyEvent.KEYCODE_MOVE_HOME])
        assertEquals(103, Evdev[KeyEvent.KEYCODE_DPAD_UP])
        assertEquals(105, Evdev[KeyEvent.KEYCODE_DPAD_LEFT])
        assertEquals(106, Evdev[KeyEvent.KEYCODE_DPAD_RIGHT])
        assertEquals(108, Evdev[KeyEvent.KEYCODE_DPAD_DOWN])
    }

    @Test
    fun `unmapped keycode degrades to UNKNOWN rather than throwing`() {
        assertEquals(Evdev.UNKNOWN, Evdev[KeyEvent.KEYCODE_CAMERA])
    }

    @Test
    fun `Mod scancodes agree with the table`() {
        Mod.entries.forEach { mod ->
            assertEquals("scanCode for $mod", Evdev[mod.keyCode], mod.scanCode)
        }
    }

    @Test
    fun `every cap in the chunk-1 layout has a scancode`() {
        SkeletonLayout.layout.rows.flatMap { it.keys }.forEach { key: Key ->
            assertNotEquals("no scancode for '${key.label}'", Evdev.UNKNOWN, key.scanCode)
        }
    }

    @Test
    fun `chunk-1 layout has the keys the acceptance criteria need`() {
        val keys = SkeletonLayout.layout.rows.flatMap { it.keys }
        assertEquals(12, keys.size)
        assertTrue(keys.any { it.keyCode == KeyEvent.KEYCODE_P })
        assertTrue(keys.any { it.keyCode == KeyEvent.KEYCODE_W })
        assertTrue(keys.filter { it.repeats }.size >= 5) // 4 arrows + Backspace
        assertEquals(
            Mod.entries.toSet(),
            keys.mapNotNull { (it.role as? KeyRole.Modifier)?.mod }.toSet(),
        )
    }
}
