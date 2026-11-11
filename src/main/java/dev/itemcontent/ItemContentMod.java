package dev.itemcontent;

import dev.itemcontent.config.ItemContentConfig;
import dev.itemcontent.config.ItemSpec;
import dev.itemcontent.item.ConfiguredItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ItemContentMod implements ModInitializer {
    public static final String MOD_ID = "itemcontent";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Map<Identifier, ConfiguredItem> ITEMS = new LinkedHashMap<>();

    @Override public void onInitialize() {
        try {
            for (ItemSpec spec : ItemContentConfig.load()) register(spec);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Invalid item-content config: " + ItemContentConfig.path(), failure);
        }
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS)
                .register(entries -> ITEMS.values().forEach(entries::add));
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                LOGGER.info("Registered {} configured items", ITEMS.size()));
    }

    private static void register(ItemSpec spec) {
        if (Registries.ITEM.containsId(spec.id()))
            throw new IllegalStateException("Item ID already registered: " + spec.id());
        ITEMS.put(spec.id(), Registry.register(Registries.ITEM, spec.id(), new ConfiguredItem(spec)));
        LOGGER.info("Registered item {} ({})", spec.id(), spec.displayName());
    }

    public static Optional<Item> item(Identifier id) { return Optional.ofNullable(ITEMS.get(id)); }
    public static Map<Identifier, ConfiguredItem> items() { return Collections.unmodifiableMap(ITEMS); }
}
