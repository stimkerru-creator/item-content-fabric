# Changelog

## 1.2.0

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
