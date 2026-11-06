package io.github.heybroy0.cmbe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.heybroy0.cmbe.adhesion.AdhesionAnalysisCache;
import io.github.heybroy0.cmbe.config.BlockConfig;
import io.github.heybroy0.cmbe.config.ConfigStore;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureAdhesionMixin {
    @Inject(method = "isSticky", at = @At("HEAD"), cancellable = true)
    private static void cmbe$overrideStickiness(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        BlockConfig config = ConfigStore.get(state);
        switch (config.adhesion) {
            case ADHESIVE -> cir.setReturnValue(true);
            case GLAZED -> cir.setReturnValue(false);
            case HITBOX -> cir.setReturnValue(AdhesionAnalysisCache.hasAnyHitboxFace(state, config));
            case ADVANCED -> cir.setReturnValue(hasAnyManualFace(state, config));
            case VANILLA -> { }
        }
    }

    @WrapOperation(
        method = "addBranchingBlocks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;canStickToEachOther(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z"
        )
    )
    private static boolean cmbe$checkContactFaces(
        BlockState first,
        BlockState second,
        Operation<Boolean> original,
        @Local(ordinal = 0) Direction direction
    ) {
        BlockConfig firstConfig = ConfigStore.get(first);
        BlockConfig secondConfig = ConfigStore.get(second);

        // Preserve every existing vanilla/modded sticky rule when neither block is overridden.
        if (firstConfig.adhesion == BlockConfig.Adhesion.VANILLA
            && secondConfig.adhesion == BlockConfig.Adhesion.VANILLA) {
            return original.call(first, second);
        }

        // Vanilla's explicit incompatible pair remains incompatible under custom adhesion.
        if ((first.is(Blocks.SLIME_BLOCK) && second.is(Blocks.HONEY_BLOCK))
            || (first.is(Blocks.HONEY_BLOCK) && second.is(Blocks.SLIME_BLOCK))) {
            return false;
        }
        return (isStickySurface(first, firstConfig) && faceEnabled(first, firstConfig, direction))
            || (isStickySurface(second, secondConfig) && faceEnabled(second, secondConfig, direction.getOpposite()));
    }

    private static boolean isStickySurface(BlockState state, BlockConfig config) {
        return switch (config.adhesion) {
            case VANILLA -> state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK);
            case ADHESIVE, HITBOX, ADVANCED -> true;
            case GLAZED -> false;
        };
    }

    private static boolean faceEnabled(BlockState state, BlockConfig config, Direction face) {
        return switch (config.adhesion) {
            case VANILLA -> true;
            case ADHESIVE -> true;
            case GLAZED -> false;
            case HITBOX -> AdhesionAnalysisCache.contactSquare(state, face, config) >= config.hitboxMinimumSquare;
            case ADVANCED -> config.manualFaceEnabled(face, state);
        };
    }

    private static boolean hasAnyManualFace(BlockState state, BlockConfig config) {
        for (Direction face : Direction.values()) {
            if (config.manualFaceEnabled(face, state)) return true;
        }
        return false;
    }
}
