package com.ekomardiatno.emkeys.ime

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Schedules auto-repeat ticks per pointer. Split out as an interface so [KeyboardController]
 * is testable on the JVM without a looper.
 */
interface Repeater {
    fun start(pointerId: Int, onTick: () -> Unit)
    fun cancel(pointerId: Int)
    fun cancelAll()
}

/**
 * Rule 7's timer: emkeys has to generate its own key repeat, because
 * `ImeAdapterImpl.sendKeyEvent` does not forward `repeatCount` and `event.repeat` is
 * therefore always `false` in the page.
 *
 * A main-looper `Handler` rather than coroutines, for two concrete reasons: `onTouchEvent`
 * already runs on the main thread, so [ModifierState] and the pointer map need zero
 * synchronisation; and `removeCallbacksAndMessages(token)` with a per-pointer token covers
 * every cancellation path in one line. (`InputMethodService` is not a `LifecycleOwner`, so a
 * `lifecycleScope` would mean pulling in `lifecycle-service` for nothing.)
 *
 * Ticks are scheduled with [Handler.postAtTime] against an **accumulating origin**, not by
 * self-reposting `postDelayed` — the latter drifts under draw jank and looks visibly uneven
 * on a 120 Hz panel.
 */
class RepeatController(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : Repeater {

    private val tokens = mutableMapOf<Int, Any>()

    override fun start(pointerId: Int, onTick: () -> Unit) {
        cancel(pointerId)
        val token = Any()
        tokens[pointerId] = token
        schedule(token, SystemClock.uptimeMillis(), tick = 1, onTick = onTick)
    }

    override fun cancel(pointerId: Int) {
        tokens.remove(pointerId)?.let(handler::removeCallbacksAndMessages)
    }

    override fun cancelAll() {
        tokens.values.forEach(handler::removeCallbacksAndMessages)
        tokens.clear()
    }

    private fun schedule(token: Any, origin: Long, tick: Int, onTick: () -> Unit) {
        val at = origin + INITIAL_DELAY_MS + (tick - 1).toLong() * INTERVAL_MS
        handler.postAtTime({
            onTick()
            schedule(token, origin, tick + 1, onTick)
        }, token, at)
    }

    private companion object {
        /** AOSP `ViewConfiguration.KEY_REPEAT_TIMEOUT`. */
        const val INITIAL_DELAY_MS = 400L

        /** AOSP `ViewConfiguration.KEY_REPEAT_DELAY` — 20 Hz. */
        const val INTERVAL_MS = 50L
    }
}
