package com.ekomardiatno.emkeys.ime

/** Fn is a hold-only local layer, never an Android modifier. A hold temporarily inverts FnLock. */
class FnState(
    var locked: Boolean = false,
    private val onLockChanged: (Boolean) -> Unit = {},
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var held = 0
    private var pressedAt = 0L
    private var consumed = false
    private var lastTap: Long? = null
    private var tapLocked = false
    val isHeld: Boolean get() = held > 0
    val isTapLocked: Boolean get() = tapLocked
    val active: Boolean get() = locked xor (isHeld || tapLocked)

    fun press() {
        if (held == 0) {
            consumed = false
            pressedAt = nowMillis()
        }
        held++
    }

    fun release(cancelled: Boolean = false) {
        if (held == 0) return
        if (cancelled) consumed = true
        held--
        if (held != 0) return
        if (consumed || cancelled) {
            lastTap = null
            return
        }
        val now = nowMillis()
        if (now - pressedAt > DOUBLE_TAP_TIMEOUT_MS) {
            lastTap = null
            return
        }
        if (tapLocked) {
            tapLocked = false
            lastTap = null
        } else {
            val previous = lastTap
            if (previous != null && now - previous in 0..DOUBLE_TAP_TIMEOUT_MS) {
                tapLocked = true
                lastTap = null
            } else {
                lastTap = now
            }
        }
    }

    fun markConsumed() { if (isHeld) consumed = true }

    fun toggleLock() {
        locked = !locked
        tapLocked = false
        lastTap = null
        onLockChanged(locked)
    }

    fun resetTransient() {
        held = 0
        pressedAt = 0L
        consumed = false
        tapLocked = false
        lastTap = null
    }

    companion object {
        const val DOUBLE_TAP_TIMEOUT_MS = 300L
    }
}
