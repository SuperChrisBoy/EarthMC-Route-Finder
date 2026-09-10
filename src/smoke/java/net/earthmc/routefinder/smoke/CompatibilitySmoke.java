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
                    if(ticks==160)net.minecraft.client.Screenshot.grab(mc,true);
                    if(ticks==180)SideUiPreview.settings(mc);
                    if(ticks==230)net.minecraft.client.Screenshot.grab(mc,true);
                    if(ticks==250)mc.stop();
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
                RouteFinderMod.LOGGER.info("ROUTE_FINDER_SMOKE_OK");
                if(Boolean.getBoolean("routefinder.preview")){
                    mc.options.guiScale().set(3);mc.resizeGui();
                    mc.gui.setScreen(new SideUiPreview());
                }else mc.stop();
            }catch(ReflectiveOperationException e){throw new IllegalStateException("Compatibility smoke failed",e);}
        });
    }
}
