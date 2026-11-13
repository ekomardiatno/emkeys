package com.ekomardiatno.emkeys

import com.ekomardiatno.emkeys.ime.ModifierState
import com.ekomardiatno.emkeys.ime.ModifierState.Phase
import com.ekomardiatno.emkeys.model.Mod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierStateTest {

    private val state = ModifierState()

    /** A tap is a press immediately followed by a release, with no base key in between. */
    private fun tap(mod: Mod) {
        state.pressMod(mod)
        state.releaseMod(mod)
    }

    /** A base key press, with the two ordering invariants in their correct places. */
    private fun baseKey() {
        state.markChordConsumed()
        // ... chord emitted and closed here ...
        state.consumeLatched()
    }

    @Test
    fun `fresh state has nothing active`() {
        assertEquals(emptyList<Mod>(), state.activeMods())
        assertFalse(state.isAnythingActive())
        Mod.entries.forEach { assertEquals(Phase.OFF, state.phase(it)) }
    }

    @Test
    fun `tap latches, and the latch clears after exactly one base key`() {
        tap(Mod.CTRL)
        assertEquals(Phase.LATCHED, state.phase(Mod.CTRL))
        assertEquals(listOf(Mod.CTRL), state.activeMods())

        baseKey()
        assertEquals(Phase.OFF, state.phase(Mod.CTRL))
        assertEquals(emptyList<Mod>(), state.activeMods())
    }

    @Test
    fun `double tap locks, third tap unlocks`() {
        tap(Mod.CTRL)
        tap(Mod.CTRL)
        assertEquals(Phase.LOCKED, state.phase(Mod.CTRL))

        baseKey()
        assertEquals("a lock survives a base key", Phase.LOCKED, state.phase(Mod.CTRL))
        baseKey()
        assertEquals(Phase.LOCKED, state.phase(Mod.CTRL))

        tap(Mod.CTRL)
        assertEquals(Phase.OFF, state.phase(Mod.CTRL))
    }

    @Test
    fun `hold is active while down and gone after release`() {
        state.pressMod(Mod.CTRL)
        assertEquals(Phase.HELD, state.phase(Mod.CTRL))
        assertEquals(listOf(Mod.CTRL), state.activeMods())
        assertEquals(listOf(Mod.CTRL), state.heldMods())

        baseKey()
        assertEquals("still held after the base key", Phase.HELD, state.phase(Mod.CTRL))

        state.releaseMod(Mod.CTRL)
        assertEquals(Phase.OFF, state.phase(Mod.CTRL))
        assertFalse(state.isAnythingActive())
    }

    @Test
    fun `invariant 1 - releasing a used hold does not latch it`() {
        state.pressMod(Mod.CTRL)
        baseKey() // markChordConsumed() fires here, while Ctrl is still down
        state.releaseMod(Mod.CTRL)

        assertEquals(
            "a held-and-used Ctrl must not become a latch",
            Phase.OFF,
            state.phase(Mod.CTRL),
        )
    }

    @Test
    fun `a hold released without being used is treated as a tap`() {
        state.pressMod(Mod.CTRL)
        state.releaseMod(Mod.CTRL)
        assertEquals(Phase.LATCHED, state.phase(Mod.CTRL))
    }

    @Test
    fun `invariant 2 - a latch survives many strokes under one pointer, then clears`() {
        tap(Mod.CTRL)
        // One held, repeating arrow: pointer down once, N strokes, pointer up once.
        state.markChordConsumed()
        repeat(8) { /* repeat tick: strokes only, no state transition */ }
        assertEquals(
            "Ctrl must still be active for every repeat tick",
            listOf(Mod.CTRL),
            state.activeMods(),
        )
        state.consumeLatched()
        assertEquals(Phase.OFF, state.phase(Mod.CTRL))
    }

    @Test
    fun `mixed latch and hold both arrive, in Mod order`() {
        tap(Mod.CTRL)
        state.pressMod(Mod.SHIFT)
        assertEquals(listOf(Mod.CTRL, Mod.SHIFT), state.activeMods())
    }

    @Test
    fun `activeMods is always in Mod emission order regardless of press order`() {
        tap(Mod.ALT)
        tap(Mod.SHIFT)
        tap(Mod.CTRL)
        assertEquals(listOf(Mod.CTRL, Mod.SHIFT, Mod.ALT), state.activeMods())
    }

    @Test
    fun `releaseAll is idempotent and clears locks too`() {
        tap(Mod.CTRL)
        tap(Mod.CTRL) // locked
        state.pressMod(Mod.SHIFT)
        assertTrue(state.isAnythingActive())

        state.releaseAll()
        state.releaseAll()

        assertFalse(state.isAnythingActive())
        assertEquals(emptyList<Mod>(), state.activeMods())
        assertEquals(emptyList<Mod>(), state.heldMods())
    }

    @Test
    fun `stranded release after releaseAll does not resurrect a latch`() {
        state.pressMod(Mod.CTRL)
        state.releaseAll()
        // The pointer-up still arrives (or ACTION_CANCEL then ACTION_UP race).
        state.releaseMod(Mod.CTRL)

        assertEquals(Phase.OFF, state.phase(Mod.CTRL))
        assertFalse(state.isAnythingActive())
    }

    @Test
    fun `release of a mod that was never pressed is a no-op`() {
        state.releaseMod(Mod.ALT)
        assertFalse(state.isAnythingActive())
    }

    @Test
    fun `a new latch generation survives the old chord ending`() {
        tap(Mod.CTRL)
        val oldLatch = state.latchedSnapshot()
        tap(Mod.CTRL) // lock
        tap(Mod.CTRL) // off
        tap(Mod.CTRL) // a new latch while the old base pointer is still down
        state.consumeLatched(oldLatch)
        assertEquals(Phase.LATCHED, state.phase(Mod.CTRL))
        state.consumeLatched(state.latchedSnapshot())
        assertEquals(Phase.OFF, state.phase(Mod.CTRL))
    }
}
