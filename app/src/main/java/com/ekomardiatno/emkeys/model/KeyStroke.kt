package com.ekomardiatno.emkeys.model

/**
 * One `KeyEvent` worth of intent, as pure data.
 *
 * This indirection exists for one concrete reason: `android.view.KeyEvent`'s constructor
 * throws `RuntimeException("Stub!")` under the unit-test `android.jar`, and
 * `returnDefaultValues = true` does not help because the object still cannot be built. So
 * the chord envelope produces [KeyStroke]s, which are plain Kotlin and therefore assertable
 * with list equality on the JVM, and exactly one file — `ime/KeyEventFactory.kt` — turns
 * them into real `KeyEvent`s.
 *
 * It also gives the logcat tracer its format for free.
 */
data class KeyStroke(
    val keyCode: Int,
    val down: Boolean,
    val metaState: Int,
    val scanCode: Int,
) {
    override fun toString(): String =
        "KeyStroke(code=$keyCode, ${if (down) "DOWN" else "UP  "}, meta=0x${
            metaState.toString(16)
        }, scan=$scanCode)"
}
