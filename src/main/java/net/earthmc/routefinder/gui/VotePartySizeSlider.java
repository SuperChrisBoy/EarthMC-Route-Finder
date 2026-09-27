package net.earthmc.routefinder.gui;

import net.earthmc.routefinder.RouteFinderMod;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A saved percentage of the responsive HUD size, in five-percent steps. */
public final class VotePartySizeSlider extends AbstractSliderButton {
    public VotePartySizeSlider(int x, int y, int width) {
        super(x, y, width, 20, Component.empty(), (RouteFinderMod.getConfig().votePartyHudScale - 30) / 120.0);
        updateMessage();
    }
    private int percent() { return 30 + (int)Math.round(value * 24) * 5; }
    @Override protected void updateMessage() {
        setMessage(Component.translatable("earthmcroutefinder.vote_party.size", percent()));
    }
    @Override protected void applyValue() {
        var config = RouteFinderMod.getConfig();
        config.votePartyHudScale = percent();
        config.save();
    }
}
