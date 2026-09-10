package net.earthmc.routefinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.earthmc.routefinder.api.EarthMcApiClient;
import net.earthmc.routefinder.gui.*;
import net.earthmc.routefinder.ice.IceRoadNetwork;
import net.earthmc.routefinder.integration.MapAddonBridge;
import net.earthmc.routefinder.model.TownData;
import net.earthmc.routefinder.teleport.*;
import org.slf4j.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class RouteFinderMod implements ClientModInitializer {
    public static final Logger LOGGER=LoggerFactory.getLogger("EarthMCRouteFinder");
    private static RouteFinderConfig config;
    private static EarthMcApiClient api;
    private static TeleportAccessService access;
    private static Object connection;
    @Override public void onInitializeClient(){
        config=RouteFinderConfig.load();api=new EarthMcApiClient();access=new TeleportAccessService(api,config);
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(connection!=mc.getConnection()){
                connection=mc.getConnection();access=new TeleportAccessService(api,config);
                TeleportViewerOverlay.close();MapAddonBridge.reset();
            }
            IceRoadPlannerOverlay.imageExportFinished();
            if(mc.level==null||!isTeleportFeatureAvailable())return;
            IceRoadNetwork.tickAutoUpdate();
            if(TeleportViewerOverlay.open())access.tick(currentTownSnapshot(),mc.getUser().getName(),Integer.toString(System.identityHashCode(connection)));
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->dispatcher.register(literal("routefinder")
            .executes(c->{Minecraft mc=Minecraft.getInstance();mc.execute(()->mc.setScreen(new TeleportViewerSettingsScreen(mc.screen)));return 1;})
            .then(literal("roads").executes(c->{config.iceRoadOverlayEnabled=!config.iceRoadOverlayEnabled;config.save();c.getSource().sendFeedback(Component.literal("Ice roads: "+config.iceRoadOverlayEnabled));return 1;}))
            .then(literal("planner").executes(c->{IceRoadPlannerOverlay.toggle();c.getSource().sendFeedback(Component.literal("Ice road planner: "+(IceRoadPlannerOverlay.active()?"on (open your world map)":"off")));return 1;}))
            .then(literal("target").then(argument("x",DoubleArgumentType.doubleArg()).then(argument("z",DoubleArgumentType.doubleArg()).executes(c->{
                if(!isTeleportFeatureAvailable()){c.getSource().sendError(Component.literal("Route Finder is unavailable on this server."));return 0;}
                TeleportViewerOverlay.open(DoubleArgumentType.getDouble(c,"x"),DoubleArgumentType.getDouble(c,"z"));
                c.getSource().sendFeedback(Component.literal("Target selected. Open your world map to view routes."));return 1;
            }))))));
        LOGGER.info("EarthMC Route Finder loaded alongside EarthMC Map Addon");
    }
    public static RouteFinderConfig getConfig(){return config;}
    public static boolean isOnEarthMcServer(){return MapAddonBridge.flag("isOnEarthMcServer");}
    public static boolean isTeleportFeatureAvailable(){return config!=null&&config.teleportViewerEnabled&&(isOnEarthMcServer()||config.teleportAllowNonEarthMc);}
    public static double dimensionCoordinateScale(){double s=MapAddonBridge.scale("dimensionCoordinateScale");return s>0?s:1;}
    public static double mapScale(){return MapAddonBridge.scale("worldMapOverlayScale");}
    public static boolean mapAvailable(){return isTeleportFeatureAvailable()&&mapScale()>0&&!MapAddonBridge.flag("isAccessBlocked")&&MapAddonBridge.flag("viewingEarth");}
    public static List<TownData> currentTownSnapshot(){return MapAddonBridge.towns();}
    public static void refreshTeleportData(double x,double z){access.beginQuery(currentTownSnapshot(),Minecraft.getInstance().getUser().getName(),x,z);}
    public static void forceRefreshTeleportData(double x,double z){refreshTeleportData(x,z);access.refresh(currentTownSnapshot(),Minecraft.getInstance().getUser().getName());}
    public static CompletableFuture<TeleportAccessService.Plan> teleportPlanAsync(double x,double z){
        access.ensure(currentTownSnapshot(),Minecraft.getInstance().getUser().getName());
        TeleportAccessService service=access;return CompletableFuture.supplyAsync(()->service.plan(x,z));
    }
    public static void loadAccessibilitySave(net.earthmc.routefinder.storage.AccessibilitySaves.Save save){
        access.replaceSpawnReports(save.reports());
        config.teleportAccessibilitySave=save.name();config.save();
        TeleportViewerOverlay.accessibilityLoaded();
    }
    public static void setTeleportSpawnReport(TeleportDestination d,TeleportDestination.PhysicalAccess a){access.setSpawnReport(d,a);}
    public static String teleportTownName(double x,double z){
        for(TownData t:currentTownSnapshot())if(contains(t,x,z))return t.name();return null;
    }
    private static boolean contains(TownData town,double x,double z){
        if(!town.intersectsWorld(x,x,z,z))return false;boolean inside=false;
        for(int[][] ring:town.polygonRings())for(int i=0,j=ring.length-1;i<ring.length;j=i++){
            int[] a=ring[i],b=ring[j];if(a.length<2||b.length<2)continue;
            if((a[1]>z)!=(b[1]>z)&&x<(double)(b[0]-a[0])*(z-a[1])/(b[1]-a[1])+a[0])inside=!inside;
        }return inside;
    }
    public static boolean composingScreenshot(){return MapAddonBridge.flag("composingScreenshot");}
    public static boolean composingPlannerExport(){return composingScreenshot()&&IceRoadPlannerOverlay.imageExportPending();}
    public static void armMapScreenshot(){MapAddonBridge.call("armMapScreenshot");}
}
