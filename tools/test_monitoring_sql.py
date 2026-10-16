"""Migration/retention regressions using the shipped SQL and real SQLite, without a phone."""
import sqlite3
import unittest
from test_configuration_sql import SCHEMA, ORIGINAL, query


class MonitoringSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        source = sqlite3.connect((SCHEMA / "10.db").as_uri() + "?mode=ro", uri=True)
        source.backup(self.db)
        source.close()
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.execute("UPDATE Vehicle SET name='Existing ute', kind='UTE', lowPressure=180")
        self.db.execute(query("Sensor", "upsert"), (1, 20, ORIGINAL))
        self.reading(1, 200)
        self.db.executescript((SCHEMA / "migrations/10.sqm").read_text())

    def tearDown(self):
        self.db.close()

    def reading(self, sensor, time):
        self.db.execute(query("Tyre", "insert"), (sensor, time, -60, 20, 230, 25, 30, 0, ORIGINAL, 3.0, None))

    def event(self, key, ended=None):
        self.db.execute(query("MonitorHistory", "insertEvent"),
                        (key, key, ORIGINAL, "Existing ute", "wheel:FRONT_LEFT", 1, "Pressure", "Critical", 100, 150))
        if ended:
            self.db.execute(query("MonitorHistory", "endEvent"), (ended, "Condition cleared", key))

    def test_migration_preserves_vehicle_binding_reading_and_matches_snapshot(self):
        self.assertEqual(("Existing ute", "UTE", 180), self.db.execute("SELECT name,kind,lowPressure FROM Vehicle").fetchone())
        self.assertEqual((1, 20, ORIGINAL), self.db.execute("SELECT * FROM Sensor").fetchone())
        self.assertEqual(1, self.db.execute(query("Tyre", "countReadings")).fetchone()[0])
        snapshot = sqlite3.connect((SCHEMA / "11.db").as_uri() + "?mode=ro", uri=True)
        for table in ("AlertEvent", "RideReport"):
            self.assertEqual(snapshot.execute(f"PRAGMA table_info({table})").fetchall(),
                             self.db.execute(f"PRAGMA table_info({table})").fetchall())
        snapshot.close()

    def test_retention_protects_latest_assigned_reading_even_with_out_of_order_insert(self):
        self.reading(1, 100)
        self.reading(2, 150)
        self.assertEqual(2, self.db.execute(query("Tyre", "countOldReadings"), {"cutoff": 300}).fetchone()[0])
        archive = self.db.execute(query("Tyre", "oldReadings"), {"cutoff": 300, "pageSize": 1000, "pageOffset": 0}).fetchall()
        self.assertEqual([100, 150], [row[1] for row in archive])
        self.db.execute(query("Tyre", "pruneReadings"), {"cutoff": 300})
        self.assertEqual([(1, 200)], self.db.execute("SELECT id,timestamp FROM Tyre").fetchall())
        self.assertEqual((1, 20, ORIGINAL), self.db.execute("SELECT * FROM Sensor").fetchone())

    def test_replacement_does_not_rewrite_or_delete_old_sensor_history(self):
        self.db.execute(query("Sensor", "deleteByVehicle"), (ORIGINAL,))
        self.db.execute(query("Sensor", "upsert"), (2, 20, ORIGINAL))
        self.reading(2, 250)
        self.assertEqual([(1, 200), (2, 250)], self.db.execute("SELECT id,timestamp FROM Tyre").fetchall())

    def test_event_lifecycle_acknowledgement_and_open_event_retention(self):
        self.event("active")
        self.event("cleared", 160)
        self.db.execute(query("MonitorHistory", "dismissEvent"), (155, "active"))
        self.assertEqual(1, len(self.db.execute(query("MonitorHistory", "openEvents")).fetchall()))
        self.db.execute(query("MonitorHistory", "pruneEvents"), (200,))
        row = self.db.execute("SELECT id,dismissed,ended FROM AlertEvent").fetchone()
        self.assertEqual(("active", 155, None), row)
        found = self.db.execute(query("MonitorHistory", "searchEvents"),
                                {"search": "%critical%", "pageSize": 30, "pageOffset": 0}).fetchall()
        self.assertEqual(1, len(found))

    def test_reports_protect_unfinished_work_and_keep_summary_after_raw_cleanup(self):
        for identity in ("open", "finished"):
            self.db.execute(query("MonitorHistory", "insertRide"),
                            (identity, ORIGINAL, "Old vehicle name", 100, 200, '["wheel:FRONT_LEFT"]', 600))
        self.db.execute(query("MonitorHistory", "endRide"), (200, "Stopped", '{"raw":230}', "finished"))
        self.db.execute(query("Tyre", "pruneReadings"), {"cutoff": 1000})
        self.assertEqual('{"raw":230}', self.db.execute("SELECT summary FROM RideReport WHERE id='finished'").fetchone()[0])
        self.db.execute(query("MonitorHistory", "pruneRides"), (300,))
        self.assertEqual([("open",)], self.db.execute("SELECT id FROM RideReport").fetchall())

    def test_archive_queries_never_delete_and_keep_unsigned_sensor_bits(self):
        self.reading(-1, 50)
        before = self.db.execute(query("Tyre", "countReadings")).fetchone()[0]
        archive = self.db.execute(query("Tyre", "oldReadings"), {"cutoff": 100, "pageSize": 1000, "pageOffset": 0}).fetchall()
        self.assertEqual(-1, archive[0][0])
        self.assertEqual(before, self.db.execute(query("Tyre", "countReadings")).fetchone()[0])

    def test_ride_alert_count_includes_already_active_conditions_and_unsigned_id_search(self):
        self.event("active")
        self.event("cleared", 160)
        self.db.execute("UPDATE AlertEvent SET sensorId=4294967295 WHERE id='active'")
        self.assertEqual(1, self.db.execute(query("MonitorHistory", "countRideAlerts"),
                                         {"vehicleId": ORIGINAL, "start": 200, "end": 300}).fetchone()[0])
        found = self.db.execute(query("MonitorHistory", "searchEvents"),
                               {"search": "%4294967295%", "pageSize": 30, "pageOffset": 0}).fetchall()
        self.assertEqual(1, len(found))

    def test_old_service_heartbeat_and_finish_cannot_update_a_new_ride(self):
        for identity in ("old", "new"):
            self.db.execute(query("MonitorHistory", "insertRide"),
                            (identity, ORIGINAL, "Ute", 100, 100, '[]', 600))
        self.db.execute(query("MonitorHistory", "endRide"), (150, "Stopped", '{}', "old"))
        self.db.execute(query("MonitorHistory", "touchRides"), (200, "old"))
        self.db.execute(query("MonitorHistory", "endRide"), (200, "Late cleanup", '{}', "old"))
        self.assertEqual((100, None), self.db.execute("SELECT lastSeen,ended FROM RideReport WHERE id='new'").fetchone())
        self.assertEqual(150, self.db.execute("SELECT ended FROM RideReport WHERE id='old'").fetchone()[0])


if __name__ == "__main__":
    unittest.main()
