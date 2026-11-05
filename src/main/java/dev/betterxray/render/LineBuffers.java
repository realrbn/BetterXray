package dev.betterxray.render;

//? if <1.21.11 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/^* Line and quad vertices for the always-on-top render types (pre-1.21.11 vertex API). ^/
public final class LineBuffers {
    private LineBuffers() {}

    /^* The twelve edges of {@code box}; each edge's normal is its direction. ^/
    public static void box(VertexConsumer out, PoseStack.Pose pose, AABB box, float r, float g, float b, float a) {
        float x0 = (float)box.minX, y0 = (float)box.minY, z0 = (float)box.minZ;
        float x1 = (float)box.maxX, y1 = (float)box.maxY, z1 = (float)box.maxZ;
        for (float y : new float[]{y0, y1}) for (float z : new float[]{z0, z1}) edge(out, pose, x0, y, z, x1, y, z, r, g, b, a);
        for (float x : new float[]{x0, x1}) for (float z : new float[]{z0, z1}) edge(out, pose, x, y0, z, x, y1, z, r, g, b, a);
        for (float x : new float[]{x0, x1}) for (float y : new float[]{y0, y1}) edge(out, pose, x, y, z0, x, y, z1, r, g, b, a);
    }

    public static void line(VertexConsumer out, PoseStack.Pose pose, Vec3 from, Vec3 to, float r, float g, float b, float a) {
        edge(out, pose, (float)from.x, (float)from.y, (float)from.z, (float)to.x, (float)to.y, (float)to.z, r, g, b, a);
    }

    public static void edge(VertexConsumer out, PoseStack.Pose pose, float ax, float ay, float az,
                            float bx, float by, float bz, float r, float g, float b, float a) {
        Vector3f normal = new Vector3f(bx - ax, by - ay, bz - az);
        if (normal.lengthSquared() > 0) normal.normalize(); else normal.set(0, 1, 0);
        //? if >=1.21 {
        out.addVertex(pose, ax, ay, az).setColor(r, g, b, a).setNormal(pose, normal.x, normal.y, normal.z);
        out.addVertex(pose, bx, by, bz).setColor(r, g, b, a).setNormal(pose, normal.x, normal.y, normal.z);
        //?} else {
        /^out.vertex(pose.pose(), ax, ay, az).color(r, g, b, a).normal(pose.normal(), normal.x, normal.y, normal.z).endVertex();
        out.vertex(pose.pose(), bx, by, bz).color(r, g, b, a).normal(pose.normal(), normal.x, normal.y, normal.z).endVertex();
        ^///?}
    }

    /^* Axis-aligned quad in the matrix's xy plane (POSITION_COLOR). ^/
    public static void quad(VertexConsumer out, Matrix4f matrix, float x0, float y0, float x1, float y1, int argb) {
        //? if >=1.21 {
        out.addVertex(matrix, x0, y0, 0).setColor(argb);
        out.addVertex(matrix, x1, y0, 0).setColor(argb);
        out.addVertex(matrix, x1, y1, 0).setColor(argb);
        out.addVertex(matrix, x0, y1, 0).setColor(argb);
        //?} else {
        /^out.vertex(matrix, x0, y0, 0).color(argb).endVertex();
        out.vertex(matrix, x1, y0, 0).color(argb).endVertex();
        out.vertex(matrix, x1, y1, 0).color(argb).endVertex();
        out.vertex(matrix, x0, y1, 0).color(argb).endVertex();
        ^///?}
    }
}
*///?}
