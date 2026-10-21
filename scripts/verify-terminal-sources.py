"""Verify the explicitly pinned embedded terminal sources, without network access."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "terminal"
manifest = json.loads((root / "UPSTREAM.json").read_text(encoding="utf-8"))
assert manifest["tag"] == "v0.118.3"
assert manifest["commit"] == "5b657c6adf4304e5198951ce815fe0205dcac29c"
listed = set()
for entry in manifest["files"]:
    relative = entry["path"]
    path = (root / relative).resolve()
    assert path.is_relative_to((root / "src/main/java").resolve())
    assert path.suffix == ".java"
    assert relative not in listed
    listed.add(relative)
    assert hashlib.sha256(path.read_bytes()).hexdigest() == entry["sha256"], relative
actual = {path.relative_to(root).as_posix() for path in (root / "src/main/java").rglob("*.java")}
assert actual == listed, "Embedded source manifest is incomplete"
print(f"Verified {len(listed)} pinned terminal Java sources")
