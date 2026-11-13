package com.ekomardiatno.emkeys

import com.ekomardiatno.emkeys.ime.KeyboardSwitcher
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardSwitcherTest {
    private val trace = mutableListOf<String>()

    private fun switcher(hasPrevious: Boolean) = KeyboardSwitcher(
        cancelInput = { trace += "cancel" },
        switchToPrevious = { trace += "previous"; hasPrevious },
        showPicker = { trace += "picker" },
    )

    @Test fun `previous cancels live input before switching without opening picker`() {
        switcher(hasPrevious = true).previous()
        assertEquals(listOf("cancel", "previous"), trace)
    }

    @Test fun `missing previous keyboard falls back to picker after one cancellation`() {
        switcher(hasPrevious = false).previous()
        assertEquals(listOf("cancel", "previous", "picker"), trace)
    }

    @Test fun `explicit picker cancels live input without attempting previous`() {
        switcher(hasPrevious = true).picker()
        assertEquals(listOf("cancel", "picker"), trace)
    }

    @Test fun `each switching request cancels its own live input`() {
        val switcher = switcher(hasPrevious = false)
        switcher.previous()
        switcher.picker()
        assertEquals(listOf("cancel", "previous", "picker", "cancel", "picker"), trace)
    }
}
