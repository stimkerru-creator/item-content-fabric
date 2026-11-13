"""Download the pinned, unmodified Temurin JRE and its corresponding source archive."""
from pathlib import Path
from urllib.request import Request, urlopen
from zipfile import ZipFile
import hashlib
import shutil

ROOT = Path(__file__).resolve().parents[1]
RELEASE = "jdk-17.0.21%2B9"
BASE = f"https://github.com/adoptium/temurin17-binaries/releases/download/{RELEASE}/"
PACKAGES = {
    "OpenJDK17U-jre_x64_windows_hotspot_17.0.21_9.zip": "fe4656bd78fb09812dc38c49da777f79cc33e7ee435a254be82f9e7f40d9d117",
    "OpenJDK17U-jdk-sources_17.0.21_9.tar.gz": "89a455fa83b19e3a166bca47a465d51712441be607543dd465083ef2437a426d",
}
cache = ROOT / "build/java-runtime-download"
cache.mkdir(parents=True, exist_ok=True)
for name, expected in PACKAGES.items():
    target = cache / name
    if not target.exists():
        pending = target.with_suffix(target.suffix + ".pending")
        with urlopen(Request(BASE + name, headers={"User-Agent": "ItemContent-release-builder"}), timeout=120) as response, pending.open("wb") as output:
            shutil.copyfileobj(response, output)
        with pending.open("rb") as stream:
            assert hashlib.file_digest(stream, "sha256").hexdigest() == expected, f"Checksum mismatch: {name}"
        pending.replace(target)
    with target.open("rb") as stream:
        assert hashlib.file_digest(stream, "sha256").hexdigest() == expected, f"Checksum mismatch: {name}"
    print(f"Verified: {name}", flush=True)

runtime = ROOT / "build/windows-runtime"
runtime.mkdir(parents=True, exist_ok=True)
with ZipFile(cache / next(iter(PACKAGES))) as archive:
    for entry in archive.infolist():
        destination = (runtime / entry.filename).resolve()
        if not destination.is_relative_to(runtime.resolve()):
            raise ValueError("Unsafe archive path")
    archive.extractall(runtime)
runtime = next(runtime.glob("*/bin/java.exe")).parent.parent
assert (runtime / "legal/java.base/LICENSE").is_file()
print(f"Windows runtime: {runtime}")
