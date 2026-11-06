package dev.itemcontent.config;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
public final class ItemContentConfig {
 private static final int MAX_ITEMS=4096, MAX_BYTES=4*1024*1024; private ItemContentConfig() {}
 public static Path path(){ return FabricLoader.getInstance().getConfigDir().resolve("item-content").resolve("items.json"); }
 public static List<ItemSpec> load() throws IOException {
  Path file=path(); Files.createDirectories(file.getParent());
  if(Files.notExists(file)){ Files.writeString(file,"{\n  \"version\": 1,\n  \"items\": []\n}\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW); return List.of(); }
  if(Files.size(file)>MAX_BYTES) throw new IOException("Configuration exceeds 4 MiB");
  JsonElement parsed=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)); if(!parsed.isJsonObject()) throw new IOException("Root must be object");
  JsonObject root=parsed.getAsJsonObject(); for(String key:root.keySet()) if(!Set.of("version","items").contains(key)) throw new IOException("Unknown key: "+key);
  if(!root.has("version")||root.get("version").getAsInt()!=1) throw new IOException("Unsupported configuration version");
  if(!root.has("items")||!root.get("items").isJsonArray()) throw new IOException("items must be array");
  JsonArray array=root.getAsJsonArray("items"); if(array.size()>MAX_ITEMS) throw new IOException("Too many items");
  List<ItemSpec> result=new ArrayList<>(); Set<Identifier> ids=new HashSet<>();
  for(JsonElement element:array){ if(!element.isJsonObject()) throw new IOException("Each item must be object"); JsonObject item=element.getAsJsonObject(); for(String key:item.keySet()) if(!Set.of("id","name","appearance","max_count","fireproof").contains(key)) throw new IOException("Unknown item key: "+key);
   String raw=required(item,"id"); Identifier id=raw.contains(":")?new Identifier(raw):new Identifier("itemcontent",raw); if(id.getNamespace().equals("minecraft")) throw new IOException("minecraft namespace is reserved: "+id); if(!ids.add(id)) throw new IOException("Duplicate ID: "+id);
   String name=required(item,"name"); Identifier appearance=new Identifier(item.has("appearance")?item.get("appearance").getAsString():"minecraft:iron_ingot"); int count=item.has("max_count")?item.get("max_count").getAsInt():64; boolean fireproof=item.has("fireproof")&&item.get("fireproof").getAsBoolean(); result.add(new ItemSpec(id,name,appearance,count,fireproof)); }
  return List.copyOf(result);
 }
 private static String required(JsonObject o,String key) throws IOException { if(!o.has(key)||!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isString()) throw new IOException("Missing text field: "+key); String s=o.get(key).getAsString().trim(); if(s.isEmpty()) throw new IOException("Empty text field: "+key); return s; }
}
