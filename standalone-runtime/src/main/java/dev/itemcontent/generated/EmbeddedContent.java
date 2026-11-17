package dev.itemcontent.generated;

import dev.itemcontent.generated.config.ItemContentConfig;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Reads only definitions embedded in selected, installed content mods. Never reads a game catalog. */
public final class EmbeddedContent {
    private static String parsingNamespace;
    private EmbeddedContent() {}
    public static boolean allowedNamespace(String namespace) { return namespace.equals(parsingNamespace); }
    public static ItemContentConfig.Document loadDocument() throws IOException {
        var items = new ArrayList<dev.itemcontent.generated.config.ItemSpec>();
        var groups = new ArrayList<ItemContentConfig.Group>();
        var ids = new HashSet<String>(); var groupIds = new HashSet<String>();
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            var marker = mod.getMetadata().getCustomValue("itemcontent:standalone");
            if (marker == null || !marker.getAsBoolean()) continue;
            String namespace = mod.getMetadata().getId();
            Path file = mod.findPath("itemcontent/content.json").orElseThrow(() -> new IOException("Missing embedded definitions: " + namespace));
            if (Files.size(file) > 4 * 1024 * 1024) throw new IOException("Embedded definitions exceed 4 MiB: " + namespace);
            final ItemContentConfig.Document document;
            try { parsingNamespace = namespace; document = ItemContentConfig.parseDocument(Files.readString(file, StandardCharsets.UTF_8)); }
            finally { parsingNamespace = null; }
            for (var item : document.items()) {
                if (!ids.add(item.id().toString())) throw new IOException("Two installed content mods configure the same item: " + item.id());
                items.add(item);
            }
            for (var group : document.groups()) {
                if (!groupIds.add(group.id())) throw new IOException("Duplicate creative group: " + group.id());
                groups.add(group);
            }
        }
        return new ItemContentConfig.Document(List.copyOf(items), List.copyOf(groups));
    }
}
