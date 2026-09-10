package net.earthmc.routefinder.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.earthmc.routefinder.RouteFinderMod;
public final class IceRoadSettingsScreen extends Screen {
    private final Screen parent;
    public IceRoadSettingsScreen(Screen parent){super(Component.literal("Ice road settings"));this.parent=parent;}
    @Override protected void init(){
        var cfg=RouteFinderMod.getConfig();int x=width/2-130,y=Math.max(35,height/2-90);
        addRenderableWidget(Button.builder(Component.literal("Ice roads: "+(cfg.iceRoadOverlayEnabled?"On":"Off")),b->{cfg.iceRoadOverlayEnabled=!cfg.iceRoadOverlayEnabled;cfg.save();rebuildWidgets();}).bounds(x,y,260,20).build());
        addRenderableWidget(Button.builder(Component.literal("Line width: "+cfg.iceRoadLineWidth),b->{cfg.iceRoadLineWidth=cfg.iceRoadLineWidth%9+1;cfg.save();rebuildWidgets();}).bounds(x,y+25,260,20).build());
        addRenderableWidget(Button.builder(Component.literal("Station size: "+cfg.iceRoadMarkerSize),b->{cfg.iceRoadMarkerSize=cfg.iceRoadMarkerSize>=24?8:cfg.iceRoadMarkerSize+2;cfg.save();rebuildWidgets();}).bounds(x,y+50,260,20).build());
        String[] filters={"All","Accessible","Blocked"};
        addRenderableWidget(Button.builder(Component.literal("Stations: "+filters[cfg.iceRoadStationFilter]),b->{cfg.iceRoadStationFilter=(cfg.iceRoadStationFilter+1)%3;cfg.save();rebuildWidgets();}).bounds(x,y+75,260,20).build());
        addRenderableWidget(Button.builder(Component.literal("Double-click route target: "+(cfg.teleportMapClickAction?"On":"Off")),b->{cfg.teleportMapClickAction=!cfg.teleportMapClickAction;cfg.save();rebuildWidgets();}).bounds(x,y+100,260,20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(x,y+135,260,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xEE10171D);super.render(g,mx,my,delta);g.drawCenteredString(font,title,width/2,15,0xFFFFFFFF);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
}
