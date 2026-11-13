package com.ekomardiatno.emkeys.ime

import android.inputmethodservice.InputMethodService
import android.content.res.Configuration
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ekomardiatno.emkeys.R
import com.ekomardiatno.emkeys.model.ThinkPadLayout
import com.ekomardiatno.emkeys.model.KeyboardAction
import com.ekomardiatno.emkeys.setup.KeyboardPreferences
import com.ekomardiatno.emkeys.view.KeyboardView

/**
 * emkeys itself.
 *
 * Two overrides carry essentially all of this class's weight:
 *
 * - [onEvaluateFullscreenMode] is `false` **unconditionally** (RESEARCH.md §1.4). The
 *   default selects fullscreen in landscape, which would cover code-server with an
 *   extract-text editor. Non-negotiable on a landscape tablet.
 * - [onEvaluateInputViewShown] follows the system hardware-keyboard configuration, keeping
 *   emkeys out of the way whenever an attached physical keyboard is available.
 */
class EmkeysInputMethodService : InputMethodService() {

    private val modifiers = ModifierState()
    private var keyboardView: KeyboardView? = null
    private var hiddenForHardwareKeyboard = false
    private val preferences by lazy { KeyboardPreferences(this) }
    private val switcher by lazy {
        KeyboardSwitcher(
            cancelInput = {
                controller.cancelAll(sendUps = true)
                keyboardView?.resetTouches()
            },
            switchToPrevious = { switchToPreviousInputMethod() },
            showPicker = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
        )
    }

    private val controller: KeyboardController by lazy {
        KeyboardController(
            // A supplier, never a cached InputConnection: the reference is invalidated on
            // every editor rebind and a stale one silently returns true.
            dispatcher = KeyDispatcher(
                ImeStrokeSink(
                    connection = { currentInputConnection },
                    now = SystemClock::uptimeMillis,
                ),
            ),
            modifiers = modifiers,
            repeater = RepeatController(),
            onStateChanged = { keyboardView?.refreshState() },
            fn = FnState(preferences.fnLocked, onLockChanged = { preferences.fnLocked = it }),
            onAction = ::performAction,
        )
    }

    override fun onCreateInputView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.ink))
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            // Gesture navigation's IME picker/hide buttons must not cover the bottom caps.
            val safe = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or
                WindowInsetsCompat.Type.displayCutout())
            // On Samsung gesture navigation the reported handle is only 15dp, but
            // the IME picker/hide buttons occupy a 56dp control row above it.
            val bottom = if (safe.bottom > 0) {
                safe.bottom.coerceAtLeast((56 * resources.displayMetrics.density).toInt())
            } else 0
            view.setPadding(safe.left, 0, safe.right, bottom)
            insets
        }
        addView(KeyboardView(context).also { view ->
            keyboardView = view
            view.bind(ThinkPadLayout.create(preferences.swapFnCtrl), controller)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // No UPs here: on a rebind the old connection may already belong to another editor.
        controller.cancelAll(sendUps = false)
        controller.fn.locked = preferences.fnLocked
        keyboardView?.bind(ThinkPadLayout.create(preferences.swapFnCtrl), controller)
    }

    private fun performAction(action: KeyboardAction) {
        when (action) {
            KeyboardAction.SWITCH_KEYBOARD -> switchKeyboard()
            KeyboardAction.PICK_KEYBOARD -> pickKeyboard()
            else -> Unit
        }
    }

    private fun switchKeyboard() = switcher.previous()

    private fun pickKeyboard() = switcher.picker()

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        val physicalKeyboard = PhysicalKeyboard.isConnected(resources.configuration)
        if (physicalKeyboard) hiddenForHardwareKeyboard = true
        return !physicalKeyboard
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (PhysicalKeyboard.isConnected(newConfig)) {
            if (isInputViewShown) {
                hiddenForHardwareKeyboard = true
                controller.cancelAll(sendUps = true)
                keyboardView?.resetTouches()
                requestHideSelf(0)
            }
        } else if (hiddenForHardwareKeyboard) {
            hiddenForHardwareKeyboard = false
            if (currentInputEditorInfo != null) {
                requestShowSelf(InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    /** The connection is still live here, so stranded modifiers get their UPs. */
    override fun onFinishInputView(finishingInput: Boolean) {
        controller.cancelAll(sendUps = true)
        keyboardView?.resetTouches()
        super.onFinishInputView(finishingInput)
    }

    /** The connection is already dead — never send UPs into a *different* one. */
    override fun onFinishInput() {
        controller.cancelAll(sendUps = false)
        keyboardView?.resetTouches()
        hiddenForHardwareKeyboard = false
        super.onFinishInput()
    }

    override fun onUnbindInput() {
        controller.cancelAll(sendUps = false)
        keyboardView?.resetTouches()
        super.onUnbindInput()
    }

    override fun onDestroy() {
        controller.cancelAll(sendUps = false)
        keyboardView = null
        super.onDestroy()
    }
}
