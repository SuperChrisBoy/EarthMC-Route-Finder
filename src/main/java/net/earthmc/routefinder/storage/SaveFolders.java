package net.earthmc.routefinder.storage;

import java.io.IOException;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;

/** Both user-facing save folders sit immediately inside the active Minecraft game directory. */
public final class SaveFolders {
    private static Path preparedPlanner;
    public static Path accessibility(){return FabricLoader.getInstance().getGameDir().resolve("EarthMC Accessibility Saves").toAbsolutePath();}
    public static synchronized Path planner() throws IOException {
        if(preparedPlanner==null)preparedPlanner=preparePlanner(FabricLoader.getInstance().getGameDir());
        return preparedPlanner;
    }
    public static Path preparePlanner(Path game) throws IOException {
        Path target=game.resolve("EarthMC Ice Road Planner").toAbsolutePath().normalize();
        Files.createDirectories(target);
        Path marker=target.resolve(".legacy-import-complete");
        if(Files.exists(marker))return target;
        Path legacy=game.resolve("earthmcroutefinder").resolve("ice-highway-planner").toAbsolutePath().normalize();
        if(Files.isDirectory(legacy,LinkOption.NOFOLLOW_LINKS)){
            try(var files=Files.walk(legacy)){
                for(Path source:files.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).toList()){
                    Path relative=legacy.relativize(source);
                    if(relative.getNameCount()>1&&relative.getName(0).toString().equals("drafts"))relative=relative.subpath(1,relative.getNameCount());
                    Path destination=target.resolve(relative).normalize();
                    if(!destination.startsWith(target))throw new IOException("Invalid planner save path.");
                    Files.createDirectories(destination.getParent());
                    if(!Files.exists(destination))Files.copy(source,destination);
                }
            }
        }
        Files.writeString(marker,"Existing planner files copied without replacing files or deleting originals.\n");
        return target;
    }
}
