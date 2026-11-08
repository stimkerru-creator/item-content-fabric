# Changelog

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
