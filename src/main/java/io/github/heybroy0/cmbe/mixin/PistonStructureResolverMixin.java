package io.github.heybroy0.cmbe.mixin;

import io.github.heybroy0.cmbe.piston.MultiblockCompanions;
import io.github.heybroy0.cmbe.piston.PushCost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private Direction pushDirection;
    @Shadow @Final private List<BlockPos> toPush;
    @Shadow @Final private List<BlockPos> toDestroy;

    @ModifyConstant(
        method = {"addBlockLine", "addBranchingBlocks"},
        constant = @Constant(intValue = 12),
        require = 0
    )
    private int cmbe$extendedBoundedDepth(int original) {
        return PushCost.MAX_PHYSICAL_BLOCKS;
    }

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void cmbe$validateExpandedPushGroup(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        if (!MultiblockCompanions.includeVanillaCompanions(level, toPush, toDestroy, pushDirection)) {
            cir.setReturnValue(false);
            return;
        }

        if (!PushCost.isWithinBudget(level, toPush)) {
            cir.setReturnValue(false);
        }
    }
}
