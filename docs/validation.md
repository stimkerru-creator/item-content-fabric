# Validation record

The 1.0.0 release was checked with JDK 21 using Gradle 8.10.2 and Minecraft
1.20.1/Fabric Loader 0.15.11.

| Check | Result |
|---|---|
| `clean build --offline` | passed |
| `ItemContentConfigTest` | 3 tests, 0 failures, 0 errors |
| remapped production JAR | passed |
| headless `runServer` | passed |
| registry log | `Registered item itemcontent:steel_plate` |
| generated parameter catalog | created with `itemcontent:mass = 1.25` |
| server shutdown | clean after runtime check |

The runtime check used only the repository's isolated `run/` directory. That
directory is ignored by Git and is not part of the release artifact.
