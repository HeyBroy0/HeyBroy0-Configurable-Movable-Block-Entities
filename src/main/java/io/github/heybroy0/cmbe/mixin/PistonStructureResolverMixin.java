package io.github.heybroy0.cmbe.mixin;

import io.github.heybroy0.cmbe.config.BlockSettings;
import io.github.heybroy0.cmbe.config.ConfigManager;
import io.github.heybroy0.cmbe.movement.PushCostCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private List<BlockPos> toPush;
    @Shadow @Final private List<BlockPos> toDestroy;

    @Inject(method = "addBlockLine", at = @At("HEAD"), cancellable = true)
    private void cmbe$breakConfiguredBlocks(
        BlockPos start,
        Direction direction,
        CallbackInfoReturnable<Boolean> cir
    ) {
        BlockState state = level.getBlockState(start);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (ConfigManager.get(blockId).movement == BlockSettings.MovementMode.BREAKABLE) {
            if (!state.isAir() && !toDestroy.contains(start)) {
                toDestroy.add(start);
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void cmbe$enforceCustomPushCost(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && !PushCostCalculator.isWithinVanillaCapacity(level, toPush)) {
            cir.setReturnValue(false);
        }
    }
}
