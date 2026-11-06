package io.github.heybroy0.cmbe.mixin;

import io.github.heybroy0.cmbe.piston.MultiblockCompanions;
import io.github.heybroy0.cmbe.piston.PushCost;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private Direction pushDirection;
    @Shadow @Final private List<BlockPos> toPush;
    @Shadow @Final private List<BlockPos> toDestroy;

    @Invoker("addBlockLine")
    protected abstract boolean cmbe$invokeAddBlockLine(BlockPos pos, Direction direction);

    @ModifyConstant(
        method = {"addBlockLine", "addBranchingBlocks"},
        constant = @Constant(intValue = 12),
        require = 0
    )
    private int cmbe$raiseBoundedResolverLimit(int original) {
        return PushCost.MAX_PHYSICAL_BLOCKS;
    }

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void cmbe$includeCompleteVanillaStructures(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        // addBlockLine is vanilla's complete collision / pushability resolver. Run it
        // for each missing companion, and iterate again if it adds more group members.
        for (int index = 0; index < toPush.size(); index++) {
            BlockPos member = toPush.get(index);
            BlockState state = level.getBlockState(member);
            BlockPos companion = MultiblockCompanions.findCompanion(member, state);
            if (companion == null) continue;

            if (toDestroy.contains(companion)) {
                cir.setReturnValue(false);
                return;
            }

            if (!toPush.contains(companion) && !cmbe$invokeAddBlockLine(companion, pushDirection)) {
                cir.setReturnValue(false);
                return;
            }

            if (toDestroy.contains(companion) || !toPush.contains(companion)) {
                cir.setReturnValue(false);
                return;
            }
        }

        if (toPush.size() > PushCost.MAX_PHYSICAL_BLOCKS
            || !PushCost.isWithinBudget(level, toPush)) {
            cir.setReturnValue(false);
        }
    }
}
