package com.ekomardiatno.emkeys.ime

import com.ekomardiatno.emkeys.model.Key
import com.ekomardiatno.emkeys.model.KeyStroke
import com.ekomardiatno.emkeys.model.Mod

/**
 * Where strokes go. Implemented for real by [ImeStrokeSink]; faked in tests.
 *
 * It is an interface precisely so the envelope logic can be exercised on the JVM:
 * `KeyEvent`'s constructor throws `RuntimeException("Stub!")` under the unit-test
 * `android.jar`.
 */
interface StrokeSink {
    /**
     * Rule 4 — `finishComposingText()` before every chord. Blink sets `isComposing` from
     * the live composition and VS Code hard-discards composing keydowns
     * (`keyCode = KEY_IN_COMPOSITION`), so a `Ctrl+X` over an open composition vanishes
     * without a trace.
     */
    fun finishComposing()

    fun send(stroke: KeyStroke)
}

/**
 * Owns the open chord sessions and turns key presses into [KeyStroke] sequences.
 *
 * A **chord session** spans one base-key pointer: opened on pointer-down, kept open across
 * every auto-repeat tick, closed on pointer-up. That is what makes `Ctrl+←` repeat emit the
 * `Ctrl` DOWN once rather than once per tick.
 *
 * Modifier ownership is reference-counted across sessions: DOWN on 0→1 and UP on 1→0.
 * Each base key keeps its own modifier snapshot for repeat, while modifier transitions
 * carry the union of all still-open sessions.
 */
class KeyDispatcher(private val sink: StrokeSink) {

    /** pointerId -> the modifiers that session opened with. */
    private val openSessions = mutableMapOf<Int, List<Mod>>()
    private val owners = mutableMapOf<Mod, Int>()

    private fun activeMeta(): Int = ChordEnvelope.metaStateOf(Mod.inOrder.filter { it in owners })

    val hasOpenSessions: Boolean get() = openSessions.isNotEmpty()

    /**
     * Open a chord for [pointerId] and emit the base key's first DOWN/UP pair.
     *
     * [mods] is snapshotted here, so a modifier released mid-repeat still gets its UP from
     * the session that sent its DOWN.
     */
    fun pressBase(pointerId: Int, key: Key, mods: List<Mod>) {
        closeChord(pointerId) // defensive: a stale session for this id would strand its mods
        sink.finishComposing()
        val snapshot = Mod.inOrder.filter { it in mods }
        openSessions[pointerId] = snapshot
        snapshot.forEach { mod ->
            val count = owners[mod] ?: 0
            owners[mod] = count + 1
            if (count == 0) {
                sink.send(KeyStroke(mod.keyCode, true, activeMeta(), mod.scanCode))
            }
        }
        ChordEnvelope.base(key, snapshot).forEach(sink::send)
    }

    /**
     * One auto-repeat tick: a **discrete** DOWN/UP pair with `repeat = 0` inside the already
     * open chord (rule 7 — `repeatCount` is not forwarded to Blink, so `event.repeat` is
     * always false and a real repeat is indistinguishable from N presses anyway).
     */
    fun repeatBase(pointerId: Int, key: Key) {
        val mods = openSessions[pointerId] ?: return
        ChordEnvelope.base(key, mods).forEach(sink::send)
    }

    /** Close [pointerId]'s chord, emitting the modifier UPs. No-op if it isn't open. */
    fun closeChord(pointerId: Int) {
        val mods = openSessions.remove(pointerId) ?: return
        mods.reversed().forEach { mod ->
            val count = owners.getValue(mod) - 1
            if (count == 0) {
                owners.remove(mod)
                sink.send(KeyStroke(mod.keyCode, false, activeMeta(), mod.scanCode))
            } else {
                owners[mod] = count
            }
        }
    }

    /**
     * Close every open chord. Idempotent.
     *
     * The stranded-modifier defence. A modifier DOWN with no matching UP leaves every
     * subsequent keystroke in Chrome carrying `ctrlKey: true`, which presents as a Chromium
     * bug rather than ours, so this is designed in rather than retrofitted.
     *
     * @param sendUps `true` while the `InputConnection` that received the DOWNs is still
     *   live (ACTION_CANCEL, `onFinishInputView`). `false` once it is dead
     *   (`onFinishInput`, `onUnbindInput`) — **never send modifier UPs into a different
     *   `InputConnection` than the DOWNs went to**; the next editor starts clean anyway.
     */
    fun closeAllChords(sendUps: Boolean) {
        if (sendUps) {
            openSessions.keys.toList().forEach(::closeChord)
        } else {
            openSessions.clear()
            owners.clear()
        }
    }
}
