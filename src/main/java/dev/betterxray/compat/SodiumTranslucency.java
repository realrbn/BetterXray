package dev.betterxray.compat;

/**
 * Sodium 0.5 (Minecraft 1.20.1) stores the ambient-occlusion shade in the vertex alpha and its
 * chunk shader multiplies it into the colour, so vertex alpha cannot carry transparency. For the
 * translucent pass Better X-Ray patches the shader to use vertex alpha as real alpha and moves
 * the shade into the colour here; X-Rayed blocks additionally get the configured opacity.
 */
public final class SodiumTranslucency {
    private static final ThreadLocal<float[]> OPACITY = ThreadLocal.withInitial(() -> new float[]{1f});
    /** Define added to the translucent-pass chunk program. */
    public static final String DEFINE = "BETTERXRAY_VERTEX_ALPHA";
    public static final String SHADE_LINE = "diffuseColor.rgb *= v_Color.a;";
    public static final String PATCHED_SHADE = "\n#ifdef " + DEFINE + "\n    diffuseColor.a *= v_Color.a;\n#else\n    "
        + SHADE_LINE + "\n#endif\n";

    private SodiumTranslucency() {}

    public static void begin(float opacity) { OPACITY.get()[0] = opacity; }
    public static void end() { OPACITY.get()[0] = 1f; }

    public static float opacity() { return OPACITY.get()[0]; }

    /** Converts an ABGR colour with the shade in alpha to a shaded colour with the given alpha. */
    public static int convert(int abgr, float opacity) {
        int shade = abgr >>> 24;
        int r = (abgr & 0xff) * shade / 255;
        int g = ((abgr >>> 8) & 0xff) * shade / 255;
        int b = ((abgr >>> 16) & 0xff) * shade / 255;
        int a = Math.round(255 * opacity);
        return a << 24 | b << 16 | g << 8 | r;
    }
}
