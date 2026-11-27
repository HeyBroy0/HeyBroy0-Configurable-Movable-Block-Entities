package com.heybroy0.movableblockentities.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;

/**
 * Resolves the other source positions that form one logical structure with a
 * block being pushed. Implementations must validate the actual relationship
 * (block identity, complementary part and relevant state), not proximity.
 */
@FunctionalInterface
public interface PistonMultiblockHandler {
    Collection<BlockPos> findCompanions(LevelReader level, BlockPos position, BlockState state);
}
