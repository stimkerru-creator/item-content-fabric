package dev.itemcontent.catalog;

import com.google.gson.*;
import dev.itemcontent.ItemContentMod;
import dev.itemcontent.api.ContentTypeRegistry;
import dev.itemcontent.definition.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resource.ResourceManager;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Registry/resource snapshot only; arbitrary Java fields are never reflected into editable settings. */
public final class ClientCatalog {
    private static final AtomicBoolean EXPORTING = new AtomicBoolean();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> { var thread = new Thread(task, "Item Content catalog export"); thread.setDaemon(true); return thread; });
    private ClientCatalog() {}
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(literal("itemcontent").then(literal("catalog").executes(context -> {
            var client = MinecraftClient.getInstance();
            try {
                var result = exportAsync(client);
                context.getSource().sendFeedback(Text.literal("Обновление каталога для редактора…"));
                result.whenComplete((file, failure) -> client.execute(() -> {
                    if (failure == null) context.getSource().sendFeedback(Text.literal("Каталог для редактора: " + file));
                    else context.getSource().sendError(Text.literal("Не удалось выгрузить каталог: " + failure.getMessage()));
                })); return 1;
            } catch (Exception failure) { context.getSource().sendError(Text.literal(failure.getMessage())); return 0; }
        }))));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            try { exportAsync(client).exceptionally(failure -> { org.slf4j.LoggerFactory.getLogger("itemcontent").warn("Unable to export editor catalog", failure); return null; }); }
            catch (Exception failure) { org.slf4j.LoggerFactory.getLogger("itemcontent").warn("Unable to snapshot editor catalog", failure); }
        }));
    }
    /** Registry inspection stays on the game thread; resource/disk I/O runs in one background job. */
    public static CompletableFuture<Path> exportAsync(MinecraftClient client) throws IOException {
        if (!EXPORTING.compareAndSet(false, true)) throw new IOException("Каталог уже обновляется. Дождитесь завершения.");
        try {
            long started = System.nanoTime();
            GameCatalog snapshot = snapshot(client); ResourceManager resources = client.getResourceManager();
            Path directory = FabricLoader.getInstance().getConfigDir().resolve("item-content");
            return CompletableFuture.supplyAsync(() -> {
                try {
                    List<GameCatalog.Entry> items = new ArrayList<>(); long[] bytes = {0};
                    for (var item : snapshot.items()) items.add(new GameCatalog.Entry(item.id(), item.name(), item.type(), item.javaClass(), item.maxCount(), item.durability(), item.fireproof(), item.groups(), item.properties(), icon(resources, new Identifier(item.id()), directory, bytes), item.variants()));
                    Path file = directory.resolve("catalog.json"); new GameCatalog(1, snapshot.types(), snapshot.groups(), items).save(file);
                    org.slf4j.LoggerFactory.getLogger("itemcontent").info("Exported editor catalog: {} items, {} ms", items.size(), (System.nanoTime()-started)/1000000);
                    return file.toAbsolutePath();
                } catch (IOException failure) { throw new CompletionException(failure); }
                finally { EXPORTING.set(false); }
            }, WRITER);
        } catch (IOException | RuntimeException failure) { EXPORTING.set(false); throw failure; }
    }
    private static GameCatalog snapshot(MinecraftClient client) throws IOException {
        if (client.world == null) throw new IOException("Сначала войдите в мир, чтобы получить актуальные вкладки и ресурсы модов.");
        ItemGroups.updateDisplayContext(client.world.getEnabledFeatures(), true, client.world.getRegistryManager());
        Map<Item, List<String>> memberships = new IdentityHashMap<>(); Set<Item> variants = Collections.newSetFromMap(new IdentityHashMap<>());
        List<GameCatalog.Group> groups = new ArrayList<>();
        for (var group : ItemGroups.getGroups()) {
            if (group.getType() != ItemGroup.Type.CATEGORY) continue;
            String id = Registries.ITEM_GROUP.getId(group).toString(); groups.add(new GameCatalog.Group(id, group.getDisplayName().getString()));
            for (var stack : group.getDisplayStacks()) {
                List<String> list = memberships.computeIfAbsent(stack.getItem(), item -> new ArrayList<>());
                if (!list.contains(id)) list.add(id);
                if (stack.hasNbt()) variants.add(stack.getItem());
            }
        }
        List<GameCatalog.Entry> items = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item); if (item == Items.AIR) continue;
            String type = classify(item); JsonObject properties = inspect(item, type);
            var configured = ItemContentMod.definitions().get(id);
            if (configured != null && !configured.existing()) { type = configured.type(); properties = configured.properties().deepCopy(); }
            // Extension-specific inspectors take precedence over the vanilla fallback.
            for (var entry : ContentTypeRegistry.getInstance().definitions().entrySet()) if (!entry.getKey().startsWith("itemcontent:")) {
                var inspected = ContentTypeRegistry.getInstance().factory(entry.getKey()).inspectExisting(item);
                if (inspected.isPresent()) { type = entry.getKey(); properties = inspected.get(); break; }
            }
            String icon = "";
            items.add(new GameCatalog.Entry(id.toString(), new ItemStack(item).getName().getString(), type, item.getClass().getName(), item.getMaxCount(), item.getMaxDamage(),
                    item.isFireproof(), memberships.getOrDefault(item, List.of()), properties, icon, variants.contains(item)));
        }
        return new GameCatalog(1, new ArrayList<>(ContentTypeRegistry.getInstance().definitions().values()), groups, items);
    }
    public static String classify(Item item) {
        String kind = item.isFood() ? "food" : item instanceof SwordItem ? "sword" : item instanceof BowItem ? "bow" : item instanceof CrossbowItem ? "crossbow" :
                item instanceof PickaxeItem ? "pickaxe" : item instanceof AxeItem ? "axe" : item instanceof ShovelItem ? "shovel" : item instanceof HoeItem ? "hoe" :
                item instanceof ArmorItem ? "armor" : item instanceof BlockItem ? "block" : "item";
        return "itemcontent:" + kind;
    }
    private static JsonObject inspect(Item item, String type) {
        JsonObject p = new JsonObject();
        var definition = ContentTypeRegistry.getInstance().definition(type);
        if (definition.properties().stream().anyMatch(property -> property.id().equals("durability"))) p.addProperty("durability", item.getMaxDamage());

        if (item.isFood()) p = ContentTypeRegistry.getInstance().factory("itemcontent:food").inspectExisting(item).orElse(p);
        if (item instanceof ToolItem tool) {
            ToolMaterial material = tool.getMaterial(); p.addProperty("enchantability", material.getEnchantability());
            setRepair(p, material.getRepairIngredient());
            p.addProperty("mining_level", material.getMiningLevel()); p.addProperty("mining_speed", material.getMiningSpeedMultiplier());
            if (item instanceof SwordItem) { p.remove("mining_level"); p.remove("mining_speed"); }
            var attributes = item.getAttributeModifiers(net.minecraft.entity.EquipmentSlot.MAINHAND);
            var attack = attributes.get(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
            var speed = attributes.get(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_SPEED);
            p.addProperty("attack_damage", 1 + attack.stream().mapToDouble(net.minecraft.entity.attribute.EntityAttributeModifier::getValue).sum());
            p.addProperty("attack_speed", 4 + speed.stream().mapToDouble(net.minecraft.entity.attribute.EntityAttributeModifier::getValue).sum());
        }
        if (item instanceof ArmorItem armor) {
            setRepair(p, armor.getMaterial().getRepairIngredient());
            p.addProperty("knockback_resistance", armor.getMaterial().getKnockbackResistance());
            p.addProperty("slot", armor.getType().name().toLowerCase(Locale.ROOT)); p.addProperty("protection", armor.getProtection());
            p.addProperty("toughness", armor.getToughness()); p.addProperty("enchantability", armor.getEnchantability());
            String material = armor.getMaterial().getName();
            if (List.of("leather", "chainmail", "iron", "gold", "diamond", "netherite").contains(material)) p.addProperty("armor_texture", material);
        }
        if (item instanceof BlockItem block) {
            p.addProperty("hardness", Math.max(0, block.getBlock().getHardness()));
            p.addProperty("resistance", block.getBlock().getBlastResistance());
            var state = block.getBlock().getDefaultState();
            p.addProperty("light", state.getLuminance());
            var sound = block.getBlock().getSoundGroup(state);
            String soundName = sound == net.minecraft.sound.BlockSoundGroup.WOOD ? "wood" : sound == net.minecraft.sound.BlockSoundGroup.METAL ? "metal" : sound == net.minecraft.sound.BlockSoundGroup.GLASS ? "glass" : sound == net.minecraft.sound.BlockSoundGroup.GRAVEL ? "gravel" : sound == net.minecraft.sound.BlockSoundGroup.WOOL ? "wool" : "stone";
            p.addProperty("sound", soundName);
            String toolName = state.isIn(net.minecraft.registry.tag.BlockTags.PICKAXE_MINEABLE) ? "pickaxe" : state.isIn(net.minecraft.registry.tag.BlockTags.AXE_MINEABLE) ? "axe" : state.isIn(net.minecraft.registry.tag.BlockTags.SHOVEL_MINEABLE) ? "shovel" : state.isIn(net.minecraft.registry.tag.BlockTags.HOE_MINEABLE) ? "hoe" : "none";
            p.addProperty("tool", toolName);
            p.addProperty("mining_level", state.isIn(net.minecraft.registry.tag.BlockTags.NEEDS_DIAMOND_TOOL) ? 3 : state.isIn(net.minecraft.registry.tag.BlockTags.NEEDS_IRON_TOOL) ? 2 : state.isIn(net.minecraft.registry.tag.BlockTags.NEEDS_STONE_TOOL) ? 1 : 0);
        }
        return p;
    }
    private static void setRepair(JsonObject properties, net.minecraft.recipe.Ingredient ingredient) {
        var stacks = ingredient.getMatchingStacks();
        if (stacks.length > 0) properties.addProperty("repair_item", Registries.ITEM.getId(stacks[0].getItem()).toString());
    }
    private static String icon(ResourceManager resources, Identifier item, Path root, long[] bytes) {
        if (bytes[0] >= 64L * 1024 * 1024) return "";
        try {
            Identifier model = new Identifier(item.getNamespace(), "item/" + item.getPath()); Map<String, String> textures = new HashMap<>();
            Set<Identifier> visited = new HashSet<>();
            for (int depth = 0; depth < 32 && visited.add(model); depth++) {
                var resource = resources.getResource(new Identifier(model.getNamespace(), "models/" + model.getPath() + ".json"));
                if (resource.isEmpty()) break;
                JsonObject object;
                try (var reader = resource.get().getReader()) { object = JsonParser.parseReader(reader).getAsJsonObject(); }
                if (object.has("textures")) object.getAsJsonObject("textures").entrySet().forEach(e -> textures.putIfAbsent(e.getKey(), e.getValue().getAsString()));
                if (!object.has("parent")) break; model = new Identifier(object.get("parent").getAsString());
            }
            String texture = textures.getOrDefault("layer0", textures.getOrDefault("all", textures.getOrDefault("side", textures.get("front"))));
            for (int depth = 0; texture != null && texture.startsWith("#") && depth < 16; depth++) texture = textures.get(texture.substring(1));
            if (texture == null || texture.startsWith("#")) return "";
            Identifier id = new Identifier(texture); Identifiers.check(id.toString());
            var resource = resources.getResource(new Identifier(id.getNamespace(), "textures/" + id.getPath() + ".png"));
            if (resource.isEmpty()) return "";
            byte[] png;
            try (var input = resource.get().getInputStream()) { png = input.readNBytes(1024 * 1024 + 1); }
            if (png.length > 1024 * 1024 || png.length < 24 || png[0] != (byte) 137 || png[1] != 80 || png[2] != 78 || png[3] != 71) return "";
            int width = java.nio.ByteBuffer.wrap(png, 16, 4).getInt(), height = java.nio.ByteBuffer.wrap(png, 20, 4).getInt();
            if (width < 1 || height < 1 || width > 4096 || height > 4096 || bytes[0] + png.length > 64L * 1024 * 1024) return "";
            String relative = "catalog-icons/" + item.getNamespace() + "/" + item.getPath() + ".png";
            Path file = root.resolve(relative); Files.createDirectories(file.getParent());
            if (!Files.exists(file) || !Arrays.equals(Files.readAllBytes(file), png)) Files.write(file, png);
            bytes[0] += png.length; return relative;
        } catch (Exception ignored) { return ""; }
    }
}
