package net.earthmc.routefinder.smoke;
import net.earthmc.routefinder.*;
import net.earthmc.routefinder.storage.*;
import net.earthmc.routefinder.gui.TeleportViewerOverlay;
import java.util.*;
public final class AccessibilitySavesSmoke {
    public static void verify(){
        var cfg=RouteFinderMod.getConfig();
        var previous=new HashMap<>(cfg.teleportSpawnReports);
        String previousName=cfg.teleportAccessibilitySave;boolean lines=cfg.teleportRouteLineVisible;
        var saves=new AccessibilitySaves(SaveFolders.accessibility());
        try{
            if(saves.list().stream().noneMatch(e->e.name().equals("Regular travel")))saves.save("Regular travel",Map.of("TOWN_SPAWN:tokyo","ACCESSIBLE","NATION_SPAWN:japan","OBSTRUCTED"));
            if(saves.list().stream().noneMatch(e->e.name().equals("Event detours")))saves.save("Event detours",Map.of("TOWN_SPAWN:tokyo","OBSTRUCTED","NATION_SPAWN:japan","ACCESSIBLE"));
            for(String name:List.of("Regular travel","Event detours")){
                var entry=saves.list().stream().filter(e->e.name().equals(name)).findFirst().orElseThrow();
                var save=saves.load(entry.file());
                RouteFinderMod.loadAccessibilitySave(save);
                if(!cfg.teleportSpawnReports.equals(save.reports())||!cfg.teleportAccessibilitySave.equals(name))throw new IllegalStateException("Accessibility load failed");
                if(cfg.teleportRouteLineVisible!=lines)throw new IllegalStateException("Load changed unrelated settings");
                if(TeleportViewerOverlay.minimapRoute().isPresent())throw new IllegalStateException("Old route was retained");
            }
            if(!SaveFolders.planner().getParent().equals(SaveFolders.accessibility().getParent()))throw new IllegalStateException("Save folders are not at the game root");
            RouteFinderMod.LOGGER.info("ROUTE_ACCESSIBILITY_SAVES_SMOKE_OK");
        }catch(Exception e){throw new IllegalStateException("Accessibility saves smoke failed",e);}
        finally{
            RouteFinderMod.loadAccessibilitySave(new AccessibilitySaves.Save(1,previousName,"",previous));
        }
    }
}
