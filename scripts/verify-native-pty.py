"""Linux host PTY checks. These do not replace Android/SELinux/Root device tests."""
import json
import os
from pathlib import Path
import selectors
import signal
import struct
import subprocess
import tempfile
import threading
import time
import unittest


class NativePtyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory(prefix="aurora-pty-")
        cls.binary = str(Path(cls.directory.name) / "pty")
        source = Path(__file__).resolve().parents[1] / "app/src/main/cpp/pty.c"
        subprocess.run(["cc", "-std=c11", "-Wall", "-Wextra", "-Werror", str(source), "-o", cls.binary], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.directory.cleanup()

    def start(self, script, timeout=5000, drain=True):
        child = subprocess.Popen([self.binary, "24", "80", str(timeout), "--", "/bin/sh", "-c", script],
                                 stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.addCleanup(self.cleanup, child)
        output = bytearray()
        changed = threading.Condition()
        reader = None
        if drain:
            def consume():
                while True:
                    data = os.read(child.stdout.fileno(), 16384)
                    if not data:
                        break
                    with changed:
                        output.extend(data)
                        changed.notify_all()
            reader = threading.Thread(target=consume, daemon=True)
            reader.start()
        return child, output, changed, reader

    @staticmethod
    def cleanup(child):
        if child.poll() is None:
            child.terminate()
        try:
            child.wait(timeout=3)
        except subprocess.TimeoutExpired:
            child.kill()
            child.wait()
        for stream in (child.stdin, child.stdout, child.stderr):
            stream.close()

    def until(self, output, changed, value):
        deadline = time.monotonic() + 3
        with changed:
            while value not in output and time.monotonic() < deadline:
                changed.wait(max(0, deadline - time.monotonic()))
        self.assertIn(value, output)

    @staticmethod
    def frame(child, op, value):
        child.stdin.write(struct.pack("<BH", op, len(value)) + value)
        child.stdin.flush()

    def result(self, child, reader):
        child.wait(timeout=5)
        if reader:
            reader.join(timeout=2)
            self.assertFalse(reader.is_alive())
        return json.loads(child.stderr.read())

    def test_real_tty_merged_output_and_exit_status(self):
        child, output, _, reader = self.start("test -t 0 && test -t 1 && test -t 2 || exit 8; printf stdout; printf stderr >&2; exit 7")
        result = self.result(child, reader)
        self.assertEqual(result["exitCode"], 7)
        self.assertEqual(result["reason"], "exit")
        self.assertIn(b"stdoutstderr", output)

    def test_interactive_input_resize_and_unicode(self):
        child, output, changed, reader = self.start("printf ready; read value; printf '<%s>' \"$value\"; stty size")
        self.until(output, changed, b"ready")
        self.frame(child, 2, struct.pack("<HH", 41, 113))
        self.frame(child, 1, "极光🌌\n".encode())
        result = self.result(child, reader)
        self.assertEqual(result["exitCode"], 0)
        self.assertIn("<极光🌌>".encode(), output)
        self.assertIn(b"41 113", output)

    def test_ctrl_c_reaches_foreground_process(self):
        child, output, changed, reader = self.start("trap 'printf interrupted; exit 9' INT; printf ready; while :; do sleep 1; done")
        self.until(output, changed, b"ready")
        self.frame(child, 1, b"\x03")
        self.assertEqual(self.result(child, reader)["exitCode"], 9)
        self.assertIn(b"interrupted", output)

    def test_deadline_is_not_normal_exit(self):
        child, _, _, reader = self.start("sleep 60", timeout=200)
        result = self.result(child, reader)
        self.assertEqual(child.returncode, 124)
        self.assertEqual(result["reason"], "deadline")
        self.assertNotEqual(result["exitCode"], 0)

    def test_large_output_is_drained_without_a_view(self):
        child, output, _, reader = self.start("head -c 2097152 /dev/zero | tr '\\000' X; printf tail")
        result = self.result(child, reader)
        self.assertEqual(result["reason"], "exit")
        self.assertEqual(len(output), 2097156)
        self.assertTrue(output.endswith(b"tail"))

    def test_stop_works_when_stdout_is_backpressured(self):
        child, _, _, _ = self.start("while :; do head -c 65536 /dev/zero; done", drain=False)
        time.sleep(0.15)
        child.terminate()
        child.wait(timeout=3)
        self.assertEqual(json.loads(child.stderr.read())["reason"], "stopped")

    def test_malformed_frame_stops_instead_of_executing_input(self):
        child, output, changed, reader = self.start("printf ready; sleep 60")
        self.until(output, changed, b"ready")
        child.stdin.write(struct.pack("<BH", 1, 4097))
        child.stdin.flush()
        self.assertEqual(self.result(child, reader)["reason"], "stopped")

    def test_stopping_session_kills_shell_background_job(self):
        marker = Path(self.directory.name) / "background.pid"
        child, output, changed, reader = self.start(f"sleep 60 & echo $! > '{marker}'; printf ready; wait")
        self.until(output, changed, b"ready")
        pid = int(marker.read_text())
        child.terminate()
        self.result(child, reader)
        stat = Path(f"/proc/{pid}/stat")
        if stat.exists():
            self.assertEqual(stat.read_text().split(") ", 1)[1][0], "Z", "Background job is still runnable")


if __name__ == "__main__":
    unittest.main(verbosity=2)
