#!/usr/bin/env python3
"""Real Frida/Linux tests against an owned target; never substitutes for Android qualification."""
import importlib.util
import json
import os
from pathlib import Path
import selectors
import shutil
import signal
import subprocess
import sys
import tarfile
import tempfile
import time
import unittest
from frida_managed_cases import ManagedCases

ROOT = Path(__file__).resolve().parents[1]
NAME = "frida-core-devkit-17.24.3-linux-x86_64.tar.xz"
DIGEST = "9a11abf474899a33af6dd9af8a064448b23947a29a3af747eb3c7851591ffec2"
SIZE = 78853964


@unittest.skipUnless(sys.platform.startswith("linux") and os.geteuid() == 0, "Explicit Linux Root host fixture")
class FridaSessionTests(ManagedCases, unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="aurora-frida-session-")
        cls.root = Path(cls.temporary.name)
        spec = importlib.util.spec_from_file_location("frida_build", ROOT / "scripts/build-frida-probe.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        archive = cls.root / NAME
        helper = module.source_helpers()
        helper.fetch(archive, "https://github.com/frida/frida/releases/download/17.24.3/" + NAME, DIGEST, SIZE)
        helper.verify(archive, DIGEST, SIZE)
        with tarfile.open(archive) as source:
            for name in ["libfrida-core.a", "frida-core.h"]:
                entries = [item for item in source.getmembers() if item.name in {name, "./" + name}]
                if len(entries) != 1 or not entries[0].isfile() or entries[0].size > 600_000_000:
                    raise ValueError("Unexpected host devkit entry")
                with source.extractfile(entries[0]) as incoming, (cls.root / name).open("xb") as output:
                    shutil.copyfileobj(incoming, output, 65536)
        native = ROOT / "app/src/main/cpp/frida"
        cls.worker = cls.root / "worker"
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-Os", "-ffunction-sections", "-fdata-sections",
                        "-I" + str(cls.root), *[str(native / name) for name in ["worker.c", "input.c", "channel_input.c", "fd_channel.c", "fd_io.c", "managed_control.c", "managed_protocol.c", "managed_watch.c", "events.c", "session.c"]],
                        "-o", str(cls.worker), "-L" + str(cls.root), "-lfrida-core", "-ldl", "-lrt", "-lm", "-lresolv",
                        "-pthread", "-Wl,--export-dynamic", "-Wl,--gc-sections", "-Wl,-z,noexecstack"], check=True)
        target = cls.root / "target.c"
        target.write_text('#include <unistd.h>\n#include <stdio.h>\n'
                          '__attribute__((noinline,visibility("default"))) int aurora_marker(int value) { return value + 1; }\n'
                          'int main(void) { puts("ready"); fflush(stdout); while (1) { aurora_marker(7); usleep(100000); } }\n')
        cls.target = cls.root / "owned-target"
        subprocess.run(["cc", "-O0", "-rdynamic", str(target), "-o", str(cls.target)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def setUp(self):
        self.process = subprocess.Popen([str(self.target)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.addCleanup(self.stop, self.process)
        self.assertEqual("ready", self.line(self.process.stdout, 5))
        stat = Path(f"/proc/{self.process.pid}/stat").read_text()
        self.ticks = int(stat[stat.rfind(")") + 1:].split()[19])

    @staticmethod
    def stop(child):
        if child.poll() is None:
            child.kill()
        child.communicate(timeout=5)

    @staticmethod
    def line(stream, timeout):
        with selectors.DefaultSelector() as selector:
            selector.register(stream, selectors.EVENT_READ)
            if not selector.select(timeout):
                raise TimeoutError("Owned fixture readiness deadline")
            return stream.readline().decode().strip()

    def start(self, script, duration=1000, ticks=None, payload=None):
        source = self.root / f"script-{self.id().rsplit('.', 1)[-1]}.js"
        source.write_text(script, encoding="utf-8")
        argv = [str(self.worker), "attach", str(self.process.pid), str(ticks or self.ticks), str(source), str(duration)]
        if payload is not None:
            payload_file = source.with_suffix(".payload.js")
            payload_file.write_text(payload, encoding="utf-8")
            argv.append(str(payload_file))
        child = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE, bufsize=0)
        self.addCleanup(self.stop, child)
        return child

    def finish(self, child, prefix=()):
        try:
            output, error = child.communicate(timeout=20)
        except subprocess.TimeoutExpired:
            child.kill()
            output, error = child.communicate(timeout=5)
            self.fail(f"Worker timed out; stdout={output[-8192:]!r}; stderr={error[-8192:]!r}")
        records = [json.loads(line) for line in [*prefix, *output.decode().splitlines()] if line]
        self.assertTrue(records, error.decode(errors="replace"))
        if child.returncode == -signal.SIGSEGV and shutil.which("gdb"):
            trace = subprocess.run(["gdb", "--batch", "-ex", "set pagination off", "-ex", "run", "-ex", "thread apply all bt",
                                    "--args", *child.args], capture_output=True, timeout=30)
            print("Frida crash diagnostic:\n" + trace.stdout.decode(errors="replace")[-24000:] + trace.stderr.decode(errors="replace")[-4000:], flush=True)
        self.assertEqual("completion", records[-1]["kind"], (child.returncode, records, error.decode(errors="replace")))
        self.assertTrue(records[-1]["cleanupConfirmed"], records)
        self.assertIsNone(self.process.poll(), "Attach cleanup killed the pre-existing owned target")
        return records

    def loaded(self, child):
        lines = []
        end = time.monotonic() + 10
        while time.monotonic() < end:
            line = self.line(child.stdout, end - time.monotonic())
            self.assertTrue(line, "Worker exited before loaded")
            lines.append(line)
            if json.loads(line)["kind"] == "loaded":
                return lines
        raise TimeoutError("No script loaded event")

    def test_native_hook_then_bounded_duration_unloads_and_detaches(self):
        child = self.start("Interceptor.attach(Module.getGlobalExportByName('aurora_marker'), { onEnter() { send('hooked'); } });", 5000)
        records = self.finish(child)
        self.assertEqual(0, child.returncode, records)
        self.assertTrue(records[-1]["success"], records)
        self.assertTrue(any("hooked" in event.get("message", "") for event in records), records)

    def test_signal_cancels_without_killing_existing_target(self):
        child = self.start("send('ready');", 30000)
        prefix = self.loaded(child)
        child.send_signal(signal.SIGTERM)
        records = self.finish(child, prefix)
        self.assertNotEqual(0, child.returncode)
        self.assertEqual("cancelled", records[-1]["reason"])
        self.assertFalse(records[-1]["success"])

    def test_bad_script_never_reports_loaded_or_success(self):
        child = self.start("this is not valid javascript ???")
        records = self.finish(child)
        self.assertNotEqual(0, child.returncode)
        self.assertFalse(records[-1]["loaded"])
        self.assertFalse(records[-1]["success"])

    def test_changed_identity_never_loads_script(self):
        for attempt in range(5):
            with self.subTest(attempt=attempt):
                child = self.start("send('must-not-run');", ticks=self.ticks + 1)
                records = self.finish(child)
                self.assertFalse(records[-1]["loaded"])
                self.assertFalse(any(event["kind"] == "message" for event in records))
                self.assertNotEqual(0, child.returncode)

    def test_oversized_message_stops_with_explicit_dropped_count(self):
        child = self.start("send('x'.repeat(70000));", 30000)
        records = self.finish(child)
        self.assertGreater(records[-1]["dropped"], 0)
        self.assertFalse(records[-1]["success"])
        self.assertNotEqual(0, child.returncode)

    @unittest.skipUnless(os.environ.get("AURORA_FRIDA_JAVA_BUNDLE"), "Explicit development bridge bundle required")
    def test_java_bridge_bundle_loads_on_native_target_without_claiming_java(self):
        bundle = Path(os.environ["AURORA_FRIDA_JAVA_BUNDLE"]).read_text(encoding="utf-8")
        child = self.start(bundle, 2000)
        records = self.finish(child)
        messages = [json.loads(event["message"]) for event in records if event["kind"] == "message"]
        payloads = [message.get("payload") for message in messages]
        bridge = [payload for payload in payloads if isinstance(payload, dict) and payload.get("aurora") == "java_bridge_ready"]
        self.assertEqual(1, len(bridge), records)
        self.assertEqual("7.0.13", bridge[0]["version"])
        self.assertFalse(bridge[0]["available"], "Owned plain C target must not claim Android ART")
        self.assertEqual(0, child.returncode, records)

    @unittest.skipUnless(os.environ.get("AURORA_FRIDA_JAVA_BUNDLE"), "Explicit development bridge bundle required")
    def test_bridge_control_delivers_selected_script_once(self):
        bundle = Path(os.environ["AURORA_FRIDA_JAVA_BUNDLE"]).read_text(encoding="utf-8")
        child = self.start(bundle, 2000, payload="send('aurora-user-control-marker');")
        records = self.finish(child)
        messages = [json.loads(event["message"]) for event in records if event["kind"] == "message"]
        self.assertEqual(1, sum(message.get("payload") == "aurora-user-control-marker" for message in messages), records)
        labels = [message.get("payload", {}).get("aurora") for message in messages if isinstance(message.get("payload"), dict)]
        self.assertIn("user_script_received", labels)
        self.assertIn("user_script_evaluated", labels)
        self.assertTrue(records[-1]["userScriptPosted"], records)
        self.assertEqual(0, child.returncode, records)

    def start_channel(self, source, bridge=None, corrupt=None):
        from frida_channel_fixture import launch, LaunchOptions
        child = launch(self.worker, (self.process.pid, self.ticks), source, LaunchOptions(bridge=bridge, corrupt=corrupt)).child
        self.addCleanup(self.stop, child)
        return child

    def test_sealed_channel_loads_selected_script_without_disk_copy(self):
        child = self.start_channel(b"send('aurora-sealed-channel-marker');")
        records = self.finish(child)
        self.assertEqual(0, child.returncode, records)
        messages = [json.loads(event["message"]) for event in records if event["kind"] == "message"]
        self.assertEqual(1, sum(message.get("payload") == "aurora-sealed-channel-marker" for message in messages))

    @unittest.skipUnless(os.environ.get("AURORA_FRIDA_JAVA_BUNDLE"), "Explicit development bridge bundle required")
    def test_sealed_channel_bridge_delivers_selected_script_once(self):
        bundle = Path(os.environ["AURORA_FRIDA_JAVA_BUNDLE"]).read_bytes()
        child = self.start_channel(b"send('aurora-sealed-bridge-marker');", bundle)
        records = self.finish(child)
        self.assertEqual(0, child.returncode, records)
        messages = [json.loads(event["message"]) for event in records if event["kind"] == "message"]
        self.assertEqual(1, sum(message.get("payload") == "aurora-sealed-bridge-marker" for message in messages))
        self.assertTrue(records[-1]["userScriptPosted"], records)

    def test_sealed_channel_rechecks_exact_content_before_attaching_target(self):
        for corrupt in ["sha", "size"]:
            with self.subTest(corrupt=corrupt):
                child = self.start_channel(b"send('must-not-run');", corrupt=corrupt)
                output, _ = child.communicate(timeout=10)
                self.assertEqual(64, child.returncode)
                self.assertEqual(b"", output)
                self.assertIsNone(self.process.poll())

    def test_target_exit_ends_session_without_waiting_full_duration(self):
        child = self.start("send('ready');", 30000)
        prefix = self.loaded(child)
        self.process.terminate()
        self.process.communicate(timeout=5)
        output, _ = child.communicate(timeout=15)
        records = [json.loads(line) for line in prefix + output.decode().splitlines() if line]
        self.assertEqual("completion", records[-1]["kind"], records)
        self.assertEqual("target_detached", records[-1]["reason"])
        self.assertTrue(records[-1]["cleanupConfirmed"], records)
        self.assertFalse(records[-1]["success"])


if __name__ == "__main__":
    unittest.main()
