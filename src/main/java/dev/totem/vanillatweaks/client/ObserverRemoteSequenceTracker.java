package dev.totem.vanillatweaks.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Session-aware stale-packet gate shared by every semantic screen family.
 *
 * <p>Sequences are monotonic only within one target client process and semantic
 * family. A different target therefore starts a new stream, and every native
 * session announcement starts a new epoch even when it names the same target
 * (for example after that target reconnects).</p>
 */
final class ObserverRemoteSequenceTracker {
    private static final Map<String, StreamState> STREAMS = new HashMap<>();

    private ObserverRemoteSequenceTracker() {
    }

    static synchronized void beginSession() {
        STREAMS.clear();
    }

    static synchronized boolean accept(String familyId, UUID targetId, long sequence) {
        if (familyId == null || familyId.isBlank() || targetId == null) {
            return false;
        }

        StreamState previous = STREAMS.get(familyId);
        if (previous != null
                && previous.targetId().equals(targetId)
                && sequence <= previous.sequence()) {
            return false;
        }

        STREAMS.put(familyId, new StreamState(targetId, sequence));
        return true;
    }

    static synchronized long lastAccepted(String familyId) {
        StreamState state = STREAMS.get(familyId);
        return state == null ? -1L : state.sequence();
    }

    private record StreamState(UUID targetId, long sequence) {
    }
}
