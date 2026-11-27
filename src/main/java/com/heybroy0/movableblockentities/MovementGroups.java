package com.heybroy0.movableblockentities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickContainerAccess;
import com.heybroy0.movableblockentities.api.PistonMovingBlockEntityInvoker;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks independent piston transactions through movement and landing.
 * The group registry is scoped by Level and stores no permanent world data.
 */
public final class MovementGroups {
    private static final ThreadLocal<Deque<Operation>> OPERATIONS =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Set<PositionKey>> COORDINATION_GUARD =
            ThreadLocal.withInitial(HashSet::new);
    private static final Map<Level, Map<BlockPos, Group>> MOVING = new WeakHashMap<>();

    private MovementGroups() {
    }

    public static void begin(Level level) {
        OPERATIONS.get().push(new Operation(level, new Group()));
    }

    public static void end(Level level) {
        Deque<Operation> stack = OPERATIONS.get();
        if (!stack.isEmpty()) {
            Operation operation = stack.pop();
            if (operation.level == level) {
                operation.group.snapshots.keySet().retainAll(operation.group.positions);
                if (operation.group.positions.isEmpty()) {
                    operation.group.snapshots.clear();
                }
            } else {
                stack.clear();
            }
        }
        if (stack.isEmpty()) {
            OPERATIONS.remove();
        }
    }

    /**
     * Snapshot scheduled ticks while the resolver still exposes the original source
     * blocks. FrozenLib is the sole owner of block-entity serialization and restoration.
     */
    public static void captureResolved(Level level, List<BlockPos> sources, Direction movementDirection) {
        Deque<Operation> stack = OPERATIONS.get();
        if (stack.isEmpty() || level == null || level.isClientSide()) return;
        Operation operation = stack.peek();
        if (operation.level != level) return;

        for (BlockPos sourcePosition : sources) {
            BlockPos source = sourcePosition.immutable();
            BlockState movedState = level.getBlockState(source);
            if (movedState.isAir()) continue;

            BlockPos destination = source.relative(movementDirection).immutable();
            String tickMode = PistonBehavior.settings(movedState).scheduledTicks();
            List<ScheduledTick<Block>> scheduledTicks = List.of();
            if ("TRANSFER".equals(tickMode) || "DUPLICATE".equals(tickMode)) {
                LevelChunkTicks<Block> sourceTicks = blockTicks(level, source);
                if (sourceTicks != null) {
                    scheduledTicks = sourceTicks.getAll()
                            .filter(tick -> tick.pos().equals(source) && tick.type() == movedState.getBlock())
                            .toList();
                }
            }

            operation.group.positions.add(destination);
            if (!scheduledTicks.isEmpty()) {
                operation.group.snapshots.put(destination,
                        new MoveSnapshot(source, movedState.getBlock(), scheduledTicks, tickMode));
            }
        }
    }

    /** Called as the piston moving block entity is attached to its position. */
    public static void register(Level level, BlockPos position) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockPos immutable = position.immutable();
        Deque<Operation> stack = OPERATIONS.get();
        Group group = !stack.isEmpty() && stack.peek().level == level
                ? stack.peek().group : new Group();
        group.positions.add(immutable);
        synchronized (MOVING) {
            MOVING.computeIfAbsent(level, ignored -> new HashMap<>()).put(immutable, group);
        }
    }

    public static boolean isMoving(Level level, BlockPos position) {
        synchronized (MOVING) {
            Map<BlockPos, Group> world = MOVING.get(level);
            return world != null && world.containsKey(position);
        }
    }

    private static boolean isSameGroup(Level level, BlockPos first, BlockPos second) {
        synchronized (MOVING) {
            Map<BlockPos, Group> world = MOVING.get(level);
            return world != null && world.get(first) != null && world.get(first) == world.get(second);
        }
    }

    /**
     * Finishes a door/bed companion before the first component finishes ordinary
     * landing notifications. An invoker avoids a direct dependency on finalTick visibility.
     */
    public static void coordinateCompanion(
            Level level, BlockPos position, PistonMovingBlockEntity entity
    ) {
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockState movedState = entity.getMovedState();
        BlockPos companionPosition = companionPosition(position, movedState);
        if (companionPosition == null || !isSameGroup(level, position, companionPosition)) {
            return;
        }

        BlockEntity candidate = level.getBlockEntity(companionPosition);
        if (!(candidate instanceof PistonMovingBlockEntity companion)
                || !isMatchingCompanion(movedState, companion.getMovedState())) {
            return;
        }

        PositionKey currentKey = new PositionKey(level, position.immutable());
        PositionKey companionKey = new PositionKey(level, companionPosition.immutable());
        Set<PositionKey> guard = COORDINATION_GUARD.get();
        if (guard.contains(currentKey) || guard.contains(companionKey)) {
            return;
        }

        guard.add(currentKey);
        guard.add(companionKey);
        try {
            ((PistonMovingBlockEntityInvoker) (Object) companion).movableblockentities$invokeFinalTick();
        } finally {
            guard.remove(currentKey);
            guard.remove(companionKey);
            if (guard.isEmpty()) {
                COORDINATION_GUARD.remove();
            }
        }
    }

    private static BlockPos companionPosition(BlockPos position, BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                    ? position.below() : position.above();
        }
        if (state.getBlock() instanceof AbstractBedBlock) {
            BedPart part = state.getValue(AbstractBedBlock.PART);
            Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
            return part == BedPart.HEAD
                    ? position.relative(facing.getOpposite())
                    : position.relative(facing);
        }
        return null;
    }

    private static boolean isMatchingCompanion(BlockState first, BlockState second) {
        if (first.getBlock() != second.getBlock()) {
            return false;
        }
        if (first.getBlock() instanceof DoorBlock) {
            return first.getValue(DoorBlock.HALF) != second.getValue(DoorBlock.HALF);
        }
        if (first.getBlock() instanceof AbstractBedBlock) {
            return first.getValue(AbstractBedBlock.PART) != second.getValue(AbstractBedBlock.PART)
                    && first.getValue(HorizontalDirectionalBlock.FACING)
                    == second.getValue(HorizontalDirectionalBlock.FACING);
        }
        return false;
    }

    /** Called after vanilla has written the final state. The operation is idempotent. */
    public static void finish(Level level, BlockPos position, PistonMovingBlockEntity entity) {
        if (level == null || level.isClientSide()) {
            return;
        }

        BlockPos immutable = position.immutable();
        Group group;
        MoveSnapshot snapshot;
        synchronized (MOVING) {
            Map<BlockPos, Group> world = MOVING.get(level);
            if (world == null) {
                return;
            }
            group = world.remove(immutable);
            if (group == null) {
                return;
            }
            group.positions.remove(immutable);
            snapshot = group.snapshots.remove(immutable);
            if (world.isEmpty()) {
                MOVING.remove(level);
            }
            if (group.positions.isEmpty()) {
                group.snapshots.clear();
            }
        }

        if (snapshot == null || !level.getBlockState(immutable).is(snapshot.expectedBlock)) {
            return;
        }

        restoreScheduledTicks(level, immutable, snapshot);
    }

    private static void restoreScheduledTicks(Level level, BlockPos destination, MoveSnapshot snapshot) {
        if (snapshot.scheduledTicks.isEmpty()
                || !("TRANSFER".equals(snapshot.tickMode) || "DUPLICATE".equals(snapshot.tickMode))) {
            return;
        }

        LevelChunkTicks<Block> destinationTicks = blockTicks(level, destination);
        if (destinationTicks == null) {
            return;
        }

        for (ScheduledTick<Block> tick : snapshot.scheduledTicks) {
            destinationTicks.schedule(new ScheduledTick<>(
                    tick.type(), destination, tick.triggerTick(), tick.priority(), tick.subTickOrder()
            ));
        }

        if ("TRANSFER".equals(snapshot.tickMode)) {
            LevelChunkTicks<Block> sourceTicks = blockTicks(level, snapshot.source);
            if (sourceTicks != null) {
                Set<ScheduledTick<Block>> originals = Set.copyOf(snapshot.scheduledTicks);
                sourceTicks.removeIf(originals::contains);
            }
        }
    }

    private static LevelChunkTicks<Block> blockTicks(Level level, BlockPos position) {
        LevelChunk chunk = level.getChunkAt(position);
        TickContainerAccess<Block> access = chunk.getBlockTicks();
        if (access instanceof LevelChunkTicks<?> ticks) {
            @SuppressWarnings("unchecked")
            LevelChunkTicks<Block> blockTicks = (LevelChunkTicks<Block>) ticks;
            return blockTicks;
        }
        return null;
    }

    private static final class Operation {
        private final Level level;
        private final Group group;

        private Operation(Level level, Group group) {
            this.level = level;
            this.group = group;
        }
    }

    private static final class Group {
        private final Set<BlockPos> positions = new HashSet<>();
        private final Map<BlockPos, MoveSnapshot> snapshots = new HashMap<>();
    }

    private record MoveSnapshot(
            BlockPos source,
            Block expectedBlock,
            List<ScheduledTick<Block>> scheduledTicks,
            String tickMode
    ) {
    }

    private record PositionKey(Level level, BlockPos position) {
    }
}
