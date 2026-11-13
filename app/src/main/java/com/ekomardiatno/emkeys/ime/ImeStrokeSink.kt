package com.ekomardiatno.emkeys.ime

import android.util.Log
import android.view.inputmethod.InputConnection
import com.ekomardiatno.emkeys.ime.KeyEventFactory.toEvent
import com.ekomardiatno.emkeys.model.KeyStroke
import com.ekomardiatno.emkeys.BuildConfig

/**
 * The real [StrokeSink]: pushes strokes into the focused editor's `InputConnection`.
 *
 * Takes a **supplier**, never a cached `InputConnection`. The reference is invalidated on
 * every editor rebind, and a stale one silently returns `true` while nothing arrives —
 * which is indistinguishable from the keyboard working.
 */
class ImeStrokeSink(
    private val connection: () -> InputConnection?,
    private val now: () -> Long,
) : StrokeSink {

    override fun finishComposing() {
        connection()?.finishComposingText()
    }

    override fun send(stroke: KeyStroke) {
        val ic = connection()
        if (ic == null) {
            if (BuildConfig.DEBUG) Log.w(TAG, "dropped (no InputConnection) $stroke")
            return
        }
        if (BuildConfig.DEBUG) Log.d(TAG, stroke.toString())
        ic.sendKeyEvent(stroke.toEvent(now()))
    }

    private companion object {
        const val TAG = "emkeys"
    }
}
