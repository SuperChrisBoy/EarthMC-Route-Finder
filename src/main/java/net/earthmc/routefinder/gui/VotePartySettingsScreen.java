package net.earthmc.routefinder.gui;

import net.earthmc.routefinder.RouteFinderMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

/** The global HUD stays visible above these controls for immediate size feedback. */
public final class VotePartySettingsScreen extends Screen {
    private final Screen parent;
    public VotePartySettingsScreen(Screen parent) {
        super(Component.translatable("earthmcroutefinder.vote_party.title"));
        this.parent = parent;
    }
    @Override protected void init() {
        var config = RouteFinderMod.getConfig();
        int w = Math.min(260, width - 16), x = (width - w) / 2, y = Math.max(90, height / 2 - 20);
        addRenderableWidget(Button.builder(Component.translatable("earthmcroutefinder.vote_party.toggle",
            Component.translatable(config.votePartyHudEnabled ? "options.on" : "options.off")), b -> {
                config.votePartyHudEnabled = !config.votePartyHudEnabled;
                config.save(); rebuildWidgets();
            }).bounds(x, y, w, 20).build());
        addRenderableWidget(new VotePartySizeSlider(x, y + 24, w));
        addRenderableWidget(Button.builder(Component.translatable("earthmcroutefinder.vote_party.reset_size"), b -> {
            config.votePartyHudScale = 60; config.save(); rebuildWidgets();
        }).bounds(x, y + 50, w / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x + w / 2 + 2, y + 50, w / 2 - 2, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xDD10171D);
        super.extractRenderState(g, mx, my, delta);
        g.centeredText(font, title, width / 2, Math.max(90, height / 2 - 20) - 15, 0xFFFFFFFF);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
