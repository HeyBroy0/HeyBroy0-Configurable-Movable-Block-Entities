package com.heybroy0.movableblockentities;

import net.frozenblock.lib.block.api.piston.PistonEvents;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Adapts the per-block configuration to FrozenLib's supported piston extension
 * points. FrozenLib remains responsible for block-entity capture/restoration
 * and its built-in chest/shelf connection behavior.
 */
public final class FrozenLibPistonIntegration {
    private FrozenLibPistonIntegration() {
    }

    public static void register() {
        PistonEvents.DETERMINE_BLOCK_ENTITY_PUSH_RESULT.register((state, direction) -> {
            return switch (PistonBehavior.movement(state)) {
                // FrozenLib then serializes the entity into the moving piston
                // and restores it at the destination.
                case MOVABLE -> PistonEvents.PushResult.SUCCESS;
                // Let vanilla/FrozenLib decide for all other modes. In
                // particular, BREAKABLE must retain vanilla destruction rules.
                case VANILLA, BREAKABLE, IMMOVABLE -> PistonEvents.PushResult.PASS;
            };
        });

        PistonEvents.DETERMINE_BLOCK_STICKINESS.register((state, direction) -> {
            return switch (PistonBehavior.adhesion(state)) {
                case ADHESIVE -> PistonEvents.StickyResult.SUCCESS;
                // FAIL is important: PASS would allow a later callback or
                // vanilla slime/honey behavior to make this block sticky.
                case GLAZED -> PistonEvents.StickyResult.FAIL;
                case VANILLA, HITBOX, ADVANCED -> PistonEvents.StickyResult.PASS;
            };
        });

        // FrozenLib fires this only after the moved state has been placed and
        // its saved block entity restored. Use that stable landing boundary
        // for tick restoration/group completion instead of guessing at vanilla
        // finalTick bytecode or wrapping Level.setBlock ourselves.
        PistonEvents.ON_MOVING_BLOCK_SET.register((level, position, state, movingEntity) ->
                MovementGroups.finish(level, position, movingEntity));

        PistonEvents.TRY_STICK_BLOCKS_TOGETHER.register((first, second, direction) -> {
            AdhesionPair pair = adhesionPair(first, second);
            if (pair.glazed()) {
                // A glazed block must not be pulled as a neighbor of slime,
                // honey, or a custom adhesive block.
                return PistonEvents.StickTogetherResult.FAIL;
            }
            if (pair.adhesive()) {
                return PistonEvents.StickTogetherResult.SUCCESS;
            }
            return PistonEvents.StickTogetherResult.PASS;
        });
    }

    private static AdhesionPair adhesionPair(BlockState first, BlockState second) {
        PistonBehavior.Adhesion firstMode = PistonBehavior.adhesion(first);
        PistonBehavior.Adhesion secondMode = PistonBehavior.adhesion(second);
        boolean glazed = firstMode == PistonBehavior.Adhesion.GLAZED
                || secondMode == PistonBehavior.Adhesion.GLAZED;
        boolean adhesive = firstMode == PistonBehavior.Adhesion.ADHESIVE
                || secondMode == PistonBehavior.Adhesion.ADHESIVE;
        return new AdhesionPair(glazed, adhesive);
    }

    private record AdhesionPair(boolean glazed, boolean adhesive) {
    }
}
