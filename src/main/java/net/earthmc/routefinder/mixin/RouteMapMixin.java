package net.earthmc.routefinder.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.*;
import net.earthmc.routefinder.RouteFinderMod;
import net.earthmc.routefinder.gui.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import org.lwjgl.glfw.GLFW;

/** Runs input before the base map addon, consuming only companion controls. */
@Mixin(targets="xaero.map.gui.GuiMap",remap=false,priority=1100)
public abstract class RouteMapMixin {
    @Shadow private double cameraX;
    @Shadow private double cameraZ;
    @Shadow private double scale;
    @Shadow private double screenScale;
    @Unique private long routefinder$lastClick;
    @Unique private double routefinder$lastX,routefinder$lastY;
    @Unique private double routefinder$scale(){return (screenScale>0?scale/screenScale:scale)/RouteFinderMod.mapScale();}
    @Inject(method="renderPreDropdown",at=@At("RETURN"))
    private void routefinder$render(GuiGraphicsExtractor g,int mx,int my,float delta,CallbackInfo ci){
        if(!RouteFinderMod.mapAvailable())return;
        Minecraft mc=Minecraft.getInstance();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        double dim=RouteFinderMod.mapScale(),s=routefinder$scale();
        if(!(s>0))return;
        if(RouteFinderMod.composingScreenshot())return;
        IceRoadPlannerOverlay.renderUi(g,w,h);
        if(!IceRoadPlannerOverlay.active())TeleportViewerOverlay.render(g,cameraX*dim,cameraZ*dim,s,w,h,RouteFinderMod.getConfig());
        IceRoadOverlay.renderUi(g,w,h,RouteFinderMod.getConfig());

    }
    @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true)
    private void routefinder$click(MouseButtonEvent event,boolean doubled,CallbackInfoReturnable<Boolean> ci){
        if(!RouteFinderMod.mapAvailable()||event.buttonInfo().button()!=0)return;
        Minecraft mc=Minecraft.getInstance();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        double x=event.x(),y=event.y(),s=routefinder$scale(),dim=RouteFinderMod.mapScale();
        if(!(s>0))return;
        double wx=(x-w/2.0)/s+cameraX*dim,wz=(y-h/2.0)/s+cameraZ*dim;
        if((IceRoadPlannerOverlay.active()&&IceRoadPlannerOverlay.click(x,y,wx,wz,w))
            ||(!IceRoadPlannerOverlay.active()&&(IceRoadOverlay.clickCard(x,y,w,h,RouteFinderMod.getConfig())||RouteToolbar.click(x,y,w,cameraX*dim,cameraZ*dim)||TeleportViewerOverlay.click(x,y,w,h,RouteFinderMod.getConfig())))
            ||IceRoadOverlay.click(x,y,w,h,RouteFinderMod.getConfig())){ci.setReturnValue(true);return;}
        long now=System.nanoTime();double dx=x-routefinder$lastX,dy=y-routefinder$lastY;
        boolean doubleClick=now-routefinder$lastClick<350_000_000L&&dx*dx+dy*dy<=64;
        routefinder$lastClick=now;routefinder$lastX=x;routefinder$lastY=y;
        if(doubleClick&&RouteFinderMod.getConfig().teleportMapClickAction){TeleportViewerOverlay.open(wx,wz);ci.setReturnValue(true);}
    }
    @Inject(method="mouseReleased",at=@At("HEAD"),cancellable=true)
    private void routefinder$release(MouseButtonEvent e,CallbackInfoReturnable<Boolean> ci){
        if(e.buttonInfo().button()==0&&(IceRoadPlannerOverlay.release()|TeleportViewerOverlay.release(RouteFinderMod.getConfig())))ci.setReturnValue(true);
    }
    @Inject(method="mouseScrolled",at=@At("HEAD"),cancellable=true)
    private void routefinder$scroll(double x,double y,double horizontal,double vertical,CallbackInfoReturnable<Boolean> ci){
        if(!RouteFinderMod.mapAvailable())return;
        Minecraft mc=Minecraft.getInstance();
        if(!IceRoadPlannerOverlay.active()&&TeleportViewerOverlay.scroll(x,y,vertical,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),RouteFinderMod.getConfig()))ci.setReturnValue(true);
    }
    @Inject(method="keyPressed",at=@At("HEAD"),cancellable=true)
    private void routefinder$key(KeyEvent e,CallbackInfoReturnable<Boolean> ci){
        if(!RouteFinderMod.mapAvailable())return;
        if(IceRoadPlannerOverlay.keyPressed(e.key())){ci.setReturnValue(true);return;}
        if(e.key()==GLFW.GLFW_KEY_ESCAPE&&TeleportViewerOverlay.open()){TeleportViewerOverlay.close();ci.setReturnValue(true);}
    }
    @Inject(method="charTyped",at=@At("HEAD"),cancellable=true)
    private void routefinder$char(CharacterEvent e,CallbackInfoReturnable<Boolean> ci){
        if(!RouteFinderMod.mapAvailable()||!e.isAllowedChatCharacter())return;
        boolean consumed=false;for(char c:e.codepointAsString().toCharArray())consumed|=IceRoadPlannerOverlay.charTyped(c);
        if(consumed)ci.setReturnValue(true);
    }
}
