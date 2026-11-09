# Validation record

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
