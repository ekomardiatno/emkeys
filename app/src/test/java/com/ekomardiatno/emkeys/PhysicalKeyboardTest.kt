package com.ekomardiatno.emkeys

import android.content.res.Configuration
import com.ekomardiatno.emkeys.ime.PhysicalKeyboard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalKeyboardTest {
    @Test fun `no hardware keyboard never suppresses the IME`() {
        assertFalse(PhysicalKeyboard.isConnected(
            Configuration.KEYBOARD_NOKEYS,
            Configuration.HARDKEYBOARDHIDDEN_NO,
        ))
    }

    @Test fun `available physical keyboard suppresses the IME`() {
        assertTrue(PhysicalKeyboard.isConnected(
            Configuration.KEYBOARD_QWERTY,
            Configuration.HARDKEYBOARDHIDDEN_NO,
        ))
    }

    @Test fun `keyboard reported hidden does not suppress the IME`() {
        assertFalse(PhysicalKeyboard.isConnected(
            Configuration.KEYBOARD_QWERTY,
            Configuration.HARDKEYBOARDHIDDEN_YES,
        ))
    }

    @Test fun `keyboard presence remains authoritative when hidden state is undefined`() {
        assertTrue(PhysicalKeyboard.isConnected(
            Configuration.KEYBOARD_QWERTY,
            Configuration.HARDKEYBOARDHIDDEN_UNDEFINED,
        ))
    }
}
