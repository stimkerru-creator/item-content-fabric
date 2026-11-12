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
                Files.writeString(Path.of("client-integration-result.txt"),"PASS catalog items="+catalog.items().size()+" groups="+catalog.groups().size()+" icons="+icons+"; provider properties, native inspection and 6 item/block models");
                System.out.println("ITEMCONTENT_CLIENT_INTEGRATION_PASS");
            } catch(Exception failure) { throw new IllegalStateException("Client integration failed",failure); }
            finally {client.scheduleStop();}
        });
    }
}
