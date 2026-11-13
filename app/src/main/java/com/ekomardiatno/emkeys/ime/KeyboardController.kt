package com.ekomardiatno.emkeys.ime

import android.view.KeyEvent
import com.ekomardiatno.emkeys.model.Key
import com.ekomardiatno.emkeys.model.KeyRole
import com.ekomardiatno.emkeys.model.KeyboardAction
import com.ekomardiatno.emkeys.model.Mod

/**
 * Touch -> dispatch. The one place the three ordering invariants of [ModifierState] are
 * actually honoured, so they are worth reading in situ:
 *
 * A base key snapshots modifiers that are physically held on its pointer-down. Its repeat
 * events reuse that chord; pointer-up closes it before releasing the base key.
 *
 * Main thread only, by construction.
 */
class KeyboardController(
    private val dispatcher: KeyDispatcher,
    private val modifiers: ModifierState,
    private val repeater: Repeater,
    private val onStateChanged: () -> Unit = {},
    val fn: FnState = FnState(),
    private val onAction: (KeyboardAction) -> Unit = {},
) {

    private data class Press(
        val cap: Key,
        val key: Key,
    )

    private val pointers = mutableMapOf<Int, Press>()

    var capsLock: Boolean = false
        private set

    /** The cap [pointerId] is currently on, for press-highlight rendering. */
    fun keyUnder(pointerId: Int): Key? = pointers[pointerId]?.cap

    fun modifierState(): ModifierState = modifiers

    /** Resolve once on pointer-down; releasing Fn mid-repeat must not change the key. */
    fun effectiveKey(key: Key): Key {
        val alternate = key.fnKey ?: return key
        val toggle = (alternate.role as? KeyRole.Action)?.action == KeyboardAction.FN_LOCK
        return if (if (toggle) fn.isHeld else fn.active) alternate else key
    }

    fun isUppercase(key: Key): Boolean =
        modifiers.isActive(Mod.SHIFT) xor (capsLock && isLetter(key))

    private fun isLetter(key: Key): Boolean = key.keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z

    fun press(pointerId: Int, cap: Key) {
        cancel(pointerId) // a reused id must not trigger a stale tap/action
        val key = effectiveKey(cap)
        pointers[pointerId] = Press(cap, key)
        when (val role = key.role) {
            is KeyRole.Modifier -> modifiers.pressMod(role.mod)
            KeyRole.Fn -> fn.press()
            KeyRole.Base -> {
                modifiers.markChordConsumed()
                fn.markConsumed()
                val mods = modifiers.activeMods().toMutableList()
                // Caps is local and letters-only. Shift reverses it, as on a hardware board.
                if (capsLock && isLetter(key)) {
                    if (!mods.remove(Mod.SHIFT)) mods.add(Mod.SHIFT)
                }
                dispatcher.pressBase(pointerId, key, mods)
                if (key.repeats) {
                    repeater.start(pointerId) { dispatcher.repeatBase(pointerId, key) }
                }
            }
            is KeyRole.Action -> fn.markConsumed()
            KeyRole.Disabled -> Unit
        }
        onStateChanged()
    }

    fun release(pointerId: Int) {
        finish(pointerId, cancelled = false)
    }

    /** Slide-off cancels repeats and releases any held modifier without sticky side effects. */
    fun cancel(pointerId: Int) {
        finish(pointerId, cancelled = true)
    }

    private fun finish(pointerId: Int, cancelled: Boolean) {
        val press = pointers.remove(pointerId) ?: return
        when (val role = press.key.role) {
            is KeyRole.Modifier -> modifiers.releaseMod(role.mod, cancelled)
            KeyRole.Fn -> fn.release(cancelled)
            KeyRole.Base -> {
                repeater.cancel(pointerId)
                dispatcher.closeChord(pointerId)
            }
            is KeyRole.Action -> {
                if (!cancelled) when (role.action) {
                    KeyboardAction.CAPS_LOCK -> capsLock = !capsLock
                    KeyboardAction.FN_LOCK -> fn.toggleLock()
                    else -> onAction(role.action)
                }
            }
            KeyRole.Disabled -> Unit
        }
        onStateChanged()
    }

    /**
     * Tear everything down. Idempotent, and the only correct response to a gesture or an
     * editor disappearing mid-chord.
     *
     * @param sendUps `true` from `ACTION_CANCEL` and `onFinishInputView` (the connection is
     *   still live); `false` from `onFinishInput` / `onUnbindInput` (it is already dead).
     */
    fun cancelAll(sendUps: Boolean) {
        repeater.cancelAll()
        dispatcher.closeAllChords(sendUps)
        modifiers.releaseAll()
        fn.resetTransient()
        capsLock = false
        pointers.clear()
        onStateChanged()
    }
}
