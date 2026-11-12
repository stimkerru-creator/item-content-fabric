package dev.itemcontent;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.item.*;
import dev.itemcontent.item.BuiltinFactory;
import dev.itemcontent.catalog.ClientCatalog;


/** Reuses a chosen existing item model, so configured items are visible without generated placeholder files. */
public final class ItemContentClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> { ItemContentMod.applyExistingOverrides(); ItemContentMod.validateReferences(); });
        ClientCatalog.register();
        ItemContentMod.registeredItems().forEach((id, item) -> {
            if (item instanceof BowItem) {
                ModelPredicateProviderRegistry.register(item, new Identifier("pull"), (stack, world, entity, seed) -> {
                    if (entity == null || entity.getActiveItem() != stack) return 0;
                    int ticks = item instanceof BuiltinFactory.AdjustableBow bow ? bow.drawTicks() : 20;
                    return (float) (stack.getMaxUseTime() - entity.getItemUseTimeLeft()) / ticks;
                });
                ModelPredicateProviderRegistry.register(item, new Identifier("pulling"), (stack, world, entity, seed) -> entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1 : 0);
            }
            if (item instanceof CrossbowItem) {
                ModelPredicateProviderRegistry.register(item, new Identifier("charged"), (stack, world, entity, seed) -> CrossbowItem.isCharged(stack) ? 1 : 0);
                ModelPredicateProviderRegistry.register(item, new Identifier("pulling"), (stack, world, entity, seed) -> entity != null && entity.isUsingItem() && entity.getActiveItem() == stack && !CrossbowItem.isCharged(stack) ? 1 : 0);
                ModelPredicateProviderRegistry.register(item, new Identifier("pull"), (stack, world, entity, seed) -> entity == null || CrossbowItem.isCharged(stack) ? 0 : (float) (stack.getMaxUseTime() - entity.getItemUseTimeLeft()) / CrossbowItem.getPullTime(stack));
                ModelPredicateProviderRegistry.register(item, new Identifier("firework"), (stack, world, entity, seed) -> CrossbowItem.isCharged(stack) && CrossbowItem.hasProjectile(stack, Items.FIREWORK_ROCKET) ? 1 : 0);
            }
        });
        ModelLoadingPlugin.register(context -> context.resolveModel().register(modelContext -> {
            Identifier id = modelContext.id();
            if (!id.getPath().startsWith("item/")) return null;
            var configured = ItemContentMod.definitions().get(new Identifier(id.getNamespace(), id.getPath().substring("item/".length())));
            if (configured == null || dev.itemcontent.api.ContentTypeRegistry.getInstance().definition(configured.type()).block()) return null;
            if (configured.customModel() || configured.existing() && configured.appearance().equals(configured.id())) return null;
            Identifier sourceItem = configured.appearance();
            return modelContext.getOrLoadModel(new Identifier(sourceItem.getNamespace(), "item/" + sourceItem.getPath()));
        }));
    }
}
