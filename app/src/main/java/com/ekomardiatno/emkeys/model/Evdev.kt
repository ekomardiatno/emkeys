package com.ekomardiatno.emkeys.model

import android.view.KeyEvent

/**
 * Android keycode -> Linux evdev scancode (RESEARCH.md §1.3 rule 6).
 *
 * Chromium on Android reads `KeyEvent.getScanCode()` with **no XKB +8 offset** and feeds
 * it straight into `event.code` / `event.location`. An empty scancode is the historic
 * soft-keyboard tell.
 *
 * **evdev codes are positional, not alphabetical.** The keyboard matrix is walked row by
 * row: `Q=16 W=17 E=18 ...`, `A=30 S=31 D=32 ...`, `Z=44 X=45 C=46 ...`, and the function
 * row breaks at `F10=68` because `NUMLOCK`/`SCROLLLOCK`/the keypad sit between it and
 * `F11=87`. Any formula over the alphabet yields `P=45` (`KeyX`) and `W=52` (`KeyComma`),
 * and it fails **silently** because VS Code dispatches on `keyCode`, not `code`. Hence a
 * literal table with [EvdevTableTest] over it, never arithmetic.
 *
 * Values verified against the NDK's `linux/input-event-codes.h`.
 */
object Evdev {

    /** No scancode known. Sent as-is; Chromium then reports an empty `event.code`. */
    const val UNKNOWN: Int = 0

    private val table: Map<Int, Int> = mapOf(
        // --- row 0: function row -------------------------------------------------
        KeyEvent.KEYCODE_ESCAPE to 1,
        KeyEvent.KEYCODE_F1 to 59,
        KeyEvent.KEYCODE_F2 to 60,
        KeyEvent.KEYCODE_F3 to 61,
        KeyEvent.KEYCODE_F4 to 62,
        KeyEvent.KEYCODE_F5 to 63,
        KeyEvent.KEYCODE_F6 to 64,
        KeyEvent.KEYCODE_F7 to 65,
        KeyEvent.KEYCODE_F8 to 66,
        KeyEvent.KEYCODE_F9 to 67,
        KeyEvent.KEYCODE_F10 to 68,
        // NOT contiguous with F10: NumLock/ScrollLock/keypad occupy 69..83.
        KeyEvent.KEYCODE_F11 to 87,
        KeyEvent.KEYCODE_F12 to 88,

        // --- row 1: digits -------------------------------------------------------
        KeyEvent.KEYCODE_1 to 2,
        KeyEvent.KEYCODE_2 to 3,
        KeyEvent.KEYCODE_3 to 4,
        KeyEvent.KEYCODE_4 to 5,
        KeyEvent.KEYCODE_5 to 6,
        KeyEvent.KEYCODE_6 to 7,
        KeyEvent.KEYCODE_7 to 8,
        KeyEvent.KEYCODE_8 to 9,
        KeyEvent.KEYCODE_9 to 10,
        KeyEvent.KEYCODE_0 to 11,
        KeyEvent.KEYCODE_MINUS to 12,
        KeyEvent.KEYCODE_EQUALS to 13,
        KeyEvent.KEYCODE_DEL to 14, // Backspace
        KeyEvent.KEYCODE_GRAVE to 41,

        // --- row 2: QWERTY -------------------------------------------------------
        KeyEvent.KEYCODE_TAB to 15,
        KeyEvent.KEYCODE_Q to 16,
        KeyEvent.KEYCODE_W to 17,
        KeyEvent.KEYCODE_E to 18,
        KeyEvent.KEYCODE_R to 19,
        KeyEvent.KEYCODE_T to 20,
        KeyEvent.KEYCODE_Y to 21,
        KeyEvent.KEYCODE_U to 22,
        KeyEvent.KEYCODE_I to 23,
        KeyEvent.KEYCODE_O to 24,
        KeyEvent.KEYCODE_P to 25,
        KeyEvent.KEYCODE_LEFT_BRACKET to 26,
        KeyEvent.KEYCODE_RIGHT_BRACKET to 27,
        KeyEvent.KEYCODE_BACKSLASH to 43,

        // --- row 3: ASDF ---------------------------------------------------------
        KeyEvent.KEYCODE_CAPS_LOCK to 58,
        KeyEvent.KEYCODE_A to 30,
        KeyEvent.KEYCODE_S to 31,
        KeyEvent.KEYCODE_D to 32,
        KeyEvent.KEYCODE_F to 33,
        KeyEvent.KEYCODE_G to 34,
        KeyEvent.KEYCODE_H to 35,
        KeyEvent.KEYCODE_J to 36,
        KeyEvent.KEYCODE_K to 37,
        KeyEvent.KEYCODE_L to 38,
        KeyEvent.KEYCODE_SEMICOLON to 39,
        KeyEvent.KEYCODE_APOSTROPHE to 40,
        KeyEvent.KEYCODE_ENTER to 28,

        // --- row 4: ZXCV ---------------------------------------------------------
        KeyEvent.KEYCODE_SHIFT_LEFT to 42,
        KeyEvent.KEYCODE_Z to 44,
        KeyEvent.KEYCODE_X to 45,
        KeyEvent.KEYCODE_C to 46,
        KeyEvent.KEYCODE_V to 47,
        KeyEvent.KEYCODE_B to 48,
        KeyEvent.KEYCODE_N to 49,
        KeyEvent.KEYCODE_M to 50,
        KeyEvent.KEYCODE_COMMA to 51,
        KeyEvent.KEYCODE_PERIOD to 52,
        KeyEvent.KEYCODE_SLASH to 53,
        KeyEvent.KEYCODE_SHIFT_RIGHT to 54,

        // --- row 5: modifiers + space -------------------------------------------
        KeyEvent.KEYCODE_CTRL_LEFT to 29,
        KeyEvent.KEYCODE_CTRL_RIGHT to 97,
        KeyEvent.KEYCODE_ALT_LEFT to 56,
        KeyEvent.KEYCODE_ALT_RIGHT to 100,
        KeyEvent.KEYCODE_META_LEFT to 125,
        KeyEvent.KEYCODE_META_RIGHT to 126,
        KeyEvent.KEYCODE_SPACE to 57,

        // --- navigation ----------------------------------------------------------
        KeyEvent.KEYCODE_MOVE_HOME to 102,
        KeyEvent.KEYCODE_DPAD_UP to 103,
        KeyEvent.KEYCODE_PAGE_UP to 104,
        KeyEvent.KEYCODE_DPAD_LEFT to 105,
        KeyEvent.KEYCODE_DPAD_RIGHT to 106,
        KeyEvent.KEYCODE_MOVE_END to 107,
        KeyEvent.KEYCODE_DPAD_DOWN to 108,
        KeyEvent.KEYCODE_PAGE_DOWN to 109,
        KeyEvent.KEYCODE_INSERT to 110,
        KeyEvent.KEYCODE_FORWARD_DEL to 111, // Delete
        KeyEvent.KEYCODE_SYSRQ to 99, // PrintScreen / SysRq
        KeyEvent.KEYCODE_SCROLL_LOCK to 70,
        KeyEvent.KEYCODE_BREAK to 119, // Android combines Pause and Break
    )

    /** The evdev scancode for [keyCode], or [UNKNOWN] if we have no mapping. */
    operator fun get(keyCode: Int): Int = table[keyCode] ?: UNKNOWN
}
