package dev.itemcontent.editor;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Installs only this tool's files, preserving previous files and rolling back failed writes. */
final class GameInstallation {
    record Result(Path game, Path backup, List<Path> installed) {}
    private record Transfer(Path source, Path target) {}
    private static final class Applied {
        final Transfer transfer; final Path backup;
        boolean saved, installed;
        Applied(Transfer transfer, Path backup) { this.transfer=transfer; this.backup=backup; }
    }
    static Path defaultGameDirectory() {
        String appData=System.getenv("APPDATA");
        if (appData!=null && !appData.isBlank()) return Path.of(appData,".minecraft");
        Path home=Path.of(System.getProperty("user.home"));
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")
                ? home.resolve("Library/Application Support/minecraft") : home.resolve(".minecraft");
    }
    static Optional<Path> bundledMod() {
        try {
            Path editor=Path.of(ItemContentEditor.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String name=editor.getFileName().toString();
            if (!name.startsWith("item-content-editor-") || !name.endsWith(".jar")) return Optional.empty();
            String version=name.substring("item-content-editor-".length(),name.length()-4);
            for (Path parent=editor.getParent(); parent!=null; parent=parent.getParent()) {
                for (String directory:List.of("mods","build/libs")) {
                    Path candidate=parent.resolve(directory).resolve("item-content-fabric-"+version+".jar");
                    if (Files.isRegularFile(candidate) && isOurMod(candidate)) return Optional.of(candidate);
                }
                if (parent.getParent()==null || parent.getNameCount()<2) break;
            }
        } catch (Exception ignored) { }
        return Optional.empty();
    }
    static Result install(ItemProject project, Path gameFolder, Path worldFolder, Path mod) throws IOException {
        Path game=gameFolder.toAbsolutePath().normalize();
        if (game.getFileName()==null) throw new IOException("Выберите папку конкретной сборки Minecraft.");
        if (Set.of("mods","config","resourcepacks","datapacks","item-content").contains(game.getFileName().toString().toLowerCase(Locale.ROOT)))
            throw new IOException("Выберите корневую папку игры или сервера, а не mods, config или resourcepacks.");
        safe(game); project.validateForExport();
        if (mod!=null && !isOurMod(mod)) throw new IOException("Выбранный JAR не является модом Item Content.");
        Path world=worldFolder==null ? null : worldFolder.toAbsolutePath().normalize();
        if (world!=null) {
            safe(world);
            if (!Files.isRegularFile(world.resolve("level.dat"))) throw new IOException("Выберите папку существующего мира с файлом level.dat.");
        }
        Files.createDirectories(game);
        Path stage=Files.createTempDirectory(game,".item-content-install-");
        Path backups=null, worldStage=null;
        List<Applied> applied=new ArrayList<>();
        try {
            Path payload=stage.resolve("files"); project.export(payload);
            List<Transfer> plan=new ArrayList<>();
            plan.add(new Transfer(payload.resolve("config/item-content/items.json"),game.resolve("config/item-content/items.json")));
            plan.add(new Transfer(payload.resolve("resourcepacks/item-content"),game.resolve("resourcepacks/item-content")));
            if (Files.exists(payload.resolve("datapacks/item-content"))) {
                if (world==null) throw new IOException("Для этих блоков нужен набор данных мира. Выберите мир в окне установки.");
                worldStage=Files.createTempDirectory(world,".item-content-install-");
                Path worldPayload=worldStage.resolve("item-content"); copyTree(payload.resolve("datapacks/item-content"),worldPayload);
                plan.add(new Transfer(worldPayload,world.resolve("datapacks/item-content")));
            }
            List<Path> installedMods=new ArrayList<>();
            Path mods=game.resolve("mods"); safe(mods);
            if (Files.isDirectory(mods)) try (var files=Files.list(mods)) {
                for (Path jar:files.filter(p -> p.getFileName().toString().startsWith("item-content-fabric-") && p.toString().endsWith(".jar")).toList()) {
                    safe(jar);
                    if (!isOurMod(jar)) throw new IOException("Не удалось подтвердить принадлежность файла Item Content: "+jar);
                    installedMods.add(jar);
                }
            }
            if (mod==null && installedMods.isEmpty()) throw new IOException("JAR мода не найден. Распакуйте полный комплект Item Content с папкой mods.");
            if (mod!=null) {
                Path stagedJar=stage.resolve(mod.getFileName().toString()); Files.copy(mod,stagedJar);
                Path destination=mods.resolve(mod.getFileName().toString());
                for (Path previous:installedMods) if (!previous.equals(destination)) plan.add(new Transfer(null,previous));
                plan.add(new Transfer(stagedJar,destination));
            }
            for (Transfer transfer:plan) safe(transfer.target());
            Path backupRoot=game.resolve(".item-content-backups"); safe(backupRoot); Files.createDirectories(backupRoot);
            backups=Files.createTempDirectory(backupRoot,"installation-");
            Path worldBackups=null;
            if (worldStage!=null) {
                Path folder=world.resolve(".item-content-backups"); safe(folder); Files.createDirectories(folder);
                worldBackups=Files.createTempDirectory(folder,"installation-");
            }
            StringBuilder manifest=new StringBuilder("Item Content installation backup\n");
            for (int index=0;index<plan.size();index++) {
                Path backupFolder=worldBackups!=null && plan.get(index).target().startsWith(world) ? worldBackups : backups;
                manifest.append(backupFolder.resolve(Integer.toString(index))).append(" -> ").append(plan.get(index).target()).append('\n');
            }
            Files.writeString(backups.resolve("paths.txt"),manifest.toString());
            for (int index=0;index<plan.size();index++) {
                Transfer transfer=plan.get(index);
                Path backupFolder=worldBackups!=null && transfer.target().startsWith(world) ? worldBackups : backups;
                Applied operation=new Applied(transfer,backupFolder.resolve(Integer.toString(index))); applied.add(operation);
                Files.createDirectories(transfer.target().getParent());
                if (Files.exists(transfer.target())) { Files.move(transfer.target(),operation.backup); operation.saved=true; }
                if (transfer.source()!=null) { Files.move(transfer.source(),transfer.target()); operation.installed=true; }
            }
            Files.writeString(backups.resolve("status.txt"),"Installation completed. Previous files were preserved.\n");
            return new Result(game,backups,plan.stream().filter(p -> p.source()!=null).map(Transfer::target).toList());
        } catch (IOException | RuntimeException failure) {
            for (int index=applied.size()-1;index>=0;index--) {
                Applied operation=applied.get(index);
                try {
                    if (operation.installed) Files.move(operation.transfer.target(),operation.backup.resolveSibling("new-"+index));
                    if (operation.saved) Files.move(operation.backup,operation.transfer.target());
                } catch (IOException rollback) { failure.addSuppressed(rollback); }
            }
            String location=backups==null ? "" : "\nРезервная копия: "+backups;
            if (failure.getSuppressed().length>0) throw new IOException("Установка прервана; часть файлов нужно восстановить из резервной копии."+location,failure);
            throw new IOException("Установка не выполнена. Прежние файлы сохранены."+location+"\n"+failure.getMessage(),failure);
        } finally {
            // Only this invocation's newly created staging directory is removed.
            cleanStage(stage);
            if (worldStage!=null) cleanStage(worldStage);
        }
    }
    private static void copyTree(Path source,Path target) throws IOException {
        try (var files=Files.walk(source)) {
            for (Path file:files.toList()) {
                Path destination=target.resolve(source.relativize(file));
                if (Files.isDirectory(file)) Files.createDirectories(destination); else Files.copy(file,destination);
            }
        }
    }
    private static void cleanStage(Path stage) throws IOException {
        try (var files=Files.walk(stage)) {
            for (Path file:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }
    private static void safe(Path target) throws IOException {
        for (Path p=target.toAbsolutePath().normalize();p!=null;p=p.getParent())
            if (Files.isSymbolicLink(p)) throw new IOException("Выберите путь без символических ссылок: "+p);
    }
    private static boolean isOurMod(Path file) throws IOException {
        try (ZipFile zip=new ZipFile(file.toFile())) {
            var entry=zip.getEntry("fabric.mod.json"); if (entry==null) return false;
            try (InputStream input=zip.getInputStream(entry)) {
                byte[] data=input.readNBytes(65537); if (data.length>65536) return false;
                return "itemcontent".equals(JsonParser.parseString(new String(data,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().get("id").getAsString());
            }
        } catch (RuntimeException failure) { throw new IOException("Не удалось проверить JAR: "+file,failure); }
    }
}
