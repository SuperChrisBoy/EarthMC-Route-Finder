package net.earthmc.routefinder.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Keyboard-editable coordinates, with no silent default height or snapping. */
public final class CoordinatesScreen extends Screen {
    public record XYZ(double x,double y,double z) {
        static XYZ parse(String x,String y,String z){
            XYZ v=new XYZ(Double.parseDouble(x.trim()),Double.parseDouble(y.trim()),Double.parseDouble(z.trim()));
            if(!Double.isFinite(v.x)||!Double.isFinite(v.y)||!Double.isFinite(v.z))throw new NumberFormatException();
            return v;
        }
    }
    private final Screen parent;
    private final Consumer<XYZ> accept;
    private final boolean heightOnly;
    private final String[] values;
    private String error="";
    private final EditBox[] fields=new EditBox[3];
    public CoordinatesScreen(Screen parent,String title,String x,String y,String z,boolean heightOnly,Consumer<XYZ> accept){
        super(Component.literal(title));this.parent=parent;this.accept=accept;this.heightOnly=heightOnly;
        values=new String[]{x,y,z};
    }
    @Override protected void init(){
        int left=Math.max(8,(width-300)/2),span=Math.min(300,width-16),top=Math.max(28,(height-164)/2);
        for(int i=0;i<3;i++){
            final int index=i;
            fields[i]=new EditBox(font,left+24,top+24+(heightOnly?0:i*26),span-24,20,Component.literal(new String[]{"X","Y","Z"}[i]));
            fields[i].setMaxLength(64);fields[i].setValue(values[i]);fields[i].setResponder(v->values[index]=v);
            fields[i].setEditable(!heightOnly||i==1);if(!heightOnly||i==1)addRenderableWidget(fields[i]);
        }
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(left,top+(heightOnly?74:126),(span-4)/2,20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply"),b->{
            try {XYZ v=XYZ.parse(values[0],values[1],values[2]);minecraft.setScreen(parent);accept.accept(v);}
            catch(NumberFormatException e){error="Enter a finite number for X, Y and Z.";}
        }).bounds(left+(span+4)/2,top+(heightOnly?74:126),(span-4)/2,20).build());
        setInitialFocus(fields[heightOnly?1:0]);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xF010171D);super.render(g,mx,my,delta);
        int left=Math.max(8,(width-300)/2),top=Math.max(28,(height-164)/2);
        g.drawCenteredString(font,title,width/2,top+4,0xFFFFFFFF);
        for(int i=0;i<3;i++)if(!heightOnly||i==1)g.drawString(font,new String[]{"X","Y","Z"}[i],left,top+30+(heightOnly?0:i*26),0xFFFFFFFF,false);
        g.drawString(font,font.plainSubstrByWidth(error,Math.min(300,width-16)),left,top+(heightOnly?56:108),0xFFFFAA66,false);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
}
