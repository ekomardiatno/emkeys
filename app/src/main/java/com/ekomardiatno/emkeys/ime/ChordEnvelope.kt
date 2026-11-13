package com.ekomardiatno.emkeys.ime

import com.ekomardiatno.emkeys.model.Key
import com.ekomardiatno.emkeys.model.KeyStroke
import com.ekomardiatno.emkeys.model.Mod

/**
 * The chord envelope of RESEARCH.md §1.2, as a pure function of the active modifiers.
 *
 * Sending the modifier DOWN/UP pairs matters *independently* of `metaState` — Blink and
 * several editors track modifier key events separately from the bits — so the envelope
 * emits both.
 *
 * `metaState` accumulates left-to-right on the way in and sheds its own bit on the way out,
 * so for `Ctrl`+`Shift` the trace is exactly:
 *
 * ```
 * CTRL_LEFT  DOWN  CTRL
 * SHIFT_LEFT DOWN  CTRL|SHIFT
 * <base>     DOWN  CTRL|SHIFT
 * <base>     UP    CTRL|SHIFT
 * SHIFT_LEFT UP    CTRL
 * CTRL_LEFT  UP    0
 * ```
 */
object ChordEnvelope {

    /** The accumulated meta bits for [mods] (rule 1: generic bit alongside the left bit). */
    fun metaStateOf(mods: List<Mod>): Int = mods.fold(0) { acc, mod -> acc or mod.metaBits }

    /** Modifier DOWNs, in [Mod] order, meta accumulating. */
    fun open(mods: List<Mod>): List<KeyStroke> {
        var meta = 0
        return mods.map { mod ->
            meta = meta or mod.metaBits
            KeyStroke(keyCode = mod.keyCode, down = true, metaState = meta, scanCode = mod.scanCode)
        }
    }

    /**
     * The base key's DOWN/UP pair, both carrying the full modifier meta.
     *
     * Rule 5: a modified key is *never* routed through `commitText()` — that is where
     * `keyCode: 229` / `key: "Unidentified"` comes from, because Chromium wraps commits in
     * synthetic events with a null Java `KeyEvent`.
     */
    fun base(key: Key, mods: List<Mod>): List<KeyStroke> {
        val meta = metaStateOf(mods)
        return listOf(
            KeyStroke(key.keyCode, down = true, metaState = meta, scanCode = key.scanCode),
            KeyStroke(key.keyCode, down = false, metaState = meta, scanCode = key.scanCode),
        )
    }

    /** Modifier UPs, reverse order, each shedding its own bit before being sent. */
    fun close(mods: List<Mod>): List<KeyStroke> {
        var meta = metaStateOf(mods)
        return mods.reversed().map { mod ->
            meta = meta and mod.metaBits.inv()
            KeyStroke(keyCode = mod.keyCode, down = false, metaState = meta, scanCode = mod.scanCode)
        }
    }
}
