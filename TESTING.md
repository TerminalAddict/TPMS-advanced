# Combined feature-batch testing

## v0.10.0 pre-release

This release combines features 31–38, the new van/ute schematics and the portrait diagram regression
fix below. The signed release uses package `nz.paulwillard.tyreping` and updates signed TyrePing
installations. TyrePing Debug remains a separate app; transfer its configuration with backup/restore
if needed. Use the feature checks below for physical-device validation.

## Vehicle diagram regression fix

This follow-up retains features 31–38 and the van/ute artwork. The previous build could silently
replace portrait diagrams with cards when text was enlarged, high visibility was enabled, or the
remaining dashboard area became wider than it was tall. Layout now uses the screen orientation
and an explicit **Settings → Use reading cards in portrait** switch, off by default.

```powershell
adb install -r "D:\TyrePing-Releases\debug\TyrePing-vehicle-diagrams-fix-debug.apk"
```

Check the car, van, ute, motorcycle and trailer diagrams in portrait, including enlarged Android
fonts and reduced space beneath the dashboard controls. Change high visibility and readout size:
the diagram should stay visible. Turn the new switch on/off and restart to check persistence.
Use cards for very large text. Rotate to landscape to check automatic cards, then return to portrait
to restore the diagram. Combined towing, car-with-spare and monowheel views continue to use cards.

Verified 16 October 2026: 162 tests passed across main (70) and data/vehicle (92), including eight
layout-selection regression tests and inspected car/van/ute renders. Four-wheel diagrams scroll
when space is short; missing-sensor labels use compact text and readings stay within the screen edges.
APK assembly, lint (0 errors / 19 warnings), and debug signature verification passed. Main Detekt
retains its existing 13 MagicNumber findings; no new findings. No phone testing performed for this fix.
APK: 90,822,439 bytes; version 0.9.1 / 901000; package `nz.paulwillard.tyreping.debug`.
SHA-256: `221980761473de222119ff6bf2cd1faa013b1333377fcca5dd471b6365589bb1`.

## Features 31–38 — towing and sensor insights debug checkpoint

Branch: `codex/towing-and-sensor-insights`, based on v0.9.1. Includes the pending van/ute artwork.
This is a local debug build, not a release. Install **TyrePing Debug** alongside signed TyrePing:

```powershell
adb install -r "D:\TyrePing-Releases\debug\TyrePing-features-31-38-debug.apk"
```

Verified checkpoint, 16 October 2026: **202 local tests passed** (data 92, main 62, background 44,
Android Auto 2, phone 2), including layout renders inspected at normal and enlarged text sizes.
Debug assembly and Android lint passed (0 errors, 19 warnings). Android Auto instrumentation tests
compile; they have not been run on a host/device. Detekt passes for data, background, Android Auto
and phone; main retains its existing 13 MagicNumber findings in AlertSlider/TyreStat.
APK: 91,028,999 bytes, version 0.9.1 / 901000, package `nz.paulwillard.tyreping.debug`, verified debug signature.
SHA-256: `241e2fe9aa7983090701ec7c2e47670594624d09bd3200bbdb23d0c3dcc162d5`.

1. **Combined dashboard:** create a car/ute/van and a single/tandem trailer. Give them distinct
   thresholds and sensor IDs. Save/select a towing combination from the dashboard. Confirm both
   vehicles' readings and warnings appear under their own names; tap each wheel to manage the
   correct binding. Edit/delete combinations, choose Individual view, switch vehicles manually,
   restart and restore a backup. Deleting a linked vehicle must not crash the dashboard or backup.
2. **Imbalance:** enable a 20 kPa threshold in Tyre insights. With a broad normal range, send
   230 and 260 kPa to the left/right tyres on one axle. Expect an imbalance despite both being
   individually in range. Equalise them to clear it. Front versus rear, motorcycle front/rear and
   spare tyres must not be compared. Stale readings (>2 min), future timestamps and pairs more
   than 1 min apart must not trigger. Test tandem front/rear and single trailer left/right.
   Start monitoring and send new packets to test speech/tones and repeat/silence; Parked pauses
   imbalance announcements, while ordinary pressure warnings retain their existing behaviour.
3. **Reception:** open Sensor reception quality; repeated unchanged broadcasts must increase counts.
   Set the known interval to 10 seconds and introduce longer gaps to see estimates. Bluetooth off/on
   and closing the app's scan consumers must not count as packet loss. Check RSSI, mean/range,
   last-observed age, unknown RSSI, weak-signal text, reset, and blank interval to disable estimates.
   Counter history is bounded and resets after process restart; the configured interval persists.
4. **Cold/hot:** bind and send fresh positive readings for every wheel. Confirm cold only when
   actually cold (use simulated readings for this software test), then send warmer/higher readings.
   Check each tyre's deltas; cooler/lower readings can show negative changes. Change calibration
   and verify it applies consistently to both baseline and current readings. Reassign a sensor,
   stop broadcasts, clear/replace the baseline and verify no misleading comparison remains.
   No automatic cold baseline should be inferred on launch.
5. **Android Auto:** connect to the Desktop Head Unit or car host while parked. Verify vehicle status,
   pressure/temperature units, warning text and last-seen age. Start with missing sensors, switch tabs,
   add/delete vehicles, and check a car with a spare. Titles and row order should stay stable during
   updates; the host row limit is honoured. Test screen-off monitoring separately on the phone.
6. **Health check:** run checks with Bluetooth off/on, Nearby devices denied/granted, background
   monitoring stopped/running, stale sensors and notifications blocked/allowed. Test tones, speech,
   vibration, sound/vibration off, muted alarm volume, Do Not Disturb and a blocked test channel.
   The app must ask you to confirm actual delivery. Silence and real tyre alerts take priority;
   Stop test or closing the dialog stops playback. Test never changes the configured alert modes.
7. **Accessibility:** enable high visibility, select reading cards in portrait and try every readout size.
   The diagram is now the default portrait view regardless of text size. Repeat at Android's largest
   font/display settings in portrait and landscape on a small screen. Readouts should wrap and scroll,
   warning text should stay steady in high visibility, and binding buttons must remain reachable.
   Use TalkBack to verify wheel labels, reading units, warnings and control descriptions.
8. **Compatibility toolkit:** scan an unknown sensor, inspect AD-field offsets/types/lengths and
   changing offsets, and export both raw and anonymised samples. Raw JSONL retains evidence;
   anonymised JSONL contains only structure/relative timing/RSSI and no names, MACs, IDs or payload
   bytes. Both exports must work after the document picker pauses scanning. Reopen the export and
   verify malformed/truncated packets don't crash inspection. No automatic unknown-protocol binding.

For simulation, use the existing ADB commands below with separate IDs/addresses for each bound wheel.
Physical BLE range, background survival, actual audio/vibration, TalkBack and Android Auto host behaviour
require device testing; local tests cannot establish them.

## Van and ute artwork — debug checkpoint

Branch: `codex/van-ute-artwork`, based on v0.9.1. Install with:

```powershell
adb install -r "D:\TyrePing-Releases\debug\TyrePing-van-ute-debug.apk"
```

Select or create a **Van** and a **Ute / pickup** in portrait orientation. The van should have an
enclosed cargo roof; the ute should have an open rear load tray. Both face the top of the screen.
Check all four tyre icons and readings, tap each wheel to manage its sensor, and switch between
light and dark themes: the background should remain clear with only the vehicle lines tinted.
The car diagram and landscape reading cards retain their existing layouts.

## About page — v0.9.1 pre-release and debug checkpoint

Developed on `codex/about-page`, based on v0.9.0. The signed v0.9.1 pre-release adds this page
and updates existing signed TyrePing installations. The separate debug checkpoint retains version
0.9.0 and includes ADB sensor simulation. Install the debug checkpoint with:

```powershell
adb install -r "D:\TyrePing-Releases\debug\TyrePing-about-debug.apk"
```

Open the three-dot menu → **About**. Check the app version, Paul Willard introduction and all three
links (TyrePing, licences and Paul's website). Each should open in the browser. Return to TyrePing,
then use the toolbar or Android Back button to return to the vehicle. Check scrolling with a large
font, landscape orientation and dark mode. If no browser is installed, a message should appear
instead of the app closing. This build includes all v0.9.0 ride tools below.

## Features 21–29 — v0.9.0 pre-release and debug checkpoint

Developed on `codex/ride-readiness-and-vehicle-tools`, based on v0.8.0 (`eed5160`).
The signed v0.9.0 pre-release contains this batch and updates existing signed TyrePing installations.
Its APK is `D:\TyrePing-Releases\0.9.0\TyrePing-v0.9.0.apk`.
The separate debug checkpoint below retains version **0.8.0 / 800000** and includes ADB simulation,
which is excluded from signed release builds. The same physical test checklist applies to both.

Install the dedicated build when your phone is connected:

```powershell
adb install -r "D:\TyrePing-Releases\debug\TyrePing-features-21-29-debug.apk"
```

This updates **TyrePing Debug** (`nz.paulwillard.tyreping.debug`) and can coexist with signed TyrePing.
Use configuration backup/restore to copy vehicles and bindings between the two apps if needed.
No physical phone, NFC tag or TPMS sensor was used for the automated checks.

1. **Ready-to-Ride:** open a vehicle with expected wheels. Tap the dashboard card. Unseen wheels
   must say not received; fresh normal readings should say recently healthy. After two minutes without
   packets, readings must become sleeping/stale, with an advancing Last seen age. Low battery and
   out-of-range pressure/temperature must show warnings. Turn Bluetooth off and check the scan warning.
2. **Missing sensors:** choose one minute in Settings → Ride tools, start background monitoring,
   send a packet, then stop sending. Expect a missing warning after roughly one minute (up to five
   seconds of refresh delay). A sensor not seen this ride must never produce a missing warning.
   Send another packet to clear it. Parked must clear missing warnings; after resuming, send fresh
   packets before testing again. Bluetooth off/on must provide a new timeout rather than an immediate
   missing warning. Check notification, optional sound/vibration and Silence for 10 min.
3. **Pressure profiles:** save Solo and Towing with different limits, including separate rear limits
   on a double-axle trailer. Apply each without sending another packet and check existing alerts update.
   Edit limits manually and confirm “active limits” disappears when no preset matches. Reject invalid
   ranges and duplicate names. Restart and confirm presets remain.
4. **Automatic vehicle selection:** configure a different paired accessory for two vehicles and
   enable the switch. Connect each accessory and check the display changes. Select manually, then
   reconnect: it must hold your choice until Resume automatic selection. Connect both accessories:
   it must avoid guessing. Test with the app open and with the monitoring service running. Android
   can report already-connected audio accessories; other accessory types may need reconnection.
5. **Multi-vehicle indicators:** explicitly assign sensors to two vehicles. View one and send a bad
   reading from the other. Expect a warning beside the selector arrow and that vehicle's menu entry.
   Good readings clear it; silent alert settings must not hide visual warnings. Check the saved
   readings after switching vehicles and after restarting.
6. **NFC:** Settings → NFC vehicle switching → Write vehicle tag. Use a spare writable tag because
   its previous NDEF contents will be replaced. After success, move it away and tap again. Test cold
   launch, switching in an already-open app, a trailer tag, cancellation, a read-only tag and NFC off.
   NFC must hold the selection against automatic selection and must not start monitoring by itself.
   A deleted-vehicle tag should show an explanation. Malformed links must leave the app usable.
7. **Widget:** add TyrePing readings from the launcher. Check pressures/temperatures use selected
   units, vehicle switching, monitoring status, warning data, and the live age counters. Stop the app:
   the widget must remain visibly a snapshot and must not pretend to receive fresh readings. Resize
   it, rotate the launcher where supported, and test a car with a spare wheel and large font settings.
8. **Maintenance:** record an installation, tread/pressure check and replacement for different wheels.
   Restart and verify history. Set a reminder due today, grant notifications, and check dashboard/widget
   and notification (background delivery can be delayed). Test a mileage-only reminder by updating
   its current odometer. Reschedule/disable it and check the due state clears. Test deleting a record.
9. **Spoken diagnostics:** enable the switch, turn up alarm volume, and test manual monitoring,
   automatic accessory waiting/activation, Bluetooth off/on and screen locked. Announcements should
   explain changes rather than repeat on each broadcast. Disable or silence them during speech.
10. **Backup:** export profiles, maintenance history/reminders and accessory mappings, modify them,
    restore and restart as instructed. Verify all return. A conflicting accessory on a retained vehicle
    must reject restore before changing database configuration. Existing pre-feature backups still import.

Sensor-free replay (Bluetooth on, app open or monitoring active; assign ID 5647616 to a wheel first):

```powershell
adb shell am broadcast -a nz.paulwillard.tyreping.debug.MOCK_ADVERTISEMENT -p nz.paulwillard.tyreping.debug --es brand bekubee --es address AA:BB:CC:DD:EE:01 --ei id 5647616 --ef kpa 230 --ef celsius 25
adb shell am broadcast -a nz.paulwillard.tyreping.debug.MOCK_ADVERTISEMENT -p nz.paulwillard.tyreping.debug --es brand bekubee --es address AA:BB:CC:DD:EE:01 --ei id 5647616 --ef kpa 120 --ef celsius 95
# Exercise a vehicle link with your real saved vehicle UUID:
adb shell am start -a android.intent.action.VIEW -d "tpmsadvanced://vehicle/YOUR-VEHICLE-UUID" -p nz.paulwillard.tyreping.debug
```

Local verification: 175 JVM tests passed (data 72, main 57, background 44, phone 2), including
freshness/session boundaries, Bluetooth recovery grace, preset validation, manual selection policy,
strict URI parsing, maintenance due rules and backup merge conflicts. Database instrumentation tests
for atomic profile application and packet ownership/deduplication compile; execution awaits a device.
Pressure-profile, maintenance and five-wheel widget renders were inspected. Final build/check details
are recorded in `PROJECT_CONTEXT.md` and the ignored `build/ride-tools-*.log` logs.

Verified APK: version 0.8.0 / 800000, minSDK28/targetSDK37, 88,321,841 bytes, APK v2 signature valid.
SHA-256: `05980c1b0cefd9ea5e02d6de3b680efe08e1a2e02e433f9c856b6e920118cfd0`.
Android lint: zero errors and 19 existing icon/resource warnings. Data/background/phone Detekt pass;
main retains its 13 existing MagicNumber findings in AlertSlider/TyreStat, with no new findings.

The earlier release checkpoints below describe their own builds, not this APK.

## BLE discovery — v0.8.0 pre-release

Developed on `codex/ble-sensor-discovery`. The signed [v0.8.0 pre-release](https://github.com/TerminalAddict/TyrePing/releases/tag/v0.8.0)
adds discovery and updates existing signed TyrePing installations. The pre-packaging debug checkpoint
still reports **0.7.0 / 700000** and uses the separate TyrePing Debug app. Install its
`app\phone\build\outputs\apk\debug\phone-debug.apk` with the command below for ADB simulation.
The signed pre-release contains discovery but does not include the debug simulation receiver.

For the MRD-TY-T05-04 kit:

1. Open **Settings → Sensors → Discover sensors**, confirm the vehicle and select a wheel.
2. Close HRTPMS. Start scanning with one sensor off its valve. Keep this page visible.
3. Tap **Mark, then fit sensor** and install that sensor while parked. Inspect new/changed devices.
   F.W/R.W and A/B markings are not unique sensor IDs. Nearby non-TPMS devices also appear.
4. If recognised, compare raw kPa/psi and °C against the actual tyre/HRTPMS readings. Record the
   decoder name, sensor ID and Bluetooth address. Confirm assignment only for the matching sensor.
5. Repeat for all four sensors, checking that each has a distinct, stable ID. Verify replacement and
   cross-vehicle warnings if an ID is already bound. Cancel must preserve current bindings.
6. Close discovery, restart TyrePing with HRTPMS closed, and check that each assigned sensor updates
   its wheel and Last seen normally. Also test while a monitoring service is running before discovery;
   it must continue receiving readings and resume normal scanning after discovery closes.
7. If unknown or implausible, use **Export device**. Capture before fitting, just after fitting,
   and settled on the tyre, noting reference pressure/temperature separately. Attach the JSONL files
   for decoder investigation. Unknown packets must never show an Assign action.
8. Toggle Bluetooth off/on, deny/regrant Nearby devices, lock the screen, leave the page, and let a
   scan reach two minutes. Discovery must stop/recover as indicated without leaving a second scan.
9. Export all packets, cancel the file picker, export again, and open the JSONL. Check full bytes,
   manufacturer IDs/data, service UUIDs/data, timestamps and decoder results. Export before closing;
   the page retains only 200 devices and 1,000 recent packets in memory.
10. Back up assigned sensors, restore, restart, and check that discovery address filters survive.

Debug replay checks (with discovery scanning and Bluetooth on):

```powershell
# Unnamed, unknown advertisement; visible/exportable, never assignable.
adb shell am broadcast -a nz.paulwillard.tyreping.debug.MOCK_ADVERTISEMENT -p nz.paulwillard.tyreping.debug --es address AA:BB:CC:DD:EE:02 --es bytes 06ff3412010203
# Known Bekubee format: first off-valve, then mark installation in the app, then a pressure change.
adb shell am broadcast -a nz.paulwillard.tyreping.debug.MOCK_ADVERTISEMENT -p nz.paulwillard.tyreping.debug --es address AA:BB:CC:DD:EE:01 --es brand bekubee --ei id 5647616 --ef kpa 0 --ef celsius 20
adb shell am broadcast -a nz.paulwillard.tyreping.debug.MOCK_ADVERTISEMENT -p nz.paulwillard.tyreping.debug --es address AA:BB:CC:DD:EE:01 --es brand bekubee --ei id 5647616 --ef kpa 230 --ef celsius 20
```

These synthetic packets exercise the existing decoder and UI; they do **not** establish that the
photographed T05-04 kit uses that protocol. Real sensor, radio and phone validation remain pending.

Local verification for this discovery build (15 October 2026):

- 155 JVM tests passed: data vehicle 61, main 53, background 41; no failures or skipped tests.
- Debug APK build and release-variant Kotlin compilation passed. APK v2 signature verified.
- Discovery page, recognised-packet and unnamed/unknown-packet layout renders were inspected.
- Data vehicle lint has no issues; main lint has zero errors/warnings and one existing hint.
  Data Detekt passes; main retains exactly 13 pre-existing MagicNumber findings in AlertSlider/TyreStat.
- Debug package: `nz.paulwillard.tyreping.debug`, version `0.7.0` / `700000`, minSDK 28, targetSDK 37.
  APK size: 86,679,891 bytes. SHA-256:
  `958c1d6a2272a9b13f1c3a60200983f2a35eef4f2d97e0ed3a31fdad23e89dd0`.
- Logs: `build/discovery-final-tests-build.log`, `build/discovery-quality.log`,
  `build/discovery-release-compile.log`. No phone commands were run.

## Original v0.7.0 feature batch

Feature batch: **v0.7.0 pre-release**, developed on `codex/monitoring-shutdown-recovery` from v0.6.0.
The original debug checkpoint described below reports version 0.6.0 (600000) and contains all ten
roadmap items. The signed pre-release uses version 0.7.0 and the normal TyrePing application ID.

Download the signed APK and checksum from the [v0.7.0 pre-release](https://github.com/TerminalAddict/TyrePing/releases/tag/v0.7.0).
It updates an existing signed TyrePing installation. ADB sensor simulation is available only in
debug builds; the commands below target **TyrePing Debug**, whose data is separate from the release app.

No phone was connected during this batch. Everything below is a **pending device test**, not a
claim that it has already passed. Local verification results are recorded at the end.

## Install when you return

In PowerShell:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "D:\TyrePing\app\phone\build\outputs\apk\debug\phone-debug.apk"
```

Open **TyrePing Debug**, grant Bluetooth/Nearby devices and notification permissions, and turn demo
mode off for simulator tests. Debug uses `nz.paulwillard.tyreping.debug`, separate from the release
app and its data. `install -r` retains existing debug data. Do not uninstall/clear data to upgrade.
The schema upgrade retains vehicles, bindings, thresholds and history.

For the remaining examples, make ADB available in this PowerShell session:

```powershell
$env:Path = "$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:Path"
function Send-TyreReading {
    param([int]$Id = 1192960, [float]$Kpa = 230, [float]$Celsius = 25,
          [int]$Wheel = 0, [int]$Battery = 80)
    adb shell am broadcast -a nz.paulwillard.tyreping.debug.MOCK_ADVERTISEMENT -p nz.paulwillard.tyreping.debug --es brand sysgration --ef kpa $Kpa --ef celsius $Celsius --ei id $Id --ei wheel $Wheel --ei battery $Battery
}
Send-TyreReading
```

Keep the vehicle screen open or start background monitoring before sending. Accepted packets return
result `1`. Sysgration IDs must be multiples of 256. Wheel hints are 0 front-left, 1 front-right,
2 rear-left, 3 rear-right; an explicit binding overrides this hint. The examples use decimal IDs;
other existing readouts may format sensor IDs as hex.

## 1. Interrupted monitoring

1. Start manual background monitoring for a named vehicle, then run:
   `adb shell am force-stop nz.paulwillard.tyreping.debug`.
2. Open TyrePing Debug. Check the saved vehicle and explanation; force-stop should normally be an
   Android stop request, not a guessed battery/memory cause. Restart and check the notification.
3. Repeat, choose **Keep stopped**, reopen and verify no repeated prompt.
4. Stop normally from the app and notification; reopen with no recovery warning.
5. Later test reboot, an app update and automatic-watch recovery. Permission loss should offer
   setup; recovery must not silently choose a different vehicle or arm a different trigger.

No sensors are required for the force-stop test. OEM process kills and low-memory explanations
require separate device observations.

## 2. Sound, vibration, repeat and silence

1. Select a car, bind front-left to ID `1192960`, choose kPa/°C units, set pressure limits to
   180–300 kPa, and enable tones and vibration in Settings. Set normal repeat to 30 seconds and
   critical repeat to 10 seconds. Check alarm volume on the phone.
2. `Send-TyreReading -Kpa 160` should trigger a normal pressure alert. Re-send frequently: repeat
   timing should remain about 30 seconds, not restart on every packet.
3. `Send-TyreReading -Kpa 100` should escalate to critical with its distinctive vibration and
   shorter repeat interval. Try speech; test with and without a usable English speech engine.
4. Silence from the app and the monitoring notification. Send another critical packet: silence
   should still apply. Resume manually and check alerts resume; visual warnings remain throughout.
5. `Send-TyreReading -Kpa 230` clears the warning and stops playback/repeats. Repeat with a high
   temperature or low battery (`-Celsius 115`, or `-Battery 5`).
6. While alerting, adjust thresholds to include the existing reading. It should clear immediately
   without needing another broadcast. Reopen with only cached data: no new audible loop until a
   fresh packet arrives. With no more packets, repeats stop after ten minutes.
7. Check foreground-to-background handoff for duplicate sounds, screen-off delivery, headphones,
   another audio app, phone calls and Do Not Disturb. Leave alerts off if you do not want them.

## 3. Calibration

1. In the bound wheel's manager open **Calibrate sensor**. Use pressure multiplier 1.1 and offset
   10 kPa; temperature multiplier 1 and offset −2°C. Apply.
2. `Send-TyreReading -Kpa 200 -Celsius 25` should display about 230 kPa and 23°C. Alerts should use
   corrected values; CSV/history should retain 200 kPa and 25°C.
3. Change calibration with no new packet: displayed values should change, Last seen should not.
4. Cancel an edit and verify it is discarded. Reset the draft then Apply and verify raw values
   return. Invalid/out-of-range inputs must disable Apply. Restart and check persistence.
5. Move/swap this sensor and verify its calibration follows its ID, not the old wheel position.

## 4. Wheel-based assignment and identification

1. Tap a tyre outline/link icon, or an empty reading. Confirm it opens the correct vehicle/wheel.
   A populated reading should still open the enlarged display; Settings offers every wheel too.
2. Enter `1192960`, review the target and any replaced sensor/previous vehicle, then Apply.
   Verify cancellation leaves assignments alone. Try moving the same ID to another vehicle and
   confirm its old wheel no longer treats it as assigned there.
3. Start **Identify sensor via Bluetooth**, send 230 kPa, then 0 kPa for the same ID. The candidate
   should highlight a pressure change but must not bind itself. Confirm its ID manually.
4. Send another nearby ID and ensure it remains separate. Leave the dialog/app and verify the
   identification scan is released. With hardware, repeat while parked by removing/refitting one sensor.

## 5. Move, swap and rotate

Bind a four-wheel car using IDs `1192960`, `1193216`, `1193472`, `1193728` and send distinct readings:

```powershell
Send-TyreReading -Id 1192960 -Wheel 0 -Kpa 210
Send-TyreReading -Id 1193216 -Wheel 1 -Kpa 220
Send-TyreReading -Id 1193472 -Wheel 2 -Kpa 230
Send-TyreReading -Id 1193728 -Wheel 3 -Kpa 240
```

Swap front-left/rear-left. The cached values and IDs should swap immediately and future broadcasts
should follow the bindings despite their embedded hints. Move to an empty wheel, then rotate three
or four assignments and compare against the preview. Cancel a preview and verify no changes.
Check calibration follows IDs, no sensor is duplicated, and restart retains the result. Old CSV
rows should retain their historical wheel. If bindings change while a confirmation is open, it
should reject the stale confirmation rather than overwrite the newer assignment.

## 6. History and CSV

1. Send several distinct pressures/temperatures, including a brief dip, then open history in Settings.
   Check both graphs, sensor colours, tap-point details and that the brief dip is retained.
2. Change window size, page Earlier/Later, return to Latest, select a session and return to all sessions.
   Sessions split after a gap over 15 minutes. An empty range should show an explanatory message.
3. Export CSV to a document provider. Check the selected time range, UTC times, unsigned sensor IDs,
   original wheel, raw kPa/°C, battery and alarm fields. A vehicle name containing commas/quotes must
   remain one CSV field. Cancel the destination picker and try an unavailable provider.
4. With a large history, verify the 20,000-reading graph notice and full selected-range CSV export.

## 7. BLE diagnostics

1. Enable Capture packets, send simulated packets, and check timestamp, full raw hex, RSSI, address,
   decoder, sensor ID and raw status fields. Try a real supported sensor when available.
2. Export JSONL and verify one complete JSON object per line. Values with unknown status meanings
   must not be labelled as specific alarms. Captured packets may contain Bluetooth addresses.
3. Turn capture off: saved data should remain exportable. Restart the app: capture should be off.
   Clear only after confirming. Test picker cancellation and provider failure.
4. A long capture should rotate around 4 MB total, with queue-drop counts visible if overloaded.
   Actual radio/payload quirks still require hardware; ADB simulation does not prove radio capture.

## 8. Vehicle configurations

Create a car with spare, monowheel, van, ute and double axle trailer. Check vehicle switching,
portrait/landscape, large system fonts, labels, sensor management and persistence after restart.
Car-with-spare has five independent slots; assign spare ID `1193984` manually and verify its values
never populate a normal wheel. Monowheel has one explicitly bound slot. Four-wheel QR kits should
populate only the road wheels of the spare car, van and ute; reject multi-sensor kits for monowheel.
Van/ute currently reuse the car artwork. Double axle trailer retains its supplied schema image.
Check separate front/rear thresholds where appropriate, plus shortcuts and Android Auto when available.

## 9. Waiting battery management

With a real BLE beacon, arm automatic monitoring and enable waiting-power savings. Check detection
while screen-off/stationary, then with motion, screen-on and charging. Verify a present beacon does
not produce a false disconnect just because scan mode changes. Active tyre monitoring must retain
full scan duty even when stationary; waiting scans continue at reduced duty. Test a phone without
significant-motion hardware if available. Compare battery usage over similar waiting periods with
the setting on/off. No battery-saving claim is established by host tests alone.

The ADB TPMS simulator does not simulate a paired-accessory connection, beacon radio presence or
Android's significant-motion sensor.

## 10. Backup and restore

1. Set up multiple kinds, bindings, calibrations, limits, units, alerts and automatic triggers.
   Export configuration JSON and keep a copy outside the app.
2. Change these values and add an unrelated vehicle. Import the saved file, inspect its summary,
   cancel once, then restore. Monitoring should stop and the app restart.
3. Check imported values/bindings and selected vehicle. The unrelated vehicle and history should
   remain. Calibration should match the backup; a sensor imported without calibration should lose
   any newer correction. Automatic watches must remain stopped until re-armed.
4. Export the safety copy and inspect the pre-restore values. Importing it is a merge and does not
   remove vehicles added later. History/raw captures are separate exports, not part of restore.
5. Edit copies of the JSON to contain an unsupported version, duplicate sensor ID, incompatible wheel,
   invalid multiplier or truncated content. Import must reject them before changing configuration.
6. Test picker cancellation and provider failure. On a disposable test setup, interrupt a confirmed
   restore and reopen: a committed journal should finish applying settings. Preserve the external
   backup before this interruption test. Test transfer to another phone when available.

## Local verification record

Host regression tests, schema migration checks, SQL transaction checks and layout renders completed.
Device checks above remain pending. Results for this build follow.

### Results for this debug artifact

- **172 Kotlin/JVM tests passed:** data vehicle 49, main 50, background 41, QR 8, unlocated 24;
  zero failures, errors or skipped tests. This includes local Paparazzi layout rendering.
- **6 host SQLite tests passed**, including upgrade from schema 8, transaction rollback, restore
  journal persistence, history preservation and equal-timestamp paging.
- SQLDelight migration verification passed. Debug APK built with the build cache disabled, and its
  APK v2 signature verifies. Updated phone instrumentation source compiles; it was not run on a device.
- Android lint: zero errors. Data vehicle/background/unlocated report no issues; main has one
  primitive-state boxing hint. Phone app retains 19 existing icon/resource warnings.
- Detekt passes for data vehicle, background, phone, QR and unlocated. Main's Detekt task remains
  red on **13 pre-existing MagicNumber findings** in `AlertSlider.kt` and `TyreStat.kt`; there are no
  new findings in this batch. Therefore the combined analysis command exits nonzero despite the
  successful tests. No baseline was regenerated or existing findings hidden.
- `git diff --check` passes. This verification record describes the debug checkpoint before release packaging.

APK: `D:\TyrePing\app\phone\build\outputs\apk\debug\phone-debug.apk`

- Package: `nz.paulwillard.tyreping.debug` / **TyrePing Debug**
- Version: `0.6.0` / `600000` (unreleased feature build)
- Minimum Android API: 28; target API: 37
- Size: 98,499,759 bytes
- SHA-256: `b1958d4f6f585ec439f8417358d78dba6bdd52e029486176c165155d53ebc103`

Logs in the ignored `build` directory: `batch2-final-tests.log`, `batch2-debug-build.log`,
`batch2-library-lint.log`, `batch2-quality.log`, `batch2-final-analysis.log`. Earlier logs may show
intermediate failures that were subsequently fixed. The known main Detekt failure above remains.
