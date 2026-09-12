package net.earthmc.routefinder.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OverlayViewportTest {
    @Test void laptopLayoutsLeaveMapSpaceAndKeepAllButtonsInsideWindow(){
        for(int[] size:new int[][]{{456,257},{640,360},{853,486},{1280,720},{1920,1080}}){
            int w=size[0],h=size[1];
            var p=OverlayViewport.planner(w,h);
            assertEquals(0,p.anchor());
            assertEquals(0,p.x(0));
            assertTrue((190+230)*p.scale()<w*0.5);
            var bar=PlannerToolbarLayout.of(p.width(),p.height());
            for(int i=0;i<bar.count();i++){
                var r=bar.button(i);
                double x=p.anchor()+(r.x()+r.width()/2.0-p.anchor())*p.scale(),y=(r.y()+10)*p.scale();
                assertTrue(x<w&&y<h);
                assertEquals(i,bar.hit(p.x(x),p.y(y)));
            }
            var v=OverlayViewport.viewer(w,h);
            assertTrue(360*v.scale()<=w*0.32);
            for(boolean advanced:new boolean[]{false,true}){
                int rows=v.viewerRows(advanced),top=advanced?154:136;
                assertTrue(rows>=1&&rows<=4);
                assertTrue((top+rows*96+53)*v.scale()<h*0.85);
            }
        }
    }
    @Test void exactCoordinatesPreserveFractionsAndRejectMissingOrNonFiniteValues(){
        var v=CoordinatesScreen.XYZ.parse("-120.25","-32"," 456.75 ");
        assertEquals(-120.25,v.x());assertEquals(-32,v.y());assertEquals(456.75,v.z());
        for(String bad:new String[]{""," ","NaN","Infinity","hello"})
            assertThrows(NumberFormatException.class,()->CoordinatesScreen.XYZ.parse("1",bad,"2"));
    }
}
