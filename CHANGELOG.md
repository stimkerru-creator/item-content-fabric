# Changelog

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
