package dev.itemcontent.editor;

import java.nio.file.*;
import java.util.*;
import dev.itemcontent.definition.GameCatalog;

/** Uses the exact production exporter and template; two content JARs, only 200+1 selected entries. */
public final class StandaloneFixture {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]), out = Path.of(args[1]); Files.createDirectories(out);
        var project = ItemProject.load(root.resolve("examples/content-types.icproject"));
        try (var source = MinecraftModel.open(root.resolve("examples/minecraft-models"))) {
            var model = MinecraftModel.load(source, "sample:item/component");
            for (String type : List.of("item", "block", "bow")) {
                var item = new ItemProject.Entry(); item.id = "itemcontent:native_" + type; item.type = "itemcontent:" + type;
                item.importedAppearance = true; item.importedModel = model; if (type.equals("bow")) item.maxCount = 1;
                project.items.add(item);
            }
        }
        var crossbow = new ItemProject.Entry(); crossbow.id = "itemcontent:crossbow"; crossbow.type = "itemcontent:crossbow";
        crossbow.appearance = "minecraft:crossbow"; crossbow.maxCount = 1; project.items.add(crossbow);
        var override = new ItemProject.Entry(); override.id = "minecraft:apple"; override.existing = true;
        override.name = "Standalone apple"; override.appearance = override.id; project.items.add(override);
        while (project.items.size() < 4000) { var item = new ItemProject.Entry(); item.id = "itemcontent:part_" + project.items.size(); project.items.add(item); }
        project.groups.add(new GameCatalog.Group("itemcontent:parts", "Детали")); project.items.get(0).group = "itemcontent:parts";
        Set<String> selected = new LinkedHashSet<>(); project.items.subList(0, 200).forEach(item -> selected.add(item.id));
        StandaloneMod.build(project, selected, new StandaloneMod.Settings("bronze_parts", "Бронзовые детали", "1.0.0", Set.of()), out.resolve("bronze_parts.jar"));
        var second = new ItemProject(); var item = new ItemProject.Entry(); item.id = "itemcontent:bronze_ingot"; second.items.add(item);
        StandaloneMod.build(second, Set.of(item.id), new StandaloneMod.Settings("other_parts", "Другой набор", "1.0.0", Set.of()), out.resolve("other_parts.jar"));
    }
}
