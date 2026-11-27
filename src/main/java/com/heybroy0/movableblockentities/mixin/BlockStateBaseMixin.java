package com.heybroy0.movableblockentities.mixin;

import com.heybroy0.movableblockentities.MovementGroups;
import com.heybroy0.movableblockentities.PistonBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin {
    @Shadow @Final private PushReaction pushReaction;

    @Inject(method = "getPistonPushReaction", at = @At("HEAD"), cancellable = true)
    private void movableblockentities$overridePushReaction(
            CallbackInfoReturnable<PushReaction> callback
    ) {
        BlockState state = (BlockState) (Object) this;
        PushReaction replacement = PistonBehavior.pushReaction(state, this.pushReaction);
        if (replacement != this.pushReaction) {
            callback.setReturnValue(replacement);
        }
    }

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void movableblockentities$preserveMovingGroup(
            LevelReader level, BlockPos position, CallbackInfoReturnable<Boolean> callback
    ) {
        if (level instanceof Level world && MovementGroups.isMoving(world, position)) {
            callback.setReturnValue(true);
        }
    }
}
