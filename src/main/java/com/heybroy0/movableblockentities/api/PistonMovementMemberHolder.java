package com.heybroy0.movableblockentities.api;

import com.heybroy0.movableblockentities.MovementGroups;

/**
 * Implemented on PistonMovingBlockEntity by a mixin. The member reference is
 * the identity of this particular resolved block within its piston transaction.
 */
public interface PistonMovementMemberHolder {
    void movableblockentities$setMovementMember(MovementGroups.PushMember member);

    MovementGroups.PushMember movableblockentities$getMovementMember();
}
