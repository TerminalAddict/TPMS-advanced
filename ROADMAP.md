# TyrePing feature roadmap

Second feature batch, requested 15 October 2026, targeting the **v0.7.0 pre-release**.
All ten items are implemented, based on v0.6.0. Automated checks and the combined debug build
are complete; physical-device validation remains pending.

The PRs in [aveshev/persistent-tpms](https://github.com/aveshev/persistent-tpms) are design references.
The implementation is adapted to TyrePing's service, scanner, reactive readings and database.

| Priority | Feature | Reference PRs | Current implementation |
|---|---|---|---|
| 1 | Unexpected monitoring shutdowns | #52 | Durable session record; Android exit reason/reboot/update explanation; restore saved vehicle and mode |
| 2 | Advanced audible/vibration alerts | #64, #81, #88 | Speech/tones, critical vibration, separate repeat intervals, ten-minute silence, reactive clearing |
| 3 | Individual sensor calibration | #80 | Pressure/temperature offsets and multipliers; corrected readings/alerts; raw history preserved |
| 4 | Tap-wheel sensor management | #72, #78 | Wheel/link controls, manual ID entry, guided pressure-change identification, explicit confirmation |
| 5 | Move, swap and rotate | #75 | Atomic changes with stale-confirmation checks, mapping preview and calibration following sensor ID |
| 6 | History graphs and CSV | Original | Window navigation, inferred reading sessions, per-sensor pressure/temperature graphs, paginated CSV |
| 7 | Raw BLE diagnostics | #51, #45 | Opt-in bounded JSONL capture, raw bytes/status bits, metadata, export and clear |
| 8 | More vehicle configurations | #76/custom | Car with spare, monowheel, van and ute; existing double axle trailer retained |
| 9 | Background battery management | #59, #62, #95 | Motion/screen/power-aware beacon waiting scans; full-duty active monitoring; hidden identification scans stop |
| 10 | Backup and restore | Original | Versioned JSON, whole-file validation, preview, safety copy, merge and crash-recoverable settings journal |

## Scope and testing

- [README.md](README.md) explains controls, defaults and limitations.
- [TESTING.md](TESTING.md) covers installation and all ten features, including ADB simulation.
- Graph sessions use a 15-minute gap heuristic; CSV and graphs contain raw readings.
- Van and ute currently reuse the car diagram. The spare and single wheel require explicit binding.
- Diagnostics capture TPMS-filtered advertisements, not every Bluetooth device nearby.
- Restores merge configuration; history and raw captures have separate exports, not imports.
- Motion savings apply to beacon waiting, never to active tyre monitoring.
- Real radio reception, audio routing, motion, screen-off/OEM behaviour, battery savings and document
  providers remain phone tests. Do not treat a successful local build as those checks passing.

## Local verification

Regression suites cover scan recovery, binding isolation, alert cadence, recovery classification,
calibration, rotations, backup validation, new vehicle/QR mappings and inferred sessions. Paparazzi
renders cover the existing vehicle layouts plus spare-car, monowheel, calibration and history.
SQLDelight verifies schema upgrades; `python tools/test_configuration_sql.py` adds transaction,
restore-journal, history-preservation and equal-timestamp paging checks against the actual SQL queries.
The final build/check results are recorded in TESTING.md and the local PROJECT_CONTEXT.md handover.
