package com.heybroy0.movableblockentities.mixin;

import com.heybroy0.movableblockentities.MovementGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PistonMovingBlockEntity.class)
abstract class PistonMovingBlockEntityMixin {
    @Inject(
            method = "<init>(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;ZZ)V",
            at = @At("TAIL")
    )
    private void movableblockentities$captureSourceData(
            BlockPos position,
            BlockState movingState,
            BlockState movedState,
            Direction movementDirection,
            boolean extending,
            boolean sourcePiston,
            CallbackInfo callback
    ) {
        MovementGroups.captureFromMovingEntity(position, movedState, movementDirection);
    }

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void movableblockentities$registerMovementGroup(Level level, CallbackInfo callback) {
        BlockEntity self = (BlockEntity) (Object) this;
        MovementGroups.register(level, self.getBlockPos());
    }

    @Inject(method = "finalTick", at = @At("HEAD"))
    private void movableblockentities$coordinateMultiblockLanding(CallbackInfo callback) {
        PistonMovingBlockEntity self = (PistonMovingBlockEntity) (Object) this;
        MovementGroups.coordinateCompanion(self.getLevel(), self.getBlockPos(), self);
    }

    @Inject(method = "finalTick", at = @At("TAIL"))
    private void movableblockentities$finishFinalLanding(CallbackInfo callback) {
        PistonMovingBlockEntity self = (PistonMovingBlockEntity) (Object) this;
        MovementGroups.finish(self.getLevel(), self.getBlockPos(), self);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private static void movableblockentities$finishTickLanding(
            Level level,
            BlockPos position,
            BlockState movingState,
            PistonMovingBlockEntity entity,
            CallbackInfo callback
    ) {
        if (!level.isClientSide() && !level.getBlockState(position).is(Blocks.MOVING_PISTON)) {
            MovementGroups.finish(level, position, entity);
        }
    }
}
