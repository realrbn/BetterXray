package dev.betterxray.mixin;

//? if >=1.21.11 {
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(BlockEntityRenderState.class)
public interface BlockEntityStateAccessor { @Accessor("blockState") BlockState xray$state(); }
//?}
