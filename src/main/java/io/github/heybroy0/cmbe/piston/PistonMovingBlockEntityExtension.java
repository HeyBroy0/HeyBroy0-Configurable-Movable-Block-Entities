package io.github.heybroy0.cmbe.piston;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public interface PistonMovingBlockEntityExtension {
    void cmbe$setCarriedBlockEntity(@Nullable BlockEntity blockEntity);
    @Nullable BlockEntity cmbe$takeCarriedBlockEntity();
}
