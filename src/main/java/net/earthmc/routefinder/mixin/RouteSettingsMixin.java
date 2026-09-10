package net.earthmc.routefinder.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.earthmc.routefinder.gui.*;
import net.earthmc.routefinder.*;
import net.minecraft.client.gui.components.*;
import java.util.function.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Use the base settings screen's searchable category and action layout. */
@Mixin(targets="net.townymap.gui.TownyMapConfigScreen",remap=false)
public abstract class RouteSettingsMixin {
    @Shadow private int ctrlX;
    @Shadow private void section(String label){throw new AssertionError();}
    @Shadow private void action(String label,Runnable run){throw new AssertionError();}
    @Shadow private void relayout(){throw new AssertionError();}
    @Shadow private void inputRow(String label,AbstractWidget control){throw new AssertionError();}
    @Shadow private CycleButton<Boolean> onOff(boolean value,Consumer<Boolean> setter){throw new AssertionError();}
    @Shadow private CycleButton<Integer> cycle(int value,int[] values,Function<Integer,Component> label,IntConsumer setter){throw new AssertionError();}
    @Unique private String routefinder$label(String suffix){return Component.translatable("earthmcroutefinder.teleport."+suffix).getString();}
    @Unique private void routefinder$toggle(String key,boolean value,Consumer<Boolean> setter){
        inputRow(routefinder$label("settings."+key),onOff(value,v->{setter.accept(v);RouteFinderMod.getConfig().save();}));
    }
    @Inject(method="init",at=@At("RETURN"))
    private void routefinder$settings(CallbackInfo ci){
        String label=Component.translatable("earthmcroutefinder.controls.settings").getString();
        section(label);
        var cfg=RouteFinderMod.getConfig();
        Minecraft mc=Minecraft.getInstance();
        Screen parent=(Screen)(Object)this;
        EditBox primary=new EditBox(mc.font,ctrlX,0,120,20,Component.literal(routefinder$label("primary_town")));
        primary.setMaxLength(64);primary.setValue(cfg.teleportPrimaryHomeTown);
        inputRow(routefinder$label("primary_town"),primary);
        Button save=Button.builder(Component.literal("Validate & save"),b->{
            String value=primary.getValue().trim();
            boolean valid=value.isBlank()||RouteFinderMod.currentTownSnapshot().stream().anyMatch(t->t.name().equalsIgnoreCase(value));
            if(valid){cfg.teleportPrimaryHomeTown=value;cfg.save();}
            b.setMessage(Component.translatable(valid?"earthmcroutefinder.teleport.saved":"earthmcroutefinder.teleport.invalid_town"));
        }).bounds(ctrlX,0,120,20).build();
        inputRow(routefinder$label("save_primary"),save);
        routefinder$toggle("advanced_enabled",cfg.teleportAdvancedEnabled,v->{cfg.teleportAdvancedEnabled=v;if(!v)cfg.teleportDefaultAdvanced=false;});
        var command=cycle(cfg.teleportCommandAction,new int[]{0,1,2},v->Component.translatable("earthmcroutefinder.teleport.command_mode."+switch(v){case 1->"chat";case 2->"execute";default->"clipboard";}).withStyle(v==2?net.minecraft.ChatFormatting.RED:net.minecraft.ChatFormatting.WHITE),v->{cfg.teleportCommandAction=v;cfg.save();});
        command.setTooltip(Tooltip.create(Component.translatable("earthmcroutefinder.settings.execute_command_warning")));
        inputRow(routefinder$label("settings.command_action"),command);
        routefinder$toggle("town_spawns",cfg.teleportShowTownSpawns,v->cfg.teleportShowTownSpawns=v);
        routefinder$toggle("nation_spawns",cfg.teleportShowNationSpawns,v->cfg.teleportShowNationSpawns=v);
        routefinder$toggle("route_line",cfg.teleportRouteLineVisible,v->cfg.teleportRouteLineVisible=v);
        routefinder$toggle("target_marker",cfg.teleportDestinationMarkerVisible,v->cfg.teleportDestinationMarkerVisible=v);
        routefinder$toggle("arrival_marker",cfg.teleportArrivalMarkerVisible,v->cfg.teleportArrivalMarkerVisible=v);
        if(!RouteFinderMod.isOnEarthMcServer())
            inputRow(Component.translatable("earthmcroutefinder.safety.teleport_override").getString(),onOff(cfg.teleportAllowNonEarthMc,v->{cfg.teleportAllowNonEarthMc=v;cfg.save();}));
        action("Accessibility saves",()->mc.setScreen(new AccessibilitySavesScreen(parent)));
        action("Ice road settings",()->mc.setScreen(new IceRoadSettingsScreen(parent)));
        relayout();
    }
}
