package dev.itemcontent.editor;

import java.nio.file.*;

/** Uses the real importer/exporter to prepare resources for Minecraft client integration. */
public final class NativeModelFixture {
    public static void main(String[] args) throws Exception {
        Path repository=Path.of(args[0]).toAbsolutePath(), output=Path.of(args[1]).toAbsolutePath();
        var project=new ItemProject();
        try(var source=MinecraftModel.open(repository.resolve("examples/minecraft-models"))) {
            var model=MinecraftModel.load(source,"sample:item/component");
            for(String type:java.util.List.of("item","block","bow")) {
                var item=new ItemProject.Entry();item.id="itemcontent:native_"+type;item.name=switch(type) { case "block" -> "Бронзовый опорный блок"; case "bow" -> "Пример модели с натяжением"; default -> "Бронзовая деталь"; };
                item.type="itemcontent:"+type;item.importedAppearance=true;item.importedModel=model;
                if(type.equals("bow"))item.maxCount=1;
                project.items.add(item);
            }
        }
        project.save(output.resolveSibling("native-models.icproject"));
        // Fixture output is replaced only by the build tool's task, which declares this directory.
        project.export(output);
    }
}
