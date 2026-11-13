package com.ekomardiatno.emkeys.ime

import android.content.res.Configuration

/** System configuration's report of an attached, currently available hardware keyboard. */
internal object PhysicalKeyboard {
    fun isConnected(configuration: Configuration): Boolean = isConnected(
        keyboard = configuration.keyboard,
        hardKeyboardHidden = configuration.hardKeyboardHidden,
    )

    internal fun isConnected(keyboard: Int, hardKeyboardHidden: Int): Boolean =
        keyboard != Configuration.KEYBOARD_NOKEYS &&
            hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
}
