package net.earthmc.routefinder;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.io.IOException;
public final class RouteFinderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean teleportViewerEnabled = true;
    public boolean teleportAllowNonEarthMc = false;
    public boolean teleportMapClickAction = true;
    public boolean teleportDefaultAdvanced = false;
    public boolean teleportAdvancedEnabled = false;
    public boolean teleportShowUncertain = true;
    public boolean teleportShowObstructed = true;
    public boolean teleportShowTownSpawns = true;
    public boolean teleportShowNationSpawns = true;
    public boolean teleportIncludeIceRoads = true;
    public boolean iceRoadOverlayEnabled = true;
    public int iceRoadLineWidth = 1;
    public int iceRoadMarkerSize = 8;
    public int iceRoadStationFilter = 0;
    public boolean teleportRouteLineVisible = true;
    public boolean teleportDestinationMarkerVisible = true;
    public boolean teleportArrivalMarkerVisible = true;
    public int teleportCommandAction = 0;
    public int teleportAdvancedMaxJoinHops = 1;
    public boolean teleportRememberPrimaryHome = true;
    public String teleportPrimaryHomeTown = "";
    public String teleportAccessibilitySave = "";
    public int teleportWindowX = 118;
    public int teleportWindowY = 34;
    public java.util.Map<String,String> teleportSpawnReports = new java.util.HashMap<>();
    public java.util.Map<String,String> iceRoadStationReports = new java.util.HashMap<>();
    private static Path path(){return FabricLoader.getInstance().getConfigDir().resolve("earthmcroutefinder.json");}
    public static RouteFinderConfig load(){
        if(Files.isRegularFile(path()))try(var reader=Files.newBufferedReader(path())){
            RouteFinderConfig c=GSON.fromJson(reader,RouteFinderConfig.class);
            if(c!=null){c.sanitize();return c;}
        }catch(IOException|JsonParseException e){RouteFinderMod.LOGGER.warn("Cannot read route settings; using defaults",e);}
        return new RouteFinderConfig();
    }
    private void sanitize(){
        iceRoadLineWidth=Math.clamp(iceRoadLineWidth,1,9);iceRoadMarkerSize=Math.clamp(iceRoadMarkerSize,8,24);
        iceRoadStationFilter=Math.clamp(iceRoadStationFilter,0,2);teleportCommandAction=Math.clamp(teleportCommandAction,0,2);
        if(teleportAccessibilitySave==null)teleportAccessibilitySave="";
        if(teleportPrimaryHomeTown==null)teleportPrimaryHomeTown="";
        if(teleportSpawnReports==null)teleportSpawnReports=new java.util.HashMap<>();
        if(iceRoadStationReports==null)iceRoadStationReports=new java.util.HashMap<>();
    }
    public void save(){
        sanitize();
        try{Files.createDirectories(path().getParent());Path temp=path().resolveSibling("earthmcroutefinder.json.tmp");
            Files.writeString(temp,GSON.toJson(this));Files.move(temp,path(),StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException e){RouteFinderMod.LOGGER.warn("Cannot save route settings",e);}
    }
}
