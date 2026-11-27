package com.heybroy0.movableblockentities.mixin;

import com.heybroy0.movableblockentities.MovementGroups;
import com.heybroy0.movableblockentities.PistonBehavior;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockMixin {
    /**
     * Vanilla rejects block entities before FrozenLib can serialize their data.
     * Only explicitly MOVABLE blocks bypass that one vanilla veto; retain
     * world-border/build-height and piston-recursion protections.
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
        if (state.isAir()
                || state.is(Blocks.MOVING_PISTON)
                || state.is(Blocks.PISTON_HEAD)
                || state.getBlock() instanceof PistonBaseBlock
                || !PistonBehavior.isInsidePistonMovementBounds(level, position, destination)) {
            callback.setReturnValue(false);
            return;
        }

        // FrozenLib owns block-entity serialization and restoration.
        callback.setReturnValue(true);
    }

    /**
     * FrozenLib protects tagged block entities from removal side effects while
     * replacing their source block with a moving piston. Configured movable
     * block entities must receive the same protection even when they are not
     * in FrozenLib's static tag. This prevents state-specific callbacks (for
     * example SculkShriekerBlockEntity.preRemoveSideEffects) from being passed
     * minecraft:moving_piston instead of the original block state.
     *
     * The flag is additive and only applies to the piston transaction's source
     * replacement. It is intentionally not used for ordinary world updates.
     */
    @WrapOperation(
            method = "moveBlocks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Ljava/util/Map;keySet()Ljava/util/Set;"
                    )
            )
    )
    private boolean movableblockentities$preserveBlockEntityDuringSourceReplacement(
            Level level,
            BlockPos position,
            BlockState replacement,
            int updateFlags,
            Operation<Boolean> original
    ) {
        BlockState sourceState = level.getBlockState(position);
        if (sourceState.hasBlockEntity()
                && PistonBehavior.movement(sourceState) == PistonBehavior.Movement.MOVABLE) {
            updateFlags |= PistonBaseBlock.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
        }
        return original.call(level, position, replacement, updateFlags);
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
