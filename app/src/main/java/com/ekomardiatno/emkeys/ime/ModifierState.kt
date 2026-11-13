package com.ekomardiatno.emkeys.ime

import com.ekomardiatno.emkeys.model.Mod

/** Physical holds plus a fast double-tap lock for each modifier. Main-thread only. */
class ModifierState(
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    enum class Phase { OFF, HELD, LOCKED }

    private val held = mutableMapOf<Mod, Int>()
    private val pressedAt = mutableMapOf<Mod, Long>()
    private val locked = mutableSetOf<Mod>()
    private val lastTap = mutableMapOf<Mod, Long>()
    private val consumedWhileHeld = mutableSetOf<Mod>()

    fun pressMod(mod: Mod) {
        if (mod !in held) {
            consumedWhileHeld -= mod
            pressedAt[mod] = nowMillis()
        }
        held[mod] = (held[mod] ?: 0) + 1
    }

    fun releaseMod(mod: Mod, cancelled: Boolean = false) {
        val count = held[mod] ?: return
        if (cancelled) consumedWhileHeld += mod
        if (count > 1) {
            held[mod] = count - 1
            return
        }
        held.remove(mod)
        val duration = nowMillis() - (pressedAt.remove(mod) ?: nowMillis())
        if (consumedWhileHeld.remove(mod) || cancelled) {
            lastTap -= mod
            return
        }
        if (duration > DOUBLE_TAP_TIMEOUT_MS) {
            lastTap -= mod
            return
        }

        val now = nowMillis()
        if (mod in locked) {
            locked -= mod
            lastTap -= mod
        } else {
            val previous = lastTap[mod]
            if (previous != null && now - previous in 0..DOUBLE_TAP_TIMEOUT_MS) {
                locked += mod
                lastTap -= mod
            } else {
                lastTap[mod] = now
            }
        }
    }

    /** Mark held modifiers as used when a base key goes down, so chord release is not a tap. */
    fun markChordConsumed() {
        consumedWhileHeld += held.keys
    }

    fun activeMods(): List<Mod> = Mod.inOrder.filter(::isActive)
    fun isActive(mod: Mod): Boolean = mod in held || mod in locked
    fun heldMods(): List<Mod> = Mod.inOrder.filter { it in held }
    fun phase(mod: Mod): Phase = when {
        mod in held -> Phase.HELD
        mod in locked -> Phase.LOCKED
        else -> Phase.OFF
    }
    fun isAnythingActive(): Boolean = held.isNotEmpty() || locked.isNotEmpty()

    fun releaseAll() {
        held.clear()
        pressedAt.clear()
        locked.clear()
        lastTap.clear()
        consumedWhileHeld.clear()
    }

    companion object {
        const val DOUBLE_TAP_TIMEOUT_MS = 300L
    }
}
