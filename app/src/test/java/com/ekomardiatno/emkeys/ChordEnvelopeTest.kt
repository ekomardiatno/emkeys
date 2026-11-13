package com.ekomardiatno.emkeys

import android.view.KeyEvent
import com.ekomardiatno.emkeys.ime.KeyDispatcher
import com.ekomardiatno.emkeys.ime.KeyboardController
import com.ekomardiatno.emkeys.ime.ModifierState
import com.ekomardiatno.emkeys.ime.Repeater
import com.ekomardiatno.emkeys.ime.StrokeSink
import com.ekomardiatno.emkeys.model.Key
import com.ekomardiatno.emkeys.model.KeyStroke
import com.ekomardiatno.emkeys.model.Mod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The acceptance contract for the dispatch core. `Ctrl+Shift+P` must produce exactly six
 * strokes, with the right `metaState` *and* the right `scanCode`, whether the modifiers
 * are physically held.
 *
 * Asserted as list equality on [KeyStroke] rather than as a sequence of mock verifications,
 * because the thing that breaks is the whole shape of the trace, not an individual call.
 */
class ChordEnvelopeTest {

    private class RecordingSink : StrokeSink {
        val strokes = mutableListOf<KeyStroke>()
        var finishComposingCalls = 0
        override fun finishComposing() {
            finishComposingCalls++
        }

        override fun send(stroke: KeyStroke) {
            strokes += stroke
        }
    }

    /** A [Repeater] that schedules nothing and can be ticked by hand. */
    private class FakeRepeater : Repeater {
        private val ticks = mutableMapOf<Int, () -> Unit>()
        override fun start(pointerId: Int, onTick: () -> Unit) {
            ticks[pointerId] = onTick
        }

        override fun cancel(pointerId: Int) {
            ticks.remove(pointerId)
        }

        override fun cancelAll() = ticks.clear()

        fun tick(pointerId: Int, times: Int = 1) {
            repeat(times) { ticks[pointerId]?.invoke() }
        }

        fun isRunning(pointerId: Int) = ticks.containsKey(pointerId)
    }

    private val sink = RecordingSink()
    private val repeater = FakeRepeater()
    private val controller = KeyboardController(
        dispatcher = KeyDispatcher(sink),
        modifiers = ModifierState(),
        repeater = repeater,
    )

    private val ctrlBits = Mod.CTRL.metaBits
    private val shiftBits = Mod.SHIFT.metaBits

    private val keyP = Key("P", KeyEvent.KEYCODE_P)
    private val keyLeft = Key("\u2190", KeyEvent.KEYCODE_DPAD_LEFT, repeats = true)
    private val capCtrl = Key.mod(Mod.CTRL)
    private val capShift = Key.mod(Mod.SHIFT)

    /** The six strokes of `Ctrl+Shift+P`, verbatim from the plan's acceptance table. */
    private val ctrlShiftP = listOf(
        KeyStroke(KeyEvent.KEYCODE_CTRL_LEFT, down = true, metaState = ctrlBits, scanCode = 29),
        KeyStroke(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            down = true,
            metaState = ctrlBits or shiftBits,
            scanCode = 42,
        ),
        KeyStroke(KeyEvent.KEYCODE_P, down = true, metaState = ctrlBits or shiftBits, scanCode = 25),
        KeyStroke(
            KeyEvent.KEYCODE_P,
            down = false,
            metaState = ctrlBits or shiftBits,
            scanCode = 25,
        ),
        KeyStroke(KeyEvent.KEYCODE_SHIFT_LEFT, down = false, metaState = ctrlBits, scanCode = 42),
        KeyStroke(KeyEvent.KEYCODE_CTRL_LEFT, down = false, metaState = 0, scanCode = 29),
    )

    private fun tap(pointerId: Int, key: Key) {
        controller.press(pointerId, key)
        controller.release(pointerId)
    }

    @Test
    fun `tapping modifiers does not latch a chord`() {
        tap(0, capCtrl)
        tap(0, capShift)
        tap(0, keyP)

        assertEquals(
            listOf(KeyStroke(KeyEvent.KEYCODE_P, true, 0, 25), KeyStroke(KeyEvent.KEYCODE_P, false, 0, 25)),
            sink.strokes,
        )
    }

    @Test
    fun `held Ctrl plus held Shift plus tapped P is byte-identical`() {
        controller.press(1, capCtrl)
        controller.press(2, capShift)
        tap(3, keyP)
        controller.release(2)
        controller.release(1)

        assertEquals(ctrlShiftP, sink.strokes)
    }

    @Test
    fun `holding the mods in the other order still emits Ctrl before Shift`() {
        controller.press(1, capShift)
        controller.press(2, capCtrl)
        tap(3, keyP)
        controller.release(2)
        controller.release(1)

        assertEquals(ctrlShiftP, sink.strokes)
    }

    @Test
    fun `rule 1 - the generic meta bit always accompanies the left bit`() {
        tap(0, capCtrl)
        tap(0, keyP)

        sink.strokes.filter { it.metaState != 0 }.forEach { stroke ->
            assertTrue(
                "META_CTRL_ON missing in $stroke",
                stroke.metaState and KeyEvent.META_CTRL_ON != 0,
            )
            assertTrue(
                "META_CTRL_LEFT_ON missing in $stroke",
                stroke.metaState and KeyEvent.META_CTRL_LEFT_ON != 0,
            )
        }
    }

    @Test
    fun `rule 2 - no AltGraph is ever produced`() {
        tap(0, Key.mod(Mod.ALT))
        tap(0, keyP)

        sink.strokes.forEach { stroke ->
            assertEquals(
                "ALT_RIGHT leaked into $stroke",
                0,
                stroke.metaState and KeyEvent.META_ALT_RIGHT_ON,
            )
            assertTrue(stroke.metaState and KeyEvent.META_ALT_ON != 0 || stroke.metaState == 0)
        }
    }

    @Test
    fun `rule 3 - no meta bit is ever set`() {
        tap(0, capCtrl)
        tap(0, capShift)
        tap(0, Key.mod(Mod.ALT))
        tap(0, keyP)

        sink.strokes.forEach { stroke ->
            assertEquals(0, stroke.metaState and KeyEvent.META_META_ON)
        }
    }

    @Test
    fun `rule 4 - finishComposingText once per chord, before any stroke`() {
        tap(0, keyP)
        assertEquals(1, sink.finishComposingCalls)

        tap(0, keyP)
        assertEquals(2, sink.finishComposingCalls)
    }

    @Test
    fun `unmodified key is a bare DOWN UP pair`() {
        tap(0, keyP)

        assertEquals(
            listOf(
                KeyStroke(KeyEvent.KEYCODE_P, down = true, metaState = 0, scanCode = 25),
                KeyStroke(KeyEvent.KEYCODE_P, down = false, metaState = 0, scanCode = 25),
            ),
            sink.strokes,
        )
    }

    @Test
    fun `modifier tap has no effect on the following base key`() {
        tap(0, capCtrl)
        tap(0, keyP)
        sink.strokes.clear()

        tap(0, keyP)
        assertEquals(
            "the second P must be unmodified",
            listOf(
                KeyStroke(KeyEvent.KEYCODE_P, down = true, metaState = 0, scanCode = 25),
                KeyStroke(KeyEvent.KEYCODE_P, down = false, metaState = 0, scanCode = 25),
            ),
            sink.strokes,
        )
    }

    @Test
    fun `fast double Ctrl tap locks and a single tap unlocks`() {
        tap(0, capCtrl)
        tap(0, capCtrl) // locked
        tap(0, keyP)
        tap(0, keyP)
        sink.strokes.clear()

        tap(0, capCtrl) // third tap unlocks
        tap(0, keyP)

        assertEquals(
            listOf(
                KeyStroke(KeyEvent.KEYCODE_P, down = true, metaState = 0, scanCode = 25),
                KeyStroke(KeyEvent.KEYCODE_P, down = false, metaState = 0, scanCode = 25),
            ),
            sink.strokes,
        )
    }

    @Test
    fun `rule 7 - each repeat tick is a discrete DOWN UP pair inside one chord`() {
        controller.press(0, keyLeft)
        repeater.tick(0, times = 4)
        controller.release(0)

        // One chord, five discrete pairs, no modifier strokes at all.
        assertEquals(10, sink.strokes.size)
        assertTrue(sink.strokes.all { it.keyCode == KeyEvent.KEYCODE_DPAD_LEFT })
        sink.strokes.chunked(2).forEach { pair ->
            assertEquals(listOf(true, false), pair.map { it.down })
        }
        assertEquals(1, sink.finishComposingCalls)
    }

    @Test
    fun `held Ctrl survives every repeat tick inside the chord`() {
        controller.press(2, capCtrl)
        controller.press(1, keyLeft)
        repeater.tick(1, times = 4)
        controller.release(1)
        controller.release(2)

        val arrows = sink.strokes.filter { it.keyCode == KeyEvent.KEYCODE_DPAD_LEFT }
        assertEquals(10, arrows.size)
        assertTrue(
            "every repeat tick must still carry Ctrl",
            arrows.all { it.metaState == ctrlBits },
        )
        // Ctrl DOWN exactly once, Ctrl UP exactly once -- not once per tick.
        assertEquals(2, sink.strokes.count { it.keyCode == KeyEvent.KEYCODE_CTRL_LEFT })
    }

    @Test
    fun `releasing the key stops the repeat timer`() {
        controller.press(0, keyLeft)
        assertTrue(repeater.isRunning(0))
        controller.release(0)
        assertTrue(!repeater.isRunning(0))
    }

    @Test
    fun `non-repeating keys never start the timer`() {
        controller.press(0, keyP)
        assertTrue(!repeater.isRunning(0))
    }

    @Test
    fun `cancelAll with sendUps closes the chord so no modifier is stranded`() {
        controller.press(1, capCtrl)
        controller.press(2, keyLeft)
        sink.strokes.clear()

        controller.cancelAll(sendUps = true)

        assertEquals(
            listOf(KeyStroke(KeyEvent.KEYCODE_CTRL_LEFT, down = false, metaState = 0, scanCode = 29)),
            sink.strokes,
        )
    }

    @Test
    fun `cancelAll without sendUps emits nothing into the dead connection`() {
        controller.press(1, capCtrl)
        controller.press(2, keyLeft)
        sink.strokes.clear()

        controller.cancelAll(sendUps = false)

        assertEquals(emptyList<KeyStroke>(), sink.strokes)
    }

    @Test
    fun `cancelAll is idempotent`() {
        controller.press(1, capCtrl)
        controller.press(2, keyLeft)
        controller.cancelAll(sendUps = true)
        sink.strokes.clear()

        controller.cancelAll(sendUps = true)
        controller.cancelAll(sendUps = false)

        assertEquals(emptyList<KeyStroke>(), sink.strokes)
    }

    @Test
    fun `criterion 9 - after a cancel mid-hold the next key is unmodified`() {
        controller.press(1, capCtrl)
        controller.cancelAll(sendUps = true) // app switch
        controller.release(1) // the stranded pointer-up finally arrives
        sink.strokes.clear()

        tap(2, keyP)

        assertEquals(
            listOf(
                KeyStroke(KeyEvent.KEYCODE_P, down = true, metaState = 0, scanCode = 25),
                KeyStroke(KeyEvent.KEYCODE_P, down = false, metaState = 0, scanCode = 25),
            ),
            sink.strokes,
        )
    }

    @Test
    fun `a reused pointer id does not strand the previous chord`() {
        controller.press(0, keyLeft)
        sink.strokes.clear()
        // No release: the same id arrives again (shouldn't happen, but is cheap to survive).
        controller.press(0, keyP)

        assertEquals(
            "the stale session must be closed, then P emitted",
            listOf(
                KeyStroke(KeyEvent.KEYCODE_P, down = true, metaState = 0, scanCode = 25),
                KeyStroke(KeyEvent.KEYCODE_P, down = false, metaState = 0, scanCode = 25),
            ),
            sink.strokes,
        )
    }

    @Test
    fun `overlapping base keys share held Ctrl until the final session closes`() {
        controller.press(0, capCtrl)
        controller.press(1, keyLeft)
        controller.press(2, keyP)

        assertEquals(1, sink.strokes.count { it.keyCode == KeyEvent.KEYCODE_CTRL_LEFT && it.down })
        controller.release(1)
        assertEquals(0, sink.strokes.count { it.keyCode == KeyEvent.KEYCODE_CTRL_LEFT && !it.down })
        repeater.tick(1) // cancelled; must not add any strokes
        controller.release(2)
        assertEquals(1, sink.strokes.count { it.keyCode == KeyEvent.KEYCODE_CTRL_LEFT && !it.down })
        assertEquals(0, sink.strokes.last().metaState)
        controller.release(0)
    }

    @Test
    fun `closing a new Shift session preserves the older Ctrl session`() {
        controller.press(0, capCtrl)
        controller.press(1, keyLeft)
        controller.press(2, capShift)
        controller.press(3, keyP)
        sink.strokes.clear()

        controller.release(3)
        assertEquals(listOf(KeyStroke(KeyEvent.KEYCODE_SHIFT_LEFT, false, ctrlBits, 42)), sink.strokes)
        repeater.tick(1)
        assertTrue(sink.strokes.takeLast(2).all { it.metaState == ctrlBits })
        controller.release(1)
        assertEquals(KeyStroke(KeyEvent.KEYCODE_CTRL_LEFT, false, 0, 29), sink.strokes.last())
    }

    @Test
    fun `cancel with overlapping sessions emits only one final UP per modifier`() {
        controller.press(10, capCtrl)
        controller.press(11, capShift)
        controller.press(1, keyLeft)
        controller.press(2, keyP)
        sink.strokes.clear()
        controller.cancelAll(sendUps = true)
        assertEquals(ctrlShiftP.takeLast(2), sink.strokes)
    }

    @Test
    fun `dead connection reset also clears reference counts`() {
        controller.press(10, capCtrl)
        controller.press(1, keyLeft)
        controller.press(2, keyP)
        controller.cancelAll(sendUps = false)
        sink.strokes.clear()
        controller.press(10, capCtrl)
        tap(0, keyP)
        controller.release(10)
        assertEquals(1, sink.strokes.count { it.keyCode == KeyEvent.KEYCODE_CTRL_LEFT && it.down })
        assertEquals(1, sink.strokes.count { it.keyCode == KeyEvent.KEYCODE_CTRL_LEFT && !it.down })
    }

    @Test
    fun `releasing a prior base key does not make a modifier tap sticky`() {
        controller.press(1, keyP)
        tap(0, capCtrl)
        controller.release(1)
        sink.strokes.clear()
        tap(2, keyP)
        assertEquals(0, sink.strokes.first { it.keyCode == KeyEvent.KEYCODE_P }.metaState)
    }

    @Test
    fun `both Shift keys can be held without the first release clearing Shift`() {
        controller.press(1, capShift)
        controller.press(2, capShift.copy(widthU = 2.85f))
        controller.release(1)
        tap(3, keyP)
        assertEquals(shiftBits, sink.strokes.first { it.keyCode == KeyEvent.KEYCODE_P }.metaState)
        controller.release(2)
        assertEquals(ModifierState.Phase.OFF, controller.modifierState().phase(Mod.SHIFT))
    }

    @Test
    fun `slide off an unused modifier does not latch it`() {
        controller.press(0, capCtrl)
        controller.cancel(0)
        assertEquals(ModifierState.Phase.OFF, controller.modifierState().phase(Mod.CTRL))
    }

    @Test
    fun `slide off a base key stops repeat and closes its chord`() {
        controller.press(0, capCtrl)
        controller.press(1, keyLeft)
        controller.cancel(1)
        val afterCancel = sink.strokes.toList()
        repeater.tick(1, 10)
        assertEquals(afterCancel, sink.strokes)
        assertEquals(KeyEvent.KEYCODE_CTRL_LEFT, sink.strokes.last().keyCode)
        assertTrue(!sink.strokes.last().down)
    }
}
