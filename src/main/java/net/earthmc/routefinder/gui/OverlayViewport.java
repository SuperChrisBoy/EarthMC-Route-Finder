package net.earthmc.routefinder.gui;

/** One transform for overlay drawing and hit testing; map coordinates stay untouched. */
public record OverlayViewport(float scale,int anchor,int width,int height) {
    public static OverlayViewport planner(int w,int h){
        float s=(float)Math.min(1,Math.min(Math.max(1,w)/950.0,h/600.0));
        return new OverlayViewport(s,0,(int)(w/s),(int)(h/s));
    }
    public static OverlayViewport viewer(int w,int h){
        float s=(float)Math.min(1,Math.min(w/1150.0,h/650.0));
        return new OverlayViewport(s,126,126+(int)((w-126)/s),(int)(h/s));
    }
    public double x(double physical){return anchor+(physical-anchor)/scale;}
    public double y(double physical){return physical/scale;}
    public int viewerRows(boolean advanced){
        return Math.max(1,Math.min(4,((int)(height*0.82)-(advanced?154:136)-53)/96));
    }
}
