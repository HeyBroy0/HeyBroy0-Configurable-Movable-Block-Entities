package com.heybroy0.movableblockentities.mixin;

import com.heybroy0.movableblockentities.MovementGroups;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BlockEntity owns setLevel; attach piston movement tracking at that common
 * lifecycle point rather than targeting an inherited method on the subclass.
 */
@Mixin(BlockEntity.class)
abstract class BlockEntityLevelMixin {
    @Inject(method = "setLevel", at = @At("TAIL"))
    private void movableblockentities$registerPistonMovement(Level level, CallbackInfo callback) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (self instanceof PistonMovingBlockEntity) {
            MovementGroups.register(level, self.getBlockPos());
        }
    }
}
