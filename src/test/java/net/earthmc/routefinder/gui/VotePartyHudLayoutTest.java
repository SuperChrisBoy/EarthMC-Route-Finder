package net.earthmc.routefinder.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VotePartyHudLayoutTest {
    @Test void scalesWithViewportAndCapsLargeDisplays() {
        assertEquals(.65f * .6f, VotePartyHudLayout.of(320, 180, 180, 0).scale());
        assertEquals(.6f, VotePartyHudLayout.of(640, 360, 180, 0).scale());
        assertEquals(.75f, VotePartyHudLayout.of(1920, 1080, 180, 0).scale());
        assertEquals(8f, VotePartyHudLayout.of(640, 360, 180, 0).y());
    }

    @Test void remainsCenteredAndInsideSmallWideTallAndHighResolutionWindows() {
        for (int[] size : new int[][]{{120, 90}, {320, 180}, {426, 240}, {640, 360}, {1920, 1080}, {2560, 720}, {360, 960}, {3840, 2160}}) {
            for (int width : new int[]{180, 240, 600}) for (int bosses : new int[]{0, 1, 3, 100}) for (int sizePercent : new int[]{30, 60, 100, 150}) {
                var layout = VotePartyHudLayout.of(size[0], size[1], width, bosses, sizePercent);
                assertTrue(layout.scale() > 0);
                assertEquals(size[0] / 2f, layout.x() + width * layout.scale() / 2, .001);
                assertTrue(layout.x() >= 7.999);
                assertTrue(layout.x() + width * layout.scale() <= size[0] - 7.999);
                assertTrue(layout.y() + VotePartyHudLayout.HEIGHT * layout.scale() <= size[1] - 7.999);
                // Independently simulate vanilla's bar loop and verify clearance below the last bar.
                int barY = 12, bottom = 0;
                for (int i = 0; i < bosses; i++) {
                    bottom = barY + 5;
                    barY += 19;
                    if (barY >= size[1] / 3) break;
                }
                assertTrue(layout.y() >= bottom + 6);
            }
        }
    }

    @Test void reactsToBossesAppearingAndDisappearing() {
        assertEquals(23f, VotePartyHudLayout.of(640, 360, 180, 1).y());
        assertEquals(61f, VotePartyHudLayout.of(640, 360, 180, 3).y());
        assertEquals(8f, VotePartyHudLayout.of(640, 360, 180, 0).y());
    }
}
