package net.earthmc.routefinder.gui;
import net.earthmc.routefinder.iceeditor.EditorTool;
public record PlannerToolbarLayout(int left,int top,int columns,int count,int screenWidth) {
    public record Rect(int x,int y,int width,int height){
        public boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+height;}
    }
    public static PlannerToolbarLayout of(int width,int height){
        int left=134,count=EditorTool.values().length+1;
        int columns=Math.max(1,Math.min(count,(width-left-8)/68));
        int rows=(count+columns-1)/columns;
        return new PlannerToolbarLayout(left,height-6-rows*28,columns,count,width);
    }
    public static Rect draftButton(int width){
        int x=width>=850?276:134;
        return new Rect(x,9,Math.max(54,Math.min(150,width-204-x)),18);
    }
    public Rect button(int index){return new Rect(left+(index%columns)*68,top+4+(index/columns)*28,64,20);}
    public Rect snapMenu(){Rect b=button(count-1);return new Rect(Math.max(4,Math.min(screenWidth-154,b.x())),Math.max(4,top-104),146,100);}
    public int hit(double x,double y){for(int i=0;i<count;i++)if(button(i).contains(x,y))return i;return -1;}
}
