package net.earthmc.routefinder.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.earthmc.routefinder.gui.TeleportViewerSettingsScreen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Use the base settings screen's searchable category and action layout. */
@Mixin(targets="net.townymap.gui.TownyMapConfigScreen",remap=false)
public abstract class RouteSettingsMixin {
    @Shadow private void section(String label){throw new AssertionError();}
    @Shadow private void action(String label,Runnable run){throw new AssertionError();}
    @Shadow private void relayout(){throw new AssertionError();}
    @Inject(method="init",at=@At("RETURN"))
    private void routefinder$settings(CallbackInfo ci){
        String label=Component.translatable("earthmcroutefinder.controls.settings").getString();
        section(label);
        action(label,()->{
            Minecraft mc=Minecraft.getInstance();
            mc.gui.setScreen(new TeleportViewerSettingsScreen((Screen)(Object)this));
        });
        relayout();
    }
}
