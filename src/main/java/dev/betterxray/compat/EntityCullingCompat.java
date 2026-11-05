package dev.betterxray.compat;

import dev.betterxray.XRayClient;
import dev.betterxray.render.XRayState;
import java.lang.reflect.Method;
import java.util.function.Function;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Entity Culling (tr7zw) hides entities and block entities that its own ray casts find behind
 * full blocks. It does not know the X-Ray view, so chests and mobs behind see-through terrain
 * vanished, and ESP targets behind walls lost their outline or box even with X-Ray off.
 *
 * <p>While the X-Ray view is on, the mod's public dynamic whitelists exempt every entity and block
 * entity from its cull pass. A whitelisted object is skipped by that pass but keeps its last culled
 * flag, so the whitelist also clears it ({@code Cullable.setCulled(false)}, which additionally
 * keeps the object visible for one second).
 *
 * <p>ESP targets are cleared on the render thread right before Entity Culling checks them
 * ({@link #keepVisible}, from the ESP visibility and extraction hooks), so a target is drawn in the same frame its
 * ESP is switched on and no live configuration is read on the cull thread. As long as the target
 * is extracted every frame it stays within that one-second window.
 *
 * <p>Everything goes through reflection: Entity Culling is never a dependency, and an incompatible
 * release only logs a warning.
 */
public final class EntityCullingCompat {
    private static final String MOD_ID = "entityculling";
    private static volatile boolean registered;
    private static boolean failed;
    private static Method setCulled;

    private EntityCullingCompat() {}

    /** Called at client start and then every tick until the registration succeeded. */
    public static void initialize() {
        if (registered || failed || !Platform.isModLoaded(MOD_ID)) return;
        try {
            Class<?> base = Class.forName("dev.tr7zw.entityculling.EntityCullingModBase");
            Object instance = base.getField("instance").get(null);
            if (instance == null) return; // not initialized yet; retried on the next tick
            setCulled = Class.forName("dev.tr7zw.entityculling.access.Cullable").getMethod("setCulled", boolean.class);
            Function<Entity, Boolean> entities = EntityCullingCompat::xrayExempt;
            Function<BlockEntity, Boolean> blockEntities = EntityCullingCompat::xrayExempt;
            base.getMethod("addDynamicEntityWhitelist", Function.class).invoke(instance, entities);
            base.getMethod("addDynamicBlockEntityWhitelist", Function.class).invoke(instance, blockEntities);
            registered = true;
            XRayClient.LOGGER.info("[XRay] Entity Culling {} found; X-Ray view and ESP targets are exempt from its culling",
                Platform.modVersion(MOD_ID));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            failed = true;
            XRayClient.LOGGER.warn("[XRay] Cannot use the Entity Culling {} API; it may hide ESP targets and blocks behind X-Ray terrain",
                Platform.modVersion(MOD_ID), e);
        }
    }

    /** Called every client tick (only retries a registration that found no Entity Culling instance yet). */
    public static void tick() { if (!registered) initialize(); }

    /** True once the whitelists are registered: Entity Culling is installed and usable. */
    public static boolean active() { return registered; }

    /**
     * Render thread, before Entity Culling's check of this entity: ESP targets ({@code esp}) and,
     * while the X-Ray view is on, all entities are never culled.
     */
    public static void keepVisible(Entity entity, boolean esp) {
        if (registered && (esp || XRayState.enabled())) uncull(entity);
    }

    /** Cull thread: everything is exempt while the X-Ray view is on. */
    private static Boolean xrayExempt(Object target) {
        if (!XRayState.enabled()) return false;
        uncull(target);
        return true;
    }

    private static void uncull(Object target) {
        try { setCulled.invoke(target, false); }
        catch (ReflectiveOperationException | RuntimeException ignored) {}
    }
}
