package dev.itemcontent.integration;
import dev.itemcontent.definition.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.nio.file.*;
public final class IntegrationClient implements ClientModInitializer {
    private int ticks; private boolean finished;
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) return;
            if (++ticks > 20*180) { finished=true; throw new IllegalStateException("Timed out waiting for test catalog"); }
            Path file=FabricLoader.getInstance().getConfigDir().resolve("item-content/catalog.json");
            if(client.world==null || !Files.isRegularFile(file))return;
            finished=true;
            try {
                var catalog=GameCatalog.load(file);
                if(catalog.items().size()<1000 || catalog.groups().size()<10)throw new AssertionError("Incomplete registry catalog");
                var sword=catalog.items().stream().filter(item->item.id().equals("minecraft:diamond_sword")).findFirst().orElseThrow();
                if(!sword.type().equals("itemcontent:sword") || !sword.properties().get("repair_item").getAsString().equals("minecraft:diamond"))throw new AssertionError("Wrong sword inspection");
                var provider=catalog.items().stream().filter(item->item.id().equals("itemcontent:launcher")).findFirst().orElseThrow();
                if(!provider.type().equals("testcontent:launcher") || provider.properties().get("fire_rate").getAsFloat()!=12.5f)throw new AssertionError("Missing addon property");
                int icons=0; for(var item:catalog.items())if(!item.icon().isBlank() && Files.isRegularFile(file.getParent().resolve(item.icon())))icons++;
                if(icons<500)throw new AssertionError("Missing normal model icons: "+icons);
                for(String path:java.util.List.of("block","decorative_chest","decorative_furnace","bow","crossbow","armor")) {
                    var item=Registries.ITEM.get(new Identifier("itemcontent:"+path));
                    if(client.getItemRenderer().getModel(new ItemStack(item),client.world,null,0)==client.getBakedModelManager().getMissingModel())throw new AssertionError("Missing rendered model: "+path);
                }
                int importedChecks=0;
                if (Registries.ITEM.containsId(new Identifier("itemcontent:native_item"))) {
                    for (String kind:java.util.List.of("item","block","bow")) {
                        var item=Registries.ITEM.get(new Identifier("itemcontent:native_"+kind));
                        var model=client.getItemRenderer().getModel(new ItemStack(item),client.world,null,0);
                        if (model==client.getBakedModelManager().getMissingModel()) throw new AssertionError("Missing imported "+kind);
                        if (!model.getParticleSprite().getContents().getId().getPath().startsWith("item/imported/")) throw new AssertionError("Imported texture was not stitched: "+kind);
                        int quads=model.getQuads(null,null,net.minecraft.util.math.random.Random.create(1)).size();
                        for (var direction:net.minecraft.util.math.Direction.values()) quads+=model.getQuads(null,direction,net.minecraft.util.math.random.Random.create(1)).size();
                        if (quads<12) throw new AssertionError("Imported geometry lost: "+kind+" quads="+quads);
                        importedChecks++;
                    }
                    var bow=Registries.ITEM.get(new Identifier("itemcontent:native_bow"));
                    var saved=client.player.getMainHandStack(); var stack=new ItemStack(bow);
                    var resting=client.getItemRenderer().getModel(stack,client.world,client.player,0);
                    try {
                        client.player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,stack); client.player.setCurrentHand(net.minecraft.util.Hand.MAIN_HAND);
                        var pulling=client.getItemRenderer().getModel(stack,client.world,client.player,0);
                        if (pulling==resting) throw new AssertionError("Imported bow override did not activate");
                        importedChecks++;
                    } finally {client.player.stopUsingItem();client.player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,saved);}
                    var block=Registries.BLOCK.get(new Identifier("itemcontent:native_block"));
                    if (client.getBlockRenderManager().getModel(block.getDefaultState())==client.getBakedModelManager().getMissingModel()) throw new AssertionError("Missing imported placed-block model");
                    importedChecks++;
                }
                Files.writeString(Path.of("client-integration-result.txt"),"PASS catalog items="+catalog.items().size()+" groups="+catalog.groups().size()+" icons="+icons+"; provider properties, native inspection and 6 item/block models; native import checks="+importedChecks);
                System.out.println("ITEMCONTENT_CLIENT_INTEGRATION_PASS");
            } catch(Exception failure) { throw new IllegalStateException("Client integration failed",failure); }
            finally {client.scheduleStop();}
        });
    }
}
