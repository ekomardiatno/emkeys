package com.ekomardiatno.emkeys

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.app.UiAutomation
import android.view.InputDevice
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ekomardiatno.emkeys.setup.KeyboardPreferences
import com.ekomardiatno.emkeys.setup.SetupActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeNotNull
import org.junit.runner.RunWith

/** Real touch-stream tests. Run only on a dedicated, idle test device with emkeys selected. */
@RunWith(AndroidJUnit4::class)
class KeyboardDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation
    private val context = instrumentation.targetContext
    private val preferences = KeyboardPreferences(context)
    private var originalFnLock = false
    private var originalSwap = false

    @Before fun prepare() {
        originalFnLock = preferences.fnLocked
        originalSwap = preferences.swapFnCtrl
        preferences.fnLocked = false
        preferences.swapFnCtrl = false
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        instrumentation.startActivitySync(Intent(context, SetupActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        await { editor()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true }
        automation.waitForIdle(500, 6000) // IME entry/insets animations must settle before reading bounds.
        await { keys().size == 84 }
    }

    @After fun restore() {
        preferences.fnLocked = originalFnLock
        preferences.swapFnCtrl = originalSwap
        automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }

    private fun nodes(): List<AccessibilityNodeInfo> = buildList {
        fun visit(node: AccessibilityNodeInfo) {
            add(node)
            repeat(node.childCount) { node.getChild(it)?.let(::visit) }
        }
        automation.windows.forEach { it.root?.let(::visit) }
    }

    private fun editor(): AccessibilityNodeInfo? = nodes().firstOrNull {
        it.viewIdResourceName == "com.ekomardiatno.emkeys:id/smoke_test"
    }

    private fun keys(): List<AccessibilityNodeInfo> = nodes().filter {
        it.packageName == context.packageName && it.className == "android.widget.Button" &&
            !it.contentDescription.isNullOrBlank()
    }

    private fun key(label: String): AccessibilityNodeInfo = keys().last {
        it.contentDescription.toString() == label
    }

    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)

    private fun tap(label: String) {
        val b = bounds(key(label))
        val start = SystemClock.uptimeMillis()
        touch(start, MotionEvent.ACTION_DOWN, listOf(b))
        touch(start, MotionEvent.ACTION_UP, listOf(b))
        instrumentation.waitForIdleSync()
    }

    private fun touch(start: Long, action: Int, caps: List<Rect>, ids: List<Int> = caps.indices.toList()) {
        val properties = ids.map { id ->
            MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }.toTypedArray()
        val coordinates = caps.map { cap ->
            MotionEvent.PointerCoords().apply {
                x = cap.exactCenterX(); y = cap.exactCenterY(); pressure = 1f; size = 0.1f
            }
        }.toTypedArray()
        val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, caps.size,
            properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 6000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail("Timed out waiting for device UI")
    }

    @Test fun alphabetSymbolsCapsAndHaptics() {
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.VIBRATE))
        "QWERTYUIOPASDFGHJKLZXCVBNM".forEach { tap(it.toString()) }
        tap("Shift"); tap("!")
        tap("Caps Lock"); tap("A"); tap("1")
        tap("Shift"); tap("A")
        assertEquals("qwertyuiopasdfghjklzxcvbnm!A1a", editor()!!.text.toString())
    }

    @Test fun threeFingerChordDoesNotLatchModifiers() {
        val ctrl = bounds(key("Ctrl"))
        val shift = bounds(key("Shift"))
        val p = bounds(key("P"))
        val start = SystemClock.uptimeMillis()
        touch(start, MotionEvent.ACTION_DOWN, listOf(ctrl))
        touch(start, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(ctrl, shift))
        touch(start, MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(ctrl, shift, p))
        touch(start, MotionEvent.ACTION_POINTER_UP or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(ctrl, shift, p))
        touch(start, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(ctrl, shift))
        touch(start, MotionEvent.ACTION_UP, listOf(ctrl))
        assertTrue(keys().filter { it.contentDescription.toString() in listOf("Ctrl", "Shift") }.none { it.isChecked })
        tap("P")
        assertTrue(editor()!!.text.toString().endsWith("p"))
    }

    @Test fun overlappingBaseKeysKeepShiftAfterOtherPointersRelease() {
        val shift = bounds(key("Shift"))
        val x = bounds(key("X"))
        val y = bounds(key("Y"))
        val start = SystemClock.uptimeMillis()
        touch(start, MotionEvent.ACTION_DOWN, listOf(shift))
        touch(start, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(shift, x))
        touch(start, MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(shift, x, y))
        // Lift Shift, then X. Y's session must retain its modifier and pointer id.
        touch(start, MotionEvent.ACTION_POINTER_UP, listOf(shift, x, y))
        touch(start, MotionEvent.ACTION_POINTER_UP, listOf(x, y), listOf(1, 2))
        SystemClock.sleep(650)
        touch(start, MotionEvent.ACTION_UP, listOf(y), listOf(2))
        val typed = editor()!!.text.toString()
        assertTrue("Expected X then repeating uppercase Y, got $typed", typed.matches(Regex("XY{2,}")))
        tap("Z")
        assertEquals(typed + "z", editor()!!.text.toString())
        assertTrue(keys().filter { it.contentDescription.toString() == "Shift" }.none { it.isChecked })
    }

    @Test fun slideOffStopsRepeatAndCancelsUnusedModifier() {
        val a = bounds(key("A"))
        val d = bounds(key("D"))
        val start = SystemClock.uptimeMillis()
        touch(start, MotionEvent.ACTION_DOWN, listOf(a))
        touch(start, MotionEvent.ACTION_MOVE, listOf(d))
        SystemClock.sleep(650) // Longer than the repeat delay; the initial A is not undoable.
        touch(start, MotionEvent.ACTION_UP, listOf(d))
        assertEquals("a", editor()!!.text.toString())

        val shift = bounds(key("Shift"))
        val space = bounds(key("Space"))
        val next = SystemClock.uptimeMillis()
        touch(next, MotionEvent.ACTION_DOWN, listOf(shift))
        touch(next, MotionEvent.ACTION_MOVE, listOf(space))
        touch(next, MotionEvent.ACTION_UP, listOf(space))
        tap("B")
        assertEquals("ab", editor()!!.text.toString())
        assertFalse(key("Shift").isChecked)
    }

    @Test fun editorTeardownStopsRepeatAndClearsHeldModifiers() {
        val shift = bounds(key("Shift"))
        val q = bounds(key("Q"))
        val start = SystemClock.uptimeMillis()
        touch(start, MotionEvent.ACTION_DOWN, listOf(shift))
        touch(start, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(shift, q))
        assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
        automation.waitForIdle(500, 6000)
        touch(start, MotionEvent.ACTION_CANCEL, listOf(shift, q))
        instrumentation.startActivitySync(Intent(context, SetupActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        await { editor()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true }
        automation.waitForIdle(500, 6000)
        await { keys().size == 84 }
        SystemClock.sleep(650)
        // An empty EditText may expose its hint as accessibility text, not an empty string.
        val blank = editor()!!
        assertTrue("Unexpected text after teardown: ${blank.text}", blank.isShowingHintText || blank.text.isNullOrEmpty())
        tap("P")
        assertEquals("p", editor()!!.text.toString())
        assertTrue(keys().filter { it.contentDescription.toString() == "Shift" }.none { it.isChecked })
    }

    @Test fun virtualAccessibilityClicksSupportLatchedChords() {
        // Exercise the virtual node action, not coordinate injection. TalkBack UX is separate.
        assertFalse(key("Win, unavailable in Chrome").isEnabled)
        assertTrue(key("A").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        assertTrue(key("Shift").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await { key("Shift").isChecked }
        assertTrue(key("B").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await { editor()?.text.toString() == "aB" }
        assertFalse(key("Shift").isChecked)
        assertTrue(key("C").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await { editor()?.text.toString() == "aBc" }
    }

    @Test fun fnCtrlSwapRebindsGeometryAndFnBehavior() {
        preferences.swapFnCtrl = true
        assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        await { keys().isEmpty() }
        await { editor()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true }
        automation.waitForIdle(500, 6000)
        await { keys().size == 84 }
        val leftCtrl = keys().first { it.contentDescription.toString() == "Ctrl" }
        assertTrue(bounds(leftCtrl).left < bounds(key("Fn")).left)
        tap("A"); tap("B"); tap("Fn"); tap("Home"); tap("X")
        assertEquals("xab", editor()!!.text.toString())
    }

    @Test fun fnSpaceAndToolbarOpenKeyboardPicker() {
        fun pickerVisible() = nodes().any {
            it.packageName != context.packageName && it.text?.toString()?.contains("emkeys", ignoreCase = true) == true
        }
        fun dismissPicker() {
            assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            await { !pickerVisible() }
            await { editor()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true }
            automation.waitForIdle(500, 6000)
            await { keys().size == 84 }
        }
        try {
            tap("Fn"); tap("Keyboards")
            await { pickerVisible() }
            dismissPicker()
            val button = nodes().first { it.packageName == context.packageName &&
                it.text?.toString() == context.getString(R.string.action_pick_ime) }
            assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await { pickerVisible() }
        } finally {
            if (pickerVisible()) dismissPicker()
        }
        assertFalse(key("Fn").isChecked)
    }

    @Test fun previousToolbarAndFnTabSwitchToRecentKeyboard() {
        val imm = context.getSystemService(InputMethodManager::class.java)
        val alternatives = imm.enabledInputMethodList.filter {
            it.packageName != context.packageName && (0 until it.subtypeCount).any { index ->
                it.getSubtypeAt(index).mode == "keyboard"
            }
        }
        val other = alternatives.firstOrNull { it.packageName == "com.google.android.inputmethod.latin" }
            ?: alternatives.firstOrNull()
        assumeNotNull(other)
        val emkeys = "com.ekomardiatno.emkeys/.ime.EmkeysInputMethodService"
        fun selected() = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        fun select(id: String) {
            // Test-only UiAutomation shell authority; the application needs no settings permission.
            require(id.matches(Regex("[A-Za-z0-9_./]+")))
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("ime set $id"))
                .bufferedReader().use { it.readText() }
            await { selected() == id }
        }
        fun openEmkeys() {
            select(emkeys)
            await { editor()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true }
            automation.waitForIdle(500, 6000)
            await { keys().size == 84 }
        }
        try {
            select(other!!.id)
            openEmkeys()
            val previous = nodes().first { it.packageName == context.packageName &&
                it.text?.toString() == context.getString(R.string.action_previous_ime) }
            assertTrue(previous.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await { selected() == other.id }
            openEmkeys()
            tap("Fn"); tap("Switch")
            await { selected() == other.id }
        } finally {
            openEmkeys()
        }
    }

    @Test fun fnLockNavigationAndBottomRowAreTouchable() {
        tap("A"); tap("B"); tap("C")
        tap("Fn")
        assertTrue(key("Fn").isChecked)
        tap("Home") // Fn + left
        tap("X")
        assertEquals("xabc", editor()!!.text.toString())
        tap("Fn"); tap("FnLk")
        assertTrue(preferences.fnLocked)
        tap("End") // locked Fn + right
        tap("Z")
        assertEquals("xabcz", editor()!!.text.toString())
        tap("Esc") // must remain bare Escape while locked
        // Native EditText uses Escape to dismiss its IME; Chrome's page behavior is separate.
        editor()!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        automation.waitForIdle(500, 6000)
        await { keys().size == 84 }
        tap("Fn"); tap("FnLk")
        assertFalse(preferences.fnLocked)
        tap("Space"); tap("Backspace")
        assertEquals("xabcz", editor()!!.text.toString())
    }

    @Test fun portraitAndLandscapeExpose84NonOverlappingKeys() {
        listOf(UiAutomation.ROTATION_FREEZE_0, UiAutomation.ROTATION_FREEZE_90).forEach { rotation ->
            assertTrue(automation.setRotation(rotation))
            automation.waitForIdle(500, 6000)
            // Rotation replaces the activity/window; do not retain a node from the old tree.
            await { editor()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true }
            automation.waitForIdle(500, 6000)
            await { keys().size == 84 }
            val caps = keys().map(::bounds)
            val screenshot = automation.takeScreenshot()!!
            val display = Rect(0, 0, screenshot.width, screenshot.height)
            context.openFileOutput("device-test-$rotation.png", 0).use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            assertEquals(84, caps.size)
            caps.forEachIndexed { i, a ->
                assertFalse(a.isEmpty)
                assertTrue("Off-screen cap: $a in $display", display.contains(a))
                caps.drop(i + 1).forEach { b -> assertFalse("Overlapping caps: $a $b", Rect.intersects(a, b)) }
            }
            tap("Fn")
            assertTrue(key("Fn").isChecked)
            tap("Home")
            assertFalse(key("Fn").isChecked)
        }
    }

    @Test fun exportKeyBounds() {
        val screenshot = automation.takeScreenshot()!!
        context.openFileOutput("device-test-layout.png", 0).use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val caps = JSONArray()
        keys().forEach { node ->
            val b = bounds(node)
            caps.put(JSONObject().put("label", node.contentDescription).put("left", b.left)
                .put("top", b.top).put("right", b.right).put("bottom", b.bottom))
        }
        instrumentation.sendStatus(0, Bundle().apply { putString("keys_json", caps.toString()) })
    }
}
