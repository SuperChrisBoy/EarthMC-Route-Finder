package net.earthmc.routefinder.gui;

/** GUI-space layout: Minecraft's GUI scale already accounts for display resolution. */
public record VotePartyHudLayout(float x, float y, float scale) {
    public static final int HEIGHT = 34;

    public static VotePartyHudLayout of(int screenWidth, int screenHeight, int panelWidth, int bosses) {
        return of(screenWidth, screenHeight, panelWidth, bosses, 60);
    }

    public static VotePartyHudLayout of(int screenWidth, int screenHeight, int panelWidth, int bosses, int sizePercent) {
        // Vanilla draws bars starting at y=12, spaced 19 pixels apart, stopping at one third height.
        int visibleBosses = Math.min(Math.max(0, bosses), Math.max(1, (int)Math.ceil((screenHeight / 3 - 12) / 19.0)));
        float top = visibleBosses == 0 ? 8 : 23 + (visibleBosses - 1) * 19;
        float scale = Math.clamp(Math.min(screenWidth / 640f, screenHeight / 360f), .65f, 1.25f);
        scale *= Math.clamp(sizePercent, 30, 150) / 100f;
        scale = Math.max(0, Math.min(scale, Math.min((screenWidth - 16f) / panelWidth, (screenHeight - top - 8) / HEIGHT)));
        return new VotePartyHudLayout((screenWidth - panelWidth * scale) / 2, top, scale);
    }
}
