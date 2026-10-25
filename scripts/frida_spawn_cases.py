"""Explicit owned native spawn tests; Android package launch remains final-device work."""
import json
import os
from pathlib import Path
import signal
import struct
import subprocess
import time
import uuid


class SpawnCases:
    def spawned(self, source):
        state = self.root / ('spawn-' + uuid.uuid4().hex)
        state.mkdir(mode=0o700)
        script = state / 'fixture.js'
        script.write_text(source)
        child = subprocess.Popen([str(self.worker), 'spawn-file', str(self.target), str(script), '6000'],
            cwd=state, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.addCleanup(self.stop, child)
        def cleanup_target():
            captured = state / 'spawn.2'
            if captured.exists() and captured.stat().st_size == 64:
                pid, ticks = struct.unpack_from('<IQ', captured.read_bytes(), 40)
                stat = Path(f'/proc/{pid}/stat')
                try:
                    value = stat.read_text()
                    if int(value[value.rfind(')') + 1:].split()[19]) == ticks:
                        os.kill(pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                except FileNotFoundError:
                    pass
        self.addCleanup(cleanup_target)
        return child, state

    def test_spawn_loads_hook_before_resume_and_preserves_resumed_target(self):
        child, state = self.spawned("Interceptor.attach(Module.getGlobalExportByName('aurora_marker'), {onEnter(){send('spawn-hook');}});")
        records = self.finish(child)
        completion = next(record for record in records if record.get("kind") == "completion")
        self.assertTrue(completion['success'], records)
        self.assertTrue((state / 'spawn.1').exists())
        self.assertTrue((state / 'spawn.3').exists())
        self.assertFalse((state / 'spawn.4').exists())
        pid, _ = struct.unpack_from('<IQ', (state / 'spawn.2').read_bytes(), 40)
        os.kill(pid, 0)
        self.assertTrue(any('spawn-hook' in record.get('message', '') for record in records))

    def test_spawn_script_error_before_resume_retires_only_the_owned_target(self):
        child, state = self.spawned("function (")
        records = self.finish(child)
        completion = next(record for record in records if record.get("kind") == "completion")
        # A syntax error fails script creation before the resume side effect.
        self.assertFalse(completion['success'], records)
        self.assertFalse((state / 'spawn.3').exists())
        self.assertTrue((state / 'spawn.4').exists())
