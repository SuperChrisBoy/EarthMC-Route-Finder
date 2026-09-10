package net.earthmc.routefinder.gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.earthmc.routefinder.RouteFinderMod;
public final class RouteToolbar {
    private static final String[] LABELS={"Ice roads","Road planner","Route settings"};
    public static void render(GuiGraphics g,int width){
        if(IceRoadPlannerOverlay.active()){
            g.fill(8,4,118,24,0xFF245F50);
            g.drawCenteredString(Minecraft.getInstance().font,net.minecraft.network.chat.Component.translatable("earthmcroutefinder.planner.exit"),63,10,0xFFFFFFFF);
            return;
        }
        int left=Math.max(4,(width-302)/2);
        for(int i=0;i<3;i++){
            int x=left+i*102;boolean active=i==0?RouteFinderMod.getConfig().iceRoadOverlayEnabled:i==1&&IceRoadPlannerOverlay.active();
            g.fill(x,4,x+98,22,active?0xEE245F50:0xEE17232C);
            g.drawCenteredString(Minecraft.getInstance().font,LABELS[i],x+49,10,0xFFFFFFFF);
        }
    }
    public static boolean click(double x,double y,int width){
        if(IceRoadPlannerOverlay.active()){
            if(x>=8&&x<118&&y>=4&&y<24){IceRoadPlannerOverlay.toggle();return true;}
            return false;
        }
        if(y<4||y>=22)return false;int left=Math.max(4,(width-302)/2);
        for(int i=0;i<3;i++)if(x>=left+i*102&&x<left+i*102+98){
            if(i==0){var cfg=RouteFinderMod.getConfig();cfg.iceRoadOverlayEnabled=!cfg.iceRoadOverlayEnabled;cfg.save();}
            if(i==1)IceRoadPlannerOverlay.toggle();
            if(i==2){Minecraft mc=Minecraft.getInstance();mc.setScreen(new TeleportViewerSettingsScreen(mc.screen));}
            return true;
        }return false;
    }
}
