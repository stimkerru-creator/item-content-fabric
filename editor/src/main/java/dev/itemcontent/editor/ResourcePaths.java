package dev.itemcontent.editor;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Injective per-item paths with bounded filename components, including 128-character IDs. */
final class ResourcePaths {
    private ResourcePaths() {}
    static String prefix(String category, String itemId) {
        String hex = HexFormat.of().formatHex(itemId.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(category).append('/').append(hex.length()).append('/');
        // Length distinguishes IDs before variable-depth chunks or imported source names begin.
        for (int offset = 0; offset < hex.length(); offset += 64)
            result.append(hex, offset, Math.min(offset + 64, hex.length())).append('/');
        return result.toString();
    }
}
