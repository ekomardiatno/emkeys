package com.ekomardiatno.emkeys.model

import android.view.KeyEvent

/** What a cap does when touched. */
sealed interface KeyRole {
    /** Emits a chord: optional modifier envelope + this key's DOWN/UP pair. */
    data object Base : KeyRole

    /** Latches/holds/locks [mod] instead of emitting anything on its own. */
    data class Modifier(val mod: Mod) : KeyRole

    data object Fn : KeyRole
    data class Action(val action: KeyboardAction) : KeyRole
    data object Disabled : KeyRole
}

enum class KeyboardAction { CAPS_LOCK, FN_LOCK, SWITCH_KEYBOARD, PICK_KEYBOARD }

/**
 * One keycap.
 *
 * @param label primary legend.
 * @param keyCode the Android keycode to emit; ignored for [KeyRole.Modifier] caps, which
 *   take theirs from [Mod].
 * @param widthU cap width in ThinkPad units (1u = one alphanumeric key).
 * @param repeats whether holding the cap auto-repeats (§1.3 rule 7).
 */
data class Key(
    val label: String,
    val keyCode: Int = KeyEvent.KEYCODE_UNKNOWN,
    val role: KeyRole = KeyRole.Base,
    val widthU: Float = 1f,
    val repeats: Boolean = false,
    val shiftedLabel: String? = null,
    val fnKey: Key? = null,
    val gapBeforeU: Float = 0f,
    val heightU: Float? = null,
    val offsetYU: Float = 0f,
    val description: String = label,
) {
    /** Evdev scancode for this cap (rule 6). */
    val scanCode: Int
        get() = when (role) {
            is KeyRole.Modifier -> role.mod.scanCode
            KeyRole.Base -> Evdev[keyCode]
            else -> Evdev.UNKNOWN // UI actions and Fn never leave the keyboard.
        }

    companion object {
        fun mod(mod: Mod, widthU: Float = 1f) = Key(
            label = mod.label,
            keyCode = mod.keyCode,
            role = KeyRole.Modifier(mod),
            widthU = widthU,
        )
    }
}

/** A row of caps. */
data class KeyRow(
    val keys: List<Key>,
    val heightU: Float = 0.9f,
    val gapAfterU: Float = 0f,
    val offsetXU: Float = 0f,
    /** Negative offsets let the two arrow tiers share the bottom row. */
    val offsetYU: Float = 0f,
)

/** A keyboard layout: rows of caps, plus the total width in units for measuring. */
data class Layout(val rows: List<KeyRow>) {
    val widthU: Float = rows.maxOfOrNull { row ->
        row.offsetXU + row.keys.sumOf { (it.widthU + it.gapBeforeU).toDouble() }
    }
        ?.toFloat() ?: 0f

    val heightU: Float = rows.sumOf { (it.heightU + it.gapAfterU + it.offsetYU).toDouble() }.toFloat()

    val keys: List<Key> get() = rows.flatMap { it.keys }
}
