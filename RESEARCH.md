# emkeys — a ThinkPad keyboard as an Android IME

Research + feasibility, 2026-11-11. Project name is provisional.

**Goal:** type code in code-server (`mac.jalanpintas.web.id`) from an Android tablet in
Chrome, with every key a developer needs — Ctrl, Alt, Esc, Tab, arrows, F1–F12,
PgUp/PgDn, Home/End, Ins/Del — laid out and dressed as a ThinkPad keyboard, in the
pingwin design language, dark and light.

---

## 0. Verdict up front

**The keyboard is viable. Build it as a plain `InputMethodService`.** No root, no
Shizuku, no ADB ritual, Play-Store-shaped if we ever want that.

The folklore that soft keyboards can't do Ctrl chords in Chrome is **wrong** — it
describes the `commitText()` path, not the `InputConnection.sendKeyEvent()` path.
Chromium's `ImeAdapterImpl.getModifiers()` reads `KeyEvent.getMetaState()` and maps it
to `WebInputEventModifier.CONTROL_KEY` etc. So `Ctrl+Shift+P` from our own IME arrives
at VS Code as a genuine `keydown` with `ctrlKey: true`.

**The trackpad is a different and much harder problem** — there is no pointer equivalent
of `sendKeyEvent`, so an IME fundamentally cannot move a cursor. It needs a separate
mechanism, and the honest answer is that it's a phase 2 with a probe to run first (§6).

Three pieces of unexpectedly good news, all verified in Chromium source:

1. **`Ctrl+W` and `Ctrl+T` are NOT stolen by Chrome before the page.** They live in
   `KeyboardShortcuts.onKeyDown`, reached only *after* the renderer declines — and
   VS Code calls `preventDefault()` on anything it binds. The tab will not close.
2. **VS Code web on Android dispatches keybindings on `keyCode`, not `code`** (it falls
   back to `FallbackKeyboardMapper` because `navigator.keyboard` is undefined on
   Android). `keyCode` is exactly what Chromium populates correctly from our Android
   keycode.
3. **xterm.js reads only `keyCode` + the modifier booleans.** `ev.code` appears nowhere
   in its `Keyboard.ts`. The integrated terminal will work.

---

## 1. How the keys actually get there

### 1.1 The dispatch path

`InputConnection.sendKeyEvent()` → `InputMethodManager.dispatchKeyEventFromInputMethod()`
→ `ViewRootImpl.dispatchKeyFromIme()`, which enqueues with `FLAG_DELIVER_POST_IME`. The
event **skips the IME stages but still traverses the normal post-IME view pipeline**
(`DecorView` → `Window.Callback` → `Activity.dispatchKeyEvent` → view hierarchy). It is
not a private side-channel to the editor — which is why Chrome can still intercept a
few keys (§3.2).

### 1.2 The chord envelope

Per key press, in order:

1. `ACTION_DOWN` for each latched modifier's real keycode (`KEYCODE_CTRL_LEFT`,
   `KEYCODE_SHIFT_LEFT`, `KEYCODE_ALT_LEFT`)
2. `ACTION_DOWN` of the base key **with `metaState` already populated**
3. `ACTION_UP` of the base key, same `metaState`
4. `ACTION_UP` of each modifier, reverse order

Sending the modifier down/up pair matters independently of `metaState` — Blink and some
editors track modifier *key* events separately from the bits.

Use the **10-arg** constructor:

```
KeyEvent(downTime, eventTime, action, keyCode, repeat, metaState,
         KeyCharacterMap.VIRTUAL_KEYBOARD, scanCode,
         FLAG_SOFT_KEYBOARD | FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD)
```

### 1.3 Seven rules that make or break it

| # | Rule | Why |
|---|---|---|
| 1 | Set `META_CTRL_ON` **alongside** `META_CTRL_LEFT_ON` | Chromium's `getModifiers()` tests `META_CTRL_ON` only; `META_CTRL_LEFT_ON` alone is never read |
| 2 | Use `META_ALT_LEFT_ON`, never `META_ALT_RIGHT_ON`, for plain Alt | `META_ALT_RIGHT_ON` maps to `ALT_GR_KEY` → `AltGraph`, not `altKey`. VS Code reads `getModifierState('AltGraph')` separately |
| 3 | Don't bother with Super/Meta | `getModifiers()` has **no** `META_KEY` assignment at all. `KeyboardEvent.metaKey` is unreachable from an Android IME. Harmless — VS Code treats us as Linux and uses Ctrl |
| 4 | Call `finishComposingText()` **before every chord** | Blink sets `isComposing` from the live composition, and VS Code hard-discards composing keydowns (`keyCode = KEY_IN_COMPOSITION`). Our Ctrl+X would silently vanish |
| 5 | Never route a modified key through `commitText()` | That's where `keyCode: 229` / `key: "Unidentified"` comes from — Chromium wraps commits in synthetic events with a null Java `KeyEvent` |
| 6 | Set the **evdev scancode** | Chromium on Android uses the raw evdev column with **no XKB +8 offset**, so `getScanCode()` feeds `event.code`/`event.location` directly. Empty `event.code` is the historic Android soft-keyboard tell. Costs one integer, both reference keyboards skip it |
| 7 | Run our own key-repeat timer | `repeatCount` is **not forwarded** by `ImeAdapterImpl.sendKeyEvent`; `event.repeat` is always `false`. Held arrows must emit discrete DOWN/UP pairs |

Scancodes (evdev, from `dom_code_data.inc`):

> **Corrected 2026-11-12 (chunk 1).** The block below previously read
> `KeyA 30 … KeyZ 44` and `F1 59 … F12 88`. Both ellipses are wrong, and wrong in a
> way that fails *silently*. **evdev codes are positional, not alphabetical** — they
> follow the physical rows of a US keyboard, not the alphabet:
>
> ```
> row 1:  Q 16  W 17  E 18  R 19  T 20  Y 21  U 22  I 23  O 24  P 25
> row 2:  A 30  S 31  D 32  F 33  G 34  H 35  J 36  K 37  L 38
> row 3:  Z 44  X 45  C 46  V 47  B 48  N 49  M 50
> ```
>
> Read as "A=30, B=31, C=32 …" you get `P` = 45 and `W` = 52 — which arrive in the
> browser as `code: "KeyX"` and `code: "KeyComma"`. Nothing errors: `keyCode` is still
> correct, so VS Code (which dispatches on `keyCode`) still works, and only
> `event.code` is quietly lying. The F-row is also **not contiguous**:
> `F1..F10 = 59..68`, but `F11 = 87` and `F12 = 88`.
>
> Therefore `model/Evdev.kt` is a **literal per-key table with a unit test**
> (`EvdevTableTest`), never a formula. Values verified against the NDK's
> `linux/input-event-codes.h`, and confirmed end-to-end on device: tapping our `P`
> cap produced `code=KeyP keyCode=80`, and our `W` cap produced `code=KeyW keyCode=87`.

```
Escape 1   Digit1..0 2..11   Minus 12  Equal 13  Backspace 14  Tab 15
Q 16  W 17  E 18  R 19  T 20  Y 21  U 22  I 23  O 24  P 25
A 30  S 31  D 32  F 33  G 34  H 35  J 36  K 37  L 38
Z 44  X 45  C 46  V 47  B 48  N 49  M 50
BracketLeft 26  BracketRight 27  Backslash 43  Semicolon 39  Quote 40
Backquote 41  Comma 51  Period 52  Slash 53  Enter 28  Space 57
ControlLeft 29  ShiftLeft 42  AltLeft 56  MetaLeft 125
ControlRight 97  ShiftRight 54  AltRight 100  MetaRight 126  CapsLock 58
F1..F10 59..68   F11 87   F12 88      (note the gap)
Home 102  ArrowUp 103  PageUp 104  ArrowLeft 105  ArrowRight 106
End 107  ArrowDown 108  PageDown 109  Insert 110  Delete 111
```

### 1.4 IME window rules

- **`onEvaluateFullscreenMode()` → `false`, unconditionally.** The default selects
  fullscreen in landscape and would cover code-server with an extract-text editor.
  Non-negotiable on a landscape tablet.
- A persistent modifier/F-key strip belongs in **`onCreateCandidatesView()`**: the
  candidates view *"will never cause application windows to resize, only cause them to
  be panned"*. Toggling it therefore doesn't retrigger a code-server layout reflow.
  That's a genuinely nice trick for an IDE.
- **There is no floating/split/resize API.** Samsung's and SwiftKey's floating keyboards
  are implemented *inside their own input view* — a narrower keyboard laid out in a
  full-width taller window, with `onComputeInsets()` reporting the truth. Fully
  available to us; just view layout. This is also how the landscape trackpad dock works.
- `switchToPreviousInputMethod()` on a globe key = one-tap hop to Gboard for prose.
  `showInputMethodPicker()` for the full list. Both public API.

### 1.5 Prior art and licences

| Project | Licence | State | Use to us |
|---|---|---|---|
| **klausw/hackerskeyboard** | **Apache-2.0** | last push 2024-10, author says no more significant updates, codebase is a fork of Android 2.3 LatinIME | **The decisive asset.** Lift `getMetaState()` / `sendModifiedKeyDownUp()` / the chording state machine verbatim with attribution |
| Julow/Unexpected-Keyboard | GPL-3.0 | very active | **Read, don't copy.** Best-written: correct flags, `VIRTUAL_KEYBOARD` deviceId, and the sharp detail that `setSelection()` must be refused under modifiers and replaced with DPAD key events so word-jump/selection work |
| florisboard | Apache-2.0 | active, Kotlin, 8.8k★ | Best greenfield skeleton (service scaffold, theming, layout format). Not a dev keyboard — we add §1 ourselves |
| HeliBoard / OpenBoard | GPL-3.0 | HeliBoard active | AOSP LatinIME lineage. Viral licence, skip as a source |
| AnySoftKeyboard | Apache-2.0 | active | Heavyweight, weaker modifier story |

**Termux's extra-keys row proves nothing here** — it is in-process UI writing straight
to the pty (`session.write(KeyHandler.getCode(...))`). No `InputConnection`, no
`KeyEvent`, no IME. It's the most-misunderstood data point in this space. Its true
analogue for us is an in-page JS overlay, not a keyboard.

---

## 2. Rejected approaches (and why)

| Approach | Verdict |
|---|---|
| **Bluetooth HID self-loopback** (`BluetoothHidDevice`, API 28+) | **Impossible.** `connect()` takes a *remote* host; one controller can't be HID Device and HID Host for itself. Same for USB gadget — the tablet would be the gadget |
| **Accessibility service for keys** | **Cannot inject.** `onKeyEvent` is observe-and-filter only; `dispatchGesture` is touch-only; `performGlobalAction` is a fixed enum. No `injectKeyEvent` exists |
| **Chrome extension** | Chrome for Android has no extensions. Firefox for Android does (and would change the whole §3.2 analysis) — but that means changing browsers |
| **Shizuku + `injectInputEvent`** for keys | Works, truly system-level — but §1 already gets us what we need, and *"the startup steps need to be performed again after each reboot"* |
| **Buy a Bluetooth keyboard** | The honest baseline: real `event.code`, `repeat`, `metaKey`, native auto-repeat, zero work. Two catches: a hardware keyboard's events hit `Activity.dispatchKeyEvent` **first**, so Chrome's reserved keys bite *harder*; and by default `onEvaluateInputViewShown()` suppresses soft keyboards when a hard keyboard is present — though emkeys overrides that, so the two *do* compose (see 5.3) |

---

## 3. The hard ceiling — what we can never do

### 3.1 Android-level

- **`INJECT_EVENTS` is `protectionLevel="signature"`.** Platform-signed or uid `shell`
  only. This is the wall behind everything else.
- **No delivery without focus.** `getCurrentInputConnection()` is `null` when no editor
  is bound. One channel, the bound editor's.
- **No system/global shortcuts.** We're inside the served view's `ViewRootImpl`. No
  launcher, no Recents, no other app, no `PhoneWindowManager`. Ctrl+Alt+Del is not a
  thing we can send. The only system key the framework hands an IME is
  `KEYCODE_BACK` for its own window (`setBackDisposition()`).
- **`KeyboardEvent.metaKey` unreachable** (§1.3 rule 3).
- **`KeyboardEvent.repeat` always false** (rule 7).
- **We are not an `InputDevice`.** No `KeyCharacterMap` layout selection, no Android 14+
  per-device keyboard-layout picker, no physical-keyboard settings surface, and
  Android 16's Mouse Keys can't be driven by us.
- **No floating/resizable IME window via public API** (§1.4).

### 3.2 Chrome-level: the keys Chrome keeps

From `KeyboardShortcuts.dispatchKeyEvent`, whose own javadoc says it runs *"before the
active view or web page gets a chance to handle the key event"*:

| Key | Fate |
|---|---|
| **`F6`** | **Unconditionally lost** — `return true` always (focus-row switching) |
| **Ctrl+Tab / Ctrl+Shift+Tab** | Tab switching. Lost |
| **Ctrl+PageDown / Ctrl+PageUp** | Tab switching. Lost |
| `KEYCODE_SEARCH`, `KEYCODE_MENU` | Always consumed |
| **bare `Esc`** | Usually survives — consumed only if a Chrome overlay/find-bar/fullscreen actually handled it |

Everything else (Ctrl+T/W/N/L/R/F/D/K/O/Q, Ctrl+1–9, F3, F5, F10, Alt+Home) is
post-page. **So remap only those four VS Code commands** and we're done.

Caveat worth a 5-minute device test: Chrome patched around the IME path with
`ChromeActivity.preHandleKeyboardEvent()`, gated on the `UniversalKeyboardHandling`
feature flag. Its production default could not be determined from source (the `true` at
`ChromeFeatureList.java:992` is inside `sFlagsDefaultValuesInTests`). **If it's off,
even F6 and Ctrl+Tab reach the page.** Test, don't assume.

Also: **`navigator.keyboard.lock()` does not exist on Android and never will** — the
Chromium Intent-to-Ship says *"there are no plans for supporting Android."* So no
Keyboard Lock escape hatch. Installing code-server as a PWA is still worth doing (it
ships a service worker) for the Esc-overlay and tab-strip cases, but §3.2 shows it
matters far less than it would on desktop.

### 3.3 The guaranteed fallback

For any key Chrome ever steals: an **in-page JS overlay** dispatching
`new KeyboardEvent(...)` is structurally immune — synthetic events have
`isTrusted: false`, so they reach VS Code's listeners but can never trigger browser
shortcut handling. Keep it in the back pocket; see §6.6 for how to inject it.

---

## 4. The ThinkPad side

### 4.1 Pick an era — they are not mixable

**Classic 7-row** (IBM → xx20: T420, X220, W520). The "7th row" is a *partial nav
strip*, not a full row:

- `Esc` is **2u tall**, spanning rows 1–2, far left
- `Delete` is **2u tall**, directly above `Backspace`
- Top strip right side: `PrtSc`/SysRq · `ScrLk`/NmLk · `Pause`/Break · `Insert` · gap ·
  `Delete` · `Home` · `PgUp`
- F-row right end: `End` · `PgDn`
- The keys flanking `↑` are the dedicated **browser Back / Forward** keys, **not**
  PgUp/PgDn (common myth — PgUp/PgDn are in the top-right nav strip)
- Arrows **full height**
- `Fn` in the bottom-left corner, left of `Ctrl`, **blue legend**
- **Blue Enter keycap** (whole cap tinted, not just the legend)
- All Fn-layer legends in **blue**
- Hardware buttons above the F-row: mute, Vol−, Vol+, mic-mute, blue ThinkVantage, power
- Embedded numeric keypad secondaries on `7 8 9 0 - = U I O P J K L M`

**Modern 6-row "Precision"** (xx30 → present). Lenovo ran 350 hours of user testing in
four countries before committing to it, and dropped keys they measured as unused:

- `ScrLk`, `Pause`, `Break`, `SysRq`, `NumLock` → **Fn combinations only**
- `Insert` relocated into the top row after `F12`
- Browser Back/Forward **gone**, replaced by `PgUp`/`PgDn` **flanking `↑`**
- Hardware volume/mic buttons folded into F1–F4; ThinkVantage button gone
- Arrows **half-height**; `[PgUp ↑ PgDn]` over `[← ↓ →]`
- Top row: `Esc`(+`FnLk`) · F1–F12 · `Home` `End` `Insert` `Delete`
- Bottom row: `Fn` `Ctrl` `Win` `Alt` `Space` `Alt` `PrtSc` `Ctrl`
- Fn-layer legends **grey/white — no blue anywhere**

And **three sub-generations of the F-row** inside "modern", which look and behave
differently:

| Gen | F-row |
|---|---|
| xx30 (2012) | `F1`…`F12` **primary**, media icons small secondaries. Legacy Fn mapping |
| xx40 (2013+) | Media/feature icon **large and centred**, `F1`…`F12` small bottom-right. `FnLk` appears on Esc |
| 2019+ | F9/F10/F11 become **Unified Communications** (notifications, answer, decline), F12 Vantage |
| 2024+ | F8 power mode, F9 PrtSc, F10 Snipping Tool, F11 Phone Link, F12 Vantage |

*(Outlier, do not model: X1 Carbon **Gen 2** shipped a 5-row board with a capacitive
adaptive touch-strip instead of a function row and no physical Caps Lock. Universally
panned, reverted in Gen 3.)*

### 4.2 Geometry — the numbers to build the grid from

Official: **key pitch 19.05 mm**, **keycap 16 mm** (16 + 3.05 gap = 19.05 ✓), key travel
**1.8 mm** on T14/T15 Gen 2+ (1.5 mm on T14s/X1C Gen 9). Keyboard module 28.5 cm on a
14″. Cross-check: the alpha block is **15u** → 15 × 19.05 = 285.75 mm ≈ 28.5 cm. The
model is confirmed.

The only machine-readable ThinkPad geometry found is
**`pfaion/x1carbon-xkb-geometry`** (MIT, X1 Carbon Gen 6, US). Normalised from it — and
note **every main row sums to exactly 15u**:

| Key | ThinkPad | ANSI 104 | Note |
|---|---|---|---|
| Alpha | **1.00u** | 1.00u | — |
| Tab | **1.45u** | 1.50u | |
| Backslash | **1.55u** | 1.50u | wider |
| CapsLock | **1.65u** | 1.75u | |
| **Enter** | **2.35u**, single-row rectangle | 2.25u | wider |
| Backspace | **2.00u** | 2.00u | |
| Left Shift | **2.15u** | 2.25u | |
| **Right Shift** | **2.85u** | 2.75u | wider |
| Ctrl / Fn | **1.15u** | 1.25u | |
| **Spacebar** | **5.00u** | 6.25u | **much shorter** |
| F-row key | **0.825u** pitch | 1.00u | own sub-pitch |
| Esc / Del in F-row | **1.1625u** | 1.00u | |
| Arrows | **0.95u** wide, 0.6u tall | 1.00u full | |

Row heights: main **0.9u**, F-row **0.6u**, PgUp/PgDn **0.525u**. Corner radius ≈ 0.05u
(~1 mm) — subtle, not rounded-rect-heavy. Aspect ratio of the whole block including the
arrow island ≈ **2.46 : 1**.

**Do not reuse ANSI 104 widths.** The spacebar especially: 5u, not 6.25u.

ANSI / ISO / JIS: ANSI is 84 keys with the rectangular Enter. ISO adds the key left of
`Z` and the tall inverted-L Enter — and that's where ThinkPad's cramped-key problem
lives (T14 Gen 3–6 shrank the four keys left of Enter to **13 mm**; Gen 7 shrank Enter to
win two of them back). JIS is 86 keys with a **shorter spacebar** and five Japanese input
keys. **We want ANSI.**

### 4.3 Fn layer — verified, including two corrections

Modern (T430s UG p.37, verbatim, and consistent through T14 Gen 5):

```
Fn+Esc    FnLock toggle (LED on Esc)
Fn+Space  keyboard backlight cycle (4 states incl. ThinkLight on xx30)
Fn+Tab    Magnifier
Fn+B      Break
Fn+P      Pause
Fn+S      SysRq
Fn+K      ScrLk          ← not Fn+C. Fn+C is IdeaPad, never ThinkPad
Fn+←/→    Home / End
Fn+4      Sleep
Fn+N      system information (2024+)
```

Classic (T420 HMM ch.6 Table 8): `Fn+PrtSc` = **SysRq**, `Fn+ScrLk` = NumLock,
`Fn+Pause` = **Break**, `Fn+PgUp` = ThinkLight, `Fn+Home/End` = brightness,
`Fn+Space` = Full Screen Magnifier, `Fn+↓↑→←` = Play-Pause / Stop / Next / Prev,
`Fn+F2` lock, `Fn+F4` sleep, `Fn+F12` hibernate, `Fn+F1/F9/F10/F11` **Reserved**.

BIOS behaviours worth knowing because users expect them: **Fn/Ctrl swap**
(`FnCtrlKeySwap`), **Fn sticky** (`FnSticky`), **F1–F12 as primary** (`FnKeyAsPrimary`,
i.e. persistent FnLock). X1C Gen 13 even adds "Fool proof FN Ctrl" making
Fn+A/C/F/V/X/Z behave as Ctrl combos.

### 4.4 Look

- Sapper's **"black cigar box"**, "raven black". TrackPoint red (launched as "IBM
  Magenta" because red meant emergency-power-off on mainframes), with a grid of fine
  dimples and a slightly concave crown.
- **No published typeface.** Community research pins the original 700/701 legends as
  Linotype **Helvetica ExtraCompressed**; modern caps are a plain condensed neutral
  grotesque in the Helvetica/Arial family. Roboto Condensed reads accurately.
- **Legend alignment: top-left (ISO-standard printing) through Gen 6; centred from
  T14 Gen 7 (2025).** On xx40+ F-rows the media icon is large and centred with the
  `F1`…`F12` small at bottom-right.
- Photo-sampled, uncalibrated: keycap face `#393836`–`#4B4B49`, deck `#1E1D1B`, blue
  Enter ≈ `#60677E`, blue Fn legend ≈ `#6B8BC9`. Matte, faintly textured — low
  specularity, no gloss highlight.
- **Smile keys**: modern caps have a scalloped bottom edge curving upward, which
  enlarges the usable surface without growing the board. Sapper sketched "D"-shaped
  keys in the early 90s; David Hill revived it for the X1 Carbon. Classic caps are
  square and properly **dished**; modern are near-flat with a very shallow dish.
- **ThinkPad 25** (2017) is the single best official artifact for "Lenovo says the
  classic layout looks like this": T470 chassis, classic 7-row, blue Enter, backlit,
  **square not scalloped** caps, retro multicolour logo.

### 4.5 Sources that actually have the data

- `pfaion/x1carbon-xkb-geometry` — MIT, the only machine-readable geometry
- **T420/T420i HMM** ch.6 Table 8 — the complete classic Fn table
- **T430s/T430si UG** p.37 — the verbatim modern Fn+B/P/S/K
- Deskthority wiki "ThinkPad keyboard" — best structured description of both eras
- sharktastica.co.uk ThinkPad layouts — confirms the 2u-tall Esc and Delete
- Lenovo PSREF — pitch, travel, touchpad size, official keyboard spec strings
- Lenovo HT512204 / HT514930 — current per-generation F-row tables
- Commons photos, flat-on and high-res: `Lenovo_ThinkPad_X220_7-row_keyboard_US_International_layout.jpg`
  (**the definitive classic reference**), `Keyboard_Lenovo_ThinkPad_T440p_(QWERTY_layout).jpg`
  (definitive xx40)
- **Modern HMMs have no keyboard diagrams** — only "FRU 1010 Keyboard". Classic-era
  HMMs are the ones with the useful documentation
- **keyboard-layout-editor.com has no ThinkPad presets.** Transcribing §4.2 is the path
- `eyemyth/qmk_firmware` has a `lenovo_t420` keymap — useful for the exact keycode set
- `hamishcoleman/thinkpad-ec` documents the matrix differences between eras

---

## 5. The breakdown you asked for

### 5.1 Replicate — the point of the exercise

**Layout & geometry**
- The **15u main grid** with ThinkPad widths from §4.2, not ANSI
- The **5u spacebar** — it's the most visible proportional tell
- The **0.825u F-row on its own sub-pitch**, visually distinct from the alpha rows
- Row heights 0.9u / 0.6u / 0.525u
- The **inverted-T arrow island** with the era's correct flanking keys
- `Fn` in the **bottom-left corner**, left of `Ctrl`
- **2.35u rectangular ANSI Enter**, 2.85u right Shift, 2u Backspace
- Subtle ~1 mm corner radius

**Keys that earn their place on a code tablet**
- `Esc` `Tab` `Ctrl` `Alt` `Shift` — the whole reason we're here
- Full `F1`–`F12`
- Arrows + `PgUp` `PgDn` `Home` `End` `Insert` `Delete` `Backspace`
- `` ` `` and `\` and `[ ] ; ' , . / - =` — all of them, unshifted, no long-press
- `Win`/Super **as a rendered key** (it's part of the layout) — see 5.3, it can't fire

**Behaviour**
- **Latched *and* chorded modifiers.** Tap Ctrl to latch for one key; hold Ctrl and
  tap to chord. Hacker's Keyboard's state machine does both
- **Sticky/caps-style double-tap to lock** a modifier for multi-key sequences
  (Ctrl+K Ctrl+C)
- Visible **latched state** on the keycap
- **Fn layer** with the correct legends and the real mappings — `Fn+B`/`P`/`S`/`K` for
  Break/Pause/SysRq/ScrLk, `Fn+←/→` for Home/End
- **FnLock** (`Fn+Esc`), with the Esc indicator
- **Fn/Ctrl swap** as a setting — it's a genuine ThinkPad BIOS option and the single
  most-complained-about ThinkPad trait
- Our own **key-repeat timer** for held arrows/Backspace (rule 7)
- `switchToPreviousInputMethod()` on a globe key for prose → Gboard

**Look**
- Raven-black deck `#1E1D1B`-ish, two-tone caps, matte and low-specular
- Condensed grotesque legends, **top-left aligned** (pre-2025 ThinkPad convention)
- Era-correct Fn-legend colour: **blue** on classic, **grey/white** on modern
- The **red TrackPoint** — as the trackpad's visual anchor/toggle affordance, which is
  both authentic and functional
- The **blue Enter** if we go classic

### 5.2 Don't replicate — authentic but pointless here

- **Classic hardware button strip** — mute/Vol±/mic-mute/ThinkVantage/**power**. The
  tablet owns volume and power. A power button in a keyboard is actively hostile
- **ThinkVantage / Vantage / Phone Link / Snipping Tool / notification-centre F-keys** —
  Windows-only software hooks with nothing to call
- **Unified Communications keys** (answer/decline Teams call) — no Teams
- **Browser Back/Forward** keys (classic) — `Alt+←/→` already exists and Chrome's own
  gestures cover it. Render them if we go classic for fidelity; map them to
  `KEYCODE_BACK`/`FORWARD` or just to Alt+arrow
- **Embedded numeric keypad** (`Fn+ScrLk` + the `7890UIOPJKLM` secondaries) — a
  numeric-layer *layout* is more useful than emulating a 2011 workaround; the legends
  are pure noise on a 10″ screen
- **ScrLk / NumLock as physical keys** — Lenovo dropped them in 2012 for measured
  reasons. Keep them on the Fn layer only
- **Backlight controls, ThinkLight, `Fn+Space`** — nothing to light. `Fn+Space` is
  better spent on something real
- **Sleep / hibernate / lock** (`Fn+4`, `Fn+F4`, `Fn+F12`, `Fn+F2`) — can't anyway (5.3)
- **Keycap travel/dish/smile 3-D rendering** — model the *silhouette* (scalloped bottom
  edge) in 2-D. Simulated depth on a flat panel reads as skeuomorphic clutter and costs
  frame time on every press
- **ISO and JIS variants** — ANSI only. ISO's cramped keys next to the big Enter are a
  *defect* Lenovo spent three generations fixing
- **CapsLock LED in the keycap** — we have real state to show instead
- **Three TrackPoint buttons + middle scroll button** — see §6; the middle button is a
  scroll *modifier*, and our scroll story is different
- **X1 Carbon Gen 2's adaptive touch-strip** — never do this

### 5.3 Can't replicate — the physics won't allow it

| ThinkPad behaviour | Why not |
|---|---|
| **Super/Win key doing anything** | Chromium's `getModifiers()` has no `META_KEY` mapping. `metaKey` is permanently `false`. We can draw the key; it cannot fire |
| **`F6`** | Chrome unconditionally consumes it before the page |
| **`Ctrl+Tab`, `Ctrl+PgUp`, `Ctrl+PgDn`** | Chrome tab-switching, pre-page. Remap the VS Code commands instead |
| **Ctrl+Alt+Del** and every system shortcut | `INJECT_EVENTS` is signature-level. We live in the focused view's `ViewRootImpl` |
| **Fn+F4 sleep / Fn+F2 lock / power** | Same wall |
| **True key auto-repeat** | `repeatCount` isn't forwarded; `event.repeat` is always false. Our timer emits N discrete presses, so anything doing repeat-suppression sees N distinct keys |
| **Any key while no editor is focused** | `getCurrentInputConnection()` is `null` |
| **A real Fn key** | `Fn` on a ThinkPad is handled in the EC below the keycode layer and never reaches the OS. Ours is a pure UI layer-shift — which is actually *more* capable, since we can map it freely |
| **FnLock surviving as firmware state** | It's an app preference. Same effect, different mechanism |
| **Physical feel** — 1.8 mm travel, dish, tactility | Glass. The only honest substitutes are haptics + a press animation |
| **Pointer input from the keyboard itself** | No `InputConnection` motion API at all (§6.1) |
| **Being a real keyboard to Android** | We're not an `InputDevice`: no `KeyCharacterMap` layout selection, no Android 14+ layout picker, no driving Android 16 Mouse Keys |
| ~~**Hardware + soft keyboard together**~~ | ~~`onEvaluateInputViewShown()` hides soft keyboards when a hard keyboard is attached~~ — **WRONG, struck 2026-11-12.** See below |

**Correction (chunk 1, verified on device).** The last row was wrong. That suppression
is not a wall, it is a **default**: it lives in `InputMethodService.onEvaluateInputViewShown()`,
which is a *public overridable method*. `EmkeysInputMethodService` overrides it to
return `true` unconditionally, and the soft keyboard then shows regardless of any
attached hardware keyboard — no `WRITE_SECURE_SETTINGS`, no `show_ime_with_hard_keyboard`
change, no root.

Proven on the SM-X916B with `show_ime_with_hard_keyboard = 0` *and* the Lenovo
TrackPoint Keyboard II connected (`dumpsys input` → `Device 17: TrackPoint Keyboard II`,
`KeyboardType: 2`): `dumpsys input_method` reported `mInputShown=true`,
`mIsInputViewShown=true`, `mInFullscreenMode=false`, and emkeys drew over code-server.

Consequence for the roadmap: §9.3's planned `show_ime_with_hard_keyboard` warning in
`SetupActivity` is **informational only**, not a prerequisite. emkeys and a physical
ThinkPad keyboard *do* compose, which also makes the TrackPoint Keyboard II usable as a
side-by-side ground-truth oracle while developing.

---

## 6. The trackpad

Requested: docked **right** automatically in landscape; in portrait a toggle that
**covers the whole keyboard**. The layout half of that is easy (§1.4 — it's just view
layout plus `onComputeInsets`). The *input* half is the hard part.

### 6.1 An IME cannot inject pointer input. Full stop.

`InputConnection` has exactly one event method: `sendKeyEvent(KeyEvent)`. Grepping the
full 1465-line AOSP file for "motion" returns **zero matches** — no `sendMotionEvent`,
no `@SystemApi` equivalent, nothing hidden. `View.dispatchTouchEvent` reaches only our
own hierarchy. The handwriting-gesture API (API 34) takes screen rects but is resolved
by the target editor's text layout and produces no pointer events in Chrome.

So the trackpad is **not** a keyboard feature wearing a different skin. It is a second
mechanism bolted alongside, and every option costs something.

### 6.2 Ranked options

**#1 — Shizuku + `injectInputEvent` with `TOOL_TYPE_MOUSE` + our own drawn cursor.**
Reflect `IInputManager.injectInputEvent` as uid 2000; build `MotionEvent`s with
`SOURCE_MOUSE`, `toolType = TOOL_TYPE_MOUSE`, non-zero `buttonState`.
*You get:* real `mousedown/mousemove/mouseup/click/contextmenu/wheel` in Chrome with
`pointerType: "mouse"` and **`isTrusted: true`**; hover via `ACTION_HOVER_MOVE`; wheel
via `ACTION_SCROLL` + `AXIS_VSCROLL`; right-click via `BUTTON_SECONDARY`. Full Monaco
desktop behaviour.
*Why it works:* Chromium's `EventForwarder` discriminates on **`toolType`, not source** —
`getToolType(0) == TOOL_TYPE_MOUSE` routes to `onMouseEvent()`. ⚠️ Get `buttonState`
right or `buttonState == 0` with DOWN/MOVE/UP is read as a **touchpad scroll**.
*Costs:* Shizuku must be re-armed after every reboot. **No OS cursor sprite** — we draw
it (§6.4). Android 14+ `InputVerifier` drops malformed streams; `deviceId` forced to −1.

**#2 — UHID from a Shizuku user service.** Register a virtual HID mouse so Android sees
a genuine `InputDevice` — scrcpy's `--mouse=uhid` trick, run locally on the tablet
instead of from a host.
*You get:* everything. A **real OS cursor** drawn by `PointerChoreographer`, correct CSS
cursor shapes (I-beam over Monaco), hover, wheel, right- and middle-click, fully trusted.
A virtual *keyboard* device this way would also reach the InputReader path — the only
route to Android 16's Mouse Keys.
*Why it's plausible:* SELinux `private/shell.te` carries
`allow shell uhid_device:chr_file rw_file_perms;` (stable android10 → main, covering both
`/dev/uhid` and `/dev/uinput`); `/dev/uhid` is `0660 uhid:uhid` and adbd adds `AID_UHID`
(3011) to its supplementary groups. Shizuku's `UserServiceManager.startUserService()`
spawns via `Runtime.exec("sh")`, so the child inherits uid 2000, adbd's groups
**including `AID_UHID`**, and `u:r:shell:s0`.
*Costs:* **zero known non-root prior art.** scrcpy's own docs warn UHID "may not work on
old Android versions due to permission errors". OEM variance (Samsung especially) is
unmeasured. Highest ceiling, unvalidated risk.

**#3 — In-page trackpad inside code-server.** A fixed-position DOM pad in the page
dispatching synthetic pointer/mouse/wheel events.
*Requires:* nothing. No root, no Shizuku, no accessibility toggle, no app involvement.
*You get:* full Monaco mouse behaviour — but only inside that tab, and `isTrusted: false`.
*Costs:* HTML5 `dragstart` can't be initiated by untrusted events; anything needing user
activation (`navigator.clipboard.read`, fullscreen) breaks; eats screen real estate; and
critically **it cannot live in the keyboard** (§6.5).

**#4 — `AccessibilityService.dispatchGesture` + overlay cursor.** What every existing
app does, and **structurally broken for a finger-on-glass trackpad** — see §6.3.

**Dead ends:** `VirtualDeviceManager`/`VirtualMouse` (`CREATE_VIRTUAL_DEVICE` is
`internal|role`, ungrantable, not held by shell); `sendevent`/writing `/dev/input/eventX`
(`neverallow shell input_device:chr_file no_w_file_perms`); a VS Code web extension for a
global trackpad (web extension hosts run in a Worker, sandboxed from the workbench DOM).

### 6.3 Why `dispatchGesture` fails — the finding that reorders everything

Enabling a gesture-capable accessibility service installs `AccessibilityInputFilter`
(`FLAG_FEATURE_INJECT_MOTION_EVENTS`) **globally**, so every real `MotionEvent` on the
device passes through `MotionEventInjector.onMotionEvent()`. That method
**unconditionally** calls `cancelAnyPendingInjectedEvents()` before forwarding. That
function removes pending `MESSAGE_SEND_MOTION_EVENT`s and cancels the gesture; and if
`mNumLastTouchPoints != 0` (the held-open-awaiting-continuation state) it cancels that
too — AOSP's own comment: *"An injected gesture is in progress and waiting for a
continuation. Cancel it."*

Gesture playback is **scheduled with delays**, so even a 50 ms tap has pending messages
for 50 ms. A trackpad means a finger on the glass emitting `ACTION_MOVE` at 60–120 Hz,
and **every one of those destroys the in-flight gesture.** AOSP wrote the problem down
itself: *"it's almost impossible to perform the gestures. Any slightly unintended
movement results in the cancellation of the gesture."*

The one exemption — `SOURCE_MOUSE` + `ACTION_HOVER_MOVE` while a gesture is open —
exists precisely so a *real mouse* can move during a held gesture. A finger on a
touchscreen never qualifies. Circular: if we already had a mouse we wouldn't need a
trackpad.

Net: `dispatchGesture` only works **record-then-replay** — move an overlay cursor with
no injection, lift the finger, then dispatch the whole tap/drag as one
`GestureDescription`. No live feedback, ~100–400 ms blind playback. (Continued strokes
are additionally gated on same-service identity and **exact float equality** of the
continuation point.) This is also why `virresh/matvt` gets away with it on Android TV:
D-pad *key* events never enter `MotionEventInjector.onMotionEvent()`.

### 6.4 Why there's no cursor under option #1

The sprite is owned by `PointerChoreographer`, an `InputListenerInterface` stage on the
**InputReader** path. `injectInputEvent()` enqueues straight into `InputDispatcher` via
`enqueueInboundEventLocked` and never traverses it. There is **no API to attach a
cursor**; `PointerIcon` only styles one that already exists. A real cursor requires a
real pointer `InputDevice` — i.e. option #2.

So under #1 we draw our own: `SYSTEM_ALERT_WINDOW` overlay, or
`attachAccessibilityOverlayToDisplay` on API 34+, with `FLAG_NOT_TOUCHABLE`.

### 6.5 If it's in the page, it can't be in the keyboard

The IME→page WebSocket is **dead on arrival**: code-server is HTTPS and a secure page
cannot open `ws://127.0.0.1` (mixed content — CSP itself is fine, `connect-src` allows
`ws:`). Routing through the vps adds network latency unusable at 60 Hz. So option #3's
pad must live **in the page**, driven by touch on a page-side element — which means it
isn't part of our keyboard at all, and the "dock right in landscape / cover in portrait"
spec would have to be re-implemented in CSS inside code-server.

Worth noting for #3: synthetic events work **better than expected** in Monaco.
`PointerEventHandler._onMouseDown` early-returns **only** for `pointerType === 'touch'`,
so `pointerType: 'mouse'` takes the full desktop path (caret, double/triple-click,
drag-select); and `GlobalPointerMoveMonitor.startMonitoring` already wraps
`setPointerCapture` in try/catch with a window-listener fallback, so synthetic drags
survive. Two hard requirements: a stable non-zero `pointerId` with `isPrimary: true`,
and **`buttons: 1` on every `pointermove`** during a drag (the monitor aborts on
`e.buttons !== initialButtons`). Hand-roll `pointerover`/`pointerout` off
`elementFromPoint` or `:hover` sticks.

### 6.6 Script injection into code-server, for the record

CSP is `script-src 'self' 'unsafe-eval' <nls> blob: 'nonce-<scriptNonce>' <shas>`. So a
**same-origin `<script src>` added to `workbench.html` passes `'self'` with no CSP
edit**; an *inline* block needs `nonce="{{WORKBENCH_SCRIPT_NONCE}}"`, which the server
substitutes. code-server carries a `csp-hashes.diff` patch, so refresh hashes if inline
content changes. `--extra-builtin-extensions-dir` **does not exist** in code-server's
CLI. This is the same `apply.sh`-style patch-after-every-upgrade situation as the pingwin
workbench CSS — it would join that repo's drift check.

### 6.7 Precedents

| Project | Mechanism | Real cursor |
|---|---|---|
| `chetbox/android-mouse-cursor` (2015) | Overlay cursor + walks `AccessibilityNodeInfo` and `performAction(ACTION_CLICK)` on the nearest node — predates `dispatchGesture` | No |
| `virresh/matvt` (Mouse Toggle for Android TV) | a11y `canPerformGestures`, overlay cursor moved by **D-pad keys**, OK issues `dispatchGesture` — dodges §6.3 because key events never enter `MotionEventInjector` | No |
| `austinauyeung/C9` | Dual backend, `dispatchGesture` *or* Shizuku — but its `ShizukuGestureStrategy.kt` builds `SOURCE_TOUCHSCREEN, 0`, a missed `TOOL_TYPE_MOUSE` opportunity | No |
| `waguri-paglu/VirtualMouse` ("Arrowroot") | **Exactly our target product**: on-screen trackpad → `/dev/uinput` → genuine Android cursor. **Root required** — and §6.2 #2 suggests that requirement may be removable | Yes |
| scrcpy `--mouse=uhid` | Shell-privileged on-device server writes a HID descriptor to `/dev/uhid` and streams reports | Yes |
| Android 16 Mouse Keys | `MouseKeysInterceptor` + `VirtualDeviceManager` in system_server. Gated on `CREATE_VIRTUAL_DEVICE`, **physical keyboards only** | Yes |

### 6.8 The UHID probe — RUN, AND IT PASSED (2026-11-11)

Device: **SM-X916B** (Galaxy Tab S9 Ultra), One UI / **Android 16, SDK 36**, arm64-v8a,
1848×2960 @ density 280. Over wireless adb at `192.168.18.95:45671`.

**Preconditions — all green:**

```
crw-rw---- 1 uhid uhid u:object_r:uhid_device:s0 10, 239 /dev/uhid
crw-rw---- 1 uhid uhid u:object_r:uhid_device:s0 10, 223 /dev/uinput
/system/bin/hid       -rwxr-xr-x root shell   (+ /system/framework/hid.jar)
/system/bin/uinput    -rwxr-xr-x root shell   (+ /system/framework/uinput.jar)
uid=2000(shell) groups=…,1004(input),…,3011(uhid),…  context=u:r:shell:s0
```

**Proof-by-existence before we even injected anything:** the tablet already has a
Bluetooth **Lenovo TrackPoint Keyboard II** paired (vendor `0x17ef`, product `0x60e1`),
and `dumpsys input` reports its sysfs path as
`/sys/devices/virtual/misc/uhid/0005:17EF:60E1.0004`. Android's own BlueDroid HID stack
creates HID peripherals through `/dev/uhid`. Its mouse half shows up as
`27: TrackPoint Keyboard II Mouse — Classes: CURSOR | BATTERY | EXTERNAL` and drives a
real OS cursor. So the UHID→cursor pipeline is live on this hardware by construction.

**Our own injection — worked:**

```
31: emkeys UHID Probe Mouse
    Classes: CURSOR | EXTERNAL
    Path: /dev/input/event19
    Identifier: bus=0x0005, vendor=0x18d1, product=0x4f4d
```

`CURSOR` means `InputReader` built a `CursorInputMapper` for it, which means
`PointerChoreographer` draws the OS pointer sprite and dispatches real `MotionEvent`s
with `TOOL_TYPE_MOUSE`. That is the highest ceiling described in §6.2 option #2, and
it is **confirmed reachable from uid `shell`** — i.e. reachable from a Shizuku user
service, which runs as exactly that uid.

**→ Trackpad decision: build option #2 (UHID via Shizuku). Option #1 is now the
fallback, not the plan.**

#### Gotchas found while probing (don't rediscover these)

- `/system/bin/hid` reads a **stream of concatenated JSON objects**, *not* a JSON array.
  A top-level `[` makes `Event.Reader.beginObject()` throw and you get
  `Error reading in object, ignoring.`
- **`"bus"` is mandatory.** Omitting it is an unguarded `NullPointerException` in
  `Event.getBus()` → `Hid.registerDevice()`. Values: `"usb"`, `"bluetooth"`.
- **Errors go to logcat (tag `HID`), not to stdout.** On failure the command exits `1`
  and prints *nothing* to the terminal. Always `adb logcat -d | grep -E 'HID |AndroidRuntime'`.
- `vid`/`pid`/`descriptor`/`report` accept hex strings (`"0x18d1"`) — they go through
  `Integer.decode`. Plain JSON numbers also work, but hex strings read better.
- `/system/bin/hid` is `0755 root:shell` — **executable but not readable** by shell, so
  `cat` it gives `Operation not permitted`. Get the format from AOSP
  `cmds/hid/src/com/android/commands/hid/{Hid,Event}.java`, not from the device.
- The virtual device lives exactly as long as the `hid` process. Exit → device gone.
- `screencap` does **not** composite the pointer sprite, so a screenshot is not a valid
  way to confirm the cursor. Trust `Classes: CURSOR` instead.

Working probe file is at `/data/local/tmp/uhid-probe.json` on the tablet and
`/tmp/uhid-probe.json` on the Mac: a 4-byte boot-mouse report descriptor (5 buttons,
rel X/Y/wheel), a `register`, four `report`s of `{0x00,0x28,0x28,0x00}` and a trailing
`delay` to hold the device open.

Transliteration targets now that it passes: scrcpy's `UhidManager.java` and
`hid_mouse.c` — pure `android.system.Os` + `ByteBuffer`, no NDK. Remember to run a read
loop draining `UHID_OPEN`/`UHID_CLOSE`/`UHID_OUTPUT` or the kernel queue backs up.

### 6.9 Honest recommendation — updated after the probe

Ship the keyboard first with **no trackpad**, because the keyboard has no unknowns.
§6.8 has now run and passed, so the sequencing collapses to:

1. ~~Run §6.8.~~ Done. UHID works → build **#2**, real cursor, highest ceiling.
2. **#1** (Shizuku + `TOOL_TYPE_MOUSE` + drawn cursor) is the fallback if #2 misbehaves
   in practice on some other device. Both need Shizuku anyway, so this is a cheap hedge.
3. Keep **#3** as the zero-dependency fallback — the only option that needs *no* special
   permissions at all, at the cost of living in the page.
4. Never build **#4**.

UX compromises to accept in all cases: relative-motion trackpad semantics (delta →
cursor move, tap → click at cursor) rather than absolute touch; scroll as an explicit
two-finger gesture or a wheel strip; and under #1/#3 no OS-level cursor shape changes.

---

## 7. UI — pingwin, dark and light

### 7.1 Tokens (from `pingwin-web/src/styles.css`, authoritative)

```
--ink         #0b1014   --amber        #f2a33c
--ink-raise   #10171d   --amber-press  #d88b25
--ink-press   #080c0f   --amber-ink    #16110a
--line        #1e2a33   --ok           #57c78a
--line-strong #2c3d49   --danger       #e05f5f
--text        #d7e0e7
--muted       #71838f
```

Fonts, the three-voice system: **Space Grotesk** display · **Manrope** body ·
**JetBrains Mono** for *anything machine-owned*. Radii **3px/4px**.

### 7.2 How that maps onto a keyboard

The pingwin palette and the ThinkPad palette want the same thing — a near-black deck
with light legends — so this is a happy accident, not a compromise.

| Surface | Dark | Rationale |
|---|---|---|
| Deck / inter-key | `--ink` `#0b1014` | stands in for ThinkPad's `#1E1D1B` deck |
| Keycap face | `--ink-raise` `#10171d` | the "raised" token, literally |
| Keycap pressed | `--ink-press` `#080c0f` | |
| Keycap border | `--line` `#1e2a33` | 1px, not a bevel — keeps the ~1 mm radius honest |
| Modifier / nav keycaps | `--ink` on `--line-strong` border | the two-tone ThinkPad cap, reproduced with tokens we already have |
| Primary legend | `--text` `#d7e0e7` | |
| Fn-layer legend | `--muted` `#71838f` | **this is the modern-era grey.** Classic era would want a blue — pingwin has none, so classic fidelity costs a new token |
| **Latched modifier** | `--amber` fill, `--amber-ink` legend | amber = active state throughout pingwin |
| Modifier held (chording) | `--amber` 1px border, `--text` legend | distinguishes held from latched |
| FnLock on | `--amber` dot on Esc | mirrors the real Esc LED |
| TrackPoint | its own red — **keep ThinkPad red, don't amber it** | `--danger` `#e05f5f` is close and is already in the palette; a true TrackPoint red is more authentic. Worth one token |
| Key legends | **JetBrains Mono** | every legend is machine-owned by pingwin's own rule. Condensed grotesque would be more ThinkPad-accurate, so this is a deliberate trade: **pingwin wins, and JetBrains Mono's uniform advance also makes a key grid easier to align** |

### 7.3 Light mode — has to be derived, pingwin has no light variant

Neither `pingwin-web` nor `pingwin-vscode-theme` ships one, so this is new design work,
not a lookup. Constraints: keep `--amber` as *the* accent (it's the brand), keep the
deck darker than the caps inverted (caps lighter than deck, same as dark mode), and
watch amber-on-white contrast — `#f2a33c` on white is ~2.1:1, **failing** for text.

Starting point to review, not a decision:

| Role | Light |
|---|---|
| Deck | `#e6eaee` — cool grey, derived from `--text` darkened |
| Keycap face | `#f7f9fa` |
| Keycap pressed | `#d7dde2` |
| Border | `#c3ccd3` / strong `#9fabb4` |
| Primary legend | `#121a20` (a near-`--ink`) |
| Fn legend | `#5a6b77` (a darkened `--muted`) |
| Latched modifier | `--amber-press` `#d88b25` fill + **`--amber-ink` `#16110a`** legend — using the *press* amber and the dark ink is what rescues contrast |
| Accent hairlines | `--amber` still fine for borders/dots (non-text) |

Mode switching: follow the system (`isNightModeActive` / `uiMode`) with a manual
override in settings, since a tablet on a desk at night and code-server's own theme
won't always agree.

### 7.4 Era choice drives the visual spec

Going **classic 7-row** means blue Fn legends and a blue Enter — two colours pingwin
doesn't have, so it means extending the palette. Going **modern 6-row** means grey Fn
legends, which `--muted` already *is*. Modern is the cheaper and more coherent choice;
classic is the more distinctive one. See the open decisions.

---

## 8. Decisions (2026-11-11)

1. **Era: modern 6-row** (xx40+/T14/X1 Carbon shape). Grey Fn legends — `--muted`
   already is that colour, so no palette extension. PgUp/PgDn flank `↑`, half-height
   arrows, `Home End Insert Delete` closing the top row, `FnLk` on `Esc`. So §4.1's
   classic column, §5.2's classic-only items (hardware button strip, browser keys,
   numeric-keypad secondaries) and the blue tokens in §7.2/§7.4 are **out of scope**.
2. **Trackpad: run the §6.8 UHID probe first**, before any trackpad code, because the
   answer picks the architecture. Keyboard work proceeds in parallel — it has no
   unknowns and no dependency on the outcome.
3. **Shizuku: acceptable.** Re-arming after a reboot is a fine tax. So §6.2 #1 and #2
   are both live; #3 is the fallback, #4 is never.
4. **Legends: JetBrains Mono.** Pingwin's machine-owned rule wins over ThinkPad's
   condensed grotesque; uniform advance also helps grid alignment, and the woff2 is
   already self-hosted in `code-server-vps` / `pingwin-vscode-theme`.
5. **Name: `emkeys`.** Settled.
6. **F-row: `F1`…`F12` as the primary legend** — xx30 style, not xx40's large media
   icon with the F-number small at bottom-right. The keys exist *because* we need
   F-keys, so a hunt-for-the-small-label `F7` would defeat the purpose. Consequences:
   - **FnLock is effectively inverted versus a modern ThinkPad** — our unshifted state
     is F-keys, and the Fn layer holds the media/feature functions (most of which we're
     dropping per §5.2 anyway, so the Fn layer on the F-row is mostly free real estate).
   - Keep `FnLk` on `Esc` as the indicator (§4.3) since it still toggles something real.
   - So we're a **modern 6-row shell with an xx30 F-row** — a combination that shipped
     (T430/X230), not a mongrel.

### Still to decide
- `minSdk` (affects nothing in §1 — every keycode we need is API 11 — but gates
  `attachAccessibilityOverlayToDisplay` (34) for the drawn cursor vs
  `SYSTEM_ALERT_WINDOW`). Less urgent now that §6.8 passed — under UHID the OS draws
  the cursor and we need neither API.
- ~~Which tablet, and its exact model/SDK~~ — **SM-X916B, Galaxy Tab S9 Ultra, Android
  16 / SDK 36, arm64-v8a, 1848×2960 @ density 280.** `minSdk` can be anything ≤ 36.
- Whether `Home`/`End`/`Insert`/`Delete` stay in the top row at 0.825u pitch or get
  promoted to the Fn layer on the arrows. `Fn+←/→` for Home/End is the authentic
  Lenovo mapping, so the escape hatch is era-correct if the targets prove too small on
  a 10″ panel. **Decide from a real render, not from the spec.**

## 9. Device checks

### 9.1 The target device (measured 2026-11-11)

```
model   SM-X916B  (Galaxy Tab S9 Ultra, 14.6")   brand samsung
os      Android 16 / SDK 36                      abi   arm64-v8a
screen  1848 x 2960 px @ density 280 (1.75x)  →  1056 x 1691 dp
adb     192.168.18.95:45671 (wireless debugging)
```

1056 dp of width in portrait, 1691 dp in landscape. A 15u ThinkPad grid at 1691 dp
landscape gives **~112 dp per 1u key** — comfortably above the 48 dp touch-target
minimum even for the 0.825u navigation keys (~93 dp). The portrait case (1056 dp →
~70 dp per 1u, ~58 dp at 0.825u) still clears 48 dp. **The full layout fits in both
orientations.** The §8 "still to decide" worry about Home/End/Ins/Del being too small
is resolved: they're fine.

### 9.2 Two findings that change the plan

**a) `show_ime_with_hard_keyboard = 0`.** This is the §3.1 suppression, live and
default-off. With a hardware keyboard attached, the *default*
`onEvaluateInputViewShown()` returns false and no soft keyboard is drawn at all.

> **Downgraded 2026-11-12 (chunk 1): this does not actually block emkeys.**
> `onEvaluateInputViewShown()` is overridable, and emkeys overrides it to `true`
> (see the correction in §5.3). Verified on device with the setting still `0` and the
> TrackPoint Keyboard II connected: emkeys showed anyway. The setting only governs
> *other* IMEs (Gboard). `SetupActivity` reports it as an informational row with the
> adb command for convenience — it is not a prerequisite.

```sh
adb shell settings put secure show_ime_with_hard_keyboard 1
```

(Settings ▸ General management ▸ Physical keyboard ▸ *Show on-screen keyboard* — it
sticks across reboots.)

**b) There is already a TrackPoint Keyboard II on this tablet.** Three bonds exist for
it; one (`…95:89`, LE, encrypted) is **connected right now**, presenting as
`26: TrackPoint Keyboard II Keyboard` and `27: TrackPoint Keyboard II Mouse`. So the
exact keyboard emkeys is imitating is sitting next to the tablet. Consequences:

- It is a **reference device** — the pitch/geometry numbers in §4.2 are cited from its
  PSREF, and they can now be confirmed by hand rather than trusted.
- It is also a **competitor to the premise.** While it's connected, Gboard (and emkeys)
  are suppressed by (a), and the physical keyboard already supplies every key §5.1
  lists. emkeys earns its keep when that keyboard isn't around — tablet in hand, on a
  couch, on a plane — which is a real and common case, but worth naming out loud.
- It is the **ground-truth oracle for §1**: press Ctrl+Shift+P on the real keyboard,
  record what Chrome/VS Code receive, then make emkeys produce a byte-identical
  `KeyEvent`. That's a much stronger test than "does it feel right".

Default IME is confirmed **Gboard** (`com.google.android.inputmethod.latin`), with
Samsung Honeyboard also enabled.

### 9.3 Still unverified

1. ~~**Is `UniversalKeyboardHandling` on in this Chrome build?**~~ **Answered 2026-11-12
   (chunk 1), and it's split:**
   - **`Ctrl+W` reaches the page.** The oracle logged
     `keydown key="w" code=KeyW keyCode=87 mods=[ctrl]`, and `preventDefault()` held —
     the tab did **not** close. So Ctrl+W is *not* reserved pre-page, and VS Code's
     "close editor" binding is usable as-is.
   - **`Ctrl+Tab` is still stolen.** It switched Chrome tabs; the page never saw it.
     So §5.3's row stands for Ctrl+Tab. In the code-server **WebAPK** there is no tab
     strip, so this likely only bites in plain-browser mode — untested there.
   - F6 is not in the chunk-1 layout; still untested.
2. **Does bare `Esc` reach code-server?** — **Yes, verified 2026-11-12.** Tapping the
   emkeys `Esc` cap closed the VS Code Command Palette in the live code-server WebAPK.
   (`dumpsys input` confirms code-server is installed as a WebAPK/PWA —
   `org.chromium.chrome.browser.webapps.SameTaskWebApkActivity` +
   `org.chromium.webapk.a8b515257d885fe85_v2` — so there is no tab strip and no omnibox
   for Chrome to steal keys for, and most of §3.2's worry list doesn't apply here.)
3. ~~The §6.8 UHID probe.~~ **Done — passed.**

### 9.4 Chunk 1 result: the premise holds

The §1.3 dispatch model was reasoned from Chromium source and had never been run. It
has now been run, on the SM-X916B, against both a self-hosted dispatch oracle
(`tools/keyoracle.html`) and the live code-server WebAPK.

**`Ctrl+Shift+P` from `InputConnection.sendKeyEvent()` opens the VS Code Command
Palette.** The oracle recorded exactly the intended six events and nothing else:

```
0001 keydown key="Control" code=ControlLeft keyCode=17 mods=[ctrl]       loc=1 repeat=false trusted=true
0002 keydown key="Shift"   code=ShiftLeft   keyCode=16 mods=[ctrl,shift] loc=1 repeat=false trusted=true
0003 keydown key="P"       code=KeyP        keyCode=80 mods=[ctrl,shift] loc=0 repeat=false trusted=true
0004 keyup   key="P"       code=KeyP        keyCode=80 mods=[ctrl,shift] loc=0 repeat=false trusted=true
0005 keyup   key="Shift"   code=ShiftLeft   keyCode=16 mods=[ctrl]       loc=1 repeat=false trusted=true
0006 keyup   key="Control" code=ControlLeft keyCode=17 mods=[]           loc=1 repeat=false trusted=true
```

Rule by rule, confirmed rather than inferred:

| Rule | Evidence |
|---|---|
| 1 — generic `META_*_ON` alongside `_LEFT_ON` | `mods=[ctrl,shift]` is populated at all |
| 2 — left variants only | `location=1` on both modifiers, `AltGraph` never set |
| 5 — never `commitText()` | no `keyCode: 229`, no `key: "Unidentified"`, no `beforeinput`/`input` |
| 6 — evdev scancode, no +8 | `code` is non-empty and **correct**: `KeyP`, not `KeyX` |
| 7 — our own repeat timer | holding `←` produced 12 discrete DOWN/UP pairs, every one `repeat=false` |

Also verified on device: the untimed modifier cycle (tap = latch for exactly one key;
double-tap = lock; third tap = unlock) and the stranded-modifier defence (latch `Ctrl`,
leave the app, come back, press `P` → `metaState = 0`).

Not machine-testable and left to hand verification: three simultaneous pointers
(hold `Ctrl` + hold `Shift` + tap `P`). `adb shell input` cannot inject concurrent
pointers; the case is covered by `ChordEnvelopeTest`, which asserts the held-modifier
path emits a trace byte-identical to the tapped one.

**Conclusion: nothing blocks building the real 84-key layout.** The known gap carried
into chunk 2 is reference-counting modifier DOWNs so that two base keys held at once
don't open two chord sessions.

### 9.5 Chunk 2 implementation (device acceptance pending)

The active layout is now `ThinkPadLayout`, not `SkeletonLayout`: 84 ANSI caps on the
15u grid, 5u Space, 2.35u Enter, 2.85u right Shift, and a two-tier arrow island. The
keyboard height is capped to leave editor space in landscape. JetBrains Mono is
bundled under OFL. The prototype remains as a historical fixture.

Modifier DOWNs are reference-counted across base-key sessions. Duplicate physical
modifier caps count held touches, and latch versions prevent an old key release
from clearing a newly latched modifier. These behaviors have JVM regression tests.

Fn is a local, one-key latch/held layer; Fn+Esc toggles a persisted FnLock. Bare Esc
stays Escape, even when locked. Holding Fn reverses FnLock. F1–F12 stay primary;
there are no fake Windows media actions. B/P map to Android's shared
`KEYCODE_BREAK` (evdev Pause 119), S to `KEYCODE_SYSRQ` (99), K to
`KEYCODE_SCROLL_LOCK` (70), and left/right to Home/End. Fn+Tab switches to the prior
IME; Fn+Space opens the picker. Caps is implemented locally as letters-only Shift
inversion, not a persistent OS CapsLock event.

Setup now offers Fn/Ctrl swap. A separate toolbar keeps switching/setup controls
outside the 84-key shell; Win is rendered disabled. Moving off a key cancels its
repeat/hold, but cannot undo the base stroke already emitted on DOWN. Virtual
accessibility buttons support tap-based chords. Per-stroke logging is debug-only.

**None of this extends the device proof in §9.4 until the full-layout checklist in
`README.md` is run.** In particular, layout usability, physical three-finger chords,
TalkBack and the new Fn codes still need device verification. The trackpad remains
out of this milestone.

### 9.6 Expanded-layout device validation (2026-11-13)

Follow-up to §9.5: Galaxy Tab S9 Ultra **SM-X916B**, Android **16 / API 36**,
One UI property `80500`, Chrome **157.0.8103.48**, physical keyboard attached.
The expanded debug APK was installed with `adb install -r`. No code-server files
were changed. The trackpad remains outside this milestone.

Device testing found and fixed problems that JVM tests could not reveal:

- Samsung's `SemVibratorHelper` calls `Vibrator` directly and crashed the IME without
  `android.permission.VIBRATE`. The manifest now declares this normal permission.
- Edge-to-edge setup content overlapped system bars/the IME. The setup screen uses
  explicit safe viewport margins and scrolls its focused field into view.
- Samsung's IME picker/hide controls covered the bottom caps despite a 15dp reported
  navigation inset. The IME reserves at least 56dp when a bottom navigation inset is
  present; both orientations were inspected after the fix. Other OEMs/navigation
  modes still need validation.
- Virtual-key bounds now use `ExploreByTouchHelper`'s screen-bounds helper and are
  invalidated on size changes. Automation waits for entry/rotation animations and
  retrieves fresh nodes after activity replacement rather than tapping stale bounds.

**Native instrumentation: `OK (5 tests)`**, final run 28.977s:

1. Inject Ctrl, Shift and P as three simultaneous touch pointers, release them, then
   type a plain `p`: no Ctrl/Shift latch remains. This verifies the Android touch
   path with OS-injected pointers, not touch-panel recognition of human fingers.
2. Export all 84 virtual-key bounds for the browser runner.
3. Rotate portrait/landscape: all 84 bounds on screen, nonempty and non-overlapping;
   Fn can be tapped and consumed by navigation in each orientation.
4. Type the alphabet, shifted punctuation and letters-only Caps/Shift inversion;
   the app has VIBRATE and no haptic crash recurs.
5. Fn Home/End edit at the correct positions, FnLock persists across input-view
   dismissal/reopening, Space/Backspace work. Native EditText dismisses its IME on
   bare Escape; the test refocuses the field before continuing.

**Chrome oracle: 10/10 cases passed**, using real cap-coordinate taps and one held
touch, not `adb keyevent` injection. `?report=1&block=1` captures events to a
loopback-only collector via an ephemeral adb reverse mapping and blocks browser
defaults. These are renderer dispatch tests, **not proof of VS Code commands**.

| Case | Observed |
|---|---|
| Alphabet | all 26 lowercase letters |
| Digits/punctuation | all 21 base symbols |
| Shifted symbols | all 21 shifted symbols |
| Ctrl+Shift+P | ControlLeft DOWN, ShiftLeft DOWN, KeyP DOWN/UP, ShiftLeft UP, ControlLeft UP; final flags clear |
| Ctrl+W | `key=w`, `code=KeyW`, Ctrl set; page receives it |
| Fn navigation/B/P/S/K | Home, End, Pause, Pause, PrintScreen, ScrollLock |
| Function row | F1–F12 except F6, deliberately omitted; F6 remains unverified |
| Caps inversion | `A`, `1`, `a` |
| FnLock/bare Esc | locked Fn+left yields Home; Esc alone remains Escape |
| Held Ctrl+Left | 16 DOWN/UP pairs, `repeat=false`, one Ctrl envelope |

All captured keyboard events were trusted, non-composing, with nonempty DOM `code`
and no `keyCode=229`. B/P both reach Chrome as Pause and S as PrintScreen, reflecting
Android's shared Break/Pause code and SysRq translation rather than distinct DOM keys.

Build validation: 71 JVM tests pass; debug, instrumentation and minified unsigned
release APKs build. Debug lint has zero errors and six advisory warnings (tool/library
versions plus the launcher qualifier). New standard-library Python tools are
`tools/keyoracle_server.py` and `tools/device_browser_smoke.py`; reproduction commands
and remaining manual acceptance are in `README.md`.

**Still pending:** the expanded build in actual code-server/PWA workflows; F6 and
Ctrl+PgUp/PgDn compatibility; manual simultaneous fingers/typing ergonomics; overlapping
base keys, slide-off and editor teardown mid-hold on hardware; Fn/Ctrl swap and keyboard
switching/fallback UI; TalkBack navigation/activation. Earlier prototype evidence in
§9.4 remains valid but must not be presented as expanded-build acceptance.

### 9.7 Keyboard acceptance/hardening follow-up (2026-11-13)

The keyboard MVP was committed as `fa172bc`. This phase finishes keyboard acceptance
and regression coverage before expanding into trackpad/Shizuku work.

**Actual code-server PWA, expanded layout:** with the existing terminal focused,
tap emkeys Ctrl, Shift, P. The VS Code Command Palette appears, with the `>` prompt
and command list. Tap emkeys Escape: the palette disappears. Tap emkeys Ctrl, P:
Quick Open appears with its file-search placeholder and recent items. Escape dismisses
it. This proves these workbench keybindings execute in
`com.android.chrome/org.chromium.chrome.browser.webapps.SameTaskWebApkActivity`,
not just in the oracle. Foreground activity and selected IME were checked before
injecting cap-coordinate taps. No command was executed, file opened/edited, or text
sent to the integrated terminal. Screenshots were inspected locally, not committed
because they contain unrelated workspace/session information.

Seven additional native regressions in `KeyboardDeviceTest` cover:

- Two base keys under Shift; release the Shift pointer and first base key while the
  second repeats. The second stays uppercase and the following key is lowercase.
  Pointer ids remain stable even after an earlier pointer leaves the stream.
- Slide from a base key past its hit target: keep the initial stroke, stop all repeat.
  Slide an unused Shift away: no latch and no character from the destination cap.
- Leave for Home during held Shift+Q, recreate the setup editor, wait beyond repeat
  delay and type plain P; no repeat or modifier should leak into the new connection.
- Invoke virtual accessibility ACTION_CLICK for `a`, latched Shift+`B`, then `c`;
  verify `aBc` and that Win remains disabled. This is not TalkBack UX acceptance.
- Persist Fn/Ctrl swap, reopen the input view, verify left-Ctrl/Fn ordering and use
  Fn+left to edit at Home. The test changes the preference directly, not its checkbox.
- Fn+Space and the toolbar open the real system picker and leave no Fn latch when
  dismissed.
- With Gboard as a known recent keyboard, toolbar Previous and Fn+Tab both select it;
  emkeys is restored afterward. Only test UiAutomation uses shell authority to seed
  history; no settings permission is added to the app. This test skips if there is no
  second enabled keyboard. The no-history picker fallback remains unverified.

The initial teardown assertion exposed an accessibility test pitfall: empty EditText
nodes can report the hint through `text`. The assertion now accepts a node explicitly
marked `isShowingHintText` as empty; literal typed hint text is not accepted. The wait
helper also returns immediately on its first successful condition, avoiding a second
side-effecting accessibility click after success. No production dispatch changes were
needed for this follow-up coverage.

**Final follow-up run: `OK (12 tests)`, 58.235s, no failures or skips.** All seven
additional scenarios above passed alongside the five earlier device checks. The host
restored the starting selected IME, accelerometer/user-rotation settings and original
code-server task after instrumentation. JVM tests (71), instrumentation build and
debug lint also pass. The production APK is unchanged from the validated MVP.

Remaining acceptance is narrower than the historical pending list in §9.6: scratch
editor/terminal workflows including Ctrl+W and terminal shortcuts; F6/Ctrl+PgUp/PgDn
in browser and PWA modes; human multitouch/typing ergonomics; rotation mid-hold;
the Fn/Ctrl checkbox UI and no-history previous-keyboard fallback; actual TalkBack
speech, focus order and explore-by-touch. Do not disable other keyboards merely to
manufacture the no-history case on the user's tablet. Trackpad/Shizuku, theme work
and release/distribution remain separate milestones.
