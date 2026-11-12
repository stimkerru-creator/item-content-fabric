package dev.itemcontent;

import dev.itemcontent.api.*;
import dev.itemcontent.config.*;
import dev.itemcontent.item.BuiltinFactory;
import dev.itemcontent.item.ConfiguredItem;
import dev.itemcontent.mixin.ItemSettingsAccess;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.*;
import java.io.IOException;
import java.util.*;

public final class ItemContentMod implements ModInitializer {
    public static final String MOD_ID = "itemcontent";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Map<Identifier, Item> ITEMS = new LinkedHashMap<>();
    private static final Map<Identifier, ConfiguredItem> LEGACY_ITEMS = new LinkedHashMap<>();
    private static final Map<Identifier, ItemSpec> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<Item, Text> NAMES = new IdentityHashMap<>();
    private static final Map<Item, ItemSpec> EXISTING = new IdentityHashMap<>();
    private static List<ItemSpec> pendingOverrides = List.of();
    private static boolean applied;

    @Override public void onInitialize() {
        try {
            var types = ContentTypeRegistry.getInstance();
            FabricLoader.getInstance().getEntrypoints("itemcontent:extensions", ItemContentExtension.class).forEach(extension -> extension.register(types));
            types.freeze();
            var config = ItemContentConfig.loadDocument();
            pendingOverrides = config.items().stream().filter(ItemSpec::existing).toList();
            for (ItemSpec spec : config.items()) if (!spec.existing()) register(spec);
            for (var group : config.groups()) {
                Identifier id = new Identifier(group.id());
                if (Registries.ITEM_GROUP.containsId(id)) throw new IllegalStateException("Creative group already exists: " + id);
                Registry.register(Registries.ITEM_GROUP, id, FabricItemGroup.builder().displayName(Text.literal(group.name()))
                        .icon(() -> new ItemStack(Registries.ITEM.get(new Identifier(group.icon())))).build());
            }
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Invalid item-content config: " + ItemContentConfig.path(), failure);
        }
        ItemGroupEvents.MODIFY_ENTRIES_ALL.register((group, entries) -> {
            if (group.getType() != ItemGroup.Type.CATEGORY) return;
            Identifier groupId = Registries.ITEM_GROUP.getId(group);
            entries.getDisplayStacks().removeIf(stack -> EXISTING.containsKey(stack.getItem()) && !EXISTING.get(stack.getItem()).group().equals("itemcontent:keep"));
            entries.getSearchTabStacks().removeIf(stack -> EXISTING.containsKey(stack.getItem()) && !EXISTING.get(stack.getItem()).group().equals("itemcontent:keep"));
            for (var entry : DEFINITIONS.entrySet()) if (entry.getValue().group().equals(groupId.toString())) {
                Item item = Registries.ITEM.get(entry.getKey()); entries.add(item);
            }
        });
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            applyExistingOverrides(); validateReferences(); LOGGER.info("Registered {} new items; {} existing item overrides", ITEMS.size(), EXISTING.size());
        });
    }
    /** Invoked after all mods registered their content, on client startup and before server start. */
    public static synchronized void applyExistingOverrides() {
        if (applied) return;
        for (var spec : pendingOverrides) {
            if (!Registries.ITEM.containsId(spec.id())) throw new IllegalStateException("Existing item not found: " + spec.id());
            Item item = Registries.ITEM.get(spec.id());
            var access = (ItemSettingsAccess) item;
            ContentTypeRegistry.getInstance().factory(spec.type()).applyExisting(item, spec.properties());
            if (item.getMaxDamage() > 0 && spec.maxCount() != 1) throw new IllegalArgumentException("Изнашиваемый предмет должен иметь стак 1: " + spec.id());
            access.itemcontent$maxCount(spec.maxCount()); access.itemcontent$fireproof(spec.fireproof());
            EXISTING.put(item, spec); DEFINITIONS.put(spec.id(), spec); NAMES.put(item, Text.literal(spec.displayName()));
        }
        applied = true;
    }
    public static void validateReferences() {
        for (var spec : DEFINITIONS.values()) {
            if (!spec.group().equals("itemcontent:keep") && !Registries.ITEM_GROUP.containsId(new Identifier(spec.group())))
                throw new IllegalStateException("Creative group not found: " + spec.group());
            if (spec.properties().has("repair_item") && !Registries.ITEM.containsId(new Identifier(spec.text("repair_item"))))
                throw new IllegalStateException("Repair item not found: " + spec.text("repair_item"));
        }
    }
    private static void register(ItemSpec spec) {
        if (Registries.ITEM.containsId(spec.id())) throw new IllegalStateException("Item ID already registered: " + spec.id());
        var created = ContentTypeRegistry.getInstance().factory(spec.type()).create(spec, BuiltinFactory.settings(spec));
        boolean expectedBlock = ContentTypeRegistry.getInstance().definition(spec.type()).block();
        if ((created.block() != null) != expectedBlock) throw new IllegalStateException("Factory returned incompatible block/item: " + spec.type());
        if (created.block() != null) {
            if (Registries.BLOCK.containsId(spec.id())) throw new IllegalStateException("Block ID already registered: " + spec.id());
            Registry.register(Registries.BLOCK, spec.id(), created.block());
        }
        Item item = Registry.register(Registries.ITEM, spec.id(), created.item());
        if (item instanceof ConfiguredItem configured) LEGACY_ITEMS.put(spec.id(), configured);
        ITEMS.put(spec.id(), item); DEFINITIONS.put(spec.id(), spec); NAMES.put(item, Text.literal(spec.displayName()));
        LOGGER.info("Registered {} {}", expectedBlock ? "block" : "item", spec.id());
    }
    public static Text configuredName(Item item) { return NAMES.get(item); }
    public static Optional<Item> item(Identifier id) { return Optional.ofNullable(ITEMS.get(id)); }
    /** Original API for plain configured items. Use registeredItems() for all native/provider types. */
    @Deprecated public static Map<Identifier, ConfiguredItem> items() { return Collections.unmodifiableMap(LEGACY_ITEMS); }
    public static Map<Identifier, Item> registeredItems() { return Collections.unmodifiableMap(ITEMS); }
    public static Map<Identifier, ItemSpec> definitions() { return Collections.unmodifiableMap(DEFINITIONS); }
}
