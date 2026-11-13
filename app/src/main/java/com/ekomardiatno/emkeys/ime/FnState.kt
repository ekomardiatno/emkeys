package com.ekomardiatno.emkeys.ime

/** Fn is a local layer, never an Android modifier. A hold temporarily inverts FnLock. */
class FnState(
    var locked: Boolean = false,
    private val onLockChanged: (Boolean) -> Unit = {},
) {
    private var held = 0
    private var consumed = false
    private var latched = false
    private var version = 0

    val isHeld: Boolean get() = held > 0
    val isLatched: Boolean get() = latched
    val active: Boolean get() = locked xor (isHeld || latched)

    fun press() {
        if (held == 0) consumed = false
        held++
    }

    fun release(cancelled: Boolean = false) {
        if (held == 0) return
        if (cancelled) consumed = true
        held--
        if (held == 0 && !consumed) {
            latched = !latched
            version++
        }
    }

    fun markConsumed() { if (isHeld) consumed = true }

    fun latchSnapshot(): Int? = if (latched) version else null

    fun consumeLatch(snapshot: Int?) {
        if (snapshot != null && snapshot == version) latched = false
    }

    fun toggleLock() {
        locked = !locked
        latched = false
        onLockChanged(locked)
    }

    fun resetTransient() {
        held = 0
        latched = false
        consumed = false
        version++
    }
}
