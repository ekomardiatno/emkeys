package com.ekomardiatno.emkeys.setup

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.CheckBox
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.updateLayoutParams
import android.view.View
import android.view.ViewGroup
import android.graphics.Rect
import com.ekomardiatno.emkeys.R
import com.ekomardiatno.emkeys.ime.EmkeysInputMethodService
import android.view.inputmethod.InputMethodManager

/**
 * Diagnostics, not decoration. Three questions that between them explain every way emkeys
 * can appear to be broken while working perfectly.
 *
 * Re-evaluated in both [onResume] and [onWindowFocusChanged] so that returning from the
 * system keyboard settings refreshes the rows without a reload.
 */
class SetupActivity : Activity() {

    private val imeComponent: ComponentName
        get() = ComponentName(this, EmkeysInputMethodService::class.java)

    private val imm: InputMethodManager
        get() = getSystemService(InputMethodManager::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_setup)
        val root = findViewById<View>(R.id.setup_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            val margins = view.layoutParams as ViewGroup.MarginLayoutParams
            val bottomChanged = margins.bottomMargin != safe.bottom
            // Size the ScrollView's actual viewport. Bottom padding alone leaves its
            // requestRectangleOnScreen calculation believing the covered area is visible.
            if (margins.leftMargin != safe.left || margins.topMargin != safe.top ||
                margins.rightMargin != safe.right || bottomChanged) {
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    leftMargin = safe.left
                    topMargin = safe.top
                    rightMargin = safe.right
                    bottomMargin = safe.bottom
                }
            }
            if (bottomChanged && insets.isVisible(WindowInsetsCompat.Type.ime())) {
                view.doOnNextLayout {
                    view.findFocus()?.let { focused ->
                        val rect = Rect()
                        focused.getDrawingRect(rect)
                        focused.requestRectangleOnScreen(rect, true)
                    }
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)

        findViewById<Button>(R.id.action_enabled).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.action_current).setOnClickListener {
            imm.showInputMethodPicker()
        }
        val preferences = KeyboardPreferences(this)
        findViewById<CheckBox>(R.id.swap_fn_ctrl).apply {
            isChecked = preferences.swapFnCtrl
            setOnCheckedChangeListener { _, checked -> preferences.swapFnCtrl = checked }
        }
        findViewById<TextView>(R.id.adb_hint).text = buildString {
            appendLine(getString(R.string.adb_hint_label))
            append(getString(R.string.adb_command))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refresh()
    }

    private fun refresh() {
        val enabled = imm.enabledInputMethodList.any { it.component == imeComponent }
        status(R.id.status_enabled, enabled) {
            if (enabled) getString(R.string.status_enabled_yes)
            else getString(R.string.status_enabled_no)
        }

        val current = currentInputMethod()
        val isCurrent = current == imeComponent
        status(R.id.status_current, isCurrent) {
            if (isCurrent) getString(R.string.status_current_yes)
            else getString(R.string.status_current_no, current?.flattenToShortString() ?: "unknown")
        }

        // Informational only. onEvaluateInputViewShown() is overridden to true, so emkeys
        // shows regardless; and show_ime_with_hard_keyboard is not writable by a normal app
        // (WRITE_SECURE_SETTINGS is signature|privileged).
        val config = resources.configuration
        val hardKeyboardPresent = config.keyboard != Configuration.KEYBOARD_NOKEYS &&
            config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
        val systemAllowsBoth = Settings.Secure.getInt(
            contentResolver,
            SHOW_IME_WITH_HARD_KEYBOARD, // @hide constant, hence the literal
            0,
        ) == 1
        status(R.id.status_hard_keyboard, ok = true) {
            when {
                !hardKeyboardPresent -> getString(R.string.status_hardkb_none)
                systemAllowsBoth -> getString(R.string.status_hardkb_allowed)
                else -> getString(R.string.status_hardkb_override)
            }
        }
    }

    /**
     * The stored value is the **short form**, e.g.
     * `com.ekomardiatno.emkeys/.ime.EmkeysInputMethodService`, so it must be unflattened and
     * compared as a [ComponentName]. A naive string `==` against a fully-qualified name
     * fails.
     */
    private fun currentInputMethod(): ComponentName? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString)

    private fun status(viewId: Int, ok: Boolean, text: () -> String) {
        findViewById<TextView>(viewId).apply {
            this.text = getString(R.string.status_row, if (ok) "\u2713" else "\u2717", text())
            setTextColor(ContextCompat.getColor(context, if (ok) R.color.ok else R.color.danger))
        }
    }

    private companion object {
        const val SHOW_IME_WITH_HARD_KEYBOARD = "show_ime_with_hard_keyboard"
    }
}
