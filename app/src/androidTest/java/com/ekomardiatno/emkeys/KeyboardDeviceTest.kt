package com.ekomardiatno.emkeys

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.app.UiAutomation
import android.view.InputDevice
import android.view.MotionEvent
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

    private fun touch(start: Long, action: Int, caps: List<Rect>) {
        val properties = caps.mapIndexed { id, _ ->
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
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertTrue("Timed out waiting for device UI", condition())
    }

    @Test fun alphabetSymbolsCapsAndHaptics() {
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.VIBRATE))
        "QWERTYUIOPASDFGHJKLZXCVBNM".forEach { tap(it.toString()) }
        tap("Shift"); tap("!")
        tap("Caps Lock"); tap("A"); tap("1")
        tap("Shift"); tap("A")
        assertEquals("qwertyuiopasdfghjklzxcvbnm!A1a", editor()!!.text.toString())
    }

    @Test fun threeFingerChordAndRolloverDoNotLatchModifiers() {
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
