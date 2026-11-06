package io.github.heybroy0.cmbe.mixin;

import io.github.heybroy0.cmbe.config.BlockConfig;
import io.github.heybroy0.cmbe.config.ConfigStore;
import net.minecraft.world.level.block.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Inject(method = "getPistonPushReaction", at = @At("HEAD"), cancellable = true)
    private void cmbe$overridePistonReaction(CallbackInfoReturnable<PushReaction> cir) {
        BlockState state = (BlockState) (Object) this;
        BlockConfig.Movement movement = ConfigStore.get(state).movement;
        switch (movement) {
            case MOVABLE -> cir.setReturnValue(PushReaction.PUSH_PULL);
            case BREAKABLE -> cir.setReturnValue(PushReaction.POPPED);
            case IMMOVABLE -> cir.setReturnValue(PushReaction.IMMOVEABLE);
            case VANILLA -> { }
        }
    }
}
