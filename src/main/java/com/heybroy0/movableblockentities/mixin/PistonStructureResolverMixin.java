package com.heybroy0.movableblockentities.mixin;

import com.heybroy0.movableblockentities.MovementGroups;
import com.heybroy0.movableblockentities.PistonBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonStructureResolver.class)
abstract class PistonStructureResolverMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private List<BlockPos> toPush;
    @Shadow @Final private Direction pushDirection;
    @Shadow @Final private boolean extending;
    @Shadow @Final private BlockPos startPos;

    @Redirect(
            method = {"resolve", "addBlockLine", "addBranchingBlocks"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;isSticky(Lnet/minecraft/world/level/block/state/BlockState;)Z"
            ),
            require = 0
    )
    private static boolean movableblockentities$customStickiness(BlockState state) {
        return PistonBehavior.isSticky(state);
    }

    @Redirect(
            method = "addBranchingBlocks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;canStickToEachOther(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z"
            ),
            require = 0
    )
    private static boolean movableblockentities$customFaceAdhesion(BlockState first, BlockState second) {
        return PistonBehavior.canStickToEachOther(first, second);
    }

    @ModifyConstant(
            method = {"resolve", "addBlockLine", "addBranchingBlocks"},
            constant = @Constant(intValue = 12),
            require = 0
    )
    private int movableblockentities$extendResolverWalk(int vanillaLimit) {
        return Math.max(vanillaLimit, PistonBehavior.resolverLimit());
    }

    @Inject(method = "resolve", at = @At("HEAD"), cancellable = true)
    private void movableblockentities$rejectGlazedStickyPull(CallbackInfoReturnable<Boolean> callback) {
        if (!this.extending
                && PistonBehavior.adhesion(this.level.getBlockState(this.startPos))
                == PistonBehavior.Adhesion.GLAZED) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void movableblockentities$checkWeightedPushCost(CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ()) return;
        if (!PistonBehavior.withinPushCapacity(this.level, this.toPush)) {
            callback.setReturnValue(false);
            return;
        }
        MovementGroups.captureResolved(this.level, this.toPush, this.pushDirection);
    }
}
