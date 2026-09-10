package net.earthmc.routefinder.gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.*;
import net.minecraft.util.Util;
import net.earthmc.routefinder.*;
import net.earthmc.routefinder.storage.*;
import java.io.IOException;
import java.util.*;

public final class AccessibilitySavesScreen extends Screen {
    private final Screen parent;
    private final AccessibilitySaves saves=new AccessibilitySaves(SaveFolders.accessibility());
    private List<AccessibilitySaves.Entry> entries=List.of();
    private EditBox name;
    private String typed="",status="";
    private int page,rows,left,span;
    public AccessibilitySavesScreen(Screen parent){super(Component.literal("Accessibility saves"));this.parent=parent;}
    @Override protected void init(){
        left=Math.max(8,(width-580)/2);span=Math.min(580,width-16);
        rows=Math.max(1,(height-200)/24);
        try{entries=saves.list();}catch(IOException e){status=e.getMessage();entries=List.of();}
        page=Math.min(page,Math.max(0,(entries.size()-1)/rows));
        name=new EditBox(font,left,42,span-120,20,Component.literal("Save name"));
        name.setMaxLength(64);name.setValue(typed);name.setHint(Component.literal("Name this accessibility save"));
        name.setResponder(v->typed=v);addRenderableWidget(name);
        button("Save current",left+span-116,42,116,()->saveCurrent(false));
        int third=(span-8)/3;
        button("Open save folder",left,68,third,()->{
            try{Util.getPlatform().openUri(saves.folder().toUri());}catch(IOException e){status=e.getMessage();}
        });
        button("Refresh list",left+third+4,68,third,()->{status="List refreshed";rebuildWidgets();});
        button("Open planner folder",left+2*(third+4),68,span-2*(third+4),()->{
            try{Util.getPlatform().openUri(SaveFolders.planner().toUri());}catch(IOException e){status=e.getMessage();}
        });
        for(int i=0;i<rows&&page*rows+i<entries.size();i++){
            var entry=entries.get(page*rows+i);int y=112+i*24;
            String label=entry.name()+(entry.error().isEmpty()?" ("+entry.reports()+" reports)":" (invalid)");
            button(font.plainSubstrByWidth(label,span-94),left,y,span-80,()->{typed=entry.name();name.setValue(typed);});
            Button load=button("Load",left+span-76,y,76,()->load(entry));
            load.active=entry.error().isEmpty();
        }
        button("< Previous",left,height-48,96,()->{if(page>0){page--;rebuildWidgets();}}).active=page>0;
        button("Next >",left+span-96,height-48,96,()->{page++;rebuildWidgets();}).active=(page+1)*rows<entries.size();
        button("Done",width/2-50,height-26,100,this::onClose);
    }
    private void saveCurrent(boolean overwrite){
        String saveName=typed.trim();
        try{
            saves.save(saveName,RouteFinderMod.getConfig().teleportSpawnReports,overwrite);
            RouteFinderMod.getConfig().teleportAccessibilitySave=saveName;
            RouteFinderMod.getConfig().save();status="Saved "+saveName;rebuildWidgets();
        }catch(java.nio.file.FileAlreadyExistsException exists){
            minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(confirmed->{
                minecraft.gui.setScreen(this);
                if(confirmed)saveCurrent(true);
            },Component.literal("Overwrite accessibility save?"),
              Component.literal("Replace \""+saveName+"\" with your current town/nation spawn reports? The previous contents will be lost.")));
        }catch(IOException e){status=e.getMessage();}
    }
    private void load(AccessibilitySaves.Entry entry){
        try{
            var save=saves.load(entry.file());
            RouteFinderMod.loadAccessibilitySave(save);
            status="Loaded "+save.name();rebuildWidgets();
        }catch(IOException e){status=e.getMessage();}
    }
    private Button button(String label,int x,int y,int w,Runnable action){
        return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xFF10171D);
        super.extractRenderState(g,mx,my,delta);
        g.centeredText(font,title,width/2,14,0xFFFFFFFF);
        String active=RouteFinderMod.getConfig().teleportAccessibilitySave;
        g.text(font,font.plainSubstrByWidth("Current: "+(active.isBlank()?"Unsaved reports":active),span),left,29,0xFF8BE5BB,false);
        g.text(font,font.plainSubstrByWidth("Load replaces town/nation spawn reports only. Save current first to keep them.",span),left,96,0xFFB9C5CB,false);
        if(entries.isEmpty())g.text(font,"No saves yet. Name and save your current reports above.",left,118,0xFFB9C5CB,false);
        g.text(font,font.plainSubstrByWidth(status,span),left,height-64,0xFFFFCC66,false);
        g.centeredText(font,(page+1)+" / "+Math.max(1,(entries.size()+rows-1)/rows),width/2,height-42,0xFFB9C5CB);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
