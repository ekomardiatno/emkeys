# emkeys

An Android `InputMethodService` that provides a ThinkPad-style coding keyboard for
code-server / VS Code in Chrome on tablets. Kotlin, Canvas-based custom view, Android
10+ (API 29), no Compose.

## Current state

- The keyboard has 84 ANSI-style keys, a function row, inverted-T arrows and no extra
  toolbar row. There is no launcher icon; Android's IME settings entry opens setup.
- Ctrl/Shift/Alt work when held in a chord. A double-tap within 300 ms locks one; a
  single tap unlocks it. Fn follows the same gesture for its layer. Fn+Esc separately
  toggles persistent FnLock; holding Fn temporarily reverses FnLock.
- If Android reports an available physical keyboard, the IME suppresses its input
  view. It also hides an already-visible view on connection and requests it again on
  disconnect if the editor remains active. Bluetooth/USB attach-detach behavior needs
  device validation.
- The latest source has 93 passing JVM tests and builds debug/instrumentation APKs;
  lint passes. The installed APK is newer than the last completed device test run.
- TalkBack testing is intentionally skipped at the user's request. Trackpad/Shizuku,
  themes, signing and distribution are out of scope unless requested.

## Architecture

```text
app/src/main/java/com/ekomardiatno/emkeys/
  model/   key roles, evdev scan-code table, ThinkPadLayout, geometry, strokes
  ime/     ModifierState, FnState, KeyboardController, KeyDispatcher, InputMethodService
  view/    Canvas keyboard, touch handling, virtual accessibility nodes and themes
  setup/   setup diagnostics and persisted Fn/Ctrl preferences
app/src/test/        JVM behavior, dispatch, geometry and hardware-keyboard policy tests
app/src/androidTest/ device-level IME, touch, typing and orientation tests
tools/               isolated Chrome oracle and shortcut probes
```

Keep Android `KeyEvent` construction in `KeyEventFactory`; the dispatch core should
remain JVM-testable. Preserve the ordering and ownership rules in `KeyboardController`
and `KeyDispatcher`, especially chord close/release, overlapping pointers and teardown.

## Build and validate

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Do not run instrumentation or send adb input while the tablet is being used in the
code-server PWA. A foreground-app guard cannot tell an editor from a live terminal in
that same PWA; get an idle window first. After a device run, restore the original IME,
rotation and foreground task. Do not edit or type into existing project files during
acceptance; use disposable untitled editors and test terminals.

`README.md` is the current user/build/acceptance guide. `RESEARCH.md` contains research
and historical evidence; check dates and do not treat old conclusions as current
behavior without confirming them against source and README.
