"""Durable report/resource facts; mixed into the Root journal fixture suite."""
import errno
from managed_journal_fixture import KEY_BYTES


class CompletionCases:
    def test_worker_fact_requires_ready_identity_and_validated_eof(self):
        self.assertEqual(0, self.call("begin")["result"])
        self.expect_failure("worker-store", errno.EPROTO)
        self.assertEqual(0, self.call("ready")["result"])
        self.expect_failure("worker-pending", errno.EPROTO)
        self.assertFalse((self.state / "managed.worker").exists())
        self.assertEqual(0, self.call("worker-store")["result"])
        self.assertEqual(1, self.call("worker-read")["result"])
        self.assertEqual(0, self.call("worker-store")["result"])

    def test_durable_worker_fact_is_bound_to_ticket_pid_and_start(self):
        self.call("begin"); self.call("ready"); self.call("worker-store")
        path = self.state / "managed.worker"; original = path.read_bytes()
        for offset in [0, 4, 7, 8, 39, 40, 44]:
            with self.subTest(offset=offset):
                changed = bytearray(original); changed[offset] ^= 1; path.write_bytes(changed)
                self.expect_failure("worker-read", errno.EPROTO)
                self.expect_failure("worker-store", errno.EPROTO)
                self.assertEqual(changed, path.read_bytes())
        path.write_bytes(original)

    def test_resource_completion_requires_retirement_and_preserves_worker_facts(self):
        self.call("begin"); self.call("ready"); self.call("release"); self.call("worker-store")
        self.expect_failure("stop-store", errno.EPROTO)
        self.call("retire")
        self.assertEqual(0, self.call("stop-store")["result"])
        self.assertEqual(1, self.call("stop-read")["result"])
        self.assertEqual(0, self.call("stop-store")["result"])
        self.assertEqual(123, (self.state / "managed.stopped").stat().st_size)
        self.assertEqual(1, self.call("worker-read")["result"])

    def test_missing_worker_report_never_becomes_injection_cleanup_proof(self):
        self.call("begin"); self.call("ready"); self.call("release"); self.call("retire")
        self.assertEqual(0, self.call("stop-store")["result"])
        self.assertEqual(1, self.call("stop-read")["result"])
        self.assertEqual(0, self.call("worker-read")["result"])
        self.assertFalse((self.state / "managed.worker").exists())

    def test_retirement_without_dispatch_has_distinct_not_created_resource_states(self):
        self.call("retire")
        self.assertEqual(0, self.call("stop-store")["result"])
        data = (self.state / "managed.stopped").read_bytes()
        self.assertEqual(bytes([8, 2, 2, 0]), data[107:111])
        self.assertEqual(bytes([255]) * 4, data[111:115])
        self.assertEqual(1, self.call("stop-read")["result"])

    def test_corrupt_completion_identity_phase_resource_or_reserved_fields_are_retained(self):
        self.call("begin"); self.call("ready"); self.call("release"); self.call("retire"); self.call("stop-store")
        path = self.state / "managed.stopped"; original = path.read_bytes()
        changes = [(0, 0), (5, ord("x")), (42, ord("x")), (107, 8), (108, 2), (109, 2),
            (110, 1), (112, 1), (115, 6), (120, 255)]
        for offset, value in changes:
            with self.subTest(offset=offset):
                changed = bytearray(original); changed[offset] = value; path.write_bytes(changed)
                self.expect_failure("stop-read", errno.EPROTO); self.expect_failure("stop-store", errno.EPROTO)
                self.assertEqual(changed, path.read_bytes())
        path.write_bytes(original)

    def test_worker_or_stopped_records_without_intent_prevent_fresh_start(self):
        for name, data in [("managed.worker", b"unknown"), ("managed.stopped", KEY_BYTES)]:
            with self.subTest(name=name):
                path = self.put(name, data)
                self.expect_failure("begin", errno.EPROTO)
                self.assertEqual(data, path.read_bytes()); path.unlink()

    def test_partial_completion_write_is_retained_and_cannot_be_overwritten(self):
        self.call("retire")
        self.expect_failure("stop-store", errno.EIO, fault="partial")
        path = self.state / "managed.stopped"; data = path.read_bytes()
        self.assertEqual(KEY_BYTES[:5], data)
        self.expect_failure("stop-read", errno.EPROTO); self.expect_failure("stop-store", errno.EPROTO)
        self.assertEqual(data, path.read_bytes())
