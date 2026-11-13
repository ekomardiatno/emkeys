package com.ekomardiatno.emkeys.ime

/** Cancel live input before leaving the IME, including the no-history picker fallback. */
class KeyboardSwitcher(
    private val cancelInput: () -> Unit,
    private val switchToPrevious: () -> Boolean,
    private val showPicker: () -> Unit,
) {
    fun previous() {
        cancelInput()
        if (!switchToPrevious()) showPicker()
    }

    fun picker() {
        cancelInput()
        showPicker()
    }
}
