package net.earthmc.routefinder.gui;
import net.earthmc.routefinder.RouteFinderMod;
import java.lang.reflect.Method;

/** Use the base column's origin and scale for clicks as well as rendering. */
public final class RouteToolbar {
    private static Method top, unscale;
    public static boolean click(double x,double y,int width){
        try {
            if(top==null){
                top=Class.forName("net.townymap.gui.MapToggleOverlay").getMethod("togglesTop",int.class);
                unscale=Class.forName("net.townymap.gui.UiScale").getMethod("unscale",double.class,double.class);
            }
            int height=net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
            int origin=(Integer)top.invoke(null,height);
            x=(Double)unscale.invoke(null,x,8.0);
            y=(Double)unscale.invoke(null,y,(double)origin);
            int row=RouteSideLayout.hit(x,y,origin);
            if(row==0){
                var config=RouteFinderMod.getConfig();
                config.iceRoadOverlayEnabled=!config.iceRoadOverlayEnabled;
                config.save();
            }else if(row==1)IceRoadPlannerOverlay.toggle();
            return row>=0;
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Route side controls are incompatible with the base addon",e);}
    }
}
