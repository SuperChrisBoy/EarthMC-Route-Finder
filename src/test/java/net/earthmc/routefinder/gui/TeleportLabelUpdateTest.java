package net.earthmc.routefinder.gui;

import net.earthmc.routefinder.teleport.TeleportAccessService;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class TeleportLabelUpdateTest {
    @Test void localLabelUpdateRetainsPlanListAndPositionWithoutLoadingIndicator() throws Exception {
        var saved = new LinkedHashMap<String,Object>();
        for (String name : List.of("currentPlan", "currentRoutes", "pendingRoutes", "selected", "scroll", "routeGeneration", "nextRoutePollAt"))
            saved.put(name, get(name));
        try {
            var plan = new TeleportAccessService.Plan(List.of(), List.of(), null, false, null);
            set("currentPlan", plan);
            set("selected", 3);
            set("scroll", 2);
            set("routeGeneration", 10L);
            set("nextRoutePollAt", Long.MAX_VALUE);
            var routes = get("currentRoutes");
            // Simulate an older calculation still in flight when another label is entered.
            set("pendingRoutes", new CompletableFuture<>());
            TeleportViewerOverlay.stationReportChanged();
            assertSame(plan, get("currentPlan"));
            assertSame(routes, get("currentRoutes"));
            assertEquals(3, get("selected"));
            assertEquals(2, get("scroll"));
            assertEquals(11L, get("routeGeneration"));
            assertEquals(0L, get("nextRoutePollAt"));
            assertFalse(TeleportViewerOverlay.loadingTeleportOptions());
            TeleportViewerOverlay.invalidateRoutes();
            assertEquals(12L, get("routeGeneration"));
            assertSame(plan, get("currentPlan"));
            assertFalse(TeleportViewerOverlay.loadingTeleportOptions());
            // Initial loading and real API refreshes must still display their status.
            set("currentPlan", null);
            assertTrue(TeleportViewerOverlay.loadingTeleportOptions());
            set("currentPlan", new TeleportAccessService.Plan(List.of(), List.of(), null, true, null));
            assertTrue(TeleportViewerOverlay.loadingTeleportOptions());
        } finally {
            for (var entry : saved.entrySet()) set(entry.getKey(), entry.getValue());
        }
    }
    private static Object get(String name) throws Exception {
        var field = TeleportViewerOverlay.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
    private static void set(String name, Object value) throws Exception {
        var field = TeleportViewerOverlay.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}