# Validation record

## 1.1.0 — 2026-11-12

- Standalone editor: `gradlew.bat -p editor build --offline --no-daemon` passed.
- Fabric runtime: `gradlew.bat build --offline --no-daemon` passed.
- **33 tests passed**: 12 runtime/export checks, 12 project-model checks, 9 editor workflow checks.
- New workflow coverage: filtered selection preserves edits; duplicates have independent fields and a new ID;
  Ctrl+S saves the opened list without dropping other items; reopened PNG appears at the right proportions;
  the initial window fits a 1366×728 usable desktop; main actions remain visible at compact sizes.
- The desktop was rendered and inspected at 1280×680 and 980×620 window sizes.
- The final editor was launched on the user's desktop. Its actual Windows window was captured
  at 1280×680 (position 43,24), wholly inside the 1366×728 usable area.
  The normal 1280×680 window shows all primary properties without vertical scrolling;
  the smaller window retains scrolling for its inspector and keeps primary actions visible.
- FlatLaf 3.7.2 and its full Apache 2.0 license are included only in the editor.
- Existing `.icproject` version 1 and game JSON version 1 are unchanged.
- Minecraft item behavior and registration are unchanged; this release updates the editor and version metadata.
  This does not add a new manual in-game PNG acceptance test or a third-party-modpack test.

The legacy checks below record earlier releases separately.

## 1.0.3 — 2026-11-10

Local checks used JDK 21 with `--release 17` for both compilation targets.

| Check | Result |
|---|---|
| `gradlew.bat build --offline --no-daemon` | passed |
| Runtime parser + desktop/runtime compatibility | 12 tests, 0 failures/errors |
| `gradlew.bat -p editor build --offline --no-daemon` | passed |
| Standalone project and Swing actions | 15 tests, 0 failures/errors |
| Total | **27 tests passed** |
| `python tools/package-release.py` | Java 17 bytecode, version, licenses and JAR separation passed |
| Windows launcher + current editor screenshot | launched and visually inspected at 1180×780 |
| Extracted release ZIP, without source tree or Gradle | launcher selected `lib` JAR; editor launched successfully |
| `git diff --check` | passed |

Regression coverage added for fractional/overflowing integers, wrong JSON types,
malformed structures, duplicate fields, misspelled keys, exact signed-int32 boundaries,
adding a field after deleting a row, and switching items while a table cell is being edited.
The packaging check verifies that the Fabric JAR contains no desktop classes and that
its desktop counterpart contains Gson and its full license, with no Minecraft or first-project dependency.

Server startup and coexistence with Item Parameters were established for 1.0.2 below.
A new 1.0.3 server startup and a manual in-game PNG acceptance test have **not** been performed.
No 4096-item load benchmark or broad third-party-modpack test is claimed.
GitHub Actions is configured for Java 17; this local record does not claim a remote CI result.

See the [requirements and usability audit](requirements-audit.md) for supported features and remaining limits.

## 1.0.2: separate item constructor

Verified with JDK 21, targeting Java 17 bytecode:

- Runtime mod: `clean build --offline --no-daemon`, 8 tests passed.
- Separate desktop app: `-p editor clean build --no-daemon`, 13 tests passed.
- The desktop project has no dependency on Minecraft, Item Parameters or Machine Editor.
- The real Item Content configuration parser accepts the desktop export and preserves
  names, IDs, counts, fire resistance and exact initial hundredths.
- Matching item definitions exported by Item Parameters are not duplicated; conflicting
  nonempty definitions fail before overwriting either catalog.
- Items without optional stats defer to the first project's parameter schema.
- Exported PNG bytes and model texture IDs are verified; invalid images and occupied
  directories are rejected without modifying existing user files.
- The Windows launcher selects the installed JDK 21 despite Java 8 being first on PATH.
- The desktop window was launched through that launcher and visually inspected from
  its rendered screenshot at 1180×780.
- A headless Fabric server with Item Parameters reached `Done`: one configured item
  registered, and the first mod loaded one item definition. The run reported an
  authentication-service timeout and temporary-JAR cleanup warnings, but completed startup.

These checks do not establish compatibility with every third-party mod or constitute a
manual in-game client acceptance test of the newly exported PNG. The old runtime check
below records the earlier release separately.

The 1.0.1 release was checked with JDK 21 using Gradle 8.10.2 and Minecraft
1.20.1/Fabric Loader 0.15.11.

| Check | Result |
|---|---|
| `clean build --offline` | passed |
| `ItemContentConfigTest` | 3 tests, 0 failures, 0 errors |
| remapped production JAR | passed |
| headless `runServer` | passed |
| registry log | `Registered item itemcontent:steel_plate` |
| generated parameter catalog | created with `itemcontent:mass = 1.25` |
| custom model configuration | parsed and accepted by unit tests |
| server shutdown | clean after runtime check |
| alongside `item-parameters-fabric-1.6.2-SNAPSHOT.jar` | passed; main mod loaded `1 item definitions and 0 formulas` |

The runtime check used only the repository's isolated `run/` directory. That
directory is ignored by Git and is not part of the release artifact.
