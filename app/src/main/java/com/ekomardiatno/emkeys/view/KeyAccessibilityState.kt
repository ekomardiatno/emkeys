package com.ekomardiatno.emkeys.view

import androidx.annotation.StringRes
import com.ekomardiatno.emkeys.R
import com.ekomardiatno.emkeys.ime.KeyboardController
import com.ekomardiatno.emkeys.ime.ModifierState.Phase
import com.ekomardiatno.emkeys.model.Key
import com.ekomardiatno.emkeys.model.KeyRole
import com.ekomardiatno.emkeys.model.KeyboardAction

/** Spoken state follows the physical cap, even when its Fn-layer action changes. */
internal object KeyAccessibilityState {
    @StringRes
    fun description(key: Key, controller: KeyboardController): Int? {
        if ((key.fnKey?.role as? KeyRole.Action)?.action == KeyboardAction.FN_LOCK) {
            return if (controller.fn.locked) R.string.key_fn_lock_on else R.string.key_fn_lock_off
        }
        return when (val role = key.role) {
            is KeyRole.Modifier -> when (controller.modifierState().phase(role.mod)) {
                Phase.OFF -> R.string.key_off
                Phase.LATCHED -> R.string.key_latched
                Phase.HELD -> R.string.key_held
                Phase.LOCKED -> R.string.key_locked
            }
            KeyRole.Fn -> with(controller.fn) {
                when {
                    locked && isHeld -> R.string.key_fn_inverted_held
                    locked && isLatched -> R.string.key_fn_inverted_latched
                    locked -> R.string.key_fn_locked
                    isHeld -> R.string.key_held
                    isLatched -> R.string.key_latched
                    else -> R.string.key_off
                }
            }
            is KeyRole.Action -> when (role.action) {
                KeyboardAction.CAPS_LOCK -> if (controller.capsLock) R.string.key_on else R.string.key_off
                KeyboardAction.FN_LOCK -> if (controller.fn.locked) R.string.key_fn_lock_on else R.string.key_fn_lock_off
                else -> null
            }
            else -> null
        }
    }
}
