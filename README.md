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
- Cars, motorcycles, trailers, and three-wheelers
- Pressure and temperature unit selection
- QR-code sensor binding with invalid-code and conflicting-assignment checks
- Favourite sensor binding
- Latest recorded readings retained across app restarts
- Manual background monitoring and automatic ride monitoring using a Bluetooth accessory or BLE beacon
- Optional experimental pressure-loss warnings
- Tap-to-enlarge readings and larger landscape cards
- Simulated TPMS advertisements through ADB in debug builds
- Dark mode
- Android Auto support
- Direct vehicle shortcuts

Supported sensor families include formats used by Sysgration, Pecham, Bekubee/HRTPMS, Wicarlink/LYTPMS, and related compatible BLE sensors. Exact compatibility depends on the sensor’s BLE advertisement format.

### Monitoring reliability

Every accepted sensor broadcast refreshes the reading timestamp, even when pressure and temperature
are unchanged. Scanning waits for Bluetooth to return and retries refused starts with a delay.
Missing permissions or unsupported scanning require user action; the monitor notification reports
waiting, retrying and failure states.

An assigned sensor is kept with its saved tyre and vehicle. Cached readings remain visible when
returning to a screen, while changing a binding clears the previous tyre's reading. A silent sensor
does not prevent alerts from other tyres. Threshold changes re-evaluate existing readings immediately.

The current feature set has 91 passing automated tests, including local layout renders. Physical
sensor reception, accessory/beacon detection, screen-off behaviour and real ride leak detection
still need hardware validation. Leak detection is experimental and disabled by default.

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

For the next release after `0.4.0`, check the prerequisites without building or publishing:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tyreping-release.ps1 -Version 0.5.0 -DryRun
```

Then build and publish the pre-release:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tyreping-release.ps1 -Version 0.5.0
```

The script asks you to type `RELEASE v0.5.0`. It updates the version, runs unit tests, builds and
verifies the signed APK, commits the version change, creates a tag, pushes `tyreping` and the tag,
and publishes a GitHub pre-release. Release notes come from commit subjects unless `-NotesFile`
is supplied. Choose a new, unused version for subsequent releases.

With the repository at `D:\TyrePing`, the default output is:

```text
D:\TyrePing-Releases\0.5.0\
    TyrePing-v0.5.0.apk
    SHA256SUMS.txt
    RELEASE_NOTES.md
```

Install that APK with:

```powershell
adb install -r D:\TyrePing-Releases\0.5.0\TyrePing-v0.5.0.apk
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
