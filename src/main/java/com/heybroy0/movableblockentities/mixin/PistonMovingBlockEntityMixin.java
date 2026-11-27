package com.heybroy0.movableblockentities.mixin;

import com.heybroy0.movableblockentities.MovementGroups;
import com.heybroy0.movableblockentities.api.PistonMovingBlockEntityInvoker;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The pre-landing hook is retained for door/bed coordination. Landing completion
 * and tick restoration use FrozenLib's ON_MOVING_BLOCK_SET event, which fires
 * after the state and saved block entity have actually been restored.
 */
@Mixin(PistonMovingBlockEntity.class)
abstract class PistonMovingBlockEntityMixin implements PistonMovingBlockEntityInvoker {
    @org.spongepowered.asm.mixin.gen.Invoker("finalTick")
    @Override
    public abstract void movableblockentities$invokeFinalTick();

    @Inject(method = "finalTick", at = @At("HEAD"))
    private void movableblockentities$coordinateMultiblockLanding(CallbackInfo callback) {
        PistonMovingBlockEntity self = (PistonMovingBlockEntity) (Object) this;
        MovementGroups.coordinateCompanion(self.getLevel(), self.getBlockPos(), self);
    }
}
