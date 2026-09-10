package net.earthmc.routefinder.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.earthmc.routefinder.RouteFinderMod;
import net.earthmc.routefinder.gui.*;
import net.earthmc.routefinder.integration.MapAddonBridge;
import xaero.hud.minimap.module.MinimapSession;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Method;

@Pseudo
@Mixin(targets="net.townymap.TownyMapMod",remap=false)
public abstract class RouteMinimapMixin {
    @Inject(method="renderOnWorldMap",at=@At("RETURN"),remap=false)
    private static void routefinder$renderWorld(GuiGraphicsExtractor g,double x,double z,double scale,int width,int height,CallbackInfo ci){
        if(!RouteFinderMod.mapAvailable())return;
        if(!RouteFinderMod.composingPlannerExport())IceRoadOverlay.render(g,x,z,scale,width,height,RouteFinderMod.getConfig());
        IceRoadPlannerOverlay.render(g,x,z,scale,width,height);
    }
    @Unique private static Method routefinder$angle,routefinder$circle;
    @Unique private static boolean routefinder$failed;
    @Inject(method="renderOnMinimap",at=@At("RETURN"),remap=false)
    private static void routefinder$render(GuiGraphicsExtractor g,Object raw,int x,int y,int size,CallbackInfo ci){
        Minecraft mc=Minecraft.getInstance();
        if(!RouteFinderMod.isTeleportFeatureAvailable()||mc.player==null||mc.level==null||!(raw instanceof MinimapSession session))return;
        if(MapAddonBridge.flag("isAccessBlocked")||!"minecraft_overworld".equals(MapAddonBridge.call("playerWorldResolved")))return;
        double coordinateScale=1;
        if(mc.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD){
            if(mc.level.dimension()==net.minecraft.world.level.Level.NETHER&&MapAddonBridge.option("netherMode")==2)coordinateScale=RouteFinderMod.dimensionCoordinateScale();
            else if(RouteFinderMod.isOnEarthMcServer())return;
        }
        try {
            if(routefinder$angle==null){
                Class<?> base=Class.forName("net.townymap.render.TownyMinimapOverlay");
                routefinder$angle=base.getDeclaredMethod("minimapAngle",MinimapSession.class,Minecraft.class);routefinder$angle.setAccessible(true);
                routefinder$circle=base.getDeclaredMethod("isCircularMinimap",MinimapSession.class);routefinder$circle.setAccessible(true);
            }
            double zoom=Math.max(.25,session.getProcessor().getMinimapZoom());
            double pixels=size/Math.max(8,session.getProcessor().getMinimapSize()/zoom);
            double angle=((Number)routefinder$angle.invoke(null,session,mc)).doubleValue();
            boolean circle=(boolean)routefinder$circle.invoke(null,session);
            double px=mc.player.getX()*coordinateScale,pz=mc.player.getZ()*coordinateScale;
            IceRoadOverlay.renderMinimap(g,px,pz,pixels,angle,x,y,size,circle,RouteFinderMod.getConfig());
            TeleportViewerOverlay.minimapRoute().ifPresent(route->IceRoadOverlay.renderSelectedRouteMinimap(g,route,px,pz,pixels,angle,x,y,size,circle,RouteFinderMod.getConfig()));
        }catch(ReflectiveOperationException|RuntimeException e){if(!routefinder$failed){routefinder$failed=true;RouteFinderMod.LOGGER.warn("Cannot integrate route minimap",e);}}
    }
}
