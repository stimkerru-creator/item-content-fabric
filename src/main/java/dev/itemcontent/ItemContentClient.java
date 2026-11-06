package dev.itemcontent;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.util.Identifier;

/** Reuses a chosen existing item model, so configured items are visible without generated placeholder files. */
public final class ItemContentClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ModelLoadingPlugin.register(context -> context.resolveModel().register(modelContext -> {
            Identifier id = modelContext.id();
            if (!"itemcontent".equals(id.getNamespace()) || !id.getPath().startsWith("item/")) return null;
            var configured = ItemContentMod.items().get(new Identifier(id.getNamespace(), id.getPath().substring("item/".length())));
            if (configured == null) return null;
            Identifier sourceItem = new Identifier(configured.appearance());
            return modelContext.getOrLoadModel(new Identifier(sourceItem.getNamespace(), "item/" + sourceItem.getPath()));
        }));
    }
}
