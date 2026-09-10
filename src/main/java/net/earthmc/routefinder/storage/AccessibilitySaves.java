package net.earthmc.routefinder.storage;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Portable, versioned snapshots of physical town/nation spawn accessibility only. */
public final class AccessibilitySaves {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public record Save(int version,String name,String savedAt,Map<String,String> reports){}
    public record Entry(Path file,String name,int reports,String error){}
    private final Path folder;
    public AccessibilitySaves(Path folder){this.folder=folder.toAbsolutePath().normalize();}
    public Path folder() throws IOException {Files.createDirectories(folder);return folder;}
    public Path save(String name,Map<String,String> reports) throws IOException {
        name=name.trim();
        if(name.isEmpty()||name.length()>64||name.equals(".")||name.equals("..")||name.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*")||name.endsWith("."))throw new IOException("Use a name of 1–64 characters without file-path symbols.");
        Map<String,String> checked=validate(reports);
        Path target=folder().resolve("save-"+name+".json");
        Path temp=Files.createTempFile(folder,".saving-",".tmp");
        try{
            Files.writeString(temp,GSON.toJson(new Save(1,name,Instant.now().toString(),checked)));
            // No replacement: an existing named snapshot must never be silently overwritten.
            Files.move(temp,target);
            return target;
        }catch(FileAlreadyExistsException e){throw new IOException("That save name already exists. Choose another name.",e);}
        finally{Files.deleteIfExists(temp);}
    }
    public Save load(Path file) throws IOException {
        Path path=file.toAbsolutePath().normalize();
        if(!folder.equals(path.getParent())||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>2_000_000)throw new IOException("Invalid accessibility save file.");
        try(var reader=Files.newBufferedReader(path)){
            Save save=GSON.fromJson(reader,Save.class);
            if(save==null||save.version()!=1||save.name()==null||save.name().isBlank()||save.name().length()>64)throw new IOException("Unsupported accessibility save.");
            return new Save(1,save.name(),save.savedAt(),validate(save.reports()));
        }catch(JsonParseException|IllegalStateException e){throw new IOException("Invalid accessibility save JSON.",e);}
    }
    public List<Entry> list() throws IOException {
        List<Entry> result=new ArrayList<>();
        try(var files=Files.list(folder())){
            for(Path file:files.filter(p->p.getFileName().toString().endsWith(".json")&&Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).sorted().toList()){
                try{Save save=load(file);result.add(new Entry(file,save.name(),save.reports().size(),""));}
                catch(IOException e){result.add(new Entry(file,file.getFileName().toString(),0,e.getMessage()));}
            }
        }
        return List.copyOf(result);
    }
    private static Map<String,String> validate(Map<String,String> reports) throws IOException {
        if(reports==null||reports.size()>100_000)throw new IOException("Invalid accessibility reports.");
        Map<String,String> copy=new TreeMap<>();
        for(var entry:reports.entrySet()){
            String key=entry.getKey(),value=entry.getValue();
            if(key==null||!(key.startsWith("TOWN_SPAWN:")||key.startsWith("NATION_SPAWN:"))||key.substring(key.indexOf(':')+1).isBlank()||key.length()>256||
               !Set.of("UNKNOWN","ACCESSIBLE","OBSTRUCTED").contains(value==null?"":value))throw new IOException("Invalid town/nation spawn report.");
            copy.put(key,value);
        }
        return Collections.unmodifiableMap(copy);
    }
}
