package net.earthmc.routefinder.storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AccessibilitySavesTest {
    @TempDir Path game;
    @Test void multiplePortableSnapshotsRetainTownAndNationReports() throws Exception {
        var saves=new AccessibilitySaves(game.resolve("EarthMC Accessibility Saves"));
        Map<String,String> reports=new HashMap<>(Map.of("TOWN_SPAWN:tokyo","OBSTRUCTED","NATION_SPAWN:japan","ACCESSIBLE"));
        Path first=saves.save("Travel 日本",reports);
        reports.put("TOWN_SPAWN:tokyo","ACCESSIBLE");
        Path second=saves.save("Event",reports);
        assertEquals("OBSTRUCTED",saves.load(first).reports().get("TOWN_SPAWN:tokyo"));
        assertEquals("ACCESSIBLE",saves.load(second).reports().get("TOWN_SPAWN:tokyo"));
        assertEquals(2,saves.list().size());
        assertThrows(java.io.IOException.class,()->saves.save("Event",Map.of()));
        assertEquals(2,saves.load(second).reports().size());
        Path empty=saves.save("Unknown",Map.of());
        assertTrue(saves.load(empty).reports().isEmpty());
    }
    @Test void overwriteRequiresExplicitRequestAndFailedValidationPreservesOriginal() throws Exception {
        var saves=new AccessibilitySaves(game.resolve("saves"));
        Path file=saves.save("Trip",Map.of("TOWN_SPAWN:town","OBSTRUCTED"));
        String original=Files.readString(file);
        assertThrows(FileAlreadyExistsException.class,()->saves.save("Trip",Map.of()));
        assertEquals(original,Files.readString(file));
        assertThrows(java.io.IOException.class,()->saves.save("Trip",Map.of("invalid","ACCESSIBLE"),true));
        assertEquals(original,Files.readString(file));
        assertEquals(file,saves.save("Trip",Map.of("NATION_SPAWN:nation","ACCESSIBLE"),true));
        assertEquals(Map.of("NATION_SPAWN:nation","ACCESSIBLE"),saves.load(file).reports());
        assertEquals(1,saves.list().size());
    }
    @Test void rejectsUnsafeNamesAndInvalidFilesWithoutChangingOtherSaves() throws Exception {
        var saves=new AccessibilitySaves(game.resolve("saves"));
        for(String name:List.of("../escape","..","a/b","a\\b","bad:name",""))assertThrows(java.io.IOException.class,()->saves.save(name,Map.of()));
        Path valid=saves.save("Valid",Map.of("TOWN_SPAWN:town","ACCESSIBLE"));
        Path bad=saves.folder().resolve("bad.json");
        Files.writeString(bad,"{broken");
        assertThrows(java.io.IOException.class,()->saves.load(bad));
        assertTrue(saves.list().stream().anyMatch(e->!e.error().isEmpty()));
        Files.writeString(bad,"{\"version\":1,\"name\":\"Bad\",\"reports\":{\"TOWN_SPAWN:town\":\"INVALID\"}}");
        assertThrows(java.io.IOException.class,()->saves.load(bad));
        assertEquals("ACCESSIBLE",saves.load(valid).reports().get("TOWN_SPAWN:town"));
        assertThrows(java.io.IOException.class,()->saves.load(game.resolve("outside.json")));
    }
    @Test void plannerMigrationCopiesOldFilesAndPreservesBothExistingAndOriginalData() throws Exception {
        Path old=game.resolve("earthmcroutefinder/ice-highway-planner");
        Files.createDirectories(old.resolve("drafts"));Files.createDirectories(old.resolve("json"));
        Files.writeString(old.resolve("drafts/ice-highway-drafts.json"),"old draft");
        Files.writeString(old.resolve("json/route.json"),"old export");
        Path target=game.resolve("EarthMC Ice Road Planner");
        Files.createDirectories(target);Files.writeString(target.resolve("ice-highway-drafts.json"),"new draft");
        assertEquals(target.toAbsolutePath(),SaveFolders.preparePlanner(game));
        assertEquals("new draft",Files.readString(target.resolve("ice-highway-drafts.json")));
        assertEquals("old export",Files.readString(target.resolve("json/route.json")));
        assertEquals("old draft",Files.readString(old.resolve("drafts/ice-highway-drafts.json")));
        Files.writeString(target.resolve("json/route.json"),"edited");
        SaveFolders.preparePlanner(game);
        assertEquals("edited",Files.readString(target.resolve("json/route.json")));
    }
    @Test void plannerDraftLibraryMovesToTopLevelOfNewFolder() throws Exception {
        Path old=game.resolve("earthmcroutefinder/ice-highway-planner/drafts");
        Files.createDirectories(old);Files.writeString(old.resolve("ice-highway-drafts.json"),"draft");
        Path target=SaveFolders.preparePlanner(game);
        assertEquals("draft",Files.readString(target.resolve("ice-highway-drafts.json")));
    }
}
