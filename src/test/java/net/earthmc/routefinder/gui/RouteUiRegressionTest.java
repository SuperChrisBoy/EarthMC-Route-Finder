package net.earthmc.routefinder.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import com.google.gson.*;
class RouteUiRegressionTest {
    @Test void toolbarWrapsAndEveryButtonRemainsClickable(){
        for(int width:new int[]{400,480,640,854,1280}){
            var layout=PlannerToolbarLayout.of(width,480);
            var draft=PlannerToolbarLayout.draftButton(width);
            assertTrue(draft.x()+draft.width()<=width-196);
            for(int i=0;i<layout.count();i++){
                var b=layout.button(i);assertTrue(b.x()+b.width()<=width-8);
                assertEquals(i,layout.hit(b.x()+2,b.y()+2));
                for(int j=i+1;j<layout.count();j++){var c=layout.button(j);assertFalse(b.x()<c.x()+c.width()&&b.x()+b.width()>c.x()&&b.y()<c.y()+c.height()&&b.y()+b.height()>c.y());}
            }
            assertTrue(layout.snapMenu().y()+layout.snapMenu().height()<=layout.top());
        }
    }
    @Test void allLiteralUiTranslationKeysHaveEnglishStrings() throws Exception {
        var lang=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/earthmcroutefinder/lang/en_us.json"))).getAsJsonObject();
        Pattern pattern=Pattern.compile("\"(earthmcroutefinder\\.[a-zA-Z0-9_.]+)\"");
        try(var paths=Files.walk(Path.of("src/main/java/net/earthmc/routefinder/gui"))){
            for(Path path:paths.filter(p->p.toString().endsWith(".java")).toList()){
                var match=pattern.matcher(Files.readString(path));
                while(match.find()){String key=match.group(1);if(!key.endsWith("."))assertTrue(lang.has(key),key+" in "+path);}
            }
        }
        for(String key:List.of("grid","vertex","segment","station","chunk_center"))assertTrue(lang.has("earthmcroutefinder.planner.snap."+key));
        assertEquals("Refresh",lang.get("earthmcroutefinder.common.refresh").getAsString());
    }
}
