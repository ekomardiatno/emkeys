package com.ekomardiatno.emkeys.model

import android.view.KeyEvent

/**
 * The chunk-1 layout: 12 deliberately fake keys.
 *
 * This is not a draft of the ThinkPad grid — it is a test fixture. Every key is here
 * because an acceptance criterion needs it:
 *
 * - `P` — `Ctrl+Shift+P` is the gate.
 * - `W` — RESEARCH.md §0 claims Chrome does **not** steal `Ctrl+W` before the page.
 *   Testing that costs one keycap.
 * - `Backspace` and the arrows — rule 7 (our own repeat timer) is untestable without a
 *   repeating key whose effect is visible.
 * - `Esc` — §9.3's open question about whether bare Esc survives the WebAPK.
 * - `Ctrl` / `Shift` / `Alt` — the modifier state machine.
 * - `Tab` — a bare non-character key that isn't Esc.
 *
 * ThinkPad geometry, the Fn layer and the other 72 keys are chunk 2. Mixing them in here
 * would mean debugging layout and dispatch at the same time.
 */
object SkeletonLayout {

    val layout: Layout = Layout(
        rows = listOf(
            KeyRow(
                listOf(
                    Key("Esc", KeyEvent.KEYCODE_ESCAPE),
                    Key("Tab", KeyEvent.KEYCODE_TAB),
                    Key("\u2190", KeyEvent.KEYCODE_DPAD_LEFT, repeats = true),
                    Key("\u2193", KeyEvent.KEYCODE_DPAD_DOWN, repeats = true),
                    Key("\u2191", KeyEvent.KEYCODE_DPAD_UP, repeats = true),
                    Key("\u2192", KeyEvent.KEYCODE_DPAD_RIGHT, repeats = true),
                    Key("Bksp", KeyEvent.KEYCODE_DEL, repeats = true),
                ),
            ),
            KeyRow(
                listOf(
                    Key.mod(Mod.CTRL),
                    Key.mod(Mod.SHIFT),
                    Key.mod(Mod.ALT),
                    Key("P", KeyEvent.KEYCODE_P),
                    Key("W", KeyEvent.KEYCODE_W),
                ),
            ),
        ),
    )
}
