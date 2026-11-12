package dev.itemcontent.item;

import com.google.gson.JsonObject;
import dev.itemcontent.api.ContentFactory;
import dev.itemcontent.config.ItemSpec;
import dev.itemcontent.definition.TypeDefinition;
import dev.itemcontent.block.ContentBlock;
import dev.itemcontent.mixin.ItemSettingsAccess;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.sound.*;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import java.util.Optional;

public final class BuiltinFactory implements ContentFactory {
    private final TypeDefinition definition;
    public BuiltinFactory(TypeDefinition definition) { this.definition = definition; }
    @Override public CreatedContent create(ItemSpec spec, Item.Settings settings) {
        String type = spec.type().substring(spec.type().indexOf(':') + 1);
        if (spec.properties().has("durability") && spec.integer("durability") > 0) settings.maxDamage(spec.integer("durability"));
        Item item;
        switch (type) {
            case "item" -> item = new ConfiguredItem(spec);
            case "food" -> item = new Item(settings.food(food(spec.properties())));
            case "sword" -> item = new SwordItem(tool(spec), 0, spec.number("attack_speed") - 4, settings);
            case "pickaxe" -> item = new PickaxeItem(tool(spec), 0, spec.number("attack_speed") - 4, settings);
            case "axe" -> item = new AxeItem(tool(spec), 0, spec.number("attack_speed") - 4, settings);
            case "shovel" -> item = new ShovelItem(tool(spec), 0, spec.number("attack_speed") - 4, settings);
            case "hoe" -> item = new HoeItem(tool(spec), 0, spec.number("attack_speed") - 4, settings);
            case "bow" -> item = new AdjustableBow(settings, spec.integer("draw_ticks"));
            case "crossbow" -> item = new CrossbowItem(settings);
            case "armor" -> item = new ArmorItem(armor(spec), ArmorItem.Type.valueOf(spec.text("slot").toUpperCase(java.util.Locale.ROOT)), settings);
            case "block", "decorative_chest", "decorative_furnace" -> {
                var blockSettings = AbstractBlock.Settings.create().strength(spec.number("hardness"), spec.number("resistance"))
                        .luminance(state -> spec.integer("light")).sounds(sound(spec.text("sound")));
                if (spec.integer("mining_level") > 0) blockSettings.requiresTool();
                if (type.equals("decorative_chest")) blockSettings.nonOpaque();
                Block block = new ContentBlock(blockSettings, !type.equals("block"), type.equals("decorative_chest"));
                return new CreatedContent(new BlockItem(block, settings), block);
            }
            default -> throw new IllegalArgumentException("Unknown builtin content type: " + type);
        }
        return new CreatedContent(item);
    }
    public static Item.Settings settings(ItemSpec spec) {
        Item.Settings settings = new Item.Settings().maxCount(spec.maxCount());
        if (spec.fireproof()) settings.fireproof();
        if (spec.properties().has("durability") && spec.integer("durability") > 0) settings.maxDamage(spec.integer("durability"));
        return settings;
    }
    public static FoodComponent food(JsonObject p) {
        var food = new FoodComponent.Builder().hunger(p.get("hunger").getAsInt()).saturationModifier(p.get("saturation").getAsFloat());
        if (p.get("always_edible").getAsBoolean()) food.alwaysEdible();
        if (p.get("meat").getAsBoolean()) food.meat();
        if (p.get("snack").getAsBoolean()) food.snack();
        return food.build();
    }
    @Override public Optional<JsonObject> inspectExisting(Item item) {
        if (definition.id().equals("itemcontent:food") && item.isFood()) {
            var p = new JsonObject(); var food = item.getFoodComponent();
            p.addProperty("hunger", food.getHunger()); p.addProperty("saturation", food.getSaturationModifier());
            p.addProperty("always_edible", food.isAlwaysEdible()); p.addProperty("meat", food.isMeat()); p.addProperty("snack", food.isSnack());
            return Optional.of(p);
        }
        return Optional.empty();
    }
    @Override public void applyExisting(Item item, JsonObject properties) {
        ItemSettingsAccess access = (ItemSettingsAccess) item;
        if (properties.has("durability")) access.itemcontent$maxDamage(properties.get("durability").getAsInt());
        if (definition.id().equals("itemcontent:food") && properties.size() != 0) {
            JsonObject current = inspectExisting(item).orElseGet(JsonObject::new);
            for (var p : definition.properties()) if (!current.has(p.id())) current.add(p.id(), p.initial());
            properties.entrySet().forEach(e -> current.add(e.getKey(), e.getValue()));
            var previous = item.getFoodComponent();
            var merged = new FoodComponent.Builder().hunger(current.get("hunger").getAsInt()).saturationModifier(current.get("saturation").getAsFloat());
            if (current.get("always_edible").getAsBoolean()) merged.alwaysEdible();
            if (current.get("meat").getAsBoolean()) merged.meat();
            if (current.get("snack").getAsBoolean()) merged.snack();
            if (previous != null) previous.getStatusEffects().forEach(effect -> merged.statusEffect(new net.minecraft.entity.effect.StatusEffectInstance(effect.getFirst()), effect.getSecond()));
            access.itemcontent$food(merged.build());
        }
    }
    private static ToolMaterial tool(ItemSpec spec) {
        return new ToolMaterial() {
            public int getDurability() { return spec.integer("durability"); }
            public float getMiningSpeedMultiplier() { return spec.properties().has("mining_speed") ? spec.number("mining_speed") : 1; }
            public float getAttackDamage() { return spec.number("attack_damage") - 1; }
            public int getMiningLevel() { return spec.properties().has("mining_level") ? spec.integer("mining_level") : 0; }
            public int getEnchantability() { return spec.integer("enchantability"); }
            public Ingredient getRepairIngredient() { return repair(spec); }
        };
    }
    private static ArmorMaterial armor(ItemSpec spec) {
        return new ArmorMaterial() {
            public int getDurability(ArmorItem.Type type) { return spec.integer("durability"); }
            public int getProtection(ArmorItem.Type type) { return spec.integer("protection"); }
            public int getEnchantability() { return spec.integer("enchantability"); }
            public SoundEvent getEquipSound() { return SoundEvents.ITEM_ARMOR_EQUIP_IRON; }
            public Ingredient getRepairIngredient() { return repair(spec); }
            public String getName() { return spec.text("armor_texture").equals("custom") ? spec.id().toString() : spec.text("armor_texture"); }
            public float getToughness() { return spec.number("toughness"); }
            public float getKnockbackResistance() { return spec.number("knockback_resistance"); }
        };
    }
    private static Ingredient repair(ItemSpec spec) {
        Identifier id = new Identifier(spec.text("repair_item"));
        if (!Registries.ITEM.containsId(id)) throw new IllegalStateException("Unknown repair item: " + id);
        return Ingredient.ofItems(Registries.ITEM.get(id));
    }
    private static BlockSoundGroup sound(String name) {
        return switch (name) { case "wood" -> BlockSoundGroup.WOOD; case "metal" -> BlockSoundGroup.METAL; case "glass" -> BlockSoundGroup.GLASS;
            case "gravel" -> BlockSoundGroup.GRAVEL; case "wool" -> BlockSoundGroup.WOOL; default -> BlockSoundGroup.STONE; };
    }
    public static final class AdjustableBow extends BowItem {
        private final int drawTicks;
        public AdjustableBow(Settings settings, int drawTicks) { super(settings); this.drawTicks = drawTicks; }
        public int drawTicks() { return drawTicks; }
        @Override public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
            long used = Math.max(0, getMaxUseTime(stack) - remainingUseTicks);
            int scaled = (int) Math.min(getMaxUseTime(stack), used * 20 / drawTicks);
            super.onStoppedUsing(stack, world, user, getMaxUseTime(stack) - scaled);
        }
    }
}
