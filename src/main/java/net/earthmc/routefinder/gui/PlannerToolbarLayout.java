package net.earthmc.routefinder.gui;
import net.earthmc.routefinder.iceeditor.EditorTool;
public record PlannerToolbarLayout(int left,int top,int columns,int count,int screenWidth) {
    public record Rect(int x,int y,int width,int height){
        public boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+height;}
    }
    public static PlannerToolbarLayout of(int width,int height){
        int left=8,count=EditorTool.values().length+1;
        return new PlannerToolbarLayout(left,height-34,3,count,width);
    }
    public static Rect draftButton(int width){
        int x=width>=850?150:8;
        return new Rect(x,9,Math.max(54,Math.min(150,width-204-x)),18);
    }
    public Rect button(int index){return new Rect(left+(index%columns)*58,128+(index/columns)*28,54,24);}
    public Rect snapMenu(){return new Rect(left+186,128,146,100);}
    public int hit(double x,double y){for(int i=0;i<count;i++)if(button(i).contains(x,y))return i;return -1;}
}
