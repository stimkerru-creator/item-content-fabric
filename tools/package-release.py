"""Verify and package only the current release artifacts; never include game data."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
VERSION = next(line.split("=", 1)[1].strip() for line in
               (ROOT / "gradle.properties").read_text().splitlines() if line.startswith("version="))
mod = ROOT / f"build/libs/item-content-fabric-{VERSION}.jar"
editor = ROOT / f"editor/build/libs/item-content-editor-{VERSION}.jar"
with ZipFile(mod) as jar:
    metadata = json.loads(jar.read("fabric.mod.json"))
    assert metadata["version"] == VERSION, "Wrong mod version"
    assert "LICENSE-item-content.txt" in jar.namelist(), "Missing mod license"
    assert not any(name.startswith("dev/itemcontent/editor/") for name in jar.namelist()), "Editor leaked into mod"
    assert int.from_bytes(jar.read("dev/itemcontent/ItemContentMod.class")[6:8], "big") == 61
with ZipFile(editor) as jar:
    assert "dev/itemcontent/editor/ItemContentEditor.class" in jar.namelist()
    assert "com/google/gson/Gson.class" in jar.namelist(), "Editor dependency is missing"
    assert "META-INF/licenses/Apache-2.0.txt" in jar.namelist(), "Missing Gson license"
    assert int.from_bytes(jar.read("dev/itemcontent/editor/ItemContentEditor.class")[6:8], "big") == 61
    assert not any(name.startswith(("net/minecraft/", "net/fabricmc/", "dev/itemparams/")) for name in jar.namelist())

out = ROOT / "build/distributions"
out.mkdir(parents=True, exist_ok=True)
archive = out / f"item-content-{VERSION}.zip"
files = {mod: f"mods/{mod.name}", editor: f"lib/{editor.name}"}
for name in ("README.md", "LICENSE", "THIRD-PARTY-NOTICES.txt", "CHANGELOG.md", "run-editor.bat", "tools/run-editor.ps1"):
    files[ROOT / name] = name
for folder in ("docs", "examples"):
    for path in sorted((ROOT / folder).rglob("*")):
        if path.is_file():
            files[path] = path.relative_to(ROOT).as_posix()
with ZipFile(archive, "w", ZIP_DEFLATED) as bundle:
    for path, destination in files.items():
        bundle.write(path, destination)
    bundle.writestr("gradle.properties", f"version={VERSION}\n")
    bundle.writestr("START-HERE.txt", """Item Content / Minecraft 1.20.1 Fabric

Windows: extract the whole ZIP, then open run-editor.bat. Java 17+ is required.
Other systems: java -jar lib/item-content-editor-""" + VERSION + """.jar
No JDK or Gradle is needed to run this prebuilt editor.
Open examples/steel-plate.icproject for a working example.
Follow docs/editor.md for images, export and installation.
mods/ contains the Fabric mod for client and server; it is not a desktop application.
Recipes and formulas belong to the separate Item Parameters project.
""")
checksums = out / f"item-content-{VERSION}-SHA256SUMS.txt"
checksums.write_text("".join(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n"
                             for path in (mod, editor, archive)), encoding="utf-8")
print(f"Verified Java 17 artifacts and licenses. Package: {archive}")
print(f"Checksums: {checksums}")
