package net.earthmc.routefinder.integration;

import com.google.gson.Gson;
import net.earthmc.routefinder.RouteFinderMod;
import net.earthmc.routefinder.model.TownData;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only adapter: the companion never saves or replaces the base mod's config. */
public final class MapAddonBridge {
    private static final Map<String,Method> METHODS=new ConcurrentHashMap<>();
    private static final Gson GSON=new Gson();
    private static Object lastSource;
    private static List<TownData> towns=List.of();
    private static long nextRead;
    private static boolean logged;
    private MapAddonBridge(){}
    public static Object call(String name){
        try {
            Method method=METHODS.get(name);
            if(method==null){method=Class.forName("net.townymap.TownyMapMod").getMethod(name);METHODS.put(name,method);}
            return method.invoke(null);
        } catch(ReflectiveOperationException e){
            if(!logged){logged=true;RouteFinderMod.LOGGER.warn("EarthMC Map Addon integration unavailable: "+name,e);}
            return null;
        }
    }
    public static Object emptySearchResult(){
        try{return Class.forName("net.townymap.gui.TownSearchOverlay$ClickResult").getMethod("none").invoke(null);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Base search integration changed",e);}
    }
    public static int option(String name){
        Object config=call("getConfig");if(config==null)return -1;
        try{return config.getClass().getField(name).getInt(config);}catch(ReflectiveOperationException e){return -1;}
    }
    public static boolean flag(String name){return Boolean.TRUE.equals(call(name));}
    public static double scale(String name){Object n=call(name);return n instanceof Number v?v.doubleValue():0;}
    public static List<TownData> towns(){
        long now=System.currentTimeMillis();if(now<nextRead)return towns;nextRead=now+1000;
        Object api=call("getApiClient");if(api==null)return List.of();
        try {
            Object source=api.getClass().getMethod("getTowns").invoke(api);
            if(source==lastSource)return towns;
            if(source instanceof List<?> list){
                List<TownData> next=new ArrayList<>(list.size());
                for(Object town:list) next.add(GSON.fromJson(GSON.toJsonTree(town),TownData.class));
                towns=List.copyOf(next);lastSource=source;
            }
        }catch(ReflectiveOperationException|RuntimeException e){RouteFinderMod.LOGGER.warn("Cannot read base map towns",e);}
        return towns;
    }
    public static void reset(){lastSource=null;towns=List.of();nextRead=0;}
}
