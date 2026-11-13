# emkeys

A native Android keyboard for coding in **code-server / VS Code in Chrome** from a
tablet. Modern ThinkPad ANSI geometry, a full function row, and developer modifiers
in the pingwin dark palette. Android 10+ (API 29), Kotlin, no Compose.

## Current milestone

The keyboard-only MVP is implemented: **84 keys**, a 5u spacebar, inverted-T arrows,
Ctrl/Shift/Alt hold chords and double-tap locks, key repeat, a hold/double-tap Fn layer, Caps,
persistent FnLock, Fn/Ctrl swap, and keyboard-switching controls. Key legends use
bundled JetBrains Mono. The keyboard needs **no root, Shizuku or network permission**.

The toolbar-free build passed **18 native device tests** on a Galaxy Tab S9 Ultra
(Android 16), including both orientations, injected three-finger touches, symbols,
shortcuts, Fn, repeat, accessibility states and the toolbar-free input view. The
preceding build passed **10 Chrome oracle cases**.
The expanded build opens Command Palette and Quick Open in the real code-server PWA
and dismisses them with Escape. Scratch
editing, selection/navigation, coding punctuation, Caps and Ctrl+W also work there.
Basic terminal typing, Ctrl+C and Ctrl+A work; Ctrl+E is intercepted by VS Code's
Quick Open in the tested configuration. Broader terminal workflows still need
acceptance checks below. TalkBack testing is skipped per user preference.
There is no trackpad, light theme or signed release yet. `RESEARCH.md` §§9.1–9.4
hold the original device and feasibility evidence; this README tracks current behavior.

Local validation: **93 JVM tests pass**, debug APKs
build, and debug lint passes with zero errors (six advisory warnings about tool
versions and an unnecessary resource qualifier). The app declares `VIBRATE` for
Samsung's IME haptic helper; no runtime permission prompt is needed.

Shift/Ctrl/Alt/Fn work while held; a fast double-tap locks them and one tap unlocks.
Fn+Esc remains the explicit persistent FnLock action. The IME now suppresses its view
when Android reports an available physical keyboard, and hides an already-open view
on connection; it requests the view again after disconnect if the editor is still active.
The latest APK is installed, but the modifier-lock and physical-keyboard changes have
not yet been device-tested. TalkBack speech and explore-by-touch validation are skipped
per user preference.

## Build

Requires JDK 17 and Android SDK/build tools 36. Configure your SDK in
`local.properties` or the Android SDK environment variables.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Install it, enable emkeys in Android's keyboard settings, then select it in the
keyboard picker. To open setup, use emkeys' settings/gear entry in Android's on-screen
keyboard settings. The app intentionally has no launcher icon. Setup includes a typing test.
The full layout is designed for tablets, not phone-sized touch targets.

## Controls

| Control | Behavior |
|---|---|
| Ctrl / Shift / Alt | Hold for a chord; fast double-tap to lock; tap once to unlock |
| Caps | Letters-only uppercase; Shift reverses it; resets when input ends |
| Fn | Hold for an alternate-layer chord; fast double-tap to lock; tap once to unlock |
| Fn + Esc | Toggle FnLock, remembered across sessions |
| Fn while locked | Temporarily reverse the Fn layer |
| Esc alone | Always Escape, even with FnLock |
| Fn + ← / → | Home / End |
| Fn + B / P | Break / Pause (Android represents both with `KEYCODE_BREAK`) |
| Fn + S / K | SysRq / Scroll Lock |
| Fn + Tab | Switch to the previous keyboard |
| Fn + Space | Open the keyboard picker |
| Setup | Open emkeys' settings/gear entry in Android's on-screen keyboard settings |

The keyboard has no extra toolbar above the keys. Use Android's keyboard-switcher
control in the navigation bar, Fn+Tab for the previous IME, or Fn+Space for the
picker. Open setup through Android's settings entry for emkeys when you need to change preferences.
The latest APK is installed on the tablet; the updated device suite and Bluetooth/USB
attach-detach behavior still need validation during an idle-device window.

F1–F12 stay primary; no Windows-only media functions are simulated. **Swap Fn and
left Ctrl** in setup; the layout refreshes the next time the keyboard opens. Both
Alt keys send left Alt (not AltGraph). Right and left Ctrl/Shift share logical
modifier state, with independent touch ownership.

Move off a key to stop its repeat or cancel an unused modifier/action. Base keys
send their first keystroke on touch-down, so sliding away cannot undo that stroke.
TalkBack exposes each cap as an individual virtual button. Modifier chords require
simultaneous held touches; a fast double-tap locks a modifier and a tap releases it.
Accessibility state descriptions distinguish **Off**, **Held** and **Locked**.
Fn reports FnLock and temporary inversion separately; Esc reports
the persistent lock and how to toggle it, without making bare Esc a toggle button.

## Limitations

- Win is visibly disabled: the documented Android/Chrome IME path does not deliver
  `metaKey`. This is not a system-level keyboard injector.
- Chrome tab mode reserves shortcuts: F6 focuses the address bar; Ctrl+PgUp/PgDn
  switch browser tabs instead of reaching the page (Chrome 157, Galaxy Tab S9 Ultra).
  Ctrl+Tab was also previously confirmed stolen. In the installed code-server PWA,
  Ctrl+Shift+P opened Command Palette, while F6 showed no visible focus change and
  Ctrl+PgUp/PgDn did not switch between the two blank editor tabs in the probe.
- Repeat is generated as discrete key-down/key-up pairs, not DOM `repeat: true`.
- Fn functions depend on the target app handling their Android keycodes.
- In the tested code-server PWA configuration, Ctrl+E opens Quick Open even with
  terminal focus instead of reaching the shell's end-of-line binding. Review VS Code
  keybindings/terminal shortcut settings; emkeys does not change the user's settings.
- No suggestions, autocorrect, alternate language layouts or gesture typing.
- Trackpad/Shizuku integration is a separate future milestone.

## On-device acceptance checklist

Use both the setup typing field and `tools/keyoracle.html`, then the real editor.
The oracle can be served with `python3 -m http.server 8777 --directory tools` and
forwarded with `adb reverse tcp:8777 tcp:8777`.

- [ ] Portrait and landscape: all 84 keys visible, arrow tiers separate, editor usable.
- [ ] Alphabet, digits, every shifted symbol, Space, Enter, Backspace and Delete.
- [x] Ctrl+Shift+P opens Command Palette; Escape closes it; Ctrl+W closes an editor.
- [ ] Hold Ctrl + Shift with two fingers, tap P with a third; modifiers release with the chord.
- [ ] Single-tap Shift/Ctrl/Alt/Fn leaves them inactive; double-tap locks and one tap unlocks.
- [ ] Overlap two base keys under Ctrl; releasing one does not release Ctrl early.
- [ ] Repeat letters/arrows/Backspace; slide off stops repeat without stuck modifiers.
- [ ] Home/End, PgUp/PgDn, Insert, F1–F12 and integrated-terminal shortcuts.
- [ ] Caps + letters/symbols; Shift reverses Caps.
- [ ] Fn navigation, FnLock toggle and persistence, Fn/Ctrl swap.
- [ ] Previous/picker controls work, with and without another recent keyboard.
- [ ] Rotate or switch apps/editors mid-hold; no repeats or modifiers leak afterward.
- [ ] Connect Bluetooth/USB keyboard: emkeys hides; disconnect it while an editor is focused.

### Completed automated device checks (2026-11-13)

- [x] All 84 virtual key bounds are on screen and non-overlapping in both orientations;
  Fn and the bottom row receive touches without hitting Samsung's system controls.
- [x] Native typing, Space/Backspace, Caps/Shift inversion, Fn navigation, FnLock toggle
  and persistence when the input view is reopened.
- [x] Injected three-finger Ctrl+Shift+P releases without leaving a modifier active.
  This is OS-injected multitouch, not a human touch-panel/ergonomics test.
- [x] Chrome receives trusted, non-composing events for A–Z, digits, all shifted
  punctuation, Ctrl+Shift+P, Ctrl+W, Fn navigation/B/P/S/K and F1–F12 **except F6**.
- [x] Chrome receives Caps inversion, bare Escape under FnLock and 16 repeated
  Ctrl+Left pairs inside one Ctrl envelope.
- [x] Expanded build in the real code-server PWA: Ctrl+Shift+P opens Command Palette,
  Ctrl+P opens Quick Open, and Escape dismisses each.
- [x] Disposable untitled editor: code punctuation, Enter, Home/End, Fn navigation,
  Shift selection, Delete/Backspace, Caps inversion and Ctrl+W/save dialog. Only the
  newly created scratch buffer was discarded; existing buffers/terminal input untouched.
- [x] Dedicated Chrome tab-mode probe: F6 focuses the omnibox and Ctrl+PgUp/PgDn switch
  between two oracle tabs without delivering those base keys to the page.
- [x] Separate idle code-server terminal: quoted `printf`, Enter, Ctrl+C cancellation
  and Ctrl+A start-of-line editing. Ctrl+E opens Quick Open, not shell end-of-line.
  The test terminal was closed; the original running terminal was not used for input.
- [x] Overlapping base-key sessions retain Shift for the remaining repeating key after
  the Shift pointer and first base key are lifted; the following key is lowercase.
- [x] Slide-off stops repeat and cancels an unused held modifier.
- [x] Leaving for Home mid-hold and recreating the editor leaks no repeat/modifier.
- [x] Rotating during held Shift+Q stops repeat, clears Shift and preserves initial text.
- [x] Actual setup checkbox persists Fn/Ctrl swap in both directions; reopening the
  input view updates their positions and Fn navigation still works.
- [x] Fn+Space opens the system picker; Fn+Tab returns to a known recent Gboard.
  No-history fallback/cancellation order have JVM policy coverage;
  Android's actual no-history picker UI is still pending.
- [ ] Updated device suite: fast double-tap locks Ctrl/Shift/Alt/Fn and one tap unlocks.
- [ ] Virtual nodes distinguish off/held/locked modifiers; FnLock and temporary inversion
  are described correctly, while bare Esc stays non-checkable.
- [x] Input view exposes all 84 caps without Previous, Switch Keyboard or Setup toolbar
  buttons; Fn+Space opens the picker and Fn+Tab switches to the recent keyboard.

The general checklist above remains open where it includes additional manual or
real-editor behavior. Oracle dispatch tests block browser defaults, so successful
delivery is not proof of a code-server command executing. F6 was omitted from the
main dispatch suite; the separate browser probe confirms tab-mode interception.

### Remaining hands-on acceptance

Use **File → New Text File** for a disposable untitled editor, not an existing project
file. Check coding punctuation, selection/navigation, Delete/Backspace and Ctrl+W;
discard only that scratch buffer (this scratch flow already passed once on the test
tablet). Basic terminal checks also passed in a separate idle terminal; use another
disposable terminal for broader shell shortcuts, never a running task. Recheck Ctrl+E
after any VS Code keybinding changes. Chrome tab-mode F6/Ctrl+PgUp/PgDn are verified;
PWA probe results are listed under Limitations and can be revisited if those shortcuts
are important.

Try actual three-finger chords and normal typing in both orientations. TalkBack speech,
focus order and explore-by-touch are intentionally not acceptance requirements for this
milestone, per user preference.

### Reproduce device automation

Use an idle test tablet with emkeys enabled; commands below target the single device
selected by `ANDROID_SERIAL`. Save outputs outside the repository if they contain
test keystrokes. Tests temporarily change FnLock/Fn-swap preferences and restore them;
rotation tests unfreeze orientation on completion. The previous-keyboard test needs
a second enabled keyboard (otherwise it is skipped), temporarily seeds its history
using test-only shell authority, and restores emkeys. Tests never disable keyboards.
The previous 18-test toolbar-free suite passed on the tablet; the current suite has
not run against the updated modifier-lock/physical-keyboard build. Do not run automation
while someone is using that tablet to work or chat: an activity/IME guard cannot
distinguish editor focus from a live terminal inside the same code-server PWA.

```sh
export ANDROID_SERIAL=YOUR_DEVICE_SERIAL
RESULTS_DIR="$(mktemp -d)"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Remember your current keyboard before selecting emkeys; restore it afterward.
adb shell settings get secure default_input_method
adb shell ime set com.ekomardiatno.emkeys/.ime.EmkeysInputMethodService
adb shell am instrument -w -r -e class com.ekomardiatno.emkeys.KeyboardDeviceTest \
  com.ekomardiatno.emkeys.test/androidx.test.runner.AndroidJUnitRunner
# Export fresh bounds after the rotation tests, in the orientation Chrome will use.
adb shell am instrument -w -r \
  -e class 'com.ekomardiatno.emkeys.KeyboardDeviceTest#exportKeyBounds' \
  com.ekomardiatno.emkeys.test/androidx.test.runner.AndroidJUnitRunner > "$RESULTS_DIR/device-bounds.txt"
python3 tools/device_browser_smoke.py --serial "$ANDROID_SERIAL" \
  --bounds "$RESULTS_DIR/device-bounds.txt" --output "$RESULTS_DIR/browser-results.json"
```

The Python runner uses a loopback-only `keyoracle_server.py` with an ephemeral adb
reverse mapping, selects emkeys temporarily and restores the starting IME on exit.
Only the isolated oracle reports events, with `?report=1`; normal static oracle use
does not transmit keystrokes. The mapping and collector are stopped after the run.

For a targeted Chrome tab-mode compatibility report, reuse the fresh bounds above:

```sh
python3 tools/browser_shortcuts_probe.py --serial "$ANDROID_SERIAL" \
  --bounds "$RESULTS_DIR/device-bounds.txt" --output "$RESULTS_DIR/browser-shortcuts.json"
```

This opens two disposable oracle tabs and reports interception or page delivery for
F6 and Ctrl+PgUp/PgDn. It restores the starting Android task and IME, removes its
reverse mapping, and stops its collector; the two test tabs are left open. Screenshots
and event JSON go beside the specified output file, not into the app or project files.

## Code map

- `model/`: key/layout data, literal evdev table, 84-key layout and pure geometry.
- `ime/`: testable dispatch/controller, modifier/Fn state, Android event adapter/service.
- `view/`: Canvas drawing, multitouch, slide-off and virtual accessibility keys.
- `setup/`: enable/select diagnostics and preferences.
- `app/src/test/`: chord traces, overlap/cancellation, layers and geometry tests.
- `app/src/androidTest/`: on-device touch injection, typing and orientation checks.
- `tools/device_browser_smoke.py`: isolated Chrome dispatch tests and event capture.
- `tools/browser_shortcuts_probe.py`: browser-reserved shortcut reports in owned tabs.
- `RESEARCH.md`: authoritative feasibility record and original hardware evidence.

JetBrains Mono is by the JetBrains Mono Project Authors, under SIL OFL 1.1. The
unmodified font and license are bundled in `res/font/` and `assets/JetBrainsMono-OFL.txt`.
