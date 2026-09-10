package net.earthmc.routefinder.smoke;
import net.earthmc.routefinder.*;
import net.earthmc.routefinder.gui.*;
import net.earthmc.routefinder.ice.IceRoadNetwork;
import java.util.List;
public final class StationPopupSmoke {
    public static void verify() throws ReflectiveOperationException {
        var field=IceRoadOverlay.class.getDeclaredField("selected");field.setAccessible(true);
        Object previous=field.get(null);
        var cfg=RouteFinderMod.getConfig();
        boolean enabled=cfg.iceRoadOverlayEnabled;
        var station=new IceRoadNetwork.Station(999999,"Station popup test","jct",32458,-2999,"",List.of());
        String key=IceRoadNetwork.reportKey(station.id()),old=cfg.iceRoadStationReports.get(key);
        try{
            field.set(null,station);cfg.iceRoadOverlayEnabled=true;
            var box=StationCardLayout.of(640,360);
            String[] expected={"UNKNOWN","ACCESSIBLE","OBSTRUCTED"};
            for(int i=0;i<3;i++){
                var button=box.button(i);
                if(!IceRoadOverlay.clickCard(button.x()+2,button.y()+2,640,360,cfg))throw new IllegalStateException("Popup button missed");
                if(!expected[i].equals(cfg.iceRoadStationReports.getOrDefault(key,"UNKNOWN")))throw new IllegalStateException("Wrong station report");
            }
            var first=box.button(0);
            IceRoadOverlay.clickCard(first.x()+first.width()+1,first.y()+2,640,360,cfg);
            if(!"OBSTRUCTED".equals(cfg.iceRoadStationReports.get(key)))throw new IllegalStateException("Gap changed report");
            if(IceRoadOverlay.clickCard(box.x()+12,335,640,360,cfg))throw new IllegalStateException("Popup steals search click");
            cfg.iceRoadOverlayEnabled=false;
            if(IceRoadOverlay.clickCard(first.x()+2,first.y()+2,640,360,cfg))throw new IllegalStateException("Invisible popup handles input");
        }finally{
            field.set(null,previous);cfg.iceRoadOverlayEnabled=enabled;
            if(old==null)cfg.iceRoadStationReports.remove(key);else cfg.iceRoadStationReports.put(key,old);
            cfg.save();
        }
        RouteFinderMod.LOGGER.info("ROUTE_STATION_POPUP_SMOKE_OK");
    }
}
