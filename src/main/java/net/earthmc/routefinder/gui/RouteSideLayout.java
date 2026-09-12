package net.earthmc.routefinder.gui;

/** Companion rows inserted after Counter and before Settings in the base column. */
public final class RouteSideLayout {
    public static final int EXTRA_HEIGHT=69;
    public static int rowY(int top,int row){return top+(6+row)*23;}
    public static int hit(double x,double y,int top){
        if(x<8||x>=100)return -1;
        for(int row=0;row<3;row++)if(y>=rowY(top,row)&&y<rowY(top,row)+20)return row;
        return -1;
    }
}
