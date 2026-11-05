package dev.betterxray.render;

//? if >=1.21.11 {
import net.minecraft.world.level.block.state.BlockState;

/** Scoped only to existing block entity submissions, never entities or item previews. */
public final class BlockEntityContext {
    public static final ThreadLocal<BlockState> CURRENT = new ThreadLocal<>();
    public static boolean faint() {
        BlockState state = CURRENT.get();
        return state != null && XRayState.enabled() && !XRayState.selected(state);
    }
    public static boolean selected() {
        BlockState state = CURRENT.get();
        return state != null && XRayState.selected(state);
    }
}
//?}
