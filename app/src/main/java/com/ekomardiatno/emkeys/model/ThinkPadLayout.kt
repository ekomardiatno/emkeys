package com.ekomardiatno.emkeys.model

import android.view.KeyEvent as K

/** Modern ANSI ThinkPad shell, F-keys primary. All main rows are exactly 15u. */
object ThinkPadLayout {
    fun create(swapFnCtrl: Boolean = false): Layout {
        val fn = Key("Fn", role = KeyRole.Fn, widthU = 1.15f)
        val ctrl = Key.mod(Mod.CTRL, 1.15f)
        return Layout(listOf(
            KeyRow(buildList {
                add(Key("Esc", K.KEYCODE_ESCAPE, widthU = 1.1625f,
                    fnKey = Key("FnLk", role = KeyRole.Action(KeyboardAction.FN_LOCK))))
                repeat(12) { i ->
                    add(Key("F${i + 1}", K.KEYCODE_F1 + i, widthU = 0.825f,
                        gapBeforeU = if (i % 4 == 0) 0.1f else 0f))
                }
                add(Key("Home", K.KEYCODE_MOVE_HOME, widthU = 0.825f, repeats = true))
                add(Key("End", K.KEYCODE_MOVE_END, widthU = 0.825f, repeats = true))
                add(Key("Ins", K.KEYCODE_INSERT, widthU = 0.825f, description = "Insert"))
                add(Key("Del", K.KEYCODE_FORWARD_DEL, widthU = 1.1625f,
                    repeats = true, description = "Delete"))
            }, heightU = 0.6f, gapAfterU = 0.14f),
            KeyRow(listOf(
                symbol("`", "~", K.KEYCODE_GRAVE),
                symbol("1", "!", K.KEYCODE_1), symbol("2", "@", K.KEYCODE_2),
                symbol("3", "#", K.KEYCODE_3), symbol("4", "$", K.KEYCODE_4),
                symbol("5", "%", K.KEYCODE_5), symbol("6", "^", K.KEYCODE_6),
                symbol("7", "&", K.KEYCODE_7), symbol("8", "*", K.KEYCODE_8),
                symbol("9", "(", K.KEYCODE_9), symbol("0", ")", K.KEYCODE_0),
                symbol("-", "_", K.KEYCODE_MINUS), symbol("=", "+", K.KEYCODE_EQUALS),
                Key("Bksp", K.KEYCODE_DEL, widthU = 2f, repeats = true, description = "Backspace"),
            )),
            KeyRow(listOf(
                Key("Tab", K.KEYCODE_TAB, widthU = 1.45f,
                    fnKey = Key("Switch", role = KeyRole.Action(KeyboardAction.SWITCH_KEYBOARD))),
                letter("Q", K.KEYCODE_Q), letter("W", K.KEYCODE_W), letter("E", K.KEYCODE_E),
                letter("R", K.KEYCODE_R), letter("T", K.KEYCODE_T), letter("Y", K.KEYCODE_Y),
                letter("U", K.KEYCODE_U), letter("I", K.KEYCODE_I), letter("O", K.KEYCODE_O),
                letter("P", K.KEYCODE_P, Key("Pause", K.KEYCODE_BREAK)),
                symbol("[", "{", K.KEYCODE_LEFT_BRACKET),
                symbol("]", "}", K.KEYCODE_RIGHT_BRACKET),
                symbol("\\", "|", K.KEYCODE_BACKSLASH).copy(widthU = 1.55f),
            )),
            KeyRow(listOf(
                Key("Caps", role = KeyRole.Action(KeyboardAction.CAPS_LOCK), widthU = 1.65f,
                    description = "Caps Lock"),
                letter("A", K.KEYCODE_A), letter("S", K.KEYCODE_S, Key("SysRq", K.KEYCODE_SYSRQ)),
                letter("D", K.KEYCODE_D), letter("F", K.KEYCODE_F), letter("G", K.KEYCODE_G),
                letter("H", K.KEYCODE_H), letter("J", K.KEYCODE_J),
                letter("K", K.KEYCODE_K, Key("ScrLk", K.KEYCODE_SCROLL_LOCK)),
                letter("L", K.KEYCODE_L), symbol(";", ":", K.KEYCODE_SEMICOLON),
                symbol("'", "\"", K.KEYCODE_APOSTROPHE),
                Key("Enter", K.KEYCODE_ENTER, widthU = 2.35f, repeats = true),
            )),
            KeyRow(listOf(
                Key.mod(Mod.SHIFT, 2.15f), letter("Z", K.KEYCODE_Z), letter("X", K.KEYCODE_X),
                letter("C", K.KEYCODE_C), letter("V", K.KEYCODE_V),
                letter("B", K.KEYCODE_B, Key("Break", K.KEYCODE_BREAK)),
                letter("N", K.KEYCODE_N), letter("M", K.KEYCODE_M),
                symbol(",", "<", K.KEYCODE_COMMA), symbol(".", ">", K.KEYCODE_PERIOD),
                symbol("/", "?", K.KEYCODE_SLASH), Key.mod(Mod.SHIFT, 2.85f),
            )),
            KeyRow(listOf(
                if (swapFnCtrl) ctrl else fn,
                if (swapFnCtrl) fn else ctrl,
                Key("Win", role = KeyRole.Disabled, description = "Win, unavailable in Chrome"),
                Key.mod(Mod.ALT),
                Key("Space", K.KEYCODE_SPACE, widthU = 5f, repeats = true,
                    fnKey = Key("Keyboards", role = KeyRole.Action(KeyboardAction.PICK_KEYBOARD))),
                Key.mod(Mod.ALT), Key("PrtSc", K.KEYCODE_SYSRQ, description = "Print Screen"),
                Key.mod(Mod.CTRL, 0.85f),
                Key("PgUp", K.KEYCODE_PAGE_UP, widthU = 0.95f, heightU = 0.6f, repeats = true),
                Key("↑", K.KEYCODE_DPAD_UP, widthU = 0.95f, heightU = 0.6f, repeats = true),
                Key("PgDn", K.KEYCODE_PAGE_DOWN, widthU = 0.95f, heightU = 0.6f, repeats = true),
            ).mapIndexed { i, key ->
                if (i < 8) key.copy(heightU = 0.9f, offsetYU = 0.3f) else key
            }, heightU = 1.2f),
            KeyRow(listOf(
                Key("←", K.KEYCODE_DPAD_LEFT, widthU = 0.95f, repeats = true,
                    fnKey = Key("Home", K.KEYCODE_MOVE_HOME, repeats = true)),
                Key("↓", K.KEYCODE_DPAD_DOWN, widthU = 0.95f, repeats = true),
                Key("→", K.KEYCODE_DPAD_RIGHT, widthU = 0.95f, repeats = true,
                    fnKey = Key("End", K.KEYCODE_MOVE_END, repeats = true)),
            ), heightU = 0.6f, offsetXU = 12.15f, offsetYU = -0.6f),
        ))
    }

    private fun symbol(label: String, shifted: String, code: Int) =
        Key(label, code, repeats = true, shiftedLabel = shifted)

    private fun letter(label: String, code: Int, fnKey: Key? = null) =
        Key(label, code, repeats = true, fnKey = fnKey)
}
