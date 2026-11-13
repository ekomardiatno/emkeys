package com.ekomardiatno.emkeys.model

import android.view.KeyEvent

/**
 * The three modifiers emkeys can actually deliver, with RESEARCH.md §1.3 rules 1-3
 * encoded once and only here.
 *
 * - **Rule 1** — [metaBits] always carries the generic `META_*_ON` bit *alongside* the
 *   `_LEFT_ON` bit. Chromium's `ImeAdapterImpl.getModifiers()` tests only the generic one,
 *   so `META_CTRL_LEFT_ON` on its own is read by nobody.
 * - **Rule 2** — left variants only. `META_ALT_RIGHT_ON` maps to `ALT_GR_KEY`, surfacing
 *   as `getModifierState('AltGraph')` rather than `altKey`, which VS Code reads separately.
 * - **Rule 3** — no Super/Meta. `getModifiers()` has no `META_KEY` assignment at all, so
 *   `KeyboardEvent.metaKey` is unreachable from an IME. An unreachable modifier is not
 *   modelled.
 *
 * [order] is the deterministic order in which modifier DOWNs are emitted (and the reverse
 * of which the UPs are emitted), so a chord is byte-reproducible across taps and holds.
 */
enum class Mod(
    val label: String,
    val keyCode: Int,
    val scanCode: Int,
    val metaBits: Int,
) {
    CTRL(
        label = "Ctrl",
        keyCode = KeyEvent.KEYCODE_CTRL_LEFT,
        scanCode = 29,
        metaBits = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON,
    ),
    SHIFT(
        label = "Shift",
        keyCode = KeyEvent.KEYCODE_SHIFT_LEFT,
        scanCode = 42,
        metaBits = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON,
    ),
    ALT(
        label = "Alt",
        keyCode = KeyEvent.KEYCODE_ALT_LEFT,
        scanCode = 56,
        metaBits = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON,
    ),
    ;

    /** Emission order for modifier DOWNs; UPs run this in reverse. */
    val order: Int get() = ordinal

    companion object {
        /** All mods, in emission order. */
        val inOrder: List<Mod> = entries.sortedBy { it.order }
    }
}
