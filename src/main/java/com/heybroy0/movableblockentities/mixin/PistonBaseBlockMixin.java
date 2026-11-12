package com.heybroy0.movableblockentities.mixin;

import com.heybroy0.movableblockentities.MovementGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockMixin {
    @Inject(method = "moveBlocks", at = @At("HEAD"))
    private void movableblockentities$beginMovement(
            Level level, BlockPos pistonPosition, Direction direction, boolean extending,
            CallbackInfoReturnable<Boolean> callback
    ) {
        MovementGroups.begin(level);
    }

    @Inject(method = "moveBlocks", at = @At("RETURN"))
    private void movableblockentities$endMovement(
            Level level, BlockPos pistonPosition, Direction direction, boolean extending,
            CallbackInfoReturnable<Boolean> callback
    ) {
        MovementGroups.end(level);
    }
}
