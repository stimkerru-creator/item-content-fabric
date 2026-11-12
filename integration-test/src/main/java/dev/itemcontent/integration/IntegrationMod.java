package dev.itemcontent.integration;

import com.google.gson.JsonObject;
import dev.itemcontent.*;
import dev.itemcontent.api.*;
import dev.itemcontent.config.ItemSpec;
import dev.itemcontent.definition.*;
import dev.itemcontent.block.ContentBlock;
import dev.itemcontent.item.BuiltinFactory;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.item.*;
import net.minecraft.block.*;
import net.minecraft.registry.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;

/** Loaded only by runIntegrationServer. Never included in the released mod. */
public final class IntegrationMod implements ModInitializer, ItemContentExtension {
    public static final class SampleItem extends Item { float rate; SampleItem(Settings settings,float rate) { super(settings); this.rate=rate; } }
    private static SampleItem original;
    private static final List<String> passed = new ArrayList<>();
    @Override public void register(ContentTypeRegistry registry) {
        original=Registry.register(Registries.ITEM,new Identifier("testcontent:original"),new SampleItem(new Item.Settings(),3));
        var type=new TypeDefinition("testcontent:launcher","Test launcher","Test provider","minecraft:combat","handheld",false,
                List.of(PropertyDefinition.number("fire_rate","Fire rate",5,1,100,true,"Shots per second")),"Test-only optional extension");
        registry.register(type,new ContentFactory() {
            public CreatedContent create(ItemSpec spec,Item.Settings settings) { return new CreatedContent(new SampleItem(settings,spec.number("fire_rate"))); }
            public Optional<JsonObject> inspectExisting(Item item) { if(!(item instanceof SampleItem sample))return Optional.empty(); var result=new JsonObject();result.addProperty("fire_rate",sample.rate);return Optional.of(result); }
            public void applyExisting(Item item,JsonObject values) { if(!(item instanceof SampleItem sample))throw new IllegalArgumentException("Wrong native class");if(values.has("fire_rate"))sample.rate=values.get("fire_rate").getAsFloat(); }
        });
    }
    @Override public void onInitialize() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                ItemContentMod.applyExistingOverrides(); ItemContentMod.validateReferences();
                check(item("sword") instanceof SwordItem,"real sword class");
                check(item("pickaxe") instanceof PickaxeItem,"real pickaxe class");
                check(item("axe") instanceof AxeItem && item("shovel") instanceof ShovelItem && item("hoe") instanceof HoeItem,"native tool classes");
                check(item("bow") instanceof BuiltinFactory.AdjustableBow && ((BuiltinFactory.AdjustableBow)item("bow")).drawTicks()==10,"adjustable native bow");
                check(item("crossbow") instanceof CrossbowItem,"native crossbow");
                check(item("food").isFood() && item("food").getFoodComponent().getHunger()==8,"native food values");
                var sword=item("sword");
                check(sword.getMaxCount()==1 && sword.getMaxDamage()==750,"sword durability and stack");
                var attributes=sword.getAttributeModifiers(EquipmentSlot.MAINHAND);
                double damage=1+attributes.get(EntityAttributes.GENERIC_ATTACK_DAMAGE).stream().mapToDouble(EntityAttributeModifier::getValue).sum();
                double speed=4+attributes.get(EntityAttributes.GENERIC_ATTACK_SPEED).stream().mapToDouble(EntityAttributeModifier::getValue).sum();
                check(Math.abs(damage-9)<1e-5 && Math.abs(speed-2.5)<1e-5,"configured total attack damage and speed");
                var armor=(ArmorItem)item("armor");check(armor.getType()==ArmorItem.Type.HELMET && armor.getProtection()==4 && armor.getMaxDamage()==321,"native armor slot protection durability");
                check(Items.GOLDEN_APPLE.getFoodComponent().getHunger()==9 && !Items.GOLDEN_APPLE.getFoodComponent().getStatusEffects().isEmpty(),"existing food keeps status effects");
                check(new ItemStack(Items.GOLDEN_APPLE).getName().getString().equals("Modified golden apple"),"existing item display name mixin");
                check(Registries.ITEM.get(new Identifier("testcontent:original"))==original && original.rate==13,"provider edits original instance without replacing class or ID");
                check(item("launcher") instanceof SampleItem && ((SampleItem)item("launcher")).rate==12.5f,"optional provider creates native custom class");
                var inspected=ContentTypeRegistry.getInstance().factory("testcontent:launcher").inspectExisting(item("launcher")).orElseThrow();
                check(inspected.get("fire_rate").getAsFloat()==12.5f,"provider exposes editable property");
                var world=server.getOverworld();
                for(String type:List.of("block","decorative_chest","decorative_furnace")) {
                    var blockItem=(BlockItem)item(type); var block=blockItem.getBlock();
                    check(block instanceof ContentBlock && Registries.BLOCK.getId(block).equals(new Identifier("itemcontent:"+type)),"block registry "+type);
                    var pos=new BlockPos(0,80,passed.size());world.setBlockState(pos,block.getDefaultState(),3);
                    check(world.getBlockState(pos).isOf(block),"world placement "+type);
                    check(world.getBlockEntity(pos)==null,"decorative has no inventory "+type);
                }
                var block=((BlockItem)item("block")).getBlock().getDefaultState();
                check(block.isIn(net.minecraft.registry.tag.BlockTags.PICKAXE_MINEABLE) && block.isIn(net.minecraft.registry.tag.BlockTags.NEEDS_IRON_TOOL),"exported datapack mining tags");
                check(new ItemStack(item("pickaxe")).isSuitableFor(block),"configured pickaxe mines required-level block");
                ItemGroups.updateDisplayContext(world.getEnabledFeatures(),true,world.getRegistryManager());
                var group=Registries.ITEM_GROUP.get(new Identifier("itemcontent:test_group"));
                check(group!=null && group.getDisplayStacks().stream().anyMatch(stack->stack.isOf(item("sword"))),"custom creative tab membership");
                check(group.getDisplayStacks().stream().anyMatch(stack->stack.isOf(Items.GOLDEN_APPLE)),"existing item moved to custom tab");
                var foods=Registries.ITEM_GROUP.get(new Identifier("minecraft:food_and_drinks"));
                check(foods.getDisplayStacks().stream().noneMatch(stack->stack.isOf(Items.GOLDEN_APPLE)),"existing item removed from previous creative tab");
                Files.writeString(Path.of("integration-result.txt"),"PASS "+passed.size()+" checks\n"+String.join("\n",passed));
                System.out.println("ITEMCONTENT_INTEGRATION_PASS "+passed.size());
            } catch(Throwable failure) {
                try {Files.writeString(Path.of("integration-result.txt"),"FAIL "+failure);} catch(Exception ignored) { }
                throw new IllegalStateException("Item Content integration failed",failure);
            } finally { server.execute(()->server.stop(false)); }
        });
    }
    private static Item item(String path) {var id=new Identifier("itemcontent:"+path); if(!Registries.ITEM.containsId(id))throw new AssertionError("Missing "+id);return Registries.ITEM.get(id);}
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);passed.add(message);}
}
