package net.earthmc.routefinder.mixin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.earthmc.routefinder.RouteFinderMod;
import net.earthmc.routefinder.gui.IceRoadPlannerOverlay;
import net.earthmc.routefinder.gui.RouteSideLayout;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Extend the existing textured button column inside its UI scale transform. */
@Mixin(targets="net.townymap.gui.MapToggleOverlay",remap=false)
public abstract class RouteSideControlsMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private static void routefinder$hideCoveredControls(CallbackInfo ci){
        if(IceRoadPlannerOverlay.active())ci.cancel();
    }
    @Inject(method={"handleClick","handleSettingsClick"},at=@At("HEAD"),cancellable=true)
    private static void routefinder$ignoreCoveredControls(CallbackInfoReturnable<Boolean> ci){
        if(IceRoadPlannerOverlay.active())ci.setReturnValue(false);
    }
    @Shadow private static void drawToggle(GuiGraphicsExtractor g,Font font,int row,int top,String label,boolean active){throw new AssertionError();}
    @Shadow private static void drawTexturedButton(GuiGraphicsExtractor g,int x,int y,int w,int h,String label,boolean active,int color){throw new AssertionError();}
    @Inject(method="togglesTop",at=@At("RETURN"),cancellable=true)
    private static void routefinder$columnTop(int height,CallbackInfoReturnable<Integer> ci){
        ci.setReturnValue(Math.max(8,ci.getReturnValue()-RouteSideLayout.EXTRA_HEIGHT/2));
    }
    @Inject(method="settingsTop",at=@At("RETURN"),cancellable=true)
    private static void routefinder$settingsTop(int height,CallbackInfoReturnable<Integer> ci){
        ci.setReturnValue(ci.getReturnValue()+RouteSideLayout.EXTRA_HEIGHT);
    }
    @Inject(method="drawSettingsButton",at=@At("HEAD"))
    private static void routefinder$sideButtons(GuiGraphicsExtractor g,Font font,int settingsY,CallbackInfo ci){
        int top=settingsY-9*23-7;
        drawToggle(g,font,6,top,Component.translatable("earthmcroutefinder.controls.ice_roads").getString(),RouteFinderMod.getConfig().iceRoadOverlayEnabled);
        int y=RouteSideLayout.rowY(top,1);
        boolean active=IceRoadPlannerOverlay.active();
        drawTexturedButton(g,8,y,92,20,Component.translatable("earthmcroutefinder.controls.planner").getString(),true,active?0xFFFFFFFF:0xFFBDBDBD);
        g.fill(10,y+3,13,y+17,active?0xFF67D76B:0xFF606060);
        int routeY=RouteSideLayout.rowY(top,2);
        drawTexturedButton(g,8,routeY,92,20,Component.translatable("earthmcroutefinder.controls.route_finder").getString(),true,0xFFFFFFFF);
    }
}
