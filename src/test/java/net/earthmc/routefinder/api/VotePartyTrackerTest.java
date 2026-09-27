package net.earthmc.routefinder.api;

import net.earthmc.routefinder.model.VotePartyStatus;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class VotePartyTrackerTest {
    @Test void countsAndReset() {
        var status = VotePartyStatus.parse("{\"voteParty\":{\"target\":2000,\"numRemaining\":500}}", 10);
        assertNotNull(status);
        assertEquals(75, status.percent());
        assertEquals(1500, status.completed());
        assertEquals(500, status.remaining());
        assertEquals(99, new VotePartyStatus(2000, 1, 0).percent());
        assertEquals(100, new VotePartyStatus(2000, 0, 0).percent());
        assertEquals(0, new VotePartyStatus(2000, 2000, 0).percent());
    }

    @Test void invalidResponsesDoNotInventProgress() {
        for (String json : new String[]{"null", "{}", "bad", "{\"voteParty\":null}",
            "{\"voteParty\":{\"target\":0,\"numRemaining\":0}}",
            "{\"voteParty\":{\"target\":100,\"numRemaining\":101}}",
            "{\"voteParty\":{\"target\":100,\"numRemaining\":-1}}",
            "{\"voteParty\":{\"target\":100,\"numRemaining\":1.5}}",
            "{\"voteParty\":{\"target\":100}}"}) assertNull(VotePartyStatus.parse(json, 0));
    }

    @Test void disabledAndInFlightDoNotStartExtraRequests() {
        AtomicInteger calls = new AtomicInteger();
        var request = new CompletableFuture<VotePartyStatus>();
        var tracker = new VotePartyTracker(() -> { calls.incrementAndGet(); return request; });
        tracker.tick(false, 0);
        assertEquals(0, calls.get());
        tracker.tick(true, 0);
        tracker.tick(true, 100_000);
        assertEquals(1, calls.get());
        request.complete(new VotePartyStatus(100, 25, 100_000));
        tracker.tick(true, 100_000);
        assertEquals(75, tracker.status().percent());
        tracker.tick(true, 129_999);
        assertEquals(1, calls.get());
        tracker.tick(false, 130_000);
        assertEquals(1, calls.get());
        tracker.tick(true, 130_000);
        assertEquals(2, calls.get());
    }

    @Test void failureKeepsStaleSnapshotAndRecoveryAcceptsPartyReset() {
        AtomicInteger calls = new AtomicInteger();
        var tracker = new VotePartyTracker(() -> switch(calls.getAndIncrement()) {
            case 0 -> CompletableFuture.completedFuture(new VotePartyStatus(100, 1, 0));
            case 1 -> CompletableFuture.failedFuture(new IllegalStateException("offline"));
            default -> CompletableFuture.completedFuture(new VotePartyStatus(100, 100, 60_002));
        });
        tracker.tick(true, 0); tracker.tick(true, 1);
        assertFalse(tracker.stale(1));
        assertTrue(tracker.stale(90_000));
        tracker.tick(true, 30_001); tracker.tick(true, 30_002);
        assertTrue(tracker.failed());
        assertTrue(tracker.stale(30_002));
        assertEquals(1, tracker.status().remaining());
        tracker.tick(true, 60_002); tracker.tick(true, 60_003);
        assertFalse(tracker.failed());
        assertFalse(tracker.stale(60_003));
        assertEquals(0, tracker.status().percent());
    }
}
