package dev.standaloneprobe;

import net.fabricmc.api.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.item.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;

/** No references to Item Content classes: the installed production JARs must supply everything. */
public final class ServerProbe implements ModInitializer {
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var passed = new ArrayList<String>();
            try {
                boolean coexist = Boolean.getBoolean("itemcontent.testCoexist");
                check(FabricLoader.getInstance().isModLoaded("itemcontent") == coexist, coexist ? "Item Content is installed alongside exported mods" : "Item Content is absent", passed);
                if (coexist) check(Registries.ITEM.containsId(new Identifier("itemcontent:coexist_probe")), "original working-list item is independently registered", passed);
                check(FabricLoader.getInstance().isModLoaded("bronze_parts") && FabricLoader.getInstance().isModLoaded("other_parts"), "two exported mods loaded", passed);
                long count = Registries.ITEM.getIds().stream().filter(id -> id.getNamespace().equals("bronze_parts")).count();
                check(count == 199, "only 199 selected new items plus one existing-item edit from 4000", passed);
                check(Registries.ITEM.getIds().stream().filter(id -> id.getNamespace().equals("other_parts")).count() == 1, "second mod adds only its one item", passed);
                check(!Registries.ITEM.containsId(new Identifier("bronze_parts:part_200")), "unselected item absent", passed);
                check(item("bronze_sword") instanceof SwordItem && item("bronze_sword").getMaxDamage() == 750, "native sword behavior", passed);
                check(item("hearty_food").getFoodComponent().getHunger() == 8, "native food values", passed);
                check(item("fast_bow") instanceof BowItem && item("crossbow") instanceof CrossbowItem, "native bows", passed);
                var block = ((BlockItem)item("rough_cobblestone")).getBlock();
                var world = server.getOverworld(); var pos = new BlockPos(0, 80, 0); world.setBlockState(pos, block.getDefaultState(), 3);
                check(world.getBlockState(pos).isOf(block), "standalone placed block", passed);
                var partial=((BlockItem)item("native_block")).getBlock().getDefaultState();
                var cube=net.minecraft.block.Blocks.QUARTZ_BLOCK.getDefaultState();
                world.setBlockState(pos.east(),partial,3);
                check(!partial.isOpaque() && partial.getCullingShape(world,pos.east()).isEmpty(), "imported model does not occlude empty space", passed);
                boolean partialFaces=true,solidFaces=true;
                for(var direction:net.minecraft.util.math.Direction.values()) {
                    var neighbour=pos.offset(direction); world.setBlockState(neighbour,partial,3);
                    partialFaces &= net.minecraft.block.Block.shouldDrawSide(cube,world,pos,direction,neighbour);
                    world.setBlockState(neighbour,block.getDefaultState(),3);
                    solidFaces &= !net.minecraft.block.Block.shouldDrawSide(cube,world,pos,direction,neighbour);
                }
                check(partialFaces, "all six quartz faces remain visible beside imported model", passed);
                check(solidFaces, "ordinary solid cube still hides all six internal faces", passed);
                check(block.getDefaultState().isIn(BlockTags.PICKAXE_MINEABLE) && block.getDefaultState().isIn(BlockTags.NEEDS_STONE_TOOL), "mining tags loaded automatically from JAR", passed);
                check(new ItemStack(Items.APPLE).getName().getString().equals("Standalone apple"), "embedded existing-item edit", passed);
                ItemGroups.updateDisplayContext(world.getEnabledFeatures(), true, world.getRegistryManager());
                var group = Registries.ITEM_GROUP.get(new Identifier("bronze_parts:parts"));
                check(group != null && group.getDisplayStacks().stream().anyMatch(stack -> stack.isOf(item("bronze_ingot"))), "custom creative group", passed);
                if (!coexist) check(!Files.exists(Path.of("config/item-content/items.json")) && !Files.exists(Path.of("config/item-content/catalog.json")), "no external config or catalog required/created", passed);
                Files.writeString(Path.of("standalone-server-result.txt"), "PASS " + passed.size() + " checks\n" + String.join("\n", passed));
            } catch (Throwable failure) {
                try { Files.writeString(Path.of("standalone-server-result.txt"), "FAIL " + failure); } catch (Exception ignored) {}
                throw new IllegalStateException("Standalone acceptance failed", failure);
            } finally { server.execute(() -> server.stop(false)); }
        });
    }
    private static Item item(String path) { var id = new Identifier("bronze_parts", path); if (!Registries.ITEM.containsId(id)) throw new AssertionError("Missing " + id); return Registries.ITEM.get(id); }
    private static void check(boolean value, String message, List<String> passed) { if (!value) throw new AssertionError(message); passed.add(message); }
}
