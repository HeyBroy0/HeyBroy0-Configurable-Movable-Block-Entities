package com.heybroy0.movableblockentities.mixin;

import com.heybroy0.movableblockentities.MovementGroups;
import com.heybroy0.movableblockentities.PistonBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockMixin {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private static void movableblockentities$applyMovementSetting(
            BlockState state,
            Level level,
            BlockPos position,
            Direction movementDirection,
            boolean allowDestroyable,
            Direction pistonFacing,
            CallbackInfoReturnable<Boolean> callback
    ) {
        PistonBehavior.Movement movement = PistonBehavior.movement(state);
        if (movement == PistonBehavior.Movement.VANILLA
                || movement == PistonBehavior.Movement.IMMOVABLE) {
            if (movement == PistonBehavior.Movement.IMMOVABLE) {
                callback.setReturnValue(false);
            }
            return;
        }

        BlockPos destination = position.relative(movementDirection);
        if (!PistonBehavior.isInsidePistonMovementBounds(level, position, destination)) {
            callback.setReturnValue(false);
            return;
        }

        // A moving placeholder cannot itself be nested inside another piston move.
        if (state.is(net.minecraft.world.level.block.Blocks.MOVING_PISTON)
                || state.getBlock() instanceof PistonBaseBlock) {
            callback.setReturnValue(false);
            return;
        }

        // BREAKABLE uses POPPED and the resolver's destroy list. MOVABLE bypasses
        // vanilla's immovable reaction and block-entity veto, but never world bounds.
        callback.setReturnValue(true);
    }

    @Inject(method = "moveBlocks", at = @At("HEAD"))
    private void movableblockentities$beginMovement(
            Level level,
            BlockPos pistonPosition,
            Direction direction,
            boolean extending,
            CallbackInfoReturnable<Boolean> callback
    ) {
        MovementGroups.begin(level);
    }

    @Inject(method = "moveBlocks", at = @At("RETURN"))
    private void movableblockentities$endMovement(
            Level level,
            BlockPos pistonPosition,
            Direction direction,
            boolean extending,
            CallbackInfoReturnable<Boolean> callback
    ) {
        MovementGroups.end(level);
    }
}
