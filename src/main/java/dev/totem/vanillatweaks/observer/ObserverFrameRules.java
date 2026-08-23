package dev.totem.vanillatweaks.observer;

/** Shared bounds for the spectator UI frame relay. */
public final class ObserverFrameRules {
    public static final int MAX_WIDTH = 640;
    public static final int MAX_HEIGHT = 360;
    public static final int TARGET_FPS = 2;
    public static final int CHUNK_BYTES = 24 * 1024;
    public static final int MAX_FRAME_BYTES = 1024 * 1024;
    public static final int MAX_CHUNKS = (MAX_FRAME_BYTES + CHUNK_BYTES - 1) / CHUNK_BYTES;

    private ObserverFrameRules() {
    }

    public static int chunkCount(int frameBytes) {
        if (frameBytes <= 0 || frameBytes > MAX_FRAME_BYTES) {
            return 0;
        }
        return (frameBytes + CHUNK_BYTES - 1) / CHUNK_BYTES;
    }

    public static boolean validChunk(
            int chunkIndex,
            int chunkCount,
            int width,
            int height,
            int sourceWidth,
            int sourceHeight,
            int bytes
    ) {
        return chunkCount >= 1
                && chunkCount <= MAX_CHUNKS
                && chunkIndex >= 0
                && chunkIndex < chunkCount
                && width >= 1
                && width <= MAX_WIDTH
                && height >= 1
                && height <= MAX_HEIGHT
                && sourceWidth >= 1
                && sourceHeight >= 1
                && bytes >= 1
                && bytes <= CHUNK_BYTES;
    }
}
