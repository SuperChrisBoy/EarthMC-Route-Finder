package net.earthmc.routefinder.api;

import net.earthmc.routefinder.model.VotePartyStatus;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Client-thread polling, independent of worlds and server connections. */
public final class VotePartyTracker {
    public static final long REFRESH_MS = 30_000;
    public static final long STALE_MS = 90_000;
    private final Supplier<CompletableFuture<VotePartyStatus>> fetch;
    private CompletableFuture<VotePartyStatus> pending;
    private VotePartyStatus status;
    private long nextFetch;
    private boolean failed;

    public VotePartyTracker(Supplier<CompletableFuture<VotePartyStatus>> fetch) { this.fetch = fetch; }

    public void tick(boolean enabled, long now) {
        if (pending != null && pending.isDone()) {
            try {
                VotePartyStatus result = pending.join();
                failed = result == null;
                if (result != null) status = result;
            } catch (RuntimeException e) { failed = true; }
            pending = null;
            nextFetch = now + REFRESH_MS;
        }
        if (!enabled || pending != null || now < nextFetch) return;
        try { pending = fetch.get(); }
        catch (RuntimeException e) { failed = true; nextFetch = now + REFRESH_MS; }
    }

    public VotePartyStatus status() { return status; }
    public boolean failed() { return failed; }
    public boolean stale(long now) { return status != null && (failed || now - status.fetchedAtMs() >= STALE_MS); }
}
