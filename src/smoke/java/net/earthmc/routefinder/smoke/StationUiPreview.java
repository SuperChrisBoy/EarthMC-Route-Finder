package net.earthmc.routefinder.smoke;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.earthmc.routefinder.RouteFinderMod;
import net.earthmc.routefinder.gui.*;
import net.earthmc.routefinder.ice.IceRoadNetwork;
import java.util.*;

/** Exercise popup layering over markers and the released base search controls. */
public final class StationUiPreview extends Screen {
    public StationUiPreview(){super(Component.literal("Station popup preview"));
        try{
            var selected=IceRoadOverlay.class.getDeclaredField("selected");selected.setAccessible(true);
            selected.set(null,new IceRoadNetwork.Station(999999,"Lhasa JCT","jct",32458,-2999,"",List.of("TNH: Tibet","TNH: Himalayan route with a long description")));
            RouteFinderMod.getConfig().iceRoadOverlayEnabled=true;
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xFF314C45);
        for(int x=0;x<width;x+=32)g.fill(x,0,x+1,height,0xFF46665C);
        var box=StationCardLayout.of(width,height);
        g.text(Minecraft.getInstance().font,"Player marker and route label underneath popup",box.x()+12,box.y()+21,0xFFFF7777,false);
        g.fill(box.x()+100,box.y()+4,box.x()+112,box.y()+16,0xFF23C7E8);
        try{
            for(var method:Class.forName("net.townymap.gui.TownSearchOverlay").getMethods()){
                if(!method.getName().equals("render"))continue;
                var types=method.getParameterTypes();Object[] args=new Object[types.length];
                args[0]=g;args[1]=width;args[2]=height;
                for(int i=3;i<args.length;i++)args[i]=Map.class.isAssignableFrom(types[i])?Map.of():List.of();
                method.invoke(null,args);break;
            }
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        IceRoadOverlay.renderUi(g,width,height,RouteFinderMod.getConfig());
    }
}
