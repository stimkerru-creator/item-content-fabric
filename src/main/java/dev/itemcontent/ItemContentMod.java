package dev.itemcontent;
import dev.itemcontent.config.*;
import dev.itemcontent.item.ConfiguredItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.item.Item;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import java.io.IOException; import java.util.*;
public final class ItemContentMod implements ModInitializer {
 public static final String MOD_ID="itemcontent"; private static final Logger LOGGER=LoggerFactory.getLogger(MOD_ID); private static final Map<Identifier,ConfiguredItem> ITEMS=new LinkedHashMap<>();
 @Override public void onInitialize(){ try{ for(ItemSpec spec:ItemContentConfig.load()) register(spec); }catch(IOException|RuntimeException e){ throw new IllegalStateException("Invalid item-content config: "+ItemContentConfig.path(),e); } ServerLifecycleEvents.SERVER_STARTING.register(server->LOGGER.info("Registered {} configured items",ITEMS.size())); }
 private static void register(ItemSpec spec){ if(Registries.ITEM.containsId(spec.id())) throw new IllegalStateException("Item ID already registered: "+spec.id()); ITEMS.put(spec.id(),Registry.register(Registries.ITEM,spec.id(),new ConfiguredItem(spec))); LOGGER.info("Registered item {} ({})",spec.id(),spec.displayName()); }
 public static Optional<Item> item(Identifier id){ return Optional.ofNullable(ITEMS.get(id)); }
 public static Map<Identifier,ConfiguredItem> items(){ return Collections.unmodifiableMap(ITEMS); }
}
