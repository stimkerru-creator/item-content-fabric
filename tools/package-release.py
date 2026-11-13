"""Verify and package only the current release artifacts; never include game data."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json
import argparse
import shutil

parser = argparse.ArgumentParser()
parser.add_argument('--windows-runtime', type=Path, help='Unmodified Windows x64 JRE root; include all runtime licenses')
args = parser.parse_args()

ROOT = Path(__file__).resolve().parents[1]
VERSION = next(line.split("=", 1)[1].strip() for line in
               (ROOT / "gradle.properties").read_text().splitlines() if line.startswith("version="))
mod = ROOT / f"build/libs/item-content-fabric-{VERSION}.jar"
editor = ROOT / f"editor/build/libs/item-content-editor-{VERSION}.jar"
with ZipFile(mod) as jar:
    metadata = json.loads(jar.read("fabric.mod.json"))
    assert metadata["version"] == VERSION, "Wrong mod version"
    assert set(metadata["depends"]) == {"fabricloader", "minecraft", "java", "fabric-api"}
    assert "dev/itemcontent/config/ParameterCatalogWriter.class" not in jar.namelist()
    assert not any(name.startswith("dev/itemparams/") for name in jar.namelist())
    assert not any(name.startswith("dev/itemcontent/integration/") for name in jar.namelist()), "Test fixture leaked into mod"
    assert "dev/itemcontent/api/ItemContentExtension.class" in jar.namelist()
    assert "dev/itemcontent/definition/TypeDefinition.class" in jar.namelist()
    assert "LICENSE-item-content.txt" in jar.namelist(), "Missing mod license"
    assert not any(name.startswith(("dev/itemcontent/editor/", "com/formdev/")) for name in jar.namelist()), "Editor or FlatLaf leaked into mod"
    assert int.from_bytes(jar.read("dev/itemcontent/ItemContentMod.class")[6:8], "big") == 61
with ZipFile(editor) as jar:
    assert "dev/itemcontent/editor/ItemContentEditor.class" in jar.namelist()
    assert "dev/itemcontent/editor/MinecraftModel.class" in jar.namelist()
    assert "dev/itemcontent/editor/vanilla-resources-1.20.1.txt" in jar.namelist()
    assert "com/google/gson/Gson.class" in jar.namelist(), "Editor dependency is missing"
    assert "com/formdev/flatlaf/FlatLightLaf.class" in jar.namelist(), "Desktop theme dependency is missing"
    assert "META-INF/licenses/FlatLaf-LICENSE.txt" in jar.namelist(), "Missing FlatLaf license"
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
Item Content works independently. Custom numeric stats and recipes are not part of this tool.
""")
packages = [mod, editor, archive]
if args.windows_runtime:
    runtime = args.windows_runtime.resolve()
    assert (runtime / 'bin/java.exe').is_file(), 'Expected Windows runtime'
    release_info = (runtime / 'release').read_text(encoding='utf-8')
    assert 'JAVA_VERSION="17.0.21"' in release_info and 'Eclipse Adoptium' in release_info, 'Runtime must match the supplied corresponding source version'
    assert (runtime / 'legal/java.base/LICENSE').is_file(), 'Missing runtime licenses'
    windows = out / f'item-content-{VERSION}-windows-x64.zip'
    with ZipFile(windows, 'w', ZIP_DEFLATED) as bundle:
        for path, destination in files.items():
            bundle.write(path, destination)
        bundle.writestr('gradle.properties', f'version={VERSION}\n')
        bundle.writestr('START-HERE.txt', f"""Item Content {VERSION} - Windows x64

1. Extract the ENTIRE ZIP into a writable folder.
2. Open run-editor.bat. Java is included; no Java installation, JDK or Gradle is needed.
3. Use the editor's Install in Minecraft action for your game instance.
4. For examples and illustrated steps see docs/editor.md and examples/.

Do not select GitHub's Source code ZIP to install the editor.
Fabric Loader and Fabric API are still required in Minecraft 1.20.1.
The included runtime is for the desktop editor, not a Minecraft installer.
""")
        bundle.writestr('JAVA-SOURCES.txt', """Bundled runtime: Eclipse Temurin OpenJDK 17.0.21+9, unmodified Windows x64 JRE.
Runtime licenses and notices are in runtime/legal/.
The corresponding source archive OpenJDK17U-jdk-sources_17.0.21_9.tar.gz is
available alongside this ZIP on this project's GitHub Release.
Upstream: https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.21%2B9
""")
        for path in sorted(runtime.rglob('*')):
            if path.is_file(): bundle.write(path, 'runtime/' + path.relative_to(runtime).as_posix())
    source_name = 'OpenJDK17U-jdk-sources_17.0.21_9.tar.gz'
    sources = ROOT / 'build/java-runtime-download' / source_name
    assert sources.is_file(), 'Provide corresponding Java source archive'
    source_target = out / source_name
    shutil.copyfile(sources, source_target)
    packages.extend([windows, source_target])
    print(f'Windows ZIP (Java included): {windows}')
checksums = out / f"item-content-{VERSION}-SHA256SUMS.txt"
checksums.write_text("".join(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n"
                             for path in packages), encoding="utf-8")
print(f"Verified Java 17 artifacts and licenses. Package: {archive}")
print(f"Checksums: {checksums}")
