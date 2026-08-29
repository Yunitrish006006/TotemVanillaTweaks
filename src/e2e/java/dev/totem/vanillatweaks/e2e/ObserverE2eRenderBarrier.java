package dev.totem.vanillatweaks.e2e;

import java.util.HashMap;
import java.util.Map;

/**
 * Deterministic screenshot barrier for Observer E2E families.
 *
 * <p>The real Screen can be initialized and have semantic state applied during
 * the same client tick. A framebuffer capture in that tick still contains the
 * preceding render. Record the first observed production-Screen frame and wait
 * for a strictly newer extraction instead of masking the race with a sleep.</p>
 */
final class ObserverE2eRenderBarrier {
    private static final Map<String, Long> BASELINES = new HashMap<>();

    private ObserverE2eRenderBarrier() { }

    static boolean passed(String family, long extractedFrames) {
        Long baseline = BASELINES.putIfAbsent(family, extractedFrames);
        return baseline != null && extractedFrames > baseline;
    }
}
