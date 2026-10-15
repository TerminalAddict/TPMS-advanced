"""Host-side checks against the actual SQLDelight queries and schema snapshots.

Run with Python 3: python tools/test_configuration_sql.py
No phone, third-party Python dependencies or application data are used.
"""
from pathlib import Path
import re
import sqlite3
import unittest

SCHEMA = Path(__file__).resolve().parents[1] / "data/vehicle/src/main/sqldelight"
QUERIES = SCHEMA / "com/masselis/tpmsadvanced/data/vehicle"
ORIGINAL = "466b2e13-e33a-4adf-a101-a920140a29a1"
OTHER = "f95a2617-bb8b-41fc-831a-c21e6eb05551"


def query(table, name):
    source = (QUERIES / f"{table}.sq").read_text(encoding="utf-8")
    return re.search(rf"(?m)^{name}:\s*([\s\S]*?;)", source).group(1)


class ConfigurationSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        source = sqlite3.connect((SCHEMA / "10.db").as_uri() + "?mode=ro", uri=True)
        source.backup(self.db)
        source.close()
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.execute(query("Vehicle", "insert"), (OTHER, "CAR_WITH_SPARE", "Other", 0))
        self.db.commit()

    def tearDown(self):
        self.db.close()

    def restore_vehicle(self, uuid=ORIGINAL):
        self.db.execute(query("Vehicle", "restoreConfiguration"),
                        (uuid, "CAR_WITH_SPARE", "Restored", 1, 180, 300, 1, 190, 320, -10, 45, 90))

    def reading(self, sensor, time, location=20):
        self.db.execute(query("Tyre", "insert"),
                        (sensor, time, -60, location, 230, 25, 30, 0, ORIGINAL, 3.0, None))

    def test_restore_keeps_history_and_unrelated_vehicles_and_one_favourite(self):
        self.reading(1, 100.125)
        self.restore_vehicle()
        self.assertEqual(2, self.db.execute("SELECT count(*) FROM Vehicle").fetchone()[0])
        self.assertEqual(1, self.db.execute("SELECT count(*) FROM Tyre").fetchone()[0])
        self.assertEqual([(ORIGINAL,)], self.db.execute("SELECT uuid FROM Vehicle WHERE isFavourite=1").fetchall())
        self.assertEqual(100.125, self.db.execute(query("Tyre", "latestBySensorByVehicle"), (1, ORIGINAL)).fetchone()[1])

    def test_import_moves_global_sensor_ownership_and_supports_spare(self):
        self.db.execute(query("Sensor", "upsert"), (-1, 20, OTHER))
        self.restore_vehicle()
        self.db.execute(query("Sensor", "deleteByVehicle"), (ORIGINAL,))
        self.db.execute(query("Sensor", "upsert"), (-1, 30, ORIGINAL))
        self.assertEqual([(-1, 30, ORIGINAL)], self.db.execute("SELECT * FROM Sensor").fetchall())

    def test_failed_transaction_rolls_back_bindings_vehicle_and_journal_together(self):
        with self.assertRaises(sqlite3.IntegrityError):
            with self.db:
                self.restore_vehicle()
                self.db.execute(query("ConfigurationRestore", "stage"), ('{"version":1}',))
                self.db.execute(query("Sensor", "upsert"), (1, 20, ORIGINAL))
                self.db.execute(query("Sensor", "upsert"), (2, 20, ORIGINAL))
        self.assertEqual("My car", self.db.execute("SELECT name FROM Vehicle WHERE uuid=?", (ORIGINAL,)).fetchone()[0])
        self.assertEqual([], self.db.execute("SELECT * FROM Sensor").fetchall())
        self.assertIsNone(self.db.execute(query("ConfigurationRestore", "pending")).fetchone())

    def test_committed_journal_survives_until_explicit_completion(self):
        with self.db:
            self.restore_vehicle()
            self.db.execute(query("ConfigurationRestore", "stage"), ('{"version":1}',))
        self.assertEqual('{"version":1}', self.db.execute(query("ConfigurationRestore", "pending")).fetchone()[0])
        self.db.execute(query("ConfigurationRestore", "complete"))
        self.assertIsNone(self.db.execute(query("ConfigurationRestore", "pending")).fetchone())

    def test_history_pages_do_not_drop_equal_timestamps_and_respect_window(self):
        for sensor, time in [(1, 100), (2, 100), (3, 100), (4, 101), (5, 102)]:
            self.reading(sensor, time)
        first = self.db.execute(query("Tyre", "historyPage"), (ORIGINAL, 100, 101, 2, 0)).fetchall()
        second = self.db.execute(query("Tyre", "historyPage"), (ORIGINAL, 100, 101, 2, 2)).fetchall()
        self.assertEqual([1, 2, 3, 4], [row[0] for row in first + second])

    def test_v060_upgrade_preserves_values_and_adds_restore_journal(self):
        older = sqlite3.connect((SCHEMA / "8.db").as_uri() + "?mode=ro", uri=True)
        upgraded = sqlite3.connect(":memory:")
        older.backup(upgraded)
        older.close()
        try:
            upgraded.execute("UPDATE Vehicle SET name='Existing vehicle', rearHighPressure=310")
            for version in (8, 9):
                upgraded.executescript((SCHEMA / f"migrations/{version}.sqm").read_text())
            self.assertEqual(("Existing vehicle", 310), upgraded.execute("SELECT name,rearHighPressure FROM Vehicle").fetchone())
            self.assertIsNone(upgraded.execute(query("ConfigurationRestore", "pending")).fetchone())
            self.assertEqual(2, upgraded.execute("SELECT count(*) FROM sqlite_master WHERE name LIKE 'tyre_vehicle_%'").fetchone()[0])
        finally:
            upgraded.close()


if __name__ == "__main__":
    unittest.main()
