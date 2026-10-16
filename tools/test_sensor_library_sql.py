"""Execute the shipped inventory migration/triggers with real SQLite, without Android."""
import sqlite3
import unittest
from test_configuration_sql import SCHEMA, ORIGINAL, query


class SensorLibrarySqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        source = sqlite3.connect((SCHEMA / "11.db").as_uri() + "?mode=ro", uri=True)
        source.backup(self.db)
        source.close()
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.execute(query("Sensor", "upsert"), (-1, 20, ORIGINAL))
        self.db.execute(query("Tyre", "insert"), (-1, 100, -60, 20, 230, 25, 30, 0, ORIGINAL, 3.0, None))
        self.db.execute(query("Tyre", "insert"), (2, 200, -70, 21, 220, 20, 90, 0, ORIGINAL, None, 90))
        self.db.executescript((SCHEMA / "migrations/11.sqm").read_text())

    def tearDown(self):
        self.db.close()

    def test_migration_retains_all_readings_and_backfills_assigned_and_unassigned_sensors(self):
        self.assertEqual(2, self.db.execute("SELECT COUNT(*) FROM Tyre").fetchone()[0])
        self.assertEqual([(-1, 100, 3.0, None), (2, 200, None, 90)], self.db.execute(
            "SELECT id,lastSeen,voltage,percent FROM SensorInventory ORDER BY id").fetchall())
        self.assertEqual(1, self.db.execute("SELECT COUNT(*) FROM SensorInstallation WHERE ended IS NULL").fetchone()[0])
        self.assertEqual([], self.db.execute("SELECT * FROM SensorHealthDaily").fetchall())
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_unassign_and_replacement_preserve_spares_old_identity_and_install_history(self):
        self.db.execute(query("Sensor", "deleteByVehicleAndLocation"), (ORIGINAL, 20))
        self.db.execute(query("Sensor", "upsert"), (3, 20, ORIGINAL))
        self.assertEqual([(-1,), (2,), (3,)], self.db.execute("SELECT id FROM SensorInventory ORDER BY id").fetchall())
        old = self.db.execute("SELECT sensorId,ended FROM SensorInstallation WHERE sensorId=-1").fetchone()
        self.assertIsNotNone(old[1])
        self.assertEqual([(3,)], self.db.execute("SELECT sensorId FROM SensorInstallation WHERE ended IS NULL").fetchall())
        self.assertEqual(2, self.db.execute("SELECT COUNT(*) FROM Tyre").fetchone()[0])

    def test_move_closes_old_assignment_without_rewriting_history(self):
        self.db.execute(query("Sensor", "upsert"), (-1, 21, ORIGINAL))
        self.assertEqual([(20, 1), (21, 0)], self.db.execute(
            "SELECT location, ended IS NOT NULL FROM SensorInstallation ORDER BY id").fetchall())
        self.assertEqual(20, self.db.execute("SELECT location FROM Tyre WHERE id=-1").fetchone()[0])
        self.db.execute(query("Sensor", "upsert"), (-1, 21, ORIGINAL))
        self.assertEqual(2, self.db.execute("SELECT COUNT(*) FROM SensorInstallation").fetchone()[0])

    def test_vehicle_delete_keeps_library_and_installation_snapshots(self):
        self.db.execute(query("Vehicle", "insert"), ("10000000-0000-0000-0000-000000000000", "CAR", "Other", 0))
        self.db.execute(query("Vehicle", "delete"), (ORIGINAL,))
        self.assertEqual(2, self.db.execute("SELECT COUNT(*) FROM SensorInventory").fetchone()[0])
        self.assertIsNotNone(self.db.execute("SELECT ended FROM SensorInstallation").fetchone()[0])

    def test_metadata_save_does_not_erase_last_reading(self):
        self.db.execute(query("SensorLibrary", "saveMetadata"), ("Spare", "Maker", "Notes", -1))
        self.assertEqual(("Spare", 100, 230), self.db.execute(
            "SELECT label,lastSeen,pressure FROM SensorInventory WHERE id=-1").fetchone())

    def test_daily_counts_accumulate_and_keep_battery_units_separate(self):
        sql = query("SensorLibrary", "addDaily")
        self.db.execute(sql, (-1, 1, -60, 1, 3.0, 1, 0, 0, 60, 0, 60, 0))
        self.db.execute(sql, (-1, 1, -80, 1, 2.8, 1, 0, 0, 180, 2, 180, 1))
        row = self.db.execute("SELECT packets,rssiTotal,voltageTotal,voltageCount,percentCount,missed,longestGap,questionable FROM SensorHealthDaily").fetchone()
        self.assertEqual((2, -140, 5.8, 2, 0, 2, 180, 1), row)

    def test_health_retention_exports_exact_eligible_days_without_touching_inventory_or_readings(self):
        sql = query("SensorLibrary", "addDaily")
        for day in (1, 20, 30):
            self.db.execute(sql, (-1, day, -60, 1, 3.0, 1, 0, 0, 60, 0, 60, 0))
        self.assertEqual(2, self.db.execute(query("SensorLibrary", "oldHealthCount"), (30,)).fetchone()[0])
        old = self.db.execute(query("SensorLibrary", "oldHealth"), (30, 1000, 0)).fetchall()
        self.assertEqual([1, 20], [row[1] for row in old])
        self.db.execute(query("SensorLibrary", "pruneHealth"), (30,))
        self.assertEqual([(30,)], self.db.execute("SELECT day FROM SensorHealthDaily").fetchall())
        self.assertEqual(2, self.db.execute("SELECT COUNT(*) FROM SensorInventory").fetchone()[0])
        self.assertEqual(2, self.db.execute("SELECT COUNT(*) FROM Tyre").fetchone()[0])
        self.assertEqual(1, self.db.execute("SELECT COUNT(*) FROM SensorInstallation").fetchone()[0])

    def test_issue_aggregation_keeps_identity_and_latest_evidence_without_touching_assignments(self):
        sql = query("SensorLibrary", "issue")
        # The shipped query mixes named time with positional fields; SQLite accepts a named mapping for time
        # after substituting this one repeated timestamp, as its generated Android binder does.
        for time, packet in ((100, '{"bytes":"01"}'), (200, '{"bytes":"02"}')):
            self.db.execute(sql.replace(":time", str(time)), ("changed", -1, "AA:BB:CC:DD:EE:01", "ID changed", packet))
        self.assertEqual((-1, 100, 200, 2, '{"bytes":"02"}'), self.db.execute(
            "SELECT sensorId,firstSeen,lastSeen,occurrences,packet FROM SensorIssue").fetchone())
        self.assertEqual([(-1,)], self.db.execute("SELECT id FROM Sensor").fetchall())

    def test_failed_assignment_transaction_rolls_back_installation_events(self):
        self.db.execute("BEGIN")
        self.db.execute(query("Sensor", "deleteByVehicleAndLocation"), (ORIGINAL, 20))
        self.db.execute(query("Sensor", "upsert"), (3, 20, ORIGINAL))
        self.db.rollback()
        self.assertEqual([(-1,)], self.db.execute("SELECT id FROM Sensor").fetchall())
        self.assertEqual([(-1,)], self.db.execute("SELECT sensorId FROM SensorInstallation WHERE ended IS NULL").fetchall())
        self.assertIsNone(self.db.execute("SELECT id FROM SensorInventory WHERE id=3").fetchone())


if __name__ == "__main__":
    unittest.main()
