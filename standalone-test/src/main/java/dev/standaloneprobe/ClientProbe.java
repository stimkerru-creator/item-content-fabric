package dev.standaloneprobe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.item.*;
import net.minecraft.util.*;
import java.nio.file.*;

public final class ClientProbe implements ClientModInitializer {
    private boolean done; private int ticks;
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done) return;
            if (++ticks > 3600) throw new AssertionError("Standalone client timed out");
            if (client.world == null || client.player == null) return; done = true;
            try {
                if (FabricLoader.getInstance().isModLoaded("itemcontent")) throw new AssertionError("Editor runtime unexpectedly installed");
                for (String name : java.util.List.of("bronze_ingot", "bronze_sword", "rough_cobblestone", "crossbow", "native_item", "native_block", "native_bow")) {
                    var item = Registries.ITEM.get(new Identifier("bronze_parts", name));
                    var model = client.getItemRenderer().getModel(new ItemStack(item), client.world, client.player, 0);
                    if (model == client.getBakedModelManager().getMissingModel()) throw new AssertionError("Missing standalone model: " + name);
                    if (name.startsWith("native_")) {
                        if (!model.getParticleSprite().getContents().getId().getNamespace().equals("bronze_parts")) throw new AssertionError("Wrong native texture namespace");
                        int quads = model.getQuads(null, null, net.minecraft.util.math.random.Random.create(1)).size();
                        for (var direction : net.minecraft.util.math.Direction.values()) quads += model.getQuads(null, direction, net.minecraft.util.math.random.Random.create(1)).size();
                        if (quads < 12) throw new AssertionError("Lost 3D geometry: " + name);
                    }
                }
                var bow = Registries.ITEM.get(new Identifier("bronze_parts", "native_bow")); var stack = new ItemStack(bow);
                var resting = client.getItemRenderer().getModel(stack, client.world, client.player, 0);
                try {
                    client.player.setStackInHand(Hand.MAIN_HAND, stack); client.player.setCurrentHand(Hand.MAIN_HAND);
                    if (client.getItemRenderer().getModel(stack, client.world, client.player, 0) == resting) throw new AssertionError("Bow override not active");
                } finally { client.player.stopUsingItem(); }
                var block = Registries.BLOCK.get(new Identifier("bronze_parts", "native_block"));
                if (client.getBlockRenderManager().getModel(block.getDefaultState()) == client.getBakedModelManager().getMissingModel()) throw new AssertionError("Missing placed 3D model");
                if (Files.exists(Path.of("config/item-content/catalog.json"))) throw new AssertionError("Unexpected catalog created");
                Files.writeString(Path.of("standalone-client-result.txt"), "PASS two independent mods; 7 item models; imported 3D geometry/textures; bow override; placed 3D block; no Item Content or resource-pack setup");
            } catch (Exception failure) { throw new IllegalStateException(failure); }
            finally { client.scheduleStop(); }
        });
    }
}
