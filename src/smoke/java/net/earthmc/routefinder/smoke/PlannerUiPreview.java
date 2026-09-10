package net.earthmc.routefinder.smoke;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.earthmc.routefinder.gui.*;
public final class PlannerUiPreview extends Screen {
    public PlannerUiPreview(){super(Component.literal("Planner UI regression preview"));
        if(!IceRoadPlannerOverlay.active())IceRoadPlannerOverlay.toggle();
        try{var field=IceRoadPlannerOverlay.class.getDeclaredField("snapMenu");field.setAccessible(true);field.setBoolean(null,true); var branch=IceRoadPlannerOverlay.class.getDeclaredField("branchPanel");branch.setAccessible(true);branch.setBoolean(null,false);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){
        g.fill(0,0,width,height,0xFF35494D);
        for(int px=0;px<width;px+=32)g.fill(px,0,px+1,height,0xFF416064);
        for(int py=0;py<height;py+=32)g.fill(0,py,width,py+1,0xFF416064);
        IceRoadPlannerOverlay.render(g,0,0,.5,width,height);
        IceRoadPlannerOverlay.renderUi(g,width,height);
        RouteToolbar.render(g,width);
        g.text(font,Component.translatable("earthmcroutefinder.common.refresh"),width-90,height-65,0xFF8BE5BB,false);
    }
}
