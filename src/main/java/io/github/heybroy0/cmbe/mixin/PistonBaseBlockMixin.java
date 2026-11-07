package io.github.heybroy0.cmbe.mixin;

import io.github.heybroy0.cmbe.config.BlockSettings;
import io.github.heybroy0.cmbe.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void cmbe$applyMovementMode(
        BlockState state,
        Level level,
        BlockPos pos,
        Direction direction,
        boolean allowDestroyable,
        Direction connectionDirection,
        CallbackInfoReturnable<Boolean> cir
    ) {
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        BlockSettings.MovementMode mode = ConfigManager.get(blockId).movement;

        if (mode == BlockSettings.MovementMode.IMMOVABLE) {
            cir.setReturnValue(false);
        } else if (mode == BlockSettings.MovementMode.MOVABLE) {
            // Do not allow a block entity to enter the vanilla moving-piston
            // path until its data-preserving capture/restore lifecycle is added.
            cir.setReturnValue(level.getBlockEntity(pos) == null);
        }
    }
}
