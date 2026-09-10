package net.earthmc.routefinder.smoke;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.earthmc.routefinder.RouteFinderMod;
import java.util.Arrays;
public final class CompatibilitySmoke implements ClientModInitializer {
    private int ticks;
    public void onInitializeClient(){
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            if(mc.gui.screen()==null||++ticks<30)return;
            if(ticks>30){
                if(Boolean.getBoolean("routefinder.preview")){
                    try {
                        if(ticks==70||ticks==120||ticks==170||ticks==220||ticks==270)net.minecraft.client.Screenshot.grab(mc,true);
                        if(ticks==80)mc.gui.setScreen(new ResponsivePreviewScreen(true));
                        if(ticks==130)mc.gui.setScreen(new ResponsivePreviewScreen(false));
                        if(ticks==180)mc.gui.setScreen(new net.earthmc.routefinder.gui.CoordinatesScreen(null,"Edit point X / Y / Z","32458.5","-32","-2999.5",false,v->{}));
                        if(ticks==230)mc.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(v->{},net.minecraft.network.chat.Component.literal("Overwrite accessibility save?"),net.minecraft.network.chat.Component.literal("Replace \"Regular travel\" with your current town/nation spawn reports? The previous contents will be lost.")));
                        if(ticks==295)mc.stop();
                    }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                }return;
            }
            try {
                for(String name:new String[]{"xaero.map.gui.GuiMap","net.townymap.TownyMapMod"}){
                    Class<?> type=Class.forName(name);
                    if(Arrays.stream(type.getDeclaredMethods()).noneMatch(m->m.getName().contains("routefinder$render")))
                        throw new IllegalStateException("Companion mixin missing: "+name);
                }
                Class<?> base=Class.forName("net.townymap.TownyMapMod");
                for(String method:new String[]{"getApiClient","isOnEarthMcServer","viewingEarth","worldMapOverlayScale","dimensionCoordinateScale","composingScreenshot","armMapScreenshot","isAccessBlocked","playerWorldResolved"})base.getMethod(method);
                Class<?> minimap=Class.forName("net.townymap.render.TownyMinimapOverlay");
                minimap.getDeclaredMethod("minimapAngle",xaero.hud.minimap.module.MinimapSession.class,net.minecraft.client.Minecraft.class);
                minimap.getDeclaredMethod("isCircularMinimap",xaero.hud.minimap.module.MinimapSession.class);
                SideControlsSmoke.verify(mc);
                StationPopupSmoke.verify();
                AccessibilitySavesSmoke.verify();
                UiWorkflowSmoke.verify(mc);
                RouteFinderMod.LOGGER.info("ROUTE_FINDER_SMOKE_OK");
                if(Boolean.getBoolean("routefinder.preview")){
                    mc.options.guiScale().set(3);mc.resizeGui();
                    SideControlsSmoke.verify(mc);
                }else mc.stop();
            }catch(ReflectiveOperationException e){throw new IllegalStateException("Compatibility smoke failed",e);}
        });
    }
}
