package dev.itemcontent;
import dev.itemcontent.config.*;
import dev.itemcontent.item.ConfiguredItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import java.io.IOException; import java.util.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
public final class ItemContentMod implements ModInitializer {
 public static final String MOD_ID="itemcontent"; private static final Logger LOGGER=LoggerFactory.getLogger(MOD_ID); private static final Map<Identifier,ConfiguredItem> ITEMS=new LinkedHashMap<>();
 @Override public void onInitialize(){ try{ List<ItemSpec> specs=ItemContentConfig.load(); for(ItemSpec spec:specs) register(spec); writeParameterCatalog(specs); }catch(IOException|RuntimeException e){ throw new IllegalStateException("Invalid item-content config: "+ItemContentConfig.path(),e); } ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> ITEMS.values().forEach(entries::add)); ServerLifecycleEvents.SERVER_STARTING.register(server->LOGGER.info("Registered {} configured items",ITEMS.size())); }
 private static void writeParameterCatalog(List<ItemSpec> specs) throws IOException {
  JsonArray items=new JsonArray(); for(ItemSpec spec:specs){ JsonObject item=new JsonObject(); item.addProperty("item",spec.id().toString()); JsonArray fields=new JsonArray(); for(ItemSpec.FieldSpec field:spec.fields()){ JsonObject f=new JsonObject(); f.addProperty("id",field.id()); f.addProperty("default",field.defaultValue()); f.addProperty("min",field.min()); f.addProperty("max",field.max()); fields.add(f); } item.add("fields",fields); items.add(item); }
  Path target=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("itemparams").resolve("items").resolve("itemcontent_generated.json"); Files.createDirectories(target.getParent()); JsonObject root=new JsonObject(); root.addProperty("version",1); root.add("items",items); Path tmp=target.resolveSibling(target.getFileName()+".tmp"); Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(root),StandardCharsets.UTF_8); try{ Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }catch(AtomicMoveNotSupportedException e){ Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING); }
 }
 private static void register(ItemSpec spec){ if(Registries.ITEM.containsId(spec.id())) throw new IllegalStateException("Item ID already registered: "+spec.id()); ITEMS.put(spec.id(),Registry.register(Registries.ITEM,spec.id(),new ConfiguredItem(spec))); LOGGER.info("Registered item {} ({})",spec.id(),spec.displayName()); }
 public static Optional<Item> item(Identifier id){ return Optional.ofNullable(ITEMS.get(id)); }
 public static Map<Identifier,ConfiguredItem> items(){ return Collections.unmodifiableMap(ITEMS); }
}
