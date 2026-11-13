package com.ekomardiatno.emkeys

import com.ekomardiatno.emkeys.ime.ModifierState
import com.ekomardiatno.emkeys.ime.ModifierState.Phase
import com.ekomardiatno.emkeys.model.Mod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierStateTest {
    private var now = 0L
    private val state = ModifierState { now }

    private fun tap(mod: Mod) {
        state.pressMod(mod)
        state.releaseMod(mod)
    }

    @Test fun `fresh state has nothing active`() {
        assertEquals(emptyList<Mod>(), state.activeMods())
        assertFalse(state.isAnythingActive())
        Mod.entries.forEach { assertEquals(Phase.OFF, state.phase(it)) }
    }

    @Test fun `single tap does nothing and a slow second tap does not lock`() {
        tap(Mod.CTRL)
        assertEquals(Phase.OFF, state.phase(Mod.CTRL))
        now += ModifierState.DOUBLE_TAP_TIMEOUT_MS + 1
        tap(Mod.CTRL)
        assertEquals(Phase.OFF, state.phase(Mod.CTRL))
        assertTrue(state.activeMods().isEmpty())
    }

    @Test fun `fast double tap locks and one tap releases`() {
        tap(Mod.CTRL)
        now += ModifierState.DOUBLE_TAP_TIMEOUT_MS
        tap(Mod.CTRL)
        assertEquals(Phase.LOCKED, state.phase(Mod.CTRL))
        assertEquals(listOf(Mod.CTRL), state.activeMods())
        tap(Mod.CTRL)
        assertEquals(Phase.OFF, state.phase(Mod.CTRL))
    }

    @Test fun `long hold does not count as either tap`() {
        state.pressMod(Mod.ALT)
        now += ModifierState.DOUBLE_TAP_TIMEOUT_MS + 1
        state.releaseMod(Mod.ALT)
        assertEquals(Phase.OFF, state.phase(Mod.ALT))
        tap(Mod.ALT)
        assertEquals(Phase.OFF, state.phase(Mod.ALT))
    }

    @Test fun `modifier is active only while held`() {
        state.pressMod(Mod.CTRL)
        assertEquals(Phase.HELD, state.phase(Mod.CTRL))
        assertEquals(listOf(Mod.CTRL), state.activeMods())
        state.releaseMod(Mod.CTRL)
        assertEquals(Phase.OFF, state.phase(Mod.CTRL))
        assertFalse(state.isAnythingActive())
    }

    @Test fun `multiple held modifiers are emitted in stable order`() {
        state.pressMod(Mod.ALT)
        state.pressMod(Mod.SHIFT)
        state.pressMod(Mod.CTRL)
        assertEquals(listOf(Mod.CTRL, Mod.SHIFT, Mod.ALT), state.activeMods())
    }

    @Test fun `overlapping same-modifier holds remain active until final release`() {
        state.pressMod(Mod.SHIFT)
        state.pressMod(Mod.SHIFT)
        state.releaseMod(Mod.SHIFT)
        assertEquals(Phase.HELD, state.phase(Mod.SHIFT))
        state.releaseMod(Mod.SHIFT)
        assertEquals(Phase.OFF, state.phase(Mod.SHIFT))
    }

    @Test fun `releaseAll is idempotent and late release is harmless`() {
        state.pressMod(Mod.CTRL)
        state.releaseAll()
        state.releaseAll()
        state.releaseMod(Mod.CTRL)
        assertFalse(state.isAnythingActive())
        assertEquals(emptyList<Mod>(), state.heldMods())
    }

    @Test fun `release of a mod that was never pressed is a no-op`() {
        state.releaseMod(Mod.ALT)
        assertFalse(state.isAnythingActive())
    }
}
