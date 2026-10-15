<p align="center">
  <img src="app/phone/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="160" alt="TyrePing launcher icon">
</p>

<h1 align="center">TyrePing</h1>

<p align="center">
  <strong>Bluetooth Low Energy (BLE) TPMS Monitor for Android</strong>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-BLE%20TPMS-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Android Auto" src="https://img.shields.io/badge/Android%20Auto-supported-3DDC84?logo=androidauto&logoColor=white">
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/TerminalAddict/TyrePing"></a>
</p>

TyrePing is an Android monitor for Bluetooth Low Energy tyre-pressure monitoring system sensors.

It is an independent fork of [TPMS Advanced](https://github.com/VincentMasselis/TPMS-advanced), maintained by [TerminalAddict](https://github.com/TerminalAddict). TyrePing retains the upstream project’s Android architecture and sensor support while providing its own application identity, release path, branding, and feature direction.

## Project status

TyrePing is under active development.

- **Application ID:** `nz.paulwillard.tyreping`
- **Repository:** `TerminalAddict/TyrePing`
- **Main branch:** `tyreping`
- **Distribution:** currently built and installed manually
- **Google Play:** not currently published

Because TyrePing has its own Android application ID, it can be installed alongside the original TPMS Advanced app. A Play Store update to TPMS Advanced will not overwrite TyrePing.

## Features

TyrePing builds on the core capabilities of TPMS Advanced:

- Continuous BLE readings, including unchanged sensor broadcasts, with an accurate Last seen time
- Automatic scan recovery after Bluetooth is disabled or Android refuses a scan
- Live tyre pressure, temperature and supported sensor battery readings
- Low/high-pressure, temperature and low sensor battery alerts
- Immediate alert updates when pressure or temperature thresholds change
- Multiple vehicles with sensor ownership and cached readings kept with the correct tyre
- Cars (including a spare-wheel option), monowheels, vans, utes, motorcycles, single- and double-axle trailers, and three-wheelers
- Pressure and temperature unit selection
- QR-code sensor binding with invalid-code and conflicting-assignment checks
- Tap-wheel sensor management, guided Bluetooth identification, and move/swap/rotate assignments
- Unfiltered BLE sensor discovery, installation markers, packet inspection and per-device JSONL export
- Individual sensor pressure and temperature calibration
- Optional speech, tones and vibration with repeat intervals and temporary silence
- Pressure/temperature history graphs, reading sessions and CSV export
- Optional raw BLE capture with status bits and diagnostic export
- Configuration backup and restore with validation and a safety copy
- Latest recorded readings retained across app restarts
- Manual background monitoring and automatic ride monitoring using a Bluetooth accessory or BLE beacon
- Interrupted monitoring detection with Android exit reasons and a session restart prompt
- Optional experimental pressure-loss warnings
- Tap-to-enlarge readings and larger landscape cards
- Simulated TPMS advertisements through ADB in debug builds
- Dark mode
- Android Auto support
- Direct vehicle shortcuts

Supported sensor families include formats used by Sysgration, Pecham, Bekubee/HRTPMS, Wicarlink/LYTPMS, and related compatible BLE sensors. Exact compatibility depends on the sensor’s BLE advertisement format.

For a double axle trailer, choose **Double axle trailer** when adding a vehicle. The hitch marks
the front: assign front-left/right sensors to the axle nearest the hitch and rear-left/right
sensors to the other axle. Four-sensor QR kits retain all four positions. Separate front/rear
pressure limits are available for the two axles.

### Monitoring reliability

Every accepted sensor broadcast refreshes the reading timestamp, even when pressure and temperature
are unchanged. Scanning waits for Bluetooth to return and retries refused starts with a delay.
Missing permissions or unsupported scanning require user action; the monitor notification reports
waiting, retrying and failure states.

If a background monitoring session or automatic ride watch is interrupted, the next app launch
offers **Restart monitoring** for the saved vehicle and mode, or **Keep stopped** to acknowledge it.
The prompt also appears when returning to an already-open app after its service stops. Android 11+
may provide a process exit reason and time; otherwise TyrePing reports that the cause is unknown.
Reboots and app updates receive their own explanation. Android stop requests are reported separately
from low-memory stops and crashes; an unknown cause is not assumed to be battery management.

The recovery prompt includes permission setup and a link to Android's App settings. If interruptions
recur, check the phone's background activity/battery restrictions there. Choose **Restart monitoring**
to resume the session; a waiting automatic watch resumes its saved accessory/beacon trigger.
Normal Stop actions do not produce a recovery warning, and a successfully restarted automatic service
does not prompt. Detection uses a small saved session record without periodic wake-ups. It cannot
notify you while Android has killed the app; reopen TyrePing to check. This applies to sessions started
with the new build. See Android's [process exit information](https://developer.android.com/reference/android/app/ApplicationExitInfo).

To test recovery without sensors, install the debug build, grant its permissions, choose a vehicle
and start background monitoring. Then simulate an Android stop from your computer:

```cmd
adb shell am force-stop nz.paulwillard.tyreping.debug
```

Open **TyrePing Debug** again. The recovery prompt should identify the saved vehicle; choose
**Restart monitoring** and check that its monitoring notification returns. Android 11+ should normally
report a stop request for this test, rather than a low-memory or battery-management explanation.
Repeat using the app or notification's normal Stop action: reopening should show no recovery prompt.
This checks recovery without requiring a sensor broadcast; it does not simulate every manufacturer's
background process management.

An assigned sensor is kept with its saved tyre and vehicle. Cached readings remain visible when
returning to a screen, while changing a binding clears the previous tyre's reading. A silent sensor
does not prevent alerts from other tyres. Threshold changes re-evaluate existing readings immediately.

The current feature set is covered by automated tests, including local layout renders. Physical
sensor reception, accessory/beacon detection, screen-off behaviour and real ride leak detection
still need hardware validation. Leak detection is experimental and disabled by default.

The v0.8.0 pre-release adds BLE sensor discovery to the v0.7.0 feature batch tracked in
[ROADMAP.md](ROADMAP.md). These additions are in testing; v0.6.0 remains the latest stable release.
See [TESTING.md](TESTING.md) for installation, the discovery workflow, and a checklist covering all
ten earlier additions, including simulated readings without physical sensors.

### Audible and vibration alerts

In Settings, choose **tones** or **speech**, enable vibration if wanted, and select normal and critical
repeat intervals. Both are off initially. Alarm volume controls loudness; speech falls back to tones
if a usable English speech engine is unavailable. Critical alerts use a more distinctive vibration:
pressure at least 25% below the minimum or 20% above the maximum, or temperature at least 20°C above
the high limit. Other out-of-range readings, possible leaks and low batteries use the normal interval.

**Silence all alerts for 10 min** appears while an audible/vibration alert is active, and in its
monitoring notification. Silence covers critical alerts too; the app offers **Resume alerts**.
Visual warnings remain. Fresh packets do not reset repeat intervals. Clearing an alert or changing
thresholds/calibration updates the alert immediately. Repeats require a reading received during the
current monitoring session and stop after ten minutes without a fresh reading. Cached values alone
do not start an audible alert after reopening the app. Phone audio routing, Do Not Disturb and
screen-off delivery remain device tests.

### Sensor assignment and calibration

Tap a tyre outline or its link icon, or use **Sensors** in Settings. An empty reading also
opens sensor management; tapping populated readings still magnifies them. Enter a sensor ID in
decimal or with a `0x` hex prefix, or choose **Identify sensor via Bluetooth**. While parked, removing
and refitting a sensor can highlight a candidate whose pressure changed by at least 15 kPa. Verify
the ID and confirm the proposed assignment; the app never binds a nearby sensor automatically.

The same dialog supports unassigning, moving into an empty wheel, swapping occupied wheels and
rotating three or more assigned sensors. Rotation shows the exact mapping before Apply. Changes
are transactional, retain sensor IDs, and preserve historical wheel positions. Cached readings
follow swaps within the vehicle. A stale move/assignment confirmation asks you to review it again.

**Calibrate sensor** applies `corrected = raw × multiplier + offset` independently to pressure and
temperature. Pressure offsets use kPa and temperature offsets use °C, regardless of display units.
Multipliers are limited to 0.5–1.5, pressure offsets to ±100 kPa and temperature offsets to ±30°C.
Cancel discards edits; Reset changes the draft until Apply is tapped. Calibration follows the physical
sensor across wheels and vehicles. Live displays and alerts use corrected values; raw recordings
stay unchanged. Calibration changes also reset the experimental leak trend.

### Discover sensors without QR codes or printed IDs

In v0.8.0, open **Settings → Sensors → Discover sensors**. Choose a wheel on
the current vehicle, start scanning, tap **Mark, then fit sensor**, and fit one sensor while parked.
The page highlights devices first seen since the mark, changed packets, and decoded pressure changes
of at least 5 kPa. These are candidates to inspect, not automatic assignments. Check the decoded
pressure and temperature against the tyre before confirming **Assign to selected wheel**. Existing
assignments and replacements are shown before saving.

Discovery temporarily removes the shared TPMS scanner's service/manufacturer filters, so unnamed
devices and unfamiliar protocols remain visible. Every packet is tried against the existing decoders,
including both Bekubee/HRTPMS variants. The page shows address, RSSI, last seen, raw advertisement,
manufacturer data, service UUIDs/service data, and any recognised readings. Unknown devices can be
exported but cannot be assigned. A previously decoded reading keeps its own timestamp, and assignment
requires a reading less than a minute old. Known sensor IDs and their discovery addresses are saved;
normal monitoring includes those addresses after restart. Addresses that change require rediscovery.

The photographed **MRD-TY-T05-04** kit is a candidate for testing, not yet a verified supported model.
Its F.W/R.W and A/B markings do not supply unique sensor IDs. A [T05 supplier page](https://www.aotu-link.com/html/products/tire-pressure-sensor-t05.html)
describes HRTpms support, but this does not establish that every T05-labelled kit uses an identical
packet format. Actual captures from this kit are needed to confirm decoding; no speculative decoder
or Bluetooth connection/pairing commands have been added.

Each scan lasts up to two minutes and stops when the page is hidden or closed. It shares one radio
callback with tyre monitoring; normal filters resume afterwards, subject to Android's scan-start
rate limit. Android may pause unfiltered scanning with the screen off; see the
[Android scanner reference](https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner).
The app still uses Android's `neverForLocation` permission assertion, which can suppress some beacon
types; “unfiltered” describes TyrePing's scan filters, not a guarantee that Android delivers every radio packet.

**Export device** saves only that address's retained packets; **Export all packets** saves the session.
JSONL includes complete received bytes (including trailing zeros), timestamps, manufacturer/service
data, RSSI, and decoder results. Memory is bounded to 200 devices and the latest 1,000 packets; older
entries rotate out. Export before closing the page, as this session capture is not persistent. The
separate opt-in BLE diagnostics capture below can retain packets across app restarts.

### History and diagnostics

Open **Pressure / temperature history and CSV** in Settings. Choose a 1-hour, 6-hour, 24-hour or
7-day window, page backwards/forwards, or return to Latest. Select a reading session to narrow the
graphs and CSV export. Sessions are inferred from gaps over 15 minutes; they are not GPS ride logs.
Tap a graph near a point for its time, sensor and value. Graphs show raw kPa/°C with sensor colours,
retain brief extrema when downsampling, and stop connecting readings across long gaps.

Graphs are limited to 20,000 readings per window and explain when a shorter window is needed.
CSV export includes every reading in the selected time range, with UTC timestamps, original wheel,
sensor ID, raw pressure/temperature, battery, RSSI and alarm flag. Calibration does not rewrite history.

**BLE diagnostics → Capture packets** saves TPMS advertisements while monitoring and all delivered
BLE advertisements while discovery is running.
Capture is opt-in and starts off after every process restart. The JSONL export includes full packet
bytes, Bluetooth address, UTC timestamp, RSSI, decoder, sensor ID and available raw status bits.
Unknown bits are not given guessed alarm meanings. Two rotating files retain about 4 MB; queue drops
are reported. Export or clear the saved capture from the same section. It is separate from CSV history.

### Additional vehicles and waiting power

**Car with spare** provides five distinct positions; its spare needs an explicit sensor assignment.
Four-sensor QR kits populate only its normal road wheels. **Monowheel** has one explicit sensor slot;
multi-sensor kits are rejected. **Van** and **Ute** currently use the four-wheel car diagram and the
same front/rear threshold controls. Car-with-spare and monowheel use labelled reading cards in portrait.
The double axle trailer retains its existing schema image and four positions.

**Save power while waiting for a BLE beacon** reduces scan duty while stationary, screen-off and
not charging. Recent significant motion, an interactive screen or charging restores full scanning.
Devices without a usable significant-motion sensor use balanced waiting scans. Active tyre monitoring
always uses full scan duty; waiting scans are never suspended. Beacon presence survives scan-mode
changes, and Android's unavailable-RSSI marker is ignored. Actual battery savings require phone testing.

### Backup and restore

Use **Backup and restore → Export configuration** to save vehicles, wheel assignments, thresholds,
calibrations, discovered sensor addresses, units, alert preferences, leak detection, waiting-power settings
and ride triggers as JSON. Discovery addresses are optional when reading backups from older builds.
History and raw captures use their separate exports. Permissions, temporary silence and running
monitoring sessions are not transferred.

Import validates the format/version, vehicles, wheel compatibility, unique sensor ownership and
setting ranges before showing a confirmation. Matching vehicle IDs and their bindings are replaced;
other vehicles and recorded history remain. Imported sensor IDs may move from another vehicle.
Global settings change, and a safety copy is saved before any database changes. Vehicles and a
settings recovery journal commit together so reopening can finish an interrupted restore.

Confirming stops background monitoring and restarts TyrePing. Re-arm automatic watches yourself.
**Export safety copy from last restore** retrieves the previous configuration if needed. Restore is
a merge, so importing that safety copy does not delete vehicles created since it was saved.

## TyrePing identity

TyrePing is installed as a separate Android application:

```text
nz.paulwillard.tyreping
```

The internal Kotlin package names remain based on the upstream project for now. They do not affect TyrePing’s ability to install as an independent app.

## Building

### Requirements

- Git
- Android Studio
- Android SDK and platform tools
- Java supplied by Android Studio, or another compatible JDK

On Windows, the usual Java location is:

```text
C:\Program Files\Android\Android Studio\jbr
```

### Clone

```bash
git clone --branch tyreping https://github.com/TerminalAddict/TyrePing.git
cd TyrePing
```

### Build a debug APK

Windows:

```cmd
gradlew.bat :app:phone:assembleDebug
```

Linux/macOS:

```bash
./gradlew :app:phone:assembleDebug
```

The debug APK is created at:

```text
app/phone/build/outputs/apk/debug/phone-debug.apk
```

### Install with ADB

Install the debug build for development:

```cmd
adb install -r app\phone\build\outputs\apk\debug\phone-debug.apk
```

Its application ID is `nz.paulwillard.tyreping.debug`; it has separate data from the signed release.
For the signed release, install the APK produced by the release script below.

## Test sensors over ADB (debug builds)

Open the debug app with demo mode off, grant its Bluetooth permissions, enable Bluetooth, and keep
the vehicle screen or background monitoring active. No physical sensors are needed. Generated
readings and raw replay pass through the normal filters, decoders, bindings, database, and alerts.
These readings are stored in the debug app's separate database.

Generate a Sysgration reading at the front-left wheel (wheel values: 0 FL, 1 FR, 2 RL, 3 RR):

```powershell
adb shell am broadcast -a nz.paulwillard.tyreping.debug.MOCK_ADVERTISEMENT -p nz.paulwillard.tyreping.debug --es brand sysgration --ef kpa 230 --ef celsius 21 --ei id 1192960 --ei wheel 0 --ei battery 80
```

Generate a Bekubee/A827 reading (bind the synthetic sensor through the normal binding screen):

```powershell
adb shell am broadcast -a nz.paulwillard.tyreping.debug.MOCK_ADVERTISEMENT -p nz.paulwillard.tyreping.debug --es brand bekubee --ef kpa 230 --ef celsius 21 --ef volts 3.0 --ei id 5647616
```

Repeat either command with unchanged pressure to test Last seen, or change `kpa` to test alerts.
Sysgration also accepts `--ez alarm true`. Its IDs must be multiples of 256. `battery` is a percentage
for Sysgration; `volts` is voltage for Bekubee. Optional `--es address AA:BB:CC:DD:EE:01` and
`--ei rssi -60` set reception metadata.

Replay a captured raw advertisement from any supported decoder:

```powershell
adb shell am broadcast -a nz.paulwillard.tyreping.debug.MOCK_ADVERTISEMENT -p nz.paulwillard.tyreping.debug --es bytes 050854504D530CFF0200AF4500A7002D56A8B4030327A8
adb logcat -s MockAdvertisements BluetoothLeScannerImpl
```

A received, accepted broadcast returns result `1`; a rejected packet returns `0` with a reason.
If no receiver is running, Android may also return `0` without a reason. The receiver requires
`android.permission.DUMP` (held by ADB shell) and is entirely absent from release builds.

## Sensor battery monitoring

Readings show the sensor's reported voltage or percentage. A low-battery warning appears below
2.6 V or at 10% and below; pressure and temperature warnings take priority in the monitoring
notification. Voltage is not converted to a percentage. Older cached readings without battery-unit
metadata stay unknown until the sensor broadcasts again.

Use the debug commands above with `--ef volts 2.5` (Bekubee) or `--ei battery 5` (Sysgration) to
exercise the warning. Low battery preserves the measured tyre pressure.

## Automatic ride monitoring

In the selected vehicle's settings, open **Automatic ride monitoring**, allow Bluetooth and
notifications, then choose a paired accessory or enter a BLE beacon's stable Bluetooth address.
Select **Start watching this vehicle** while the app is open. One vehicle can be watched at a time;
starting another watch replaces it. The foreground notification shows whether TyrePing is waiting
or monitoring, and its **Stop** action disables the watch.

A Bluetooth connection starts tyre monitoring. Existing headset/audio connections are detected;
for other accessories, reconnect after starting the watch. A disconnect has a 20-second grace period.
Beacon mode requires two sufficiently strong packets at least one second apart, waits 60 seconds
without a strong packet before declaring absence, then applies the same 20-second grace period.
The default minimum signal is −80 dBm and can be changed in settings. Refused beacon scans retry.

The watch keeps a foreground service running, including while waiting, and uses battery. It can
restore its selected vehicle after an Android service restart; reopen the app and start watching
again after a reboot or force-stop. Android/vendor background limits and some filtered beacon
formats can still prevent detection. A rotating/random beacon address is not supported. Physical
accessory, beacon, screen-off and battery-use checks remain pending.

On Android 10/11, setup also requests Location access **Allow all the time**, which those versions
require for background Bluetooth discovery. TyrePing does not record GPS location. Android 12+
uses Nearby devices permission instead. See [Android's Bluetooth permission guidance](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).

## Experimental leak detection

Enable **Experimental leak detection** in settings to watch live readings for pressure loss. It is
off by default and applies to all vehicles. A drop of at least 7 kPa from the session peak must remain
on two readings at least five seconds apart. Cooling by more than 1°C resets the baseline, as do a
sensor change, pressure below 10 kPa, a ten-minute gap, or a new listening session. Disabling the
setting clears the warning; re-enabling starts a fresh trend. Trends are not restored from history.

The tyre card and monitoring notification show **Possible leak (experimental)**. Pressure and
temperature alerts take priority. This heuristic can miss leaks or give false warnings; keep normal
pressure alerts enabled. It has automated coverage but has not been validated on a real ride.

To exercise the warning without sensors, enable it in the debug app, keep the relevant vehicle
screen or manual background monitor active, and run this PowerShell example (demo mode off):

```powershell
foreach ($pressure in @(240, 225, 225)) {
    adb shell am broadcast -a nz.paulwillard.tyreping.debug.MOCK_ADVERTISEMENT -p nz.paulwillard.tyreping.debug --es brand sysgration --ef kpa $pressure --ef celsius 25 --ei id 1192960 --ei wheel 0 --ei battery 80
    Start-Sleep -Seconds 6
}
```

Use the same sensor ID as the wheel's binding, or an unbound matching wheel. This tests the TPMS
reading path; it does not emulate a Bluetooth accessory connection or a beacon radio signal.

## Larger readings

Tap any tyre's readings to enlarge the live values. The view closes after five seconds, or with
**Close**, Back, or a tap outside it. Rotate the phone to landscape for larger labelled cards. Cards
scroll when the available height or system font size requires more room; three-wheel layouts keep
the single axle centred across the row.

## Upstream synchronisation

The recommended remotes are:

```text
origin    https://github.com/TerminalAddict/TyrePing.git
upstream  https://github.com/VincentMasselis/TPMS-advanced.git
```

To update the local upstream-tracking `develop` branch:

```cmd
git fetch upstream
git switch develop
git pull --ff-only upstream develop
git push origin develop
```

Then merge reviewed upstream changes into TyrePing:

```cmd
git switch tyreping
git merge develop
```

Resolve and test any conflicts before pushing:

```cmd
gradlew.bat :app:phone:assembleDebug
git push
```

Upstream changes are selected deliberately. TyrePing does not have to adopt every behavioural or release change made by TPMS Advanced.

## Release methodology

Run the release script from PowerShell in the repository. It requires a clean `tyreping` branch
that exactly matches `origin/tyreping`, an authenticated GitHub CLI, the Android toolchain, and
the existing signing files in `secrets/`. Commit and push the intended changes first.

For example, to prepare the `0.8.0` pre-release, check the prerequisites without building or publishing:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tyreping-release.ps1 -Version 0.8.0 -DryRun
```

Then build and publish the pre-release:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tyreping-release.ps1 -Version 0.8.0
```

The script asks you to type `RELEASE v0.8.0`. It updates the version, runs unit tests, builds and
verifies the signed APK, commits the version change, creates a tag, pushes `tyreping` and the tag,
and publishes a GitHub pre-release. Release notes come from commit subjects unless `-NotesFile`
is supplied. Choose a new, unused version for subsequent releases.

With the repository at `D:\TyrePing`, the default output is:

```text
D:\TyrePing-Releases\0.8.0\
    TyrePing-v0.8.0.apk
    SHA256SUMS.txt
    RELEASE_NOTES.md
```

Install that APK with:

```powershell
adb install -r D:\TyrePing-Releases\0.8.0\TyrePing-v0.8.0.apk
```

Add `-Stable` to publish a normal release. Use `-Resume` only when recovering a failed publication
whose release tag already exists locally and on GitHub. `-DryRun` checks prerequisites; it does not
run the build or tests.

The script checks the permanent TyrePing signing certificate and publishes the APK and checksum.
Signing files stay local.

## Development direction

Planned or under evaluation:

- Additional BLE sensor formats
- Additional motorcycle-focused monitoring features
- Optional display and notification settings

Features may be adapted from other open-source projects only after checking licence compatibility and attribution requirements.

## Safety

TyrePing is an advisory monitoring tool, not a replacement for regular tyre inspection or a calibrated pressure gauge.

Do not rely solely on the app for vehicle safety decisions. Check tyre pressures manually and follow the vehicle and tyre manufacturers’ recommendations.

## Upstream project

TyrePing is based on:

- [VincentMasselis/TPMS-advanced](https://github.com/VincentMasselis/TPMS-advanced)

Generic fixes and improvements that are useful to the upstream project may still be contributed back where appropriate.

## Licence

TyrePing retains the upstream project’s Apache License 2.0 licensing.

See [LICENSE](LICENSE) for the full licence text.

The TyrePing name, package identity, branding, and launcher artwork are specific to this fork. References to the upstream project do not imply endorsement by its original author.
