package io.github.heybroy0.cmbe.mixin;

import io.github.heybroy0.cmbe.piston.PistonPushGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void cmbe$useGroupDestinationState(
        BlockPos pos,
        CallbackInfoReturnable<BlockState> cir
    ) {
        BlockState projected = PistonPushGroups.projectedState(pos);
        if (projected != null) cir.setReturnValue(projected);
    }
}
