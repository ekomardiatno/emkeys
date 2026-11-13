package com.ekomardiatno.emkeys

import android.view.KeyEvent
import com.ekomardiatno.emkeys.ime.ChordEnvelope
import com.ekomardiatno.emkeys.ime.KeyDispatcher
import com.ekomardiatno.emkeys.ime.StrokeSink
import com.ekomardiatno.emkeys.model.Key
import com.ekomardiatno.emkeys.model.KeyStroke
import com.ekomardiatno.emkeys.model.Mod
import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class KeyDispatcherOwnershipTest {
    @Test fun `randomized overlap reuse repeat and teardown keep modifier transitions balanced`() {
        val down = mutableSetOf<Mod>()
        var strokes = 0
        val dispatcher = KeyDispatcher(object : StrokeSink {
            override fun finishComposing() = Unit
            override fun send(stroke: KeyStroke) {
                strokes++
                val mod = Mod.entries.firstOrNull { it.keyCode == stroke.keyCode } ?: return
                if (stroke.down) assertTrue("Duplicate DOWN: $mod", down.add(mod))
                else assertTrue("Unmatched UP: $mod", down.remove(mod))
                assertEquals(ChordEnvelope.metaStateOf(down.toList()), stroke.metaState)
            }
        })
        val random = Random(84)
        val key = Key("A", KeyEvent.KEYCODE_A)
        repeat(2000) {
            val pointer = random.nextInt(8)
            when (random.nextInt(6)) {
                0, 1 -> dispatcher.pressBase(pointer, key, Mod.entries.filter { random.nextBoolean() })
                2 -> dispatcher.closeChord(pointer)
                3 -> dispatcher.repeatBase(pointer, key)
                4 -> dispatcher.closeAllChords(sendUps = true)
                5 -> {
                    // A new connection starts with clean state even when the old one died mid-hold.
                    dispatcher.closeAllChords(sendUps = false)
                    down.clear()
                }
            }
        }
        dispatcher.closeAllChords(sendUps = true)
        assertTrue(down.isEmpty())
        assertFalse(dispatcher.hasOpenSessions)
        assertTrue(strokes > 2000)
    }

    @Test fun `unsorted duplicate modifier requests open one canonical envelope`() {
        val strokes = mutableListOf<KeyStroke>()
        val dispatcher = KeyDispatcher(object : StrokeSink {
            override fun finishComposing() = Unit
            override fun send(stroke: KeyStroke) { strokes += stroke }
        })
        val key = Key("P", KeyEvent.KEYCODE_P)
        dispatcher.pressBase(0, key, listOf(Mod.SHIFT, Mod.CTRL, Mod.CTRL))
        dispatcher.closeChord(0)
        val mods = listOf(Mod.CTRL, Mod.SHIFT)
        assertEquals(ChordEnvelope.open(mods) + ChordEnvelope.base(key, mods) + ChordEnvelope.close(mods), strokes)
    }
}
