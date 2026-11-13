package com.ekomardiatno.emkeys.setup

import android.content.Context
import androidx.core.content.edit

class KeyboardPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE)

    var swapFnCtrl: Boolean
        get() = preferences.getBoolean("swap_fn_ctrl", false)
        set(value) { preferences.edit { putBoolean("swap_fn_ctrl", value) } }

    var fnLocked: Boolean
        get() = preferences.getBoolean("fn_locked", false)
        set(value) { preferences.edit { putBoolean("fn_locked", value) } }
}
