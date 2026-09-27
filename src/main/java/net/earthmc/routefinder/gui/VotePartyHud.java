package net.earthmc.routefinder.gui;

import net.earthmc.routefinder.RouteFinderMod;
import net.earthmc.routefinder.api.VotePartyTracker;
import net.earthmc.routefinder.mixin.BossHealthOverlayAccessor;
import net.earthmc.routefinder.mixin.PlayerTabOverlayAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Small global EarthMC status panel, also drawn above menu backgrounds. */
public final class VotePartyHud {
    private VotePartyHud() {}

    public static void register(VotePartyTracker tracker) {
        HudElementRegistry.attachElementAfter(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.SUBTITLES, Identifier.fromNamespaceAndPath("earthmcroutefinder", "vote_party"), (g, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() == null) render(g, tracker);
        });
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) ->
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> render(g, tracker)));
    }

    private static void render(GuiGraphicsExtractor g, VotePartyTracker tracker) {
        if (!RouteFinderMod.getConfig().votePartyHudEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        var status = tracker.status();
        boolean stale = tracker.stale(System.currentTimeMillis());
        Component title = Component.translatable("earthmcroutefinder.vote_party.title");
        Component detail = status == null
            ? Component.translatable(tracker.failed() ? "earthmcroutefinder.vote_party.unavailable" : "earthmcroutefinder.vote_party.loading")
            : Component.translatable(stale ? "earthmcroutefinder.vote_party.stale" : "earthmcroutefinder.vote_party.progress", status.percent(), status.remaining());
        int width = Math.max(140, Math.max(mc.font.width(title), mc.font.width(detail)) + 12);
        // Give the full-width player list priority while Tab is held.
        if (mc.gui.screen() == null && ((PlayerTabOverlayAccessor)mc.gui.hud.getTabList()).routefinder$visible()) return;
        int bosses = mc.level == null || mc.gui.hud.isHidden() ? 0
            : ((BossHealthOverlayAccessor)mc.gui.hud.getBossOverlay()).routefinder$events().size();
        var layout = VotePartyHudLayout.of(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), width, bosses, RouteFinderMod.getConfig().votePartyHudScale);
        if (layout.scale() <= 0) return;
        int color = stale || tracker.failed() ? 0xFFE5B65C : 0xFF65DDB3;
        g.pose().pushMatrix();
        g.pose().translate(layout.x(), layout.y());
        g.pose().scale(layout.scale(), layout.scale());
        try {
            g.fill(0, 0, width, VotePartyHudLayout.HEIGHT, 0xDD10171D);
            g.text(mc.font, title, 6, 4, 0xFFFFFFFF, false);
            g.text(mc.font, detail, 6, 15, 0xFFE0E6EA, false);
            g.fill(6, 27, width - 6, 30, 0xFF39454F);
            if (status != null) {
                int filled = (int)Math.round((width - 12) * status.completed() / (double)status.target());
                g.fill(6, 27, 6 + filled, 30, color);
            }
        } finally { g.pose().popMatrix(); }
    }
}
