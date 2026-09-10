package net.earthmc.routefinder.gui;

/** Shared popup geometry. Reserve the bottom strip for map search, favorites and zoom. */
public record StationCardLayout(int x,int y,int width,int height) {
    public record Rect(int x,int y,int width,int height) {
        public boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+height;}
    }
    public static StationCardLayout of(int screenWidth,int screenHeight){
        int width=Math.min(300,Math.max(1,screenWidth-16));
        return new StationCardLayout(Math.max(8,screenWidth-width-12),Math.max(8,screenHeight-64-92),width,92);
    }
    public boolean contains(double px,double py){return new Rect(x,y,width,height).contains(px,py);}
    public Rect button(int index){
        int cell=(width-16)/3;
        return new Rect(x+8+index*cell,y+57,cell-4,21);
    }
    public int buttonAt(double px,double py){
        for(int i=0;i<3;i++)if(button(i).contains(px,py))return i;
        return -1;
    }
}
