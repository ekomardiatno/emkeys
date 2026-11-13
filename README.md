# emkeys

A native Android keyboard for coding in **code-server / VS Code in Chrome** from a
tablet. Modern ThinkPad ANSI geometry, a full function row, and developer modifiers
in the pingwin dark palette. Android 10+ (API 29), Kotlin, no Compose.

## Current milestone

The keyboard-only MVP is implemented: **84 keys**, a 5u spacebar, inverted-T arrows,
Ctrl/Shift/Alt chords and sticky modifiers, key repeat, a local Fn layer, Caps,
persistent FnLock, Fn/Ctrl swap, and keyboard-switching controls. Key legends use
bundled JetBrains Mono. The keyboard needs **no root, Shizuku or network permission**.

The expanded keyboard passed **14 native device tests and 10 Chrome oracle cases**
on a Galaxy Tab S9 Ultra (Android 16), including both orientations, injected
three-finger touches, symbols, shortcuts, Fn and repeat. The original prototype was
also verified against code-server; the expanded build now opens Command Palette
and Quick Open in the real code-server PWA and dismisses them with Escape. Scratch
editing, selection/navigation, coding punctuation, Caps and Ctrl+W also work there.
Basic terminal typing, Ctrl+C and Ctrl+A work; Ctrl+E is intercepted by VS Code's
Quick Open in the tested configuration. **TalkBack and broader terminal workflows
still need acceptance checks below**.
There is no trackpad, light theme
or signed release yet. See `RESEARCH.md` §§9.6–9.8 for the exact evidence and limits.

Local validation: **75 JVM tests pass**, debug and unsigned/minified release APKs
build, and debug lint passes with zero errors (six advisory warnings about tool
versions and an unnecessary resource qualifier). The app declares `VIBRATE` for
Samsung's IME haptic helper; no runtime permission prompt is needed.

## Build

Requires JDK 17 and Android SDK/build tools 36. Configure your SDK in
`local.properties` or the Android SDK environment variables.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Install it, open **emkeys setup**, enable emkeys in Android's keyboard settings,
then select it in the keyboard picker. The setup screen also has a typing test.
The full layout is designed for tablets, not phone-sized touch targets.

## Controls

| Control | Behavior |
|---|---|
| Ctrl / Shift / Alt | Tap to latch for one key; tap twice to lock; third tap clears |
| Held modifier | Chord with other fingers; using it does not also latch it |
| Caps | Letters-only uppercase; Shift reverses it; resets when input ends |
| Fn | Tap for one layer key, or hold to chord |
| Fn + Esc | Toggle FnLock, remembered across sessions |
| Fn while locked | Temporarily reverse the Fn layer |
| Esc alone | Always Escape, even with FnLock |
| Fn + ← / → | Home / End |
| Fn + B / P | Break / Pause (Android represents both with `KEYCODE_BREAK`) |
| Fn + S / K | SysRq / Scroll Lock |
| Fn + Tab | Switch to the previous keyboard |
| Fn + Space | Open the keyboard picker |
| Toolbar | Previous keyboard, keyboard picker, setup |

F1–F12 stay primary; no Windows-only media functions are simulated. **Swap Fn and
left Ctrl** in setup; the layout refreshes the next time the keyboard opens. Both
Alt keys send left Alt (not AltGraph). Right and left Ctrl/Shift share logical
modifier state, with independent touch ownership.

Move off a key to stop its repeat or cancel an unused modifier/action. Base keys
send their first keystroke on touch-down, so sliding away cannot undo that stroke.
TalkBack exposes each cap as an individual virtual button; tapping modifiers first
allows chords without simultaneous touches.

## Limitations

- Win is visibly disabled: the documented Android/Chrome IME path does not deliver
  `metaKey`. This is not a system-level keyboard injector.
- Chrome tab mode reserves shortcuts: F6 focuses the address bar; Ctrl+PgUp/PgDn
  switch browser tabs instead of reaching the page (Chrome 157, Galaxy Tab S9 Ultra).
  Ctrl+Tab was also previously confirmed stolen. Use VS Code remappings as needed;
  the installed code-server PWA's shortcut behavior must be checked separately.
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
- [ ] Hold Ctrl + Shift with two fingers, tap P with a third; no surprise latch.
- [ ] Overlap two base keys under Ctrl; releasing one does not release Ctrl early.
- [ ] Repeat letters/arrows/Backspace; slide off stops repeat without stuck modifiers.
- [ ] Home/End, PgUp/PgDn, Insert, F1–F12 and integrated-terminal shortcuts.
- [ ] Caps + letters/symbols; Shift reverses Caps.
- [ ] Fn navigation, FnLock toggle and persistence, Fn/Ctrl swap.
- [ ] Previous/picker controls work, with and without another recent keyboard.
- [ ] Rotate or switch apps/editors mid-hold; no repeats or modifiers leak afterward.
- [ ] Physical keyboard attached: emkeys remains visible, with no fullscreen editor.
- [ ] TalkBack: navigate and activate individual keys, including latched chords.

### Completed automated device checks (2026-11-13)

- [x] All 84 virtual key bounds are on screen and non-overlapping in both orientations;
  Fn and the bottom row receive touches without hitting Samsung's system controls.
- [x] Native typing, Space/Backspace, Caps/Shift inversion, Fn navigation, FnLock toggle
  and persistence when the input view is reopened.
- [x] Injected three-finger Ctrl+Shift+P releases without leaving a modifier latch.
  This is OS-injected multitouch, not a human touch-panel/ergonomics test.
- [x] Chrome receives trusted, non-composing events for A–Z, digits, all shifted
  punctuation, Ctrl+Shift+P, Ctrl+W, Fn navigation/B/P/S/K and F1–F12 **except F6**.
- [x] Chrome receives Caps inversion, bare Escape under FnLock and 16 repeated
  Ctrl+Left pairs inside one Ctrl envelope.
- [x] Keyboard stays visible with the physical keyboard attached; no fullscreen editor.
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
- [x] Slide-off stops repeat and cancels an unused modifier without latching it.
- [x] Leaving for Home mid-hold and recreating the editor leaks no repeat/modifier.
- [x] Rotating during held Shift+Q stops repeat, clears Shift and preserves initial text.
- [x] Actual setup checkbox persists Fn/Ctrl swap in both directions; reopening the
  input view updates their positions and Fn navigation still works.
- [x] Fn+Space/toolbar picker open the system picker; Previous/Fn+Tab return to a known
  recent Gboard. No-history fallback/cancellation order have JVM policy coverage;
  Android's actual no-history picker UI is still pending.
- [x] Virtual accessibility clicks type `aBc` using one-shot Shift, and Win is disabled.
  TalkBack speech/focus/touch navigation is still pending.

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
their behavior in the installed code-server PWA still needs separate checking.

Try actual three-finger chords and normal typing in both orientations. Then use
TalkBack to explore and activate individual keys and latched chords. Automated virtual
node actions do not establish TalkBack focus order, spoken feedback or touch ergonomics.

### Reproduce device automation

Use an idle test tablet with emkeys enabled; commands below target the single device
selected by `ANDROID_SERIAL`. Save outputs outside the repository if they contain
test keystrokes. Tests temporarily change FnLock/Fn-swap preferences and restore them;
rotation tests unfreeze orientation on completion. The previous-keyboard test needs
a second enabled keyboard (otherwise it is skipped), temporarily seeds its history
using test-only shell authority, and restores emkeys. Tests never disable keyboards.

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
