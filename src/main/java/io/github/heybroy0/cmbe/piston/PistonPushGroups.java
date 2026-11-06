package io.github.heybroy0.cmbe.piston;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A short-lived projection for the members of one piston movement group.
 *
 * Only queries made synchronously while a group member is landing receive the
 * projected view. Unrelated world queries outside that placement call see the
 * real world state.
 */
public final class PistonPushGroups {
    private static final ThreadLocal<Deque<Builder>> BUILDERS =
        ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Group> ACTIVE = new ThreadLocal<>();
    private static final Map<BlockPos, Group> BY_DESTINATION = new ConcurrentHashMap<>();

    private PistonPushGroups() {}

    public static void beginOperation() {
        BUILDERS.get().push(new Builder());
    }

    public static void record(BlockPos source, BlockPos destination, BlockState movedState) {
        Deque<Builder> stack = BUILDERS.get();
        if (stack.isEmpty()) return;
        Builder builder = stack.peek();
        builder.sources.add(source.immutable());
        builder.destinations.put(destination.immutable(), movedState);
    }

    public static void finishOperation() {
        Deque<Builder> stack = BUILDERS.get();
        if (stack.isEmpty()) return;

        Builder finished = stack.pop();
        if (!stack.isEmpty()) {
            stack.peek().merge(finished);
            return;
        }
        BUILDERS.remove();

        if (finished.destinations.isEmpty()) return;
        Group group = new Group(finished.destinations, finished.sources);
        for (BlockPos destination : finished.destinations.keySet()) {
            BY_DESTINATION.put(destination, group);
        }
    }

    public static BlockState projectedState(BlockPos position) {
        Group group = ACTIVE.get();
        if (group == null) return null;

        BlockState destinationState = group.destinations.get(position);
        if (destinationState != null) return destinationState;
        if (group.sources.contains(position)) return Blocks.AIR.defaultBlockState();
        return null;
    }

    public static <T> T duringLanding(BlockPos destination, Supplier<T> action) {
        Group group = BY_DESTINATION.get(destination);
        if (group == null) return action.get();

        Group previous = ACTIVE.get();
        ACTIVE.set(group);
        try {
            return action.get();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    /**
     * Immediately completes a moved vanilla multiblock companion if that member
     * is still animating. The companion must belong to this exact push group.
     */
    public static void fastLandMultiblockCompanion(Level level, BlockPos landedPosition) {
        Group group = ACTIVE.get();
        if (group == null) return;

        BlockState landedState = group.destinations.get(landedPosition);
        if (landedState == null) return;

        BlockPos companion = MultiblockCompanions.findCompanion(landedPosition, landedState);
        if (companion == null || !group.destinations.containsKey(companion)) return;

        BlockEntity entity = level.getBlockEntity(companion);
        if (entity instanceof PistonMovingBlockEntity moving) {
            moving.finalTick();
        }
    }

    public static void memberLanded(BlockPos destination) {
        Group group = BY_DESTINATION.remove(destination);
        if (group == null) return;
        if (group.remaining.decrementAndGet() <= 0) {
            for (BlockPos member : group.destinations.keySet()) {
                BY_DESTINATION.remove(member, group);
            }
        }
    }

    private static final class Builder {
        private final Map<BlockPos, BlockState> destinations = new LinkedHashMap<>();
        private final Set<BlockPos> sources = new LinkedHashSet<>();

        private void merge(Builder other) {
            destinations.putAll(other.destinations);
            sources.addAll(other.sources);
        }
    }

    private static final class Group {
        private final Map<BlockPos, BlockState> destinations;
        private final Set<BlockPos> sources;
        private final AtomicInteger remaining;

        private Group(Map<BlockPos, BlockState> destinations, Set<BlockPos> sources) {
            this.destinations = Map.copyOf(new HashMap<>(destinations));
            this.sources = Set.copyOf(new HashSet<>(sources));
            this.remaining = new AtomicInteger(this.destinations.size());
        }
    }
}
