package dev.betterxray.render;

//? if <1.21.11 {
/*import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import java.util.OptionalDouble;

/^*
 * Always-on-top render types for highlight boxes, tracers, ESP boxes and label quads (the
 * pre-1.21.11 counterpart of always-on-top gizmos): no depth test, no depth writes, translucent
 * blending, vanilla shaders.
 ^/
public final class XRayRenderTypes {
    private XRayRenderTypes() {}

    /^* Highlight boxes, 2.5 px like the gizmo stroke on newer versions. ^/
    public static final RenderType HIGHLIGHT_BOXES = Types.lines("betterxray_highlight_boxes", 2.5);
    /^* Tracers, 1.5 px like the gizmo line on newer versions. ^/
    public static final RenderType TRACERS = Types.lines("betterxray_tracers", 1.5);
    /^* ESP boxes, same stroke as highlight boxes. ^/
    public static final RenderType ESP_BOXES = Types.lines("betterxray_esp_boxes", 2.5);
    /^* Filled quads for player label backgrounds and health bars. ^/
    public static final RenderType LABEL_QUADS = Types.quads("betterxray_label_quads");

    /^* Subclass only to reach the protected render state shards and builders. ^/
    private abstract static class Types extends RenderType {
        private Types() { super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {}); }

        static RenderType lines(String name, double width) {
            return create(name, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
                CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(width)))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .createCompositeState(false));
        }

        static RenderType quads(String name) {
            return create(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
                CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .createCompositeState(false));
        }
    }
}
*///?}
