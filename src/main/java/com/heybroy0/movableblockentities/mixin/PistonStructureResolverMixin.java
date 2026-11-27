package com.heybroy0.movableblockentities.mixin;

import com.heybroy0.movableblockentities.MovementGroups;
import com.heybroy0.movableblockentities.PistonBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Keeps only resolver responsibilities that FrozenLib does not expose as
 * extension events. Stickiness and block-entity eligibility are delegated to
 * FrozenLib's PistonEvents API.
 */
@Mixin(PistonStructureResolver.class)
abstract class PistonStructureResolverMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private List<BlockPos> toPush;
    @Shadow @Final private Direction pushDirection;

    @ModifyConstant(
            method = {"resolve", "addBlockLine", "addBranchingBlocks"},
            constant = @Constant(intValue = 12),
            require = 0
    )
    private int movableblockentities$extendResolverWalk(int vanillaLimit) {
        return Math.max(vanillaLimit, PistonBehavior.resolverLimit());
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
