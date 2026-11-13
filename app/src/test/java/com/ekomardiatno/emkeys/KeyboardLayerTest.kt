package com.ekomardiatno.emkeys

import android.view.KeyEvent as K
import com.ekomardiatno.emkeys.ime.FnState
import com.ekomardiatno.emkeys.ime.KeyDispatcher
import com.ekomardiatno.emkeys.ime.KeyboardController
import com.ekomardiatno.emkeys.ime.ModifierState
import com.ekomardiatno.emkeys.ime.Repeater
import com.ekomardiatno.emkeys.ime.StrokeSink
import com.ekomardiatno.emkeys.model.KeyStroke
import com.ekomardiatno.emkeys.model.KeyboardAction
import com.ekomardiatno.emkeys.model.Mod
import com.ekomardiatno.emkeys.model.ThinkPadLayout
import org.junit.Assert.*
import org.junit.Test

class KeyboardLayerTest {
    private var now = 0L
    private val strokes = mutableListOf<KeyStroke>()
    private val actions = mutableListOf<KeyboardAction>()
    private val persistedLocks = mutableListOf<Boolean>()
    private var tick: (() -> Unit)? = null
    private val controller = KeyboardController(
        KeyDispatcher(object : StrokeSink {
            override fun finishComposing() = Unit
            override fun send(stroke: KeyStroke) { strokes += stroke }
        }),
        ModifierState(),
        object : Repeater {
            override fun start(pointerId: Int, onTick: () -> Unit) { tick = onTick }
            override fun cancel(pointerId: Int) { tick = null }
            override fun cancelAll() { tick = null }
        },
        fn = FnState(onLockChanged = { persistedLocks += it }, nowMillis = { now }),
        onAction = { actions += it },
    )
    private val keys = ThinkPadLayout.create().keys
    private fun key(label: String) = keys.first { it.label == label }
    private fun tap(label: String, pointer: Int = 0) {
        controller.press(pointer, key(label))
        controller.release(pointer)
    }
    private fun withFn(label: String) {
        controller.press(90, key("Fn"))
        tap(label, 91)
        controller.release(90)
    }
    private fun baseDown() = strokes.filter { it.down && it.keyCode !in Mod.entries.map { m -> m.keyCode } }

    @Test fun `tapping Fn has no effect and holding Fn maps alternate key`() {
        tap("Fn")
        tap("←")
        withFn("←")
        tap("←")
        assertEquals(listOf(K.KEYCODE_DPAD_LEFT, K.KEYCODE_MOVE_HOME, K.KEYCODE_DPAD_LEFT), baseDown().map { it.keyCode })
        assertFalse(controller.fn.active)
    }

    @Test fun `held Fn plus Ctrl maps a repeating arrow and keeps its snapshot`() {
        controller.press(1, key("Fn"))
        controller.press(2, key("Ctrl"))
        controller.press(3, key("←"))
        controller.release(1)
        tick?.invoke()
        tick?.invoke()
        controller.release(3)
        controller.release(2)
        assertEquals(3, baseDown().size)
        assertTrue(baseDown().all { it.keyCode == K.KEYCODE_MOVE_HOME && it.metaState == Mod.CTRL.metaBits })
        assertFalse(controller.fn.active)
    }

    @Test fun `Fn Esc toggles persistent lock but bare Esc always escapes`() {
        controller.press(1, key("Fn"))
        tap("Esc", 2)
        controller.release(1)
        assertTrue(controller.fn.locked)
        tap("Esc")
        assertEquals(K.KEYCODE_ESCAPE, baseDown().single().keyCode)
        tap("←")
        assertEquals(K.KEYCODE_MOVE_HOME, baseDown().last().keyCode)
        controller.cancelAll(sendUps = true)
        assertTrue(controller.fn.locked)
        controller.press(1, key("Fn"))
        tap("Esc", 2)
        controller.release(1)
        assertFalse(controller.fn.locked)
        assertEquals(listOf(true, false), persistedLocks)
    }

    @Test fun `Fn temporarily inverts the locked layer`() {
        controller.fn.locked = true
        controller.press(1, key("Fn"))
        tap("←", 2)
        controller.release(1)
        tap("←")
        assertEquals(listOf(K.KEYCODE_DPAD_LEFT, K.KEYCODE_MOVE_HOME), baseDown().map { it.keyCode })
    }

    @Test fun `Fn mappings use Android keycodes and never emit Fn`() {
        listOf("B", "P", "S", "K").forEach(::withFn)
        assertEquals(listOf(K.KEYCODE_BREAK, K.KEYCODE_BREAK, K.KEYCODE_SYSRQ, K.KEYCODE_SCROLL_LOCK),
            baseDown().map { it.keyCode })
        assertTrue(baseDown().all { it.scanCode != 0 && it.metaState == 0 })
    }

    @Test fun `F keys remain primary with FnLock`() {
        controller.fn.locked = true
        tap("F12")
        assertEquals(K.KEYCODE_F12, baseDown().single().keyCode)
    }

    @Test fun `Fn Tab and Space invoke UI actions without typing`() {
        withFn("Tab")
        withFn("Space")
        assertEquals(listOf(KeyboardAction.SWITCH_KEYBOARD, KeyboardAction.PICK_KEYBOARD), actions)
        assertTrue(strokes.isEmpty())
        assertFalse(controller.fn.active)
    }

    @Test fun `cancelled action does not fire`() {
        tap("Fn")
        controller.press(0, key("Tab"))
        assertTrue(actions.isEmpty())
        controller.cancel(0)
        assertTrue(actions.isEmpty())
    }

    @Test fun `cancelled Fn hold becomes inactive`() {
        controller.press(0, key("Fn"))
        controller.cancel(0)
        assertFalse(controller.fn.active)
    }

    @Test fun `tapping Fn alone does not activate alternate layer`() {
        tap("Fn")
        tap("←")
        assertEquals(K.KEYCODE_DPAD_LEFT, baseDown().last().keyCode)
    }

    @Test fun `fast double tap Fn locks layer and one tap unlocks`() {
        tap("Fn")
        now += FnState.DOUBLE_TAP_TIMEOUT_MS
        tap("Fn")
        assertTrue(controller.fn.isTapLocked)
        assertTrue(controller.fn.active)
        tap("Fn")
        assertFalse(controller.fn.isTapLocked)
        assertFalse(controller.fn.active)
    }

    @Test fun `Caps affects letters but held Shift reverses Caps`() {
        tap("Caps")
        tap("A")
        tap("1")
        controller.press(10, key("Shift")); tap("A", 11); controller.release(10)
        assertEquals(listOf(Mod.SHIFT.metaBits, 0, 0), baseDown().map { it.metaState })
        assertTrue(controller.capsLock)
        tap("Caps")
        assertFalse(controller.capsLock)
    }

    @Test fun `Caps remains active while a Ctrl tap has no effect`() {
        tap("Caps"); tap("Ctrl"); tap("P")
        assertEquals(Mod.SHIFT.metaBits, baseDown().single().metaState)
        controller.cancelAll(sendUps = true)
        assertFalse(controller.capsLock)
    }

    @Test fun `Win does not send an unsupported key`() {
        tap("Win")
        assertTrue(strokes.isEmpty())
    }

    @Test fun `Shift symbols retain their physical keycode and scancode`() {
        controller.press(10, key("Shift")); tap("[", 11); controller.release(10)
        assertEquals(KeyStroke(K.KEYCODE_LEFT_BRACKET, true, Mod.SHIFT.metaBits, 26), baseDown().single())
    }

    @Test fun `reused action pointer is cancelled rather than clicked`() {
        controller.press(0, key("Caps"))
        controller.press(0, key("A"))
        assertFalse(controller.capsLock)
    }
}
