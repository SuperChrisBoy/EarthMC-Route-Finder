package net.earthmc.routefinder.smoke;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.*;
import net.earthmc.routefinder.gui.*;
import net.earthmc.routefinder.*;
import java.util.*;
import java.lang.reflect.*;
public final class UiWorkflowSmoke {
    static Object field(Object owner,Class<?> type,String name) throws ReflectiveOperationException{
        var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    static void set(Class<?> type,String name,Object value) throws ReflectiveOperationException{
        var f=type.getDeclaredField(name);f.setAccessible(true);f.set(null,value);
    }
    static Object call(Object owner,Class<?> type,String name,Class<?>[] args,Object... values) throws ReflectiveOperationException{
        var m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m.invoke(owner,values);
    }
    static void press(Screen screen,String label) throws ReflectiveOperationException{
        Button b=(Button)screen.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().equals(label)).findFirst().orElseThrow();
        Object action=field(b,Button.class,"onPress");
        var method=Arrays.stream(action.getClass().getDeclaredMethods()).filter(m->m.getName().equals("onPress")).findFirst().orElseThrow();
        method.setAccessible(true);method.invoke(action,b);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    static void verifyPlanner(Minecraft mc,Screen parent) throws ReflectiveOperationException {
        Class<?> type=IceRoadPlannerOverlay.class;
        List drafts=(List)field(null,type,"drafts");
        var original=new ArrayList(drafts);
        boolean wasActive=IceRoadPlannerOverlay.active();
        Object oldIndex=field(null,type,"draftIndex"),oldY=field(null,type,"placementY"),oldTool=field(null,type,"tool");
        try{
            if(!wasActive)IceRoadPlannerOverlay.toggle();
            var window=mc.getWindow();
            var viewport=OverlayViewport.planner(window.getGuiScaledWidth(),window.getGuiScaledHeight());
            var bar=PlannerToolbarLayout.of(viewport.width(),viewport.height());
            double clickX=28*viewport.scale(),clickY=(bar.top()-12)*viewport.scale();
            if(!IceRoadPlannerOverlay.click(clickX,clickY,0,0,window.getGuiScaledWidth())||!(mc.gui.screen() instanceof CoordinatesScreen))
                throw new IllegalStateException("Scaled Y button missed");
            if(Boolean.TRUE.equals(field(null,type,"uiPressHandled")))throw new IllegalStateException("Dialog leaves stale map release");
            press(mc.gui.screen(),"Cancel");
            Class<?> draftType=Class.forName(type.getName()+"$Draft");
            var constructor=draftType.getDeclaredConstructor(String.class);constructor.setAccessible(true);
            drafts.add(constructor.newInstance("Coordinate workflow smoke"));
            set(type,"draftIndex",drafts.size()-1);set(type,"tool",0);
            call(null,type,"placeExact",new Class[]{double.class,double.class,double.class},1.25,70.0,2.75);
            set(type,"selectedPointBranch",0);set(type,"selectedPoint",0);
            call(null,type,"editCoordinates",new Class[]{boolean.class},false);
            var boxes=mc.gui.screen().children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).toList();
            if(!boxes.get(0).getValue().equals("1.25")||!boxes.get(1).getValue().equals("70.0")||!boxes.get(2).getValue().equals("2.75"))throw new IllegalStateException("Selected point coordinates not prefilled");
            boxes.get(0).setValue("9.75");boxes.get(1).setValue("-45");boxes.get(2).setValue("0.25");
            press(mc.gui.screen(),"Apply");
            Object point=call(null,type,"selectedPointValue",new Class[]{});
            for(var entry:Map.of("x",9.75,"y",-45.0,"z",0.25).entrySet())
                if(!field(point,point.getClass(),entry.getKey()).equals(entry.getValue()))throw new IllegalStateException("XYZ edit changed exact coordinate");
            set(type,"placementY",null);
            call(null,type,"place",new Class[]{double.class,double.class},100.0,100.0);
            if(!(mc.gui.screen() instanceof CoordinatesScreen))throw new IllegalStateException("Map point skipped Y prompt");
            press(mc.gui.screen(),"Cancel");
            Object branch=call(null,type,"branch",new Class[]{});
            List vertices=(List)field(branch,branch.getClass(),"vertices");
            if(vertices.size()!=1)throw new IllegalStateException("Cancelled map placement created point");
            call(null,type,"place",new Class[]{double.class,double.class},100.0,100.0);
            ((EditBox)mc.gui.screen().children().stream().filter(c->c instanceof EditBox).findFirst().orElseThrow()).setValue("-32");
            press(mc.gui.screen(),"Apply");
            Object placed=vertices.getLast();
            if(vertices.size()!=2||!field(placed,placed.getClass(),"y").equals(-32.0))throw new IllegalStateException("Chosen placement Y not applied");
            Class<?> pointType=placed.getClass();
            var pointConstructor=pointType.getDeclaredConstructor(double.class,double.class,double.class);pointConstructor.setAccessible(true);
            for(int i=0;i<3;i++){
                call(null,type,"placeMapPoint",new Class[]{pointType},pointConstructor.newInstance(200.5+i*100,200.5,-32));
                if(!Boolean.TRUE.equals(field(null,type,"pointConnecting"))
                    ||!Objects.equals(call(null,type,"selectedPointValue",new Class[]{}),vertices.getLast()))
                    throw new IllegalStateException("Drawing did not advance to latest point");
            }
            if(vertices.size()!=5)throw new IllegalStateException("Continuous drawing lost points");
            call(null,type,"cancelPointConnection",new Class[]{});
            if(Boolean.TRUE.equals(field(null,type,"pointConnecting")))throw new IllegalStateException("Finish drawing ignored");
        }finally{
            if(IceRoadPlannerOverlay.active()!=wasActive)IceRoadPlannerOverlay.toggle();
            drafts.clear();drafts.addAll(original);set(type,"draftIndex",oldIndex);set(type,"placementY",oldY);set(type,"tool",oldTool);
            call(null,type,"clearMarkerSelection",new Class[]{});
            call(null,type,"saveLibraryQuiet",new Class[]{});
            mc.gui.setScreen(parent);
        }
    }
    public static void verify(Minecraft mc) throws ReflectiveOperationException{
        Screen parent=mc.gui.screen();
        CoordinatesScreen.XYZ[] applied={null};
        var screen=new CoordinatesScreen(parent,"Edit XYZ","-12.25","64","45.75",false,v->applied[0]=v);
        mc.gui.setScreen(screen);
        var boxes=screen.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).toList();
        if(!boxes.get(0).getValue().equals("-12.25")||!boxes.get(2).getValue().equals("45.75"))throw new IllegalStateException("Coordinates not prefilled");
        boxes.get(1).setValue("");press(screen,"Apply");
        if(mc.gui.screen()!=screen||applied[0]!=null)throw new IllegalStateException("Blank Y accepted");
        boxes.get(1).setValue("-32");press(screen,"Apply");
        if(applied[0]==null||applied[0].x()!=-12.25||applied[0].y()!=-32||applied[0].z()!=45.75)throw new IllegalStateException("Exact XYZ lost");
        Class<?> planner=IceRoadPlannerOverlay.class;
        Object oldY=field(null,planner,"placementY");
        try{
            set(planner,"placementY",null);
            call(null,planner,"choosePlacementY",new Class[]{Runnable.class},(Object)null);
            if(!(mc.gui.screen() instanceof CoordinatesScreen))throw new IllegalStateException("No height prompt");
            var y=(EditBox)mc.gui.screen().children().stream().filter(c->c instanceof EditBox).findFirst().orElseThrow();
            if(!y.getValue().isEmpty())throw new IllegalStateException("Silent default height");
            press(mc.gui.screen(),"Cancel");
            if(field(null,planner,"placementY")!=null)throw new IllegalStateException("Cancel chose height");
        }finally{set(planner,"placementY",oldY);mc.gui.setScreen(parent);}
        verifyPlanner(mc,parent);
        var saves=new AccessibilitySavesScreen(parent);mc.gui.setScreen(saves);
        String oldSaveName=RouteFinderMod.getConfig().teleportAccessibilitySave;
        var store=new net.earthmc.routefinder.storage.AccessibilitySaves(net.earthmc.routefinder.storage.SaveFolders.accessibility());
        try{
            var path=store.save("Overwrite smoke",Map.of("TOWN_SPAWN:smoke","OBSTRUCTED"),true);
            var name=saves.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();
            name.setValue("Overwrite smoke");press(saves,"Save current");
            if(!(mc.gui.screen() instanceof ConfirmScreen))throw new IllegalStateException("No overwrite warning");
            press(mc.gui.screen(),"No");
            if(!store.load(path).reports().equals(Map.of("TOWN_SPAWN:smoke","OBSTRUCTED")))throw new IllegalStateException("Cancel overwrote save");
            press(saves,"Save current");press(mc.gui.screen(),"Yes");
            if(!store.load(path).reports().equals(RouteFinderMod.getConfig().teleportSpawnReports))throw new IllegalStateException("Confirmed overwrite failed");
            java.nio.file.Files.delete(path);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        RouteFinderMod.getConfig().teleportAccessibilitySave=oldSaveName;RouteFinderMod.getConfig().save();
        mc.gui.setScreen(parent);
        RouteFinderMod.LOGGER.info("ROUTE_UI_WORKFLOW_SMOKE_OK");
    }
}
