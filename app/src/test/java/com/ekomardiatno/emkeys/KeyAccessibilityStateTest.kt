package com.ekomardiatno.emkeys

import com.ekomardiatno.emkeys.ime.KeyDispatcher
import com.ekomardiatno.emkeys.ime.KeyboardController
import com.ekomardiatno.emkeys.ime.ModifierState
import com.ekomardiatno.emkeys.ime.Repeater
import com.ekomardiatno.emkeys.ime.StrokeSink
import com.ekomardiatno.emkeys.model.Key
import com.ekomardiatno.emkeys.model.KeyStroke
import com.ekomardiatno.emkeys.model.Mod
import com.ekomardiatno.emkeys.model.ThinkPadLayout
import com.ekomardiatno.emkeys.view.KeyAccessibilityState
import org.junit.Assert.*
import org.junit.Test

class KeyAccessibilityStateTest {
    private val strokes = mutableListOf<KeyStroke>()
    private val controller = KeyboardController(
        KeyDispatcher(object : StrokeSink {
            override fun finishComposing() = Unit
            override fun send(stroke: KeyStroke) { strokes += stroke }
        }),
        ModifierState(),
        object : Repeater {
            override fun start(pointerId: Int, onTick: () -> Unit) = Unit
            override fun cancel(pointerId: Int) = Unit
            override fun cancelAll() = Unit
        },
    )
    private val keys = ThinkPadLayout.create().keys
    private fun key(label: String) = keys.first { it.label == label }
    private fun state(label: String) = KeyAccessibilityState.description(key(label), controller)
    private fun tap(label: String, pointer: Int = 0) {
        controller.press(pointer, key(label))
        controller.release(pointer)
    }

    @Test fun `all modifiers distinguish off held one-shot and locked`() {
        Mod.entries.forEach { mod ->
            val cap = Key.mod(mod)
            fun state() = KeyAccessibilityState.description(cap, controller)
            assertEquals(R.string.key_off, state())
            controller.press(0, cap)
            assertEquals(R.string.key_held, state())
            controller.release(0)
            assertEquals(R.string.key_latched, state())
            controller.press(0, cap)
            assertEquals(R.string.key_held, state())
            controller.release(0)
            assertEquals(R.string.key_locked, state())
            controller.press(0, cap)
            controller.release(0)
            assertEquals(R.string.key_off, state())
        }
    }

    @Test fun `one-shot description clears after a base key`() {
        tap("Shift")
        assertEquals(R.string.key_latched, state("Shift"))
        tap("A")
        assertEquals(R.string.key_off, state("Shift"))
    }

    @Test fun `locked description survives a base key`() {
        tap("Ctrl"); tap("Ctrl"); tap("A")
        assertEquals(R.string.key_locked, state("Ctrl"))
    }

    @Test fun `both caps of a shared modifier have identical states`() {
        tap("Shift"); tap("Shift")
        keys.filter { it.label == "Shift" }.forEach {
            assertEquals(R.string.key_locked, KeyAccessibilityState.description(it, controller))
        }
    }

    @Test fun `Fn starts off and a tap describes a one-shot layer`() {
        assertEquals(R.string.key_off, state("Fn"))
        tap("Fn")
        assertEquals(R.string.key_latched, state("Fn"))
        tap("←")
        assertEquals(R.string.key_off, state("Fn"))
    }

    @Test fun `held Fn reports held and does not latch after a chord`() {
        controller.press(1, key("Fn"))
        assertEquals(R.string.key_held, state("Fn"))
        tap("←", 2)
        controller.release(1)
        assertEquals(R.string.key_off, state("Fn"))
    }

    @Test fun `FnLock is described on Fn and on bare Escape`() {
        tap("Fn"); tap("Esc")
        assertEquals(R.string.key_fn_locked, state("Fn"))
        assertEquals(R.string.key_fn_lock_on, state("Esc"))
        assertEquals(key("Esc"), controller.effectiveKey(key("Esc")))
    }

    @Test fun `Escape explains how to enable FnLock when off`() {
        assertEquals(R.string.key_fn_lock_off, state("Esc"))
        tap("Fn")
        assertEquals(R.string.key_fn_lock_off, state("Esc"))
    }

    @Test fun `FnLock toggle action reports persistent state not active layer`() {
        tap("Fn")
        val toggle = controller.effectiveKey(key("Esc"))
        assertEquals(R.string.key_fn_lock_off, KeyAccessibilityState.description(toggle, controller))
        tap("Esc")
        assertEquals(R.string.key_fn_lock_on, KeyAccessibilityState.description(toggle, controller))
    }

    @Test fun `holding Fn while locked describes temporary layer inversion`() {
        controller.fn.locked = true
        controller.press(1, key("Fn"))
        assertFalse(controller.fn.active)
        assertEquals(R.string.key_fn_inverted_held, state("Fn"))
        assertEquals(R.string.key_fn_lock_on, state("Esc"))
        tap("←", 2)
        controller.release(1)
        assertEquals(R.string.key_fn_locked, state("Fn"))
    }

    @Test fun `tapping Fn while locked describes inversion for next key`() {
        controller.fn.locked = true
        tap("Fn")
        assertFalse(controller.fn.active)
        assertEquals(R.string.key_fn_inverted_latched, state("Fn"))
        tap("←")
        assertEquals(R.string.key_fn_locked, state("Fn"))
    }

    @Test fun `FnLock description clears when toggled off`() {
        tap("Fn"); tap("Esc")
        tap("Fn"); tap("Esc")
        assertEquals(R.string.key_off, state("Fn"))
        assertEquals(R.string.key_fn_lock_off, state("Esc"))
    }

    @Test fun `Caps describes its own toggle not Shift inversion`() {
        assertEquals(R.string.key_off, state("Caps"))
        tap("Caps"); tap("Shift")
        assertEquals(R.string.key_on, state("Caps"))
        assertEquals(R.string.key_latched, state("Shift"))
        tap("Caps")
        assertEquals(R.string.key_off, state("Caps"))
    }

    @Test fun `ordinary disabled and keyboard-switch caps have no toggle state`() {
        listOf("A", "Win", "Tab", "Space").forEach { assertNull(state(it)) }
        tap("Fn")
        listOf("Tab", "Space").forEach {
            assertNull(KeyAccessibilityState.description(controller.effectiveKey(key(it)), controller))
        }
    }

    @Test fun `teardown clears transient descriptions but keeps FnLock`() {
        tap("Shift"); tap("Shift"); tap("Caps")
        controller.fn.locked = true
        tap("Fn")
        controller.cancelAll(sendUps = true)
        assertEquals(R.string.key_off, state("Shift"))
        assertEquals(R.string.key_off, state("Caps"))
        assertEquals(R.string.key_fn_locked, state("Fn"))
        assertEquals(R.string.key_fn_lock_on, state("Esc"))
    }

    @Test fun `reading states neither emits input nor consumes a latch`() {
        tap("Ctrl"); tap("Fn")
        repeat(3) { keys.forEach { KeyAccessibilityState.description(it, controller) } }
        assertTrue(strokes.isEmpty())
        assertEquals(R.string.key_latched, state("Ctrl"))
        assertEquals(R.string.key_latched, state("Fn"))
    }
}
