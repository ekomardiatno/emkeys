package com.ekomardiatno.emkeys

import com.ekomardiatno.emkeys.ime.KeyDispatcher
import com.ekomardiatno.emkeys.ime.FnState
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
    private var now = 0L
    private val strokes = mutableListOf<KeyStroke>()
    private val controller = KeyboardController(
        KeyDispatcher(object : StrokeSink {
            override fun finishComposing() = Unit
            override fun send(stroke: KeyStroke) { strokes += stroke }
        }),
        ModifierState { now },
        object : Repeater {
            override fun start(pointerId: Int, onTick: () -> Unit) = Unit
            override fun cancel(pointerId: Int) = Unit
            override fun cancelAll() = Unit
        },
        fn = FnState(nowMillis = { now }),
    )
    private val keys = ThinkPadLayout.create().keys
    private fun key(label: String) = keys.first { it.label == label }
    private fun state(label: String) = KeyAccessibilityState.description(key(label), controller)
    private fun tap(label: String, pointer: Int = 0) {
        controller.press(pointer, key(label))
        controller.release(pointer)
    }
    private fun withFn(label: String) {
        controller.press(10, key("Fn")); tap(label, 11); controller.release(10)
    }

    @Test fun `all modifiers distinguish off from held and taps leave them off`() {
        Mod.entries.forEach { mod ->
            val cap = Key.mod(mod)
            fun state() = KeyAccessibilityState.description(cap, controller)
            assertEquals(R.string.key_off, state())
            controller.press(0, cap)
            assertEquals(R.string.key_held, state())
            controller.release(0)
            assertEquals(R.string.key_off, state())
            now += ModifierState.DOUBLE_TAP_TIMEOUT_MS + 1
            tap(cap.label)
            assertEquals(R.string.key_off, state())
            now += ModifierState.DOUBLE_TAP_TIMEOUT_MS + 1
        }
    }

    @Test fun `modifier fast double tap locks and one tap unlocks`() {
        tap("Ctrl")
        assertEquals(R.string.key_off, state("Ctrl"))
        now += ModifierState.DOUBLE_TAP_TIMEOUT_MS
        tap("Ctrl")
        assertEquals(R.string.key_locked, state("Ctrl"))
        tap("Ctrl")
        assertEquals(R.string.key_off, state("Ctrl"))
    }

    @Test fun `modifier tap has no effect on following base key`() {
        tap("Shift")
        tap("A")
        assertEquals(R.string.key_off, state("Shift"))
    }

    @Test fun `both caps of a shared modifier report held state`() {
        controller.press(0, key("Shift"))
        keys.filter { it.label == "Shift" }.forEach {
            assertEquals(R.string.key_held, KeyAccessibilityState.description(it, controller))
        }
        controller.release(0)
    }

    @Test fun `Fn starts off and a tap does not activate the layer`() {
        assertEquals(R.string.key_off, state("Fn"))
        tap("Fn")
        assertEquals(R.string.key_off, state("Fn"))
        tap("←")
        assertEquals(R.string.key_off, state("Fn"))
    }

    @Test fun `Fn fast double tap locks layer and one tap unlocks`() {
        tap("Fn")
        assertEquals(R.string.key_off, state("Fn"))
        now += FnState.DOUBLE_TAP_TIMEOUT_MS
        tap("Fn")
        assertEquals(R.string.key_fn_tap_locked, state("Fn"))
        tap("Fn")
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
        withFn("Esc")
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
        assertEquals(R.string.key_fn_lock_off, KeyAccessibilityState.description(controller.effectiveKey(key("Esc")), controller))
        withFn("Esc")
        val toggle = key("Esc")
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

    @Test fun `tapping Fn while locked does not invert the layer`() {
        controller.fn.locked = true
        tap("Fn")
        assertTrue(controller.fn.active)
        assertEquals(R.string.key_fn_locked, state("Fn"))
        tap("←")
        assertEquals(R.string.key_fn_locked, state("Fn"))
    }

    @Test fun `FnLock description clears when toggled off`() {
        withFn("Esc")
        withFn("Esc")
        assertEquals(R.string.key_off, state("Fn"))
        assertEquals(R.string.key_fn_lock_off, state("Esc"))
    }

    @Test fun `Caps describes its own toggle not Shift inversion`() {
        assertEquals(R.string.key_off, state("Caps"))
        tap("Caps")
        controller.press(3, key("Shift"))
        assertEquals(R.string.key_on, state("Caps"))
        assertEquals(R.string.key_held, state("Shift"))
        controller.release(3)
        tap("Caps")
        assertEquals(R.string.key_off, state("Caps"))
    }

    @Test fun `ordinary disabled and keyboard-switch caps have no toggle state`() {
        listOf("A", "Win", "Tab", "Space").forEach { assertNull(state(it)) }
        controller.press(10, key("Fn"))
        listOf("Tab", "Space").forEach {
            assertNull(KeyAccessibilityState.description(controller.effectiveKey(key(it)), controller))
        }
    }

    @Test fun `teardown clears transient descriptions but keeps FnLock`() {
        controller.press(1, key("Shift")); tap("Caps"); controller.release(1)
        controller.fn.locked = true
        controller.press(2, key("Fn"))
        controller.cancelAll(sendUps = true)
        assertEquals(R.string.key_off, state("Shift"))
        assertEquals(R.string.key_off, state("Caps"))
        assertEquals(R.string.key_fn_locked, state("Fn"))
        assertEquals(R.string.key_fn_lock_on, state("Esc"))
    }

    @Test fun `reading states neither emits input nor changes held state`() {
        controller.press(1, key("Ctrl")); controller.press(2, key("Fn"))
        repeat(3) { keys.forEach { KeyAccessibilityState.description(it, controller) } }
        assertTrue(strokes.isEmpty())
        assertEquals(R.string.key_held, state("Ctrl"))
        assertEquals(R.string.key_held, state("Fn"))
    }
}
