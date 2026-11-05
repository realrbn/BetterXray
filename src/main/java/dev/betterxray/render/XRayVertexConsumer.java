package dev.betterxray.render;

//? if <1.21.11 || (neoforge && <26.1) {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
//? if >=1.21.11 {
import net.minecraft.util.ARGB;
//?}

/^*
 * Chunk-mesh vertex filter for the vanilla block renderer (Fabric before 1.21.11, NeoForge):
 * deselected geometry gets the configured alpha, selected geometry full light and at least
 * 80 % directional brightness. For Cave-Find ({@link #cave}) the decision is made per quad:
 * faces toward underground air are written opaque and fully lit into the block's own layer.
 ^/
public final class XRayVertexConsumer implements VertexConsumer {
    private final boolean selected;
    private final float opacity;
    /^* Cave-Find: buffer of the block's own layer and the faint (translucent) buffer, null = drop. ^/
    private final VertexConsumer caveBuffer;
    private final VertexConsumer faintBuffer;
    private final BlockAndTintGetter level;
    private final BlockPos pos;
    /^* Where the current quad goes; whether it is written opaque with full light. ^/
    private VertexConsumer delegate;
    private boolean lit;

    public XRayVertexConsumer(VertexConsumer delegate, boolean selected) {
        this(delegate, selected, null, null, null, null);
    }

    private XRayVertexConsumer(VertexConsumer delegate, boolean selected, VertexConsumer caveBuffer,
                               VertexConsumer faintBuffer, BlockAndTintGetter level, BlockPos pos) {
        this.delegate = delegate;
        this.selected = selected;
        this.lit = selected;
        this.opacity = XRayState.get().opacity();
        this.caveBuffer = caveBuffer;
        this.faintBuffer = faintBuffer;
        this.level = level;
        this.pos = pos;
    }

    /^*
     * Cave-Find for a deselected block: quads facing underground air go opaque and fully lit into
     * {@code caveBuffer} (the block's own layer), all others faint into {@code faintBuffer}, or are
     * dropped when it is null (renderers that pick one buffer per block, or 0 % opacity).
     ^/
    public static XRayVertexConsumer cave(VertexConsumer caveBuffer, VertexConsumer faintBuffer,
                                          BlockAndTintGetter level, BlockPos pos) {
        return new XRayVertexConsumer(faintBuffer, false, caveBuffer, faintBuffer, level, pos);
    }

    private int alpha(int a) { return lit ? 255 : Math.round(a * opacity); }

    private float[] brightness(float[] values) {
        if (!lit) return values;
        // Cave walls keep more of their shade so their shape stays readable.
        float min = selected ? 0.8f : 0.5f;
        float[] raised = values.clone();
        for (int i = 0; i < raised.length; i++) raised[i] = Math.max(min, raised[i]);
        return raised;
    }

    /^* Routes a Cave-Find quad; false drops it. ^/
    private boolean route(BakedQuad quad) {
        if (level == null) return true;
        //? if >=1.21.11 {
        Direction face = quad.direction();
        //?} else {
        /^Direction face = quad.getDirection();
        ^///?}
        lit = XRayState.caveFace(level, pos, face);
        delegate = lit ? caveBuffer : faintBuffer;
        return delegate != null;
    }

    //? if >=1.21 {
    @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, alpha(a)); return this; }
    @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
    @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
    @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(lit ? 240 : u, lit ? 240 : v); return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }

    //? if >=1.21.11 {
    // putBulkData writes each vertex through addVertex(...), which sets the packed color.
    @Override public VertexConsumer setColor(int argb) { delegate.setColor(ARGB.color(alpha(ARGB.alpha(argb)), argb)); return this; }
    @Override public VertexConsumer setLineWidth(float width) { delegate.setLineWidth(width); return this; }

    @Override public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b,
                                      float a, int[] lights, int overlay) {
        if (!route(quad)) return;
        VertexConsumer.super.putBulkData(pose, quad, brightness(brightness), r, g, b, a, lights, overlay);
    }
    //?} else {
    /^@Override public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b,
                                      float a, int[] lights, int overlay, boolean readColor) {
        if (!route(quad)) return;
        VertexConsumer.super.putBulkData(pose, quad, brightness(brightness), r, g, b, a, lights, overlay, readColor);
    }
    ^///?}
    //?} else {
    /^@Override public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
    @Override public VertexConsumer color(int r, int g, int b, int a) { delegate.color(r, g, b, alpha(a)); return this; }
    @Override public VertexConsumer uv(float u, float v) { delegate.uv(u, v); return this; }
    @Override public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
    @Override public VertexConsumer uv2(int u, int v) { delegate.uv2(lit ? 240 : u, lit ? 240 : v); return this; }
    @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
    @Override public void endVertex() { delegate.endVertex(); }
    @Override public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(r, g, b, alpha(a)); }
    @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }

    @Override public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b,
                                      int[] lights, int overlay, boolean readColor) {
        if (!route(quad)) return;
        VertexConsumer.super.putBulkData(pose, quad, brightness(brightness), r, g, b, lights, overlay, readColor);
    }
    ^///?}
}
*///?}
