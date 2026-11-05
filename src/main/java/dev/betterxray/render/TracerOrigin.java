package dev.betterxray.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Start point for tracers: the world position that appears at the screen center a little in
 * front of the camera. It is unprojected from the frame's own matrices, so hurt tilt, view
 * bobbing and nausea (all part of the projection) are taken into account automatically.
 */
public final class TracerOrigin {
    private TracerOrigin() {}

    /**
     * @param eye          camera position
     * @param projection   projection matrix of the level pass (including view effects)
     * @param viewRotation camera rotation matrix (no translation)
     */
    public static Vec3 compute(Vec3 eye, Matrix4f projection, Matrix4f viewRotation) {
        Matrix4f inverse = new Matrix4f(projection).mul(viewRotation).invert();
        Vector4f near = inverse.transform(new Vector4f(0f, 0f, -1f, 1f));
        Vector4f far = inverse.transform(new Vector4f(0f, 0f, 1f, 1f));
        Vector3f nearPoint = new Vector3f(near.x / near.w, near.y / near.w, near.z / near.w);
        Vector3f farPoint = new Vector3f(far.x / far.w, far.y / far.w, far.z / far.w);
        Vector3f direction = farPoint.sub(nearPoint, new Vector3f());
        if (direction.lengthSquared() < 1.0e-12f || !direction.isFinite()) return eye;
        // 0.25 blocks along the view ray from the near plane, like the gizmo start used on 26.x.
        Vector3f start = nearPoint.add(direction.normalize().mul(0.25f));
        return eye.add(start.x, start.y, start.z);
    }
}
