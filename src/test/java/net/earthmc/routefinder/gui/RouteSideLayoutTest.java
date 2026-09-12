package net.earthmc.routefinder.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RouteSideLayoutTest {
    @Test void insertedRowsKeepBaseControlsAndSettingsSeparate(){
        for(int top:new int[]{8,45,120}){
            assertEquals(-1,RouteSideLayout.hit(20,top+5*23+10,top));
            assertEquals(0,RouteSideLayout.hit(20,top+6*23+10,top));
            assertEquals(1,RouteSideLayout.hit(20,top+7*23+10,top));
            assertEquals(-1,RouteSideLayout.hit(20,top+6*23+21,top));
            assertEquals(2,RouteSideLayout.hit(20,top+8*23+7,top));
            assertEquals(-1,RouteSideLayout.hit(20,top+9*23+7,top));
            assertEquals(-1,RouteSideLayout.hit(101,top+6*23+10,top));
        }
    }
}
