package net.earthmc.routefinder.smoke;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.earthmc.routefinder.gui.RouteSideLayout;
import java.util.List;
public final class SideControlsSmoke {
    public static void verify(Minecraft mc) throws ReflectiveOperationException {
        Class<?> controls=Class.forName("net.townymap.gui.MapToggleOverlay");
        int top=(Integer)controls.getMethod("togglesTop",int.class).invoke(null,480);
        var settingsTop=controls.getDeclaredMethod("settingsTop",int.class);settingsTop.setAccessible(true);
        int sy=(Integer)settingsTop.invoke(null,480);
        if(sy!=top+191||RouteSideLayout.hit(20,sy,top)!=-1)throw new IllegalStateException("Side column overlaps settings");
        int actualTop=(Integer)controls.getMethod("togglesTop",int.class).invoke(null,mc.getWindow().getGuiScaledHeight());
        float scale=(Float)Class.forName("net.townymap.gui.UiScale").getMethod("get").invoke(null);
        boolean roads=net.earthmc.routefinder.RouteFinderMod.getConfig().iceRoadOverlayEnabled;
        boolean planner=net.earthmc.routefinder.gui.IceRoadPlannerOverlay.active();
        for(int row=0;row<2;row++){
            double x=8+12*scale,y=actualTop+(RouteSideLayout.rowY(actualTop,row)+10-actualTop)*scale;
            if(!net.earthmc.routefinder.gui.RouteToolbar.click(x,y,mc.getWindow().getGuiScaledWidth()))throw new IllegalStateException("Side click missed");
            boolean changed=row==0?net.earthmc.routefinder.RouteFinderMod.getConfig().iceRoadOverlayEnabled!=roads:net.earthmc.routefinder.gui.IceRoadPlannerOverlay.active()!=planner;
            if(!changed)throw new IllegalStateException("Side action did not toggle");
            net.earthmc.routefinder.gui.RouteToolbar.click(x,y,mc.getWindow().getGuiScaledWidth());
        }
        Class<?> type=Class.forName("net.townymap.gui.TownyMapConfigScreen");
        Screen screen=(Screen)type.getConstructor(Screen.class).newInstance((Object)null);
        mc.setScreen(screen);
        var categories=type.getDeclaredField("categories");categories.setAccessible(true);
        String label=net.minecraft.network.chat.Component.translatable("earthmcroutefinder.controls.settings").getString();
        if(!((List<?>)categories.get(screen)).contains(label))throw new IllegalStateException("Route settings category missing");
        if(screen.children().stream().noneMatch(child->child instanceof Button b&&b.getMessage().getString().equals(label)))throw new IllegalStateException("Route settings action missing");
        net.earthmc.routefinder.RouteFinderMod.LOGGER.info("ROUTE_SIDE_CONTROLS_SMOKE_OK");
    }
}
