package net.earthmc.routefinder.integration;

import net.minecraft.client.Minecraft;
import net.earthmc.routefinder.model.MapJumpTarget;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

import java.util.ArrayList;
import java.util.List;

public final class XaeroWaypointBridge {

    private static final String ROUTE_PREFIX = "RF: ";
    private static final String TELEPORT_ROUTE_PREFIX = "RF · ";
    private static final String LEGACY_TELEPORT_ROUTE_PREFIX = "RF Route · ";
    private static final List<Waypoint> TELEPORT_VIEWER_WAYPOINTS = new ArrayList<>();
    public record RouteWaypoint(String label,int x,int y,int z){}

    private XaeroWaypointBridge() {
    }

    public static boolean createRouteWaypoint(MapJumpTarget target) {
        if (target == null) return false;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return false;

        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null || session.getWorldManager() == null) return false;

        MinimapWorld world = session.getWorldManager().getCurrentWorld();
        if (world == null || world.getCurrentWaypointSet() == null) return false;

        WaypointSet set = world.getCurrentWaypointSet();
        removePreviousTownyRoutes(set);

        int y = client.player.getBlockY();
        String label = ROUTE_PREFIX + cleanLabel(target.label());
        // The waypoint goes into the current dimension's set, so the target's EarthMC (overworld)
        // coordinates have to be brought into that dimension first — otherwise a route created in
        // the Nether points 8x too far away.
        double dimScale = net.earthmc.routefinder.RouteFinderMod.dimensionCoordinateScale();
        Waypoint waypoint = new Waypoint(
                (int) Math.round(target.x() / dimScale),
                y,
                (int) Math.round(target.z() / dimScale),
                label,
                symbol(target.label()),
                WaypointColor.PURPLE,
                WaypointPurpose.DESTINATION,
                true,
                false
        );
        waypoint.setOneoffDestination(true);
        set.add(waypoint, true);
        session.getWaypointSession().setSetChangedTime(System.currentTimeMillis());
        return true;
    }
    public static boolean createTeleportWaypoint(String label,int x,int y,int z){
        WaypointSet set=currentWaypointSet();Minecraft client=Minecraft.getInstance();if(set==null||client==null||client.player==null)return false;
        double scale=net.earthmc.routefinder.RouteFinderMod.dimensionCoordinateScale();Waypoint waypoint=new Waypoint((int)Math.round(x/scale),y<=0?client.player.getBlockY():y,(int)Math.round(z/scale),cleanLabel(label),symbol(label),WaypointColor.PURPLE,WaypointPurpose.NORMAL,true,y>0);set.add(waypoint,true);TELEPORT_VIEWER_WAYPOINTS.add(waypoint);touch();return true;
    }
    public static boolean replaceTeleportRouteWaypoints(List<RouteWaypoint> route){WaypointSet set=currentWaypointSet();Minecraft client=Minecraft.getInstance();if(set==null||client==null||client.player==null||route==null||route.isEmpty())return false;removeTeleportWaypoints();double scale=net.earthmc.routefinder.RouteFinderMod.dimensionCoordinateScale();int number=1;for(RouteWaypoint point:route){String name=TELEPORT_ROUTE_PREFIX+number+" "+cleanLabel(point.label());Waypoint waypoint=new Waypoint((int)Math.round(point.x()/scale),point.y()<=0?client.player.getBlockY():point.y(),(int)Math.round(point.z()/scale),name,Integer.toString(Math.min(9,number)),WaypointColor.PURPLE,WaypointPurpose.NORMAL,true,point.y()>0);set.add(waypoint,true);TELEPORT_VIEWER_WAYPOINTS.add(waypoint);number++;}touch();return true;}
    public static boolean removeTeleportWaypoints(){WaypointSet set=currentWaypointSet();if(set==null)return false;java.util.LinkedHashSet<Waypoint> remove=new java.util.LinkedHashSet<>(TELEPORT_VIEWER_WAYPOINTS);for(Waypoint waypoint:set.getWaypoints())if(waypoint.isTemporary()&&waypoint.getName()!=null&&(waypoint.getName().startsWith(TELEPORT_ROUTE_PREFIX)||waypoint.getName().startsWith(LEGACY_TELEPORT_ROUTE_PREFIX)))remove.add(waypoint);boolean removed=false;for(Waypoint waypoint:remove){try{set.remove(waypoint);removed=true;}catch(RuntimeException ignored){}}TELEPORT_VIEWER_WAYPOINTS.clear();if(removed)touch();return removed;}

    private static WaypointSet currentWaypointSet() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return null;
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null || session.getWorldManager() == null) return null;
        MinimapWorld world = session.getWorldManager().getCurrentWorld();
        if (world == null) return null;
        return world.getCurrentWaypointSet();
    }

    private static void touch() {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session != null && session.getWaypointSession() != null) {
            session.getWaypointSession().setSetChangedTime(System.currentTimeMillis());
        }
    }

    private static void removePreviousTownyRoutes(WaypointSet set) {
        List<Waypoint> toRemove = new ArrayList<>();
        for (Waypoint waypoint : set.getWaypoints()) {
            if (waypoint.isTemporary()
                    && waypoint.isDestination()
                    && waypoint.getName() != null
                    && waypoint.getName().startsWith(ROUTE_PREFIX)) {
                toRemove.add(waypoint);
            }
        }
        for (Waypoint waypoint : toRemove) {
            set.remove(waypoint);
        }
    }

    private static String cleanLabel(String label) {
        String cleaned = label == null ? "Target" : label.replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) return "Target";
        if (cleaned.length() > 64) return cleaned.substring(0, 64);
        return cleaned;
    }

    private static String symbol(String label) {
        if (label != null) {
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (Character.isLetterOrDigit(c)) {
                    return Character.toString(Character.toUpperCase(c));
                }
            }
        }
        return "T";
    }
}
