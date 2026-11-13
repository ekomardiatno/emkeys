package com.ekomardiatno.emkeys.ime

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.ekomardiatno.emkeys.model.KeyStroke

/**
 * The **only** place in emkeys that constructs an `android.view.KeyEvent`.
 *
 * Keeping it to one function is what lets the whole dispatch core be unit-tested on the
 * JVM: everything upstream deals in [KeyStroke], and `KeyEvent`'s constructor throws
 * `RuntimeException("Stub!")` under the unit-test `android.jar`.
 *
 * The 10-arg constructor (RESEARCH.md §1.2) is mandatory — the short forms cannot carry a
 * scancode, a device id or the flags.
 */
object KeyEventFactory {

    fun KeyStroke.toEvent(now: Long): KeyEvent = KeyEvent(
        /* downTime  */ now,
        /* eventTime */ now,
        /* action    */ if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
        /* code      */ keyCode,
        /* repeat    */ 0, // rule 7: never non-zero -- repeatCount is not forwarded to Blink
        /* metaState */ metaState,
        /* deviceId  */ KeyCharacterMap.VIRTUAL_KEYBOARD,
        /* scanCode  */ scanCode, // rule 6: raw evdev, no XKB +8 offset
        /* flags     */ KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
        /* source    */ InputDevice.SOURCE_KEYBOARD,
    )
}
