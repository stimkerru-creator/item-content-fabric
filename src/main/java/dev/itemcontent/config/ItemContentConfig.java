package dev.itemcontent.config;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.math.BigDecimal;
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
 for(JsonElement element:array){ if(!element.isJsonObject()) throw new IOException("Each item must be object"); JsonObject item=element.getAsJsonObject(); for(String key:item.keySet()) if(!Set.of("id","name","appearance","max_count","fireproof","fields").contains(key)) throw new IOException("Unknown item key: "+key);
   String raw=required(item,"id"); Identifier id=raw.contains(":")?new Identifier(raw):new Identifier("itemcontent",raw); if(!id.getNamespace().equals("itemcontent")) throw new IOException("Configured items must use the reserved itemcontent namespace: "+id); if(!ids.add(id)) throw new IOException("Duplicate ID: "+id);
   String name=required(item,"name"); Identifier appearance=new Identifier(item.has("appearance")?item.get("appearance").getAsString():"minecraft:iron_ingot"); int count=item.has("max_count")?item.get("max_count").getAsInt():64; boolean fireproof=item.has("fireproof")&&item.get("fireproof").getAsBoolean(); List<ItemSpec.FieldSpec> fields=new ArrayList<>(); if(item.has("fields")){ if(!item.get("fields").isJsonArray()) throw new IOException("fields must be array"); for(JsonElement fieldElement:item.getAsJsonArray("fields")){ if(fieldElement.isJsonPrimitive()&&fieldElement.getAsJsonPrimitive().isString()){ String rawField=fieldElement.getAsString(); fields.add(new ItemSpec.FieldSpec(rawField.contains(":")?rawField:"itemcontent:"+rawField,BigDecimal.ZERO,BigDecimal.valueOf(Integer.MIN_VALUE,2),BigDecimal.valueOf(Integer.MAX_VALUE,2))); } else if(fieldElement.isJsonObject()){ JsonObject field=fieldElement.getAsJsonObject(); String rawField=required(field,"id"); String fieldId=rawField.contains(":")?rawField:"itemcontent:"+rawField; BigDecimal value=field.has("default")?field.get("default").getAsBigDecimal():BigDecimal.ZERO; BigDecimal min=field.has("min")?field.get("min").getAsBigDecimal():BigDecimal.valueOf(Integer.MIN_VALUE,2); BigDecimal max=field.has("max")?field.get("max").getAsBigDecimal():BigDecimal.valueOf(Integer.MAX_VALUE,2); fields.add(new ItemSpec.FieldSpec(fieldId,value,min,max)); } else throw new IOException("Invalid field entry"); } } result.add(new ItemSpec(id,name,appearance,count,fireproof,fields)); }
  return List.copyOf(result);
 }
 private static String required(JsonObject o,String key) throws IOException { if(!o.has(key)||!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isString()) throw new IOException("Missing text field: "+key); String s=o.get(key).getAsString().trim(); if(s.isEmpty()) throw new IOException("Empty text field: "+key); return s; }
}
