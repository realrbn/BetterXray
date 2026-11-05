package dev.betterxray.render;

/** Marks the worker thread that is currently meshing a chunk section (pre-1.21.11 renderers). */
public final class ChunkMeshing {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    private ChunkMeshing() {}
    public static void begin() { DEPTH.get()[0]++; }
    public static void end() { int[] depth = DEPTH.get(); if (depth[0] > 0) depth[0]--; }
    public static boolean active() { return DEPTH.get()[0] > 0; }
}
