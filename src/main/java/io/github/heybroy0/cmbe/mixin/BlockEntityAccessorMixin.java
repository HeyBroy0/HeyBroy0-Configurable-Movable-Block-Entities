package io.github.heybroy0.cmbe.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockEntity.class)
public abstract class BlockEntityAccessorMixin implements BlockEntityAccessor {
    @Shadow @Final @Mutable protected BlockPos worldPosition;

    @Override
    public void cmbe$setWorldPosition(BlockPos position) {
        this.worldPosition = position;
    }
}
