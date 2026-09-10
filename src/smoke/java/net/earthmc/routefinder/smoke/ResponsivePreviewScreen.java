package net.earthmc.routefinder.smoke;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.earthmc.routefinder.gui.*;
import net.earthmc.routefinder.teleport.*;
import net.earthmc.routefinder.*;
import java.util.*;
public final class ResponsivePreviewScreen extends Screen {
    private final boolean planner;
    public ResponsivePreviewScreen(boolean planner) throws ReflectiveOperationException{
        super(Component.literal("Responsive overlay preview"));this.planner=planner;
        if(IceRoadPlannerOverlay.active()!=planner)IceRoadPlannerOverlay.toggle();
        if(!planner){
            UiWorkflowSmoke.set(TeleportViewerOverlay.class,"open",true);
            UiWorkflowSmoke.set(TeleportViewerOverlay.class,"minimized",false);
            UiWorkflowSmoke.set(TeleportViewerOverlay.class,"advanced",false);
            UiWorkflowSmoke.set(TeleportViewerOverlay.class,"resultsCleared",true);
            var routes=new ArrayList<TeleportRoute>();
            for(int i=0;i<8;i++){
                String name=List.of("Wumengfu","Lijiang","QuJing","Miku").get(i%4);
                var dest=new TeleportDestination(TeleportDestination.Type.TOWN_SPAWN,name,100,64,100,"/t spawn "+name,
                    TeleportDestination.Eligibility.ACCESSIBLE,TeleportDestination.PhysicalAccess.UNKNOWN,TeleportDestination.Reason.SAME_NATION_ACCESS);
                routes.add(new TeleportRoute(TeleportRoute.Mode.STANDARD,List.of(),dest,616+i*100,TeleportRoute.MembershipRisk.LOW,TeleportRoute.Quality.GOOD,0,0,0));
            }
            UiWorkflowSmoke.set(TeleportViewerOverlay.class,"currentRoutes",routes);
            var cfg=RouteFinderMod.getConfig();cfg.teleportWindowX=126;cfg.teleportWindowY=10;
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xFF344F3B);
        for(int x=0;x<width;x+=32)g.fill(x,0,x+1,height,0xFF49694C);
        for(int y=0;y<height;y+=32)g.fill(0,y,width,y+1,0xFF49694C);
        try{
            Class<?> mod=Class.forName("net.townymap.TownyMapMod"),controls=Class.forName("net.townymap.gui.MapToggleOverlay");
            Object cfg=mod.getMethod("getConfig").invoke(null);
            var render=Arrays.stream(controls.getMethods()).filter(m->m.getName().equals("render")).findFirst().orElseThrow();
            render.invoke(null,g,height,cfg,false,false);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        if(planner)IceRoadPlannerOverlay.renderUi(g,width,height);
        else TeleportViewerOverlay.render(g,0,0,1,width,height,RouteFinderMod.getConfig());
    }
}
