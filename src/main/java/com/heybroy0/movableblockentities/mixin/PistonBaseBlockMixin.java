package com.heybroy0.movableblockentities.mixin;

import com.heybroy0.movableblockentities.MovementGroups;
import com.heybroy0.movableblockentities.PistonBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockMixin {
    /**
     * Vanilla rejects block entities before FrozenLib can serialize their data.
     * Only explicitly MOVABLE blocks bypass that one vanilla veto; retain world
     * bounds and piston recursion guards. VANILLA and BREAKABLE still use the
     * complete vanilla eligibility test.
     */
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void movableblockentities$allowConfiguredMovableBlocks(
            BlockState state,
            Level level,
            BlockPos position,
            Direction movementDirection,
            boolean allowDestroyable,
            Direction pistonFacing,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (PistonBehavior.movement(state) != PistonBehavior.Movement.MOVABLE) {
            return;
        }

        BlockPos destination = position.relative(movementDirection);
        if (!PistonBehavior.isInsidePistonMovementBounds(level, position, destination)
                || state.is(Blocks.MOVING_PISTON)
                || state.getBlock() instanceof PistonBaseBlock) {
            callback.setReturnValue(false);
            return;
        }

        // FrozenLib's moving-block-entity mixin owns BE snapshot/restore.
        // Do not duplicate that NBT lifecycle in MovementGroups.
        callback.setReturnValue(true);
    }

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
