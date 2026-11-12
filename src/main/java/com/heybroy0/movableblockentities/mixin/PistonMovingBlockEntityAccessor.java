package com.heybroy0.movableblockentities.mixin;

import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PistonMovingBlockEntity.class)
public interface PistonMovingBlockEntityAccessor {
    @Invoker("finalTick")
    void movableblockentities$invokeFinalTick();
}
