package dev.betterxray.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Experimental faint terrain under {@code separateAo} shader packs. Plain
 * string transforms of the GLSL Iris generates for Sodium terrain; no Iris types, so it is testable
 * on its own.
 *
 * <p>Faint quads carry a flag and their opacity in the otherwise unused upper bits of Iris' raw
 * {@code mc_Entity} attribute ({@link #faintBits}). The vertex shader masks those bits out of the
 * expressions Iris derives the block ID from, so the pack keeps seeing the real ID, and forwards the
 * opacity; the fragment shader multiplies the alpha of every colour output by it after the pack's
 * own {@code main} ran. Vertices without the flag get 1.0, so the patch changes nothing else.
 */
public final class IrisShaderPatch {
    static final String MARKER = "// betterxray: faint opacity patch";
    /** Raw mc_Entity = ((blockId + 1) << 1) | renderType; bits 22..29 opacity, bit 30 flag. */
    private static final int OPACITY_SHIFT = 22, FLAG = 1 << 30;
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void)?\\s*\\)");
    private static final Pattern VEC4_OUT = Pattern.compile("(?m)^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+vec4\\s+(\\w+)\\s*;");
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*#version[^\\n]*\\n");

    public static int faintBits(float opacity) {
        int value = Math.max(0, Math.min(255, Math.round(opacity * 255f)));
        return FLAG | (value << OPACITY_SHIFT);
    }

    /** Both stages patched, or null when the program does not fit (then it stays unchanged). */
    public static String[] patch(String vertex, String fragment) {
        if (vertex == null || fragment == null || vertex.contains(MARKER) || fragment.contains(MARKER)) return null;
        if (!MAIN.matcher(vertex).find() || !MAIN.matcher(fragment).find()) return null;
        List<String> outputs = new ArrayList<>();
        Matcher out = VEC4_OUT.matcher(fragment);
        while (out.find()) outputs.add(out.group(1));
        if (outputs.isEmpty()) return null;

        String vs = vertex.replace("mc_Entity >> 1u", "(mc_Entity & 0x3FFFFFu) >> 1u");
        if (!Pattern.compile("\\bin\\s+uint\\s+mc_Entity\\s*;").matcher(vs).find()) {
            Matcher version = VERSION.matcher(vs);
            if (!version.find()) return null;
            vs = vs.substring(0, version.end()) + "in uint mc_Entity;\n" + vs.substring(version.end());
        }
        vs = MAIN.matcher(vs).replaceFirst("void betterxray_main()") + "\n" + MARKER + "\n"
            + "flat out float betterxray_opacity;\n"
            + "void main() {\n"
            + "    betterxray_main();\n"
            + "    uint betterxray_bits = mc_Entity >> " + OPACITY_SHIFT + "u;\n"
            + "    betterxray_opacity = (betterxray_bits & 0x100u) != 0u ? float(betterxray_bits & 0xFFu) / 255.0 : 1.0;\n"
            + "}\n";

        StringBuilder fs = new StringBuilder(MAIN.matcher(fragment).replaceFirst("void betterxray_main()"));
        fs.append('\n').append(MARKER).append('\n')
            .append("flat in float betterxray_opacity;\n")
            .append("void main() {\n")
            .append("    betterxray_main();\n");
        for (String name : outputs) fs.append("    ").append(name).append(".a *= betterxray_opacity;\n");
        fs.append("}\n");
        return new String[] {vs, fs.toString()};
    }

    private IrisShaderPatch() {}
}
