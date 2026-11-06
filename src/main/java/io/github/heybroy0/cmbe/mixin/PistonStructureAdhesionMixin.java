package io.github.heybroy0.cmbe.mixin;

import io.github.heybroy0.cmbe.config.BlockConfig;
import io.github.heybroy0.cmbe.config.ConfigStore;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureAdhesionMixin {
    @Inject(method = "isSticky", at = @At("HEAD"), cancellable = true)
    private static void cmbe$adhesionMode(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        BlockConfig.Adhesion adhesion = ConfigStore.get(state).adhesion;
        if (adhesion == BlockConfig.Adhesion.ALL) cir.setReturnValue(true);
        else if (adhesion == BlockConfig.Adhesion.NONE) cir.setReturnValue(false);
        // HITBOX/ADVANCED deliberately preserve vanilla behavior until their analysis UI ships.
    }
}
