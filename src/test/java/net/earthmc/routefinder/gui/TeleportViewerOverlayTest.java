package net.earthmc.routefinder.gui;

import net.earthmc.routefinder.teleport.TeleportDestination;
import net.earthmc.routefinder.teleport.TeleportRoute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportViewerOverlayTest {
    @Test void closingAndReopeningRemembersLocationResultsAndSelection() throws Exception {
        var values=new java.util.LinkedHashMap<String,Object>();
        for(String name:List.of("open","hasTarget","minimized","targetX","targetZ","currentRoutes","selected","scroll","resultsCleared","dragging")){
            var field=TeleportViewerOverlay.class.getDeclaredField(name);field.setAccessible(true);values.put(name,field.get(null));
        }
        try{
            List<TeleportRoute> routes=List.of(route("First",TeleportDestination.Type.TOWN_SPAWN,TeleportDestination.PhysicalAccess.UNKNOWN),
                route("Selected",TeleportDestination.Type.TOWN_SPAWN,TeleportDestination.PhysicalAccess.ACCESSIBLE));
            var setup=new java.util.HashMap<String,Object>();
            setup.put("open",true);setup.put("hasTarget",true);setup.put("targetX",123.5);setup.put("targetZ",-456.5);
            setup.put("currentRoutes",routes);setup.put("selected",1);setup.put("scroll",1);setup.put("resultsCleared",false);
            for(var entry:setup.entrySet()){var field=TeleportViewerOverlay.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(null,entry.getValue());}
            TeleportViewerOverlay.close();
            assertEquals(false,TeleportViewerOverlay.open());
            TeleportViewerOverlay.show(9999,9999);
            assertEquals(true,TeleportViewerOverlay.open());
            for(var entry:setup.entrySet()){var field=TeleportViewerOverlay.class.getDeclaredField(entry.getKey());field.setAccessible(true);assertEquals(entry.getValue(),field.get(null),entry.getKey());}
        }finally{for(var entry:values.entrySet()){var field=TeleportViewerOverlay.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(null,entry.getValue());}}
    }
    @Test void blockedTownAndNationSpawnsAreAlwaysDisplayed() {
        TeleportRoute open=route("Open",TeleportDestination.Type.TOWN_SPAWN,TeleportDestination.PhysicalAccess.UNKNOWN);
        TeleportRoute blockedTown=route("BlockedTown",TeleportDestination.Type.TOWN_SPAWN,TeleportDestination.PhysicalAccess.OBSTRUCTED);
        TeleportRoute blockedNation=route("BlockedNation",TeleportDestination.Type.NATION_SPAWN,TeleportDestination.PhysicalAccess.OBSTRUCTED);
        List<TeleportRoute> routes=List.of(open,blockedTown,blockedNation);

        assertEquals(3,TeleportViewerOverlay.filterRoutes(routes,true,true,true,true).size());
        assertEquals(routes,TeleportViewerOverlay.filterRoutes(routes,true,true,true,false));
    }

    @Test void threeSeparateAccessibilityButtonsMapDirectlyToTheirStates() {
        assertEquals(TeleportDestination.PhysicalAccess.UNKNOWN,TeleportViewerOverlay.accessButtonAt(14,14));
        assertEquals(TeleportDestination.PhysicalAccess.ACCESSIBLE,TeleportViewerOverlay.accessButtonAt(126,14));
        assertEquals(TeleportDestination.PhysicalAccess.OBSTRUCTED,TeleportViewerOverlay.accessButtonAt(238,14));
        assertEquals(null,TeleportViewerOverlay.accessButtonAt(123,14));
    }

    @Test void exitAccessibilityHasExactlyTheThreeSupportedStates() {
        assertEquals(List.of("UNKNOWN","ACCESSIBLE","OBSTRUCTED"),
                java.util.Arrays.stream(TeleportDestination.PhysicalAccess.values()).map(Enum::name).toList());
    }

    @Test void viewerExitFiltersShowEverythingAccessibleOrBlocked() {
        TeleportRoute unknown=route("Unknown",TeleportDestination.Type.TOWN_SPAWN,TeleportDestination.PhysicalAccess.UNKNOWN);
        TeleportRoute accessible=route("Accessible",TeleportDestination.Type.TOWN_SPAWN,TeleportDestination.PhysicalAccess.ACCESSIBLE);
        TeleportRoute blocked=route("Blocked",TeleportDestination.Type.TOWN_SPAWN,TeleportDestination.PhysicalAccess.OBSTRUCTED);
        List<TeleportRoute> routes=List.of(unknown,accessible,blocked);
        assertEquals(routes,TeleportViewerOverlay.filterRoutes(routes,true,true,TeleportViewerOverlay.ExitFilter.EVERYTHING));
        assertEquals(List.of(accessible),TeleportViewerOverlay.filterRoutes(routes,true,true,TeleportViewerOverlay.ExitFilter.ACCESSIBLE));
        assertEquals(List.of(blocked),TeleportViewerOverlay.filterRoutes(routes,true,true,TeleportViewerOverlay.ExitFilter.BLOCKED));
    }

    @Test void usableViewOnlyShowsCommandsThePlayerCanUse() {
        TeleportRoute usableTown=route("OwnTown",TeleportDestination.Type.TOWN_SPAWN,
                TeleportDestination.Eligibility.ACCESSIBLE,TeleportDestination.PhysicalAccess.UNKNOWN);
        TeleportRoute unavailableTown=route("ClosedTown",TeleportDestination.Type.TOWN_SPAWN,
                TeleportDestination.Eligibility.UNAVAILABLE,TeleportDestination.PhysicalAccess.UNKNOWN);
        TeleportRoute uncertainNation=route("UnknownNation",TeleportDestination.Type.NATION_SPAWN,
                TeleportDestination.Eligibility.UNCERTAIN,TeleportDestination.PhysicalAccess.UNKNOWN);
        List<TeleportRoute> routes=List.of(usableTown,unavailableTown,uncertainNation);

        assertEquals(List.of(usableTown),TeleportViewerOverlay.filterRoutes(routes,true,true,
                TeleportViewerOverlay.DestinationView.USABLE,TeleportViewerOverlay.ExitFilter.EVERYTHING));
        assertEquals(routes,TeleportViewerOverlay.filterRoutes(routes,true,true,
                TeleportViewerOverlay.DestinationView.ALL_SPAWNS,TeleportViewerOverlay.ExitFilter.EVERYTHING));
    }

    @Test void destinationViewButtonsSelectUsableAndAllSpawns() {
        assertEquals(TeleportViewerOverlay.DestinationView.USABLE,TeleportViewerOverlay.destinationViewAt(8,8));
        assertEquals(TeleportViewerOverlay.DestinationView.ALL_SPAWNS,TeleportViewerOverlay.destinationViewAt(124,8));
        assertEquals(null,TeleportViewerOverlay.destinationViewAt(121,8));
    }

    @Test void threeExitFilterButtonsHaveSeparateClickTargets() {
        assertEquals(TeleportViewerOverlay.ExitFilter.EVERYTHING,TeleportViewerOverlay.exitFilterAt(8,8));
        assertEquals(TeleportViewerOverlay.ExitFilter.ACCESSIBLE,TeleportViewerOverlay.exitFilterAt(124,8));
        assertEquals(TeleportViewerOverlay.ExitFilter.BLOCKED,TeleportViewerOverlay.exitFilterAt(240,8));
        assertEquals(null,TeleportViewerOverlay.exitFilterAt(121,8));
    }

    private static TeleportRoute route(String name,TeleportDestination.Type type,TeleportDestination.PhysicalAccess access) {
        return route(name,type,TeleportDestination.Eligibility.ACCESSIBLE,access);
    }

    private static TeleportRoute route(String name,TeleportDestination.Type type,TeleportDestination.Eligibility eligibility,TeleportDestination.PhysicalAccess access) {
        TeleportDestination destination=new TeleportDestination(type,name,0,64,0,"/spawn",eligibility,access,TeleportDestination.Reason.OWN_TOWN);
        return new TeleportRoute(TeleportRoute.Mode.STANDARD,List.of(),destination,0,TeleportRoute.MembershipRisk.LOW,TeleportRoute.Quality.GOOD,0,0,0);
    }
}
