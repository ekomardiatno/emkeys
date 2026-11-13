# emkeys

A native Android keyboard for coding in **code-server / VS Code in Chrome** from a
tablet. Modern ThinkPad ANSI geometry, a full function row, and developer modifiers
in the pingwin dark palette. Android 10+ (API 29), Kotlin, no Compose.

## Current milestone

The keyboard-only MVP is implemented: **84 keys**, a 5u spacebar, inverted-T arrows,
Ctrl/Shift/Alt chords and sticky modifiers, key repeat, a local Fn layer, Caps,
persistent FnLock, Fn/Ctrl swap, and keyboard-switching controls. Key legends use
bundled JetBrains Mono. The keyboard needs **no root, Shizuku or network permission**.

The expanded keyboard passed **12 native device tests and 10 Chrome oracle cases**
on a Galaxy Tab S9 Ultra (Android 16), including both orientations, injected
three-finger touches, symbols, shortcuts, Fn and repeat. The original prototype was
also verified against code-server; the expanded build now opens Command Palette
and Quick Open in the real code-server PWA and dismisses them with Escape.
**Broader editor/terminal workflows and TalkBack still need acceptance checks below**.
There is no trackpad, light theme
or signed release yet. See `RESEARCH.md` §§9.6–9.7 for the exact evidence and limits.

Local validation: **71 JVM tests pass**, debug and unsigned/minified release APKs
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
- Chrome reserves some shortcuts. Ctrl+Tab was confirmed stolen in browser mode;
  F6 and Ctrl+PgUp/PgDn remain compatibility checks. Use VS Code remappings as needed.
- Repeat is generated as discrete key-down/key-up pairs, not DOM `repeat: true`.
- Fn functions depend on the target app handling their Android keycodes.
- No suggestions, autocorrect, alternate language layouts or gesture typing.
- Trackpad/Shizuku integration is a separate future milestone.

## On-device acceptance checklist

Use both the setup typing field and `tools/keyoracle.html`, then the real editor.
The oracle can be served with `python3 -m http.server 8777 --directory tools` and
forwarded with `adb reverse tcp:8777 tcp:8777`.

- [ ] Portrait and landscape: all 84 keys visible, arrow tiers separate, editor usable.
- [ ] Alphabet, digits, every shifted symbol, Space, Enter, Backspace and Delete.
- [ ] Ctrl+Shift+P opens Command Palette; Escape closes it; Ctrl+W closes an editor.
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
  Ctrl+P opens Quick Open, and Escape dismisses each. No files or terminal input changed.
- [x] Overlapping base-key sessions retain Shift for the remaining repeating key after
  the Shift pointer and first base key are lifted; the following key is lowercase.
- [x] Slide-off stops repeat and cancels an unused modifier without latching it.
- [x] Leaving for Home mid-hold and recreating the editor leaks no repeat/modifier.
- [x] Reopening the input view applies persisted Fn/Ctrl swap; Fn navigation still works.
- [x] Fn+Space/toolbar picker open the system picker; Previous/Fn+Tab return to a known
  recent Gboard. The no-history fallback is still pending.
- [x] Virtual accessibility clicks type `aBc` using one-shot Shift, and Win is disabled.
  TalkBack speech/focus/touch navigation is still pending.

The general checklist above remains open where it includes additional manual or
real-editor behavior. Oracle dispatch tests block browser defaults, so successful
delivery is not proof of a code-server command executing. F6 was deliberately omitted;
its browser compatibility remains unverified.

### Remaining hands-on acceptance

Use **File → New Text File** for a disposable untitled editor, not an existing project
file. Check coding punctuation, selection/navigation, Delete/Backspace and Ctrl+W;
discard only that scratch buffer. For terminal checks, create a separate idle terminal
rather than sending test shortcuts into a running shell task. Test F6 and
Ctrl+PgUp/PgDn separately in Chrome tab mode and the installed code-server PWA.

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

## Code map

- `model/`: key/layout data, literal evdev table, 84-key layout and pure geometry.
- `ime/`: testable dispatch/controller, modifier/Fn state, Android event adapter/service.
- `view/`: Canvas drawing, multitouch, slide-off and virtual accessibility keys.
- `setup/`: enable/select diagnostics and preferences.
- `app/src/test/`: chord traces, overlap/cancellation, layers and geometry tests.
- `app/src/androidTest/`: on-device touch injection, typing and orientation checks.
- `tools/device_browser_smoke.py`: isolated Chrome dispatch tests and event capture.
- `RESEARCH.md`: authoritative feasibility record and original hardware evidence.

JetBrains Mono is by the JetBrains Mono Project Authors, under SIL OFL 1.1. The
unmodified font and license are bundled in `res/font/` and `assets/JetBrainsMono-OFL.txt`.
