package dev.itemcontent.api;

import com.google.gson.JsonObject;
import dev.itemcontent.config.ItemSpec;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import java.util.Optional;

/** Factories own their mod-specific initialization. No class names are executed from configuration. */
public interface ContentFactory {
    CreatedContent create(ItemSpec definition, Item.Settings settings);
    default Optional<JsonObject> inspectExisting(Item item) { return Optional.empty(); }
    default void applyExisting(Item item, JsonObject properties) {
        if (properties.size() != 0) throw new IllegalArgumentException("This extension cannot modify existing items");
    }
    record CreatedContent(Item item, Block block) {
        public CreatedContent { java.util.Objects.requireNonNull(item); }
        public CreatedContent(Item item) { this(item, null); }
    }
}
