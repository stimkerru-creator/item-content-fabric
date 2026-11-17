# Changelog

## 1.4.0

- Build standalone Fabric mod JARs with only selected items and their resources.
- Include the required registration code; players no longer need Item Content or its editor/configs.
- Set the generated mod's name, stable namespace and version in a selection table.
- Embed models, textures and mining tags directly in the mod; no manual resource-pack setup.
- Isolate the generated runtime from Item Content and share it across authored mods through Fabric nested JARs.
- Preserve mod metadata and item selection in editor documents.
- Keep the original working-list installation mode in the File menu.
- Add explicit dependencies and reject unsupported third-party provider code rather than silently losing behavior.
- Add 4000-to-200 selection/export and two independent mod client/server acceptance checks.

## 1.3.1

- Isolate generated bow/crossbow models and block face textures; fail on duplicate export paths.
- Bound generated/imported path components so all supported 128-character IDs export on Windows.
- Replace obsolete block mining rules with the current owned datapack, preserving a backup.
- Require a world selection when previously installed mining rules need updating or clearing.
- Validate Java model geometry, transformations and override predicates before import/export.
- Reject Windows junctions and symbolic links in installation and export destinations.
- Publish the standalone editor JAR with the same build's ZIPs and checksums.
- Add regression tests for the independent acceptance audit findings.

## 1.3.0

- Import Minecraft Java 1.20.1 models from JSON, ZIP or a resource-pack folder.
- Collect parent models, textures, texture animation metadata and item overrides automatically.
- Accept standalone Java Block/Item exports with PNG files next to the model.
- Embed imported resources in editor document version 3; retain version 1/2 reads.
- Export per-object resource paths without overwriting the source pack's namespaces.
- Validate missing resources, parent cycles, ZIP paths and size limits before export.
- Connect a game catalog by choosing the instance folder rather than a service JSON file.
- Publish a ready-to-run Windows x64 ZIP with an unmodified Temurin Java runtime.
- Prefer bundled Java, remove automatic source builds from the end-user launcher.
- Add downloadable Release assets and an automated Windows release workflow.

## 1.2.0

- Launch immutable editor copies so rebuilding cannot break dialogs in a running instance.
- Use explicit save, discard and cancel labels when closing the editor.

- Clarify editor sections and use a custom PNG by default for new items.
- Select borrowed appearance from the game catalog instead of typing arbitrary IDs.
- Add explicit game-folder installation with backup/rollback; retain portable export.

- Add native food, weapons, tools, armor and placeable decorative blocks.
- Generate item, bow/crossbow, armor and block resources and mining datapacks.
- Add a real-game catalog with creative tabs, icons and declared editable properties.
- Support explicit existing-item overrides without replacing registry objects.
- Add optional typed providers via itemcontent:extensions; no mandatory third-party mod integration.
- Extend the desktop editor with class/subtype selection, behavior forms and creative-tab movement.
- Keep version 1 documents/configs readable; export version 2.
- Add isolated Fabric server/client integration fixtures, schema/export/UI tests and examples.

## 1.1.1

- Remove custom numeric characteristics from item definitions, the editor and exports.
- Stop reading or generating Item Parameters catalogs; both mods remain independently usable.
- Read legacy documents without interpreting stats; back up the complete original before saving.
- Fix clipped text by letting FlatLaf calculate input heights from font metrics and insets.
- Document the item-only scope and migration of old files without deleting game data.

## 1.1.0

- Replace the legacy desktop layout with an item-first FlatLaf editor.
- Make item creation the primary action; move catalog file operations to File.
- Add name/ID search, independent duplication, save shortcuts and a larger PNG preview.
- Fit the initial window to the available desktop; keep primary actions visible at compact sizes.
- Preserve existing item/project formats and Minecraft runtime behavior.
- Bundle FlatLaf and its Apache 2.0 license only in the editor.


## 1.0.3

- Reject fractional stack limits, invalid property types, duplicate parameter IDs and misspelled field keys before registration.
- Keep parameter IDs unique when adding after deletion; preserve active cell edits when switching items.
- Preserve PNG proportions in the preview.
- Ship a ready-to-run release ZIP, artifact checks and a requirements audit.
- Include the project license and third-party notices in the Fabric artifact.

## 1.0.2

- Add a separate desktop item constructor in this repository, with its own launcher.
- Edit names, IDs, stack limits, fire resistance and optional initial fields without JSON editing.
- Preview and export PNG textures and Minecraft resource-pack models.
- Save and reopen item projects; keep formulas and recipes in the original Item Parameters project.
- Prevent duplicate parameter entries when used with exports from Item Parameters.
- Add desktop workflow tests and verify exports against the runtime loader.

## 1.0.1

- Add `custom_model` mode for resource-pack JSON models and PNG textures.
- Keep the existing `appearance` fallback mode fully compatible.
- Validate and test custom-model configuration loading.

## 1.0.0

- Register configurable real items before the Minecraft registry freeze.
- Reserve the `itemcontent` namespace and reject collisions.
- Generate the optional `item-parameters` catalog with two-decimal field validation.
- Reuse an existing item model on the client.
- Add unit tests and a Java 17 GitHub Actions build.
