package net.earthmc.routefinder.integration;
import com.terraformersmc.modmenu.api.*;
import net.earthmc.routefinder.gui.TeleportViewerSettingsScreen;
public final class RouteFinderModMenu implements ModMenuApi {
    public ConfigScreenFactory<?> getModConfigScreenFactory(){return TeleportViewerSettingsScreen::new;}
}
