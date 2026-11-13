package com.ekomardiatno.emkeys.ime

import com.ekomardiatno.emkeys.model.Mod

/**
 * Which modifiers are currently in force, and why.
 *
 * Two orthogonal things are tracked per modifier: a **sticky** level advanced by taps
 * (`OFF -> LATCHED -> LOCKED -> OFF`), and whether a pointer is physically **held** on the
 * cap. A modifier is active if either is true, which is what makes tap-then-tap and
 * hold-three-fingers produce byte-identical chords.
 *
 * Three ordering invariants, each a real bug if inverted:
 *
 * 1. [markChordConsumed] fires on base-key **pointer down**. Without it, lifting a held
 *    Ctrl after using it reads as a tap and *latches* it, so the next keystroke silently
 *    carries Ctrl.
 * 2. [consumeLatched] fires on base-key **pointer up** — not per emitted stroke. A held,
 *    repeating arrow emits many strokes under one latch, which is what makes `Ctrl+←`
 *    word-jump repeat work.
 * 3. The caller must close the chord (emit the modifier UPs) **before** calling
 *    [consumeLatched]; the UPs need the modifiers still listed as active.
 *
 * The tap cycle is deliberately untimed — no double-tap window. A lock is "tap twice",
 * however slowly, and a third tap clears it. Deterministic, and it matches how sticky keys
 * behave elsewhere.
 *
 * Single-threaded by construction: every caller is `onTouchEvent` or a main-looper
 * `Handler` tick, so there is no synchronisation here on purpose.
 */
class ModifierState {

    /** What a cap should render as. */
    enum class Phase { OFF, LATCHED, HELD, LOCKED }

    private enum class Sticky { OFF, LATCHED, LOCKED }

    private val sticky = mutableMapOf<Mod, Sticky>()
    private val held = mutableMapOf<Mod, Int>()
    private val latchVersions = mutableMapOf<Mod, Int>()
    private var version = 0

    /** Mods that were held while a base key went down, so their release is not a tap. */
    private val consumedWhileHeld = mutableSetOf<Mod>()

    /** A pointer went down on [mod]'s cap. */
    fun pressMod(mod: Mod) {
        if (mod !in held) consumedWhileHeld -= mod
        held[mod] = (held[mod] ?: 0) + 1
    }

    /**
     * A pointer came up off [mod]'s cap.
     *
     * If the hold was used in a chord it is just a release. Otherwise it was a tap, and the
     * sticky level advances. A release for a mod that is not held is ignored — that happens
     * after [releaseAll] has already torn the state down.
     */
    fun releaseMod(mod: Mod, cancelled: Boolean = false) {
        val count = held[mod] ?: return
        if (cancelled) consumedWhileHeld += mod
        if (count > 1) {
            held[mod] = count - 1
            return
        }
        held.remove(mod)
        if (consumedWhileHeld.remove(mod)) return
        sticky[mod] = when (sticky[mod] ?: Sticky.OFF) {
            Sticky.OFF -> Sticky.LATCHED
            Sticky.LATCHED -> Sticky.LOCKED
            Sticky.LOCKED -> Sticky.OFF
        }
        latchVersions[mod] = ++version
    }

    /** Invariant 1 — call on base-key pointer **down**. */
    fun markChordConsumed() {
        consumedWhileHeld += held.keys
    }

    /** Capture ownership on base-key DOWN; a later latch must not be consumed by this key. */
    fun latchedSnapshot(): Map<Mod, Int> = latchVersions.filterKeys { sticky(it) == Sticky.LATCHED }

    /** Invariant 2 — call on base-key pointer **up**, after the chord has been closed. */
    fun consumeLatched(snapshot: Map<Mod, Int> = latchedSnapshot()) {
        sticky.entries.forEach { entry ->
            if (entry.value == Sticky.LATCHED && snapshot[entry.key] == latchVersions[entry.key]) {
                entry.setValue(Sticky.OFF)
            }
        }
    }

    /** The modifiers a chord should carry, in [Mod] emission order. */
    fun activeMods(): List<Mod> = Mod.inOrder.filter(::isActive)

    fun isActive(mod: Mod): Boolean = mod in held || sticky(mod) != Sticky.OFF

    /** The mods with a pointer currently down on them. */
    fun heldMods(): List<Mod> = Mod.inOrder.filter { it in held }

    fun phase(mod: Mod): Phase = when {
        mod in held -> Phase.HELD
        sticky(mod) == Sticky.LATCHED -> Phase.LATCHED
        sticky(mod) == Sticky.LOCKED -> Phase.LOCKED
        else -> Phase.OFF
    }

    fun isAnythingActive(): Boolean = held.isNotEmpty() || sticky.values.any { it != Sticky.OFF }

    /**
     * Forget everything. Idempotent.
     *
     * Part of the stranded-modifier defence: the worst failure mode in emkeys is a modifier
     * DOWN with no matching UP, which leaves every later keystroke in Chrome carrying
     * `ctrlKey: true` and reads as a Chromium bug rather than ours. Locks are cleared too —
     * coming back to a fresh editor with a surprise lock still on is the same bug wearing a
     * different hat.
     */
    fun releaseAll() {
        held.clear()
        consumedWhileHeld.clear()
        sticky.clear()
        latchVersions.clear()
    }

    private fun sticky(mod: Mod): Sticky = sticky[mod] ?: Sticky.OFF
}
