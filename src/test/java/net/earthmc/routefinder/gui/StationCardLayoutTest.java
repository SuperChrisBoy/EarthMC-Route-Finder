package net.earthmc.routefinder.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StationCardLayoutTest {
    @Test void popupFitsAboveBottomControlsAndButtonHitsMatchDrawing(){
        for(int width:new int[]{240,320,480,640,1280})for(int height:new int[]{240,360,480,720}){
            var box=StationCardLayout.of(width,height);
            assertTrue(box.x()>=8&&box.x()+box.width()<=width-8);
            assertTrue(box.y()>=8&&box.y()+box.height()<=height-64);
            for(int i=0;i<3;i++){
                var b=box.button(i);
                assertEquals(i,box.buttonAt(b.x()+1,b.y()+1));
                assertEquals(-1,box.buttonAt(b.x()+b.width(),b.y()+1));
                assertTrue(box.contains(b.x(),b.y()));
                assertTrue(b.x()+b.width()<=box.x()+box.width()-8);
            }
            assertFalse(box.contains(box.x()+10,height-25));
            assertEquals(-1,box.buttonAt(box.x()+10,box.y()+10));
        }
    }
}
