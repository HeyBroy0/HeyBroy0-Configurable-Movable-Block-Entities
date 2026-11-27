package com.heybroy0.movableblockentities;

import com.heybroy0.movableblockentities.api.PistonMovingBlockEntityInvoker;
import com.heybroy0.movableblockentities.api.PistonMovementMemberHolder;
import com.heybroy0.movableblockentities.api.PistonMultiblockHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickContainerAccess;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks each resolved block as a distinct member of one piston transaction.
 *
 * Positions are indexes used to attach a newly-created moving entity to its
 * resolved member. Once attached, landing, companion coordination and survival
 * protection use the member/entity identity rather than position alone.
 */
public final class MovementGroups {
    private static final AtomicLong NEXT_TRANSACTION_ID = new AtomicLong();
    private static final ThreadLocal<Deque<Operation>> OPERATIONS =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Set<PushMember>> COORDINATION_GUARD =
            ThreadLocal.withInitial(HashSet::new);

    /** Active moving destination -> exact resolved member for that movement. */
    private static final Map<Level, Map<BlockPos, PushMember>> MOVING = new WeakHashMap<>();

    private MovementGroups() {
    }

    public static void begin(Level level) {
        OPERATIONS.get().push(new Operation(
                level, new Group(level, NEXT_TRANSACTION_ID.incrementAndGet())
        ));
    }

    public static void end(Level level) {
        Deque<Operation> stack = OPERATIONS.get();
        if (!stack.isEmpty()) {
            Operation operation = stack.pop();
            if (operation.level == level) {
                // A member not attached to a moving piston entity was not
                // actually accepted by the movement transaction.
                for (PushMember member : new ArrayList<>(operation.group.members)) {
                    if (!member.attached) {
                        discardUnattached(member);
                    }
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
     * Called while the resolver still exposes its original source states.
     * Each resolved entry receives an identity unique within its transaction.
     */
    public static void captureResolved(Level level, List<BlockPos> sources, Direction movementDirection) {
        Deque<Operation> stack = OPERATIONS.get();
        if (stack.isEmpty() || level == null || level.isClientSide()) return;
        Operation operation = stack.peek();
        if (operation.level != level) return;

        Group group = operation.group;
        group.clear();
        int sequence = 0;
        for (BlockPos sourcePosition : sources) {
            BlockPos source = sourcePosition.immutable();
            if (group.membersBySource.containsKey(source)) {
                continue;
            }

            BlockState movedState = level.getBlockState(source);
            if (movedState.isAir()) {
                continue;
            }

            BlockPos destination = source.relative(movementDirection).immutable();
            PushMember member = new PushMember(
                    group,
                    new MemberIdentity(group.transactionId, sequence++),
                    source,
                    destination,
                    movedState
            );
            group.members.add(member);
            group.membersBySource.put(source, member);
            group.membersByDestination.put(destination, member);

            String tickMode = PistonBehavior.settings(movedState).scheduledTicks();
            if ("TRANSFER".equals(tickMode) || "DUPLICATE".equals(tickMode)) {
                LevelChunkTicks<Block> sourceTicks = blockTicks(level, source);
                if (sourceTicks != null) {
                    List<ScheduledTick<Block>> scheduledTicks = sourceTicks.getAll()
                            .filter(tick -> tick.pos().equals(source)
                                    && tick.type() == movedState.getBlock())
                            .toList();
                    if (!scheduledTicks.isEmpty()) {
                        member.snapshot = new MoveSnapshot(
                                source, movedState.getBlock(), scheduledTicks, tickMode
                        );
                    }
                }
            }
        }

        // Establish explicit member-to-member relationships only when the
        // handler's exact source position was also in the resolved list.
        for (PushMember member : group.members) {
            for (BlockPos companionSource : PistonMultiblockHandlers.findCompanions(
                    level, member.source, member.expectedState
            )) {
                PushMember companion = group.membersBySource.get(companionSource);
                if (companion != null && companion != member) {
                    member.companions.add(companion);
                }
            }
        }
    }

    /**
     * Called when a PistonMovingBlockEntity is attached to the level. The
     * destination is used once to find the resolver-created member; a state
     * equality check prevents attaching a different resolved block by accident.
     */
    public static void register(Level level, PistonMovingBlockEntity entity) {
        if (level == null || level.isClientSide() || entity == null) {
            return;
        }

        PistonMovementMemberHolder holder = (PistonMovementMemberHolder) entity;
        if (holder.movableblockentities$getMovementMember() != null) {
            return;
        }

        Deque<Operation> stack = OPERATIONS.get();
        if (stack.isEmpty() || stack.peek().level != level) {
            return;
        }

        BlockPos destination = entity.getBlockPos().immutable();
        PushMember member = stack.peek().group.membersByDestination.get(destination);
        if (member == null || !member.expectedState.equals(entity.getMovedState())) {
            return;
        }

        synchronized (MOVING) {
            Map<BlockPos, PushMember> world = MOVING.computeIfAbsent(level, ignored -> new HashMap<>());
            PushMember existing = world.get(destination);
            if (existing != null && existing != member && !existing.landed) {
                return;
            }

            holder.movableblockentities$setMovementMember(member);
            member.movingEntity = entity;
            member.attached = true;
            world.put(destination, member);
        }
    }

    /**
     * Only the exact state represented by an attached, unresolved push member
     * receives the temporary survival exemption. Unrelated blocks at the same
     * position or other halves of nearby structures do not qualify.
     */
    public static boolean shouldPreserveMovingMember(Level level, BlockPos position, BlockState state) {
        PushMember member;
        synchronized (MOVING) {
            Map<BlockPos, PushMember> world = MOVING.get(level);
            member = world == null ? null : world.get(position);
        }
        if (member == null || member.landed || !member.attached
                || !member.expectedState.equals(state) || member.movingEntity == null) {
            return false;
        }

        PistonMovementMemberHolder holder = (PistonMovementMemberHolder) member.movingEntity;
        return holder.movableblockentities$getMovementMember() == member
                && member.movingEntity.getMovedState().equals(state);
    }

    /**
     * Coordinate all resolved components of this exact structure before one
     * component performs its ordinary finalTick. No adjacent-position scan is
     * performed here; relationships were recorded during resolver capture.
     */
    public static void coordinateCompanions(PistonMovingBlockEntity entity) {
        if (entity == null || entity.getLevel() == null || entity.getLevel().isClientSide()) {
            return;
        }

        PushMember own = ((PistonMovementMemberHolder) entity)
                .movableblockentities$getMovementMember();
        if (own == null) {
            return;
        }

        Set<PushMember> guard = COORDINATION_GUARD.get();
        if (guard.contains(own)) {
            return;
        }

        Set<PushMember> structure = new LinkedHashSet<>();
        Deque<PushMember> pending = new ArrayDeque<>();
        pending.add(own);
        while (!pending.isEmpty()) {
            PushMember member = pending.removeFirst();
            if (!structure.add(member)) {
                continue;
            }
            pending.addAll(member.companions);
        }

        guard.addAll(structure);
        try {
            for (PushMember member : structure) {
                PistonMovingBlockEntity component = member.movingEntity;
                if (member != own && component != null && !member.landed) {
                    ((PistonMovingBlockEntityInvoker) (Object) component)
                            .movableblockentities$invokeFinalTick();
                }
            }
        } finally {
            guard.removeAll(structure);
            if (guard.isEmpty()) {
                COORDINATION_GUARD.remove();
            }
        }
    }

    /** Called after FrozenLib has placed the moved state and restored its block entity. */
    public static void finish(
            Level level, BlockPos position, PistonMovingBlockEntity entity
    ) {
        if (level == null || level.isClientSide() || entity == null) {
            return;
        }

        PushMember member = ((PistonMovementMemberHolder) entity)
                .movableblockentities$getMovementMember();
        if (member == null || member.group.level != level) {
            return;
        }

        BlockPos destination = position.immutable();
        MoveSnapshot snapshot;
        synchronized (MOVING) {
            Map<BlockPos, PushMember> world = MOVING.get(level);
            if (world == null || world.get(destination) != member) {
                return;
            }
            world.remove(destination);
            if (world.isEmpty()) {
                MOVING.remove(level);
            }

            member.landed = true;
            member.attached = false;
            snapshot = member.snapshot;
            member.snapshot = null;
        }

        // A failed or substituted landing must not receive this member's ticks.
        if (snapshot != null && member.expectedState.equals(level.getBlockState(destination))) {
            restoreScheduledTicks(level, destination, snapshot);
        }
        member.movingEntity = null;
        member.group.remove(member);
    }

    private static void discardUnattached(PushMember member) {
        member.snapshot = null;
        member.group.remove(member);
    }

    private static void restoreScheduledTicks(
            Level level, BlockPos destination, MoveSnapshot snapshot
    ) {
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
        private final Level level;
        private final long transactionId;
        private final Set<PushMember> members = new LinkedHashSet<>();
        private final Map<BlockPos, PushMember> membersBySource = new HashMap<>();
        private final Map<BlockPos, PushMember> membersByDestination = new HashMap<>();

        private Group(Level level, long transactionId) {
            this.level = level;
            this.transactionId = transactionId;
        }

        private void remove(PushMember member) {
            members.remove(member);
            membersBySource.remove(member.source, member);
            membersByDestination.remove(member.destination, member);
            if (members.isEmpty()) {
                membersBySource.clear();
                membersByDestination.clear();
            }
        }

        private void clear() {
            members.clear();
            membersBySource.clear();
            membersByDestination.clear();
        }
    }

    /** Unique identity for one resolved block inside one push transaction. */
    public record MemberIdentity(long transactionId, int sequence) {
    }

    /**
     * A resolved source block and its moving entity form one member. The
     * destination map is an attachment index; after attachment, member identity
     * is carried by the PistonMovingBlockEntity itself.
     */
    public static final class PushMember {
        private final Group group;
        private final MemberIdentity identity;
        private final BlockPos source;
        private final BlockPos destination;
        private final BlockState expectedState;
        private final Set<PushMember> companions = new LinkedHashSet<>();
        private MoveSnapshot snapshot;
        private PistonMovingBlockEntity movingEntity;
        private boolean attached;
        private boolean landed;

        private PushMember(
                Group group,
                MemberIdentity identity,
                BlockPos source,
                BlockPos destination,
                BlockState expectedState
        ) {
            this.group = group;
            this.identity = identity;
            this.source = source;
            this.destination = destination;
            this.expectedState = expectedState;
        }

        public MemberIdentity identity() {
            return identity;
        }

        public BlockPos source() {
            return source;
        }

        public BlockPos destination() {
            return destination;
        }
    }

    private record MoveSnapshot(
            BlockPos source,
            Block expectedBlock,
            List<ScheduledTick<Block>> scheduledTicks,
            String tickMode
    ) {
    }
}
