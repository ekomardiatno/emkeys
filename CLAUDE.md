# emkeys

An Android IME that types code. A ThinkPad-layout soft keyboard for driving
**code-server in Chrome** from a tablet, where Gboard is useless because it has no
Ctrl, no Esc, no F-row and no arrows.

**`RESEARCH.md` is the authoritative research record.** It is long, it is
verified-from-source, and it is *already done*. Read it before re-deriving anything
about Android IMEs, Chromium key dispatch, VS Code keybinding resolution, the ThinkPad
geometry, or the trackpad options. Do not re-research it.

Status: **keyboard-only MVP implemented (chunk 2)** — full 84-key modern ThinkPad
layout, local Fn/Caps, persistent FnLock, Fn/Ctrl swap, switching toolbar, slide-off,
and virtual accessibility keys. The expanded keyboard passed 5 native device tests
and 10 Chrome oracle cases on the Galaxy Tab S9 Ultra (see `RESEARCH.md` §9.6).
Real-editor acceptance, manual multitouch ergonomics, switching and TalkBack still
need the checklist in `README.md`. Do not confuse event delivery with a verified
code-server command, or injected pointers with human touch-panel testing.

---

## 1. What chunk 1 was for

Not keycaps. The point was to prove on hardware that an `InputMethodService` using
`sendKeyEvent` delivers a working `Ctrl+Shift+P` to code-server — *before* investing in
84 keys of ThinkPad geometry. Everything in `RESEARCH.md` §1.3 was reasoned from
Chromium source and had never been run.

It ran. It works. See `RESEARCH.md` §9.4 for the evidence.

Two things in `RESEARCH.md` were wrong and are now corrected in place:

1. **§1.3 scancodes.** evdev codes are **positional, not alphabetical**
   (`Q=16 W=17 … A=30 S=31 … Z=44 X=45 …`, and `F11=87 F12=88` after `F1..F10=59..68`).
   The alphabetical reading fails *silently* — `keyCode` stays right, only `event.code`
   lies — which is why `model/Evdev.kt` is a literal table with a unit test, never a
   formula.
2. **§5.3 "hardware + soft keyboard together: impossible".** Wrong. That suppression is
   `InputMethodService.onEvaluateInputViewShown()`, a public overridable method. We
   override it to `true` and emkeys shows with a physical keyboard connected and
   `show_ime_with_hard_keyboard = 0`.

---

## 2. Architecture

The whole design exists to make the dispatch core **testable on the JVM with no device
and no Robolectric**.

```
model/                 pure Kotlin, no Android construction
  Evdev.kt             literal Android keyCode -> evdev scancode table (+ unit test)
  Mod.kt               CTRL/SHIFT/ALT: keyCode, scanCode, metaBits, canonical order
  KeyStroke.kt         data class(keyCode, down, metaState, scanCode)
  Key.kt               Key / KeyRole (Base | Modifier) / KeyRow / Layout
  SkeletonLayout.kt    the 12-key chunk-1 layout
  ThinkPadLayout.kt    the active 84-key modern ANSI layout
  LayoutGeometry.kt   pure bounds and hit targets for drawing/touch/accessibility

ime/
  ChordEnvelope.kt     List<Mod> + Key -> List<KeyStroke>.  Pure. The contract.
  ModifierState.kt     OFF / LATCHED / HELD / LOCKED, untimed tap cycle
  FnState.kt           local latch/hold + persistent-lock callback
  KeyDispatcher.kt     chord sessions keyed by pointerId; talks to a StrokeSink
  RepeatController.kt  Handler-based repeat; implements Repeater
  KeyboardController.kt the one place the ordering invariants live
  ImeStrokeSink.kt     StrokeSink -> InputConnection. Logs every stroke to logcat.
  KeyEventFactory.kt   KeyStroke -> android.view.KeyEvent.  THE ONLY KeyEvent ctor.
  EmkeysInputMethodService.kt

view/
  Theme.kt             pingwin tokens resolved from colors.xml
  KeyboardView.kt      measure, draw, pointers, slide-off, ExploreByTouchHelper

setup/
  SetupActivity.kt     three diagnostics + a smoke-test EditText
  KeyboardPreferences.kt persisted FnLock + Fn/Ctrl swap
```

### Why `KeyStroke` exists

`android.view.KeyEvent`'s constructor throws `RuntimeException("Stub!")` under the unit
-test `android.jar`. So the envelope produces pure-Kotlin `KeyStroke`s, and exactly one
file (`KeyEventFactory.kt`) converts them. `KeyEvent.KEYCODE_*` and `META_*` constants
*do* resolve off-device (kotlinc inlines Java `static final` primitives), so the model
can name them freely — only *construction* is device-only.

Consequence: **if you change dispatch behaviour, add a JVM regression test.** 71 tests
currently pass; no emulator is needed for core, layer, ownership or geometry tests.

### The chord envelope

Modifier DOWNs with meta *accumulating*, base DOWN/UP with the full meta, modifier UPs
in reverse with each shedding its own bit:

```
CTRL_LEFT  DOWN  meta=0x3000
SHIFT_LEFT DOWN  meta=0x3041
P          DOWN  meta=0x3041
P          UP    meta=0x3041
SHIFT_LEFT UP    meta=0x3000
CTRL_LEFT  UP    meta=0x0
```

A **chord session spans one base-key pointer** — opened on pointer-down, held open
across every repeat tick, closed on pointer-up. That is what makes a held arrow emit N
discrete pairs inside a single `Ctrl` press.

### Three ordering invariants (`KeyboardController`)

They are easy to get subtly wrong, so they live in one function each:

1. `markChordConsumed()` **before** dispatching a base key — a modifier that was *held
   and used* must not also latch when released.
2. `consumeLatched()` **after** the chord closes — a latch clears after exactly one base
   key, not before it is sent.
3. `repeater.cancel()` **before** `dispatcher.closeChord()` — never let a repeat tick
   fire into a session that is being torn down.

### Stranded modifiers

`cancelAll(sendUps)`:

- `sendUps = true` for `ACTION_CANCEL` and `onFinishInputView` — connection is live, so
  flush the UPs.
- `sendUps = false` for `onFinishInput` and `onUnbindInput` — connection is dead or
  about to belong to someone else. **Never send UPs into a different `InputConnection`.**

Verified on device: latch `Ctrl`, press Home, come back, tap `P` → `metaState = 0`.

---

## 3. Build and run

```sh
cd ~/Desktop/projects/emkeys
./gradlew :app:testDebugUnitTest     # no device
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

Toolchain matches `pingwin-android`: Gradle 9.3.1, AGP 8.13.2, Kotlin 2.2.20, JDK 17,
compileSdk/buildTools 36, minSdk 29. Dependencies are `androidx.core:core-ktx`,
`androidx.customview` (virtual accessibility keys), and `junit`. No Compose, Hilt or Room.

Debug signing only. The `keystoreProps` block in `app/build.gradle.kts` no-ops when
`keystore.properties` is absent; there is no `.jks` yet.

### Install and enable

```sh
ADB=~/Library/Android/sdk/platform-tools/adb
D=192.168.18.95:45671                       # SM-X916B, wireless

$ADB -s $D install -r app/build/outputs/apk/debug/app-debug.apk
$ADB -s $D shell ime list -a -s             # must list com.ekomardiatno.emkeys/...
$ADB -s $D shell ime enable  com.ekomardiatno.emkeys/.ime.EmkeysInputMethodService
$ADB -s $D shell ime set     com.ekomardiatno.emkeys/.ime.EmkeysInputMethodService
```

**Restore the normal keyboard when done testing**, if desired (or use the toolbar):

```sh
$ADB -s $D shell ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME
```

If `ime list -a -s` does not show emkeys, the registration is broken. There are three
silent failure modes and all three are documented in `AndroidManifest.xml`: missing
`BIND_INPUT_METHOD` permission, missing `android.view.InputMethod` intent filter, or a
`android.view.im` meta-data resource that does not resolve.

### Observing dispatch

`ImeStrokeSink` logs every stroke:

```sh
$ADB -s $D logcat -c && $ADB -s $D logcat -d -s emkeys:D
# D emkeys: KeyStroke(code=113, DOWN, meta=0x3000, scan=29)
```

That is **our** side. For the **browser's** side use the oracle:

```sh
cd tools && python3 -m http.server 8777 &
$ADB -s $D reverse tcp:8777 tcp:8777
$ADB -s $D shell am start -n com.android.chrome/com.google.android.apps.chrome.Main \
  -a android.intent.action.VIEW -d 'http://localhost:8777/keyoracle.html'
```

`tools/keyoracle.html` logs `key / code / keyCode / mods / location / repeat /
isComposing / isTrusted`, newest first, flags empty `code` and `keyCode === 229` in red,
and `preventDefault()`s anything with ctrl/alt/meta so the `Ctrl+W` test doesn't close
the page. DevTools and `chrome://inspect` were rejected — see the file header.

---

## 4. Device facts (SM-X916B, Galaxy Tab S9 Ultra)

- Android 16 / SDK 36. 2960×1848 landscape, density 280 (1.75×).
- Wireless adb `192.168.18.95:45671`. The Bash sandbox blocks the adb binary — these
  commands need `dangerouslyDisableSandbox`. Host-side pipes are flaky; put the pipe
  *inside* the on-device shell string instead.
- `show_ime_with_hard_keyboard = 0`, and a **Lenovo TrackPoint Keyboard II is paired and
  usually connected**. Neither suppresses emkeys (we override
  `onEvaluateInputViewShown()`), but both suppress Gboard. The TrackPoint keyboard is a
  useful ground-truth oracle: it is the exact keyboard emkeys imitates.
- code-server is installed as a **WebAPK/PWA**
  (`org.chromium.chrome.browser.webapps.SameTaskWebApkActivity`), so no tab strip and no
  omnibox — most of `RESEARCH.md` §3.2's key-stealing worries don't apply there.
- Chrome is `com.android.chrome`, v157, currently in **desktop-site mode** (UA says
  `X11; Linux x86_64`, so `navigator.keyboard` is *present*). On a mobile UA it would be
  undefined and VS Code would use `FallbackKeyboardMapper`. Worth remembering — it
  changes which mapper path you are testing.
- There is a secondary user 150; `pm list packages` throws `SecurityException` for it.
  Harmless.
- Driving the device with `adb shell input` while someone is using the tablet is a
  losing game — the foreground app changes under you. Verify `topResumedActivity`
  inline, in the same on-device shell, right before the taps that matter.

### Historical chunk-1 cap coordinates (not valid for the current layout)

`unit = 413.857`, `x_centre(i) = 217.43 + i*420.857`; row 0 `y = 1689`, row 1 `y = 1790`.

| Row 0 | Esc 217 | Tab 638 | ← 1059 | ↓ 1480 | ↑ 1901 | → 2322 | Bksp 2742 |
|---|---|---|---|---|---|---|---|
| **Row 1** | **Ctrl 217** | **Shift 638** | **Alt 1059** | **P 1480** | **W 1901** | | |

---

## 5. Chunk-2 ownership rules

`KeyDispatcher` now reference-counts modifier DOWNs across overlapping base-key
sessions: DOWN on 0→1, UP on 1→0. Modifier transition meta includes the union of live
sessions; each base/repeat pair retains its own snapshotted modifiers.

Both physical Shift/Ctrl/Alt caps share logical state but count held touches.
Latches have versions so releasing an older base key cannot consume a newly tapped
modifier or Fn latch. Canceling an unused modifier must not turn it into a tap.

Fn is never emitted. Fn+Esc toggles the stored lock; bare Esc always remains Escape.
Holding Fn reverses the locked layer. Caps is local, letters-only Shift inversion;
it resets with editor teardown. FnLock survives teardown. F1–F12 remain primary.

---

## 6. Deliberately not here yet

Light mode · the UHID/Shizuku trackpad · haptics tuning · key preview popups ·
GitHub remote and release CI · release signing. Full-layout on-device acceptance
remains pending; see `README.md`.

`RESEARCH.md` holds the design/research record; some items (such as light-mode tokens)
are proposals, not final decisions.
