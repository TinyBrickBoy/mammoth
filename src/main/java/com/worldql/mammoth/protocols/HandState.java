package com.worldql.mammoth.protocols;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.worldql.mammoth.ghost.GhostPlayer;

import java.util.Collections;

/**
 * The "hand states" metadata byte drives both the shield block pose and the bow draw pose, so
 * raising a shield and pulling a bow share this code.
 */
final class HandState {
    /** Metadata index of a living entity's hand states. */
    private static final int HAND_STATES_INDEX = 8;
    private static final byte HAND_ACTIVE = 0x01;
    private static final byte OFF_HAND = 0x02;

    private HandState() {
    }

    static void broadcast(GhostPlayer ghost, boolean active, boolean offhand) {
        byte bitmask = 0;
        if (active) {
            bitmask |= HAND_ACTIVE;
            if (offhand) {
                bitmask |= OFF_HAND;
            }
        }

        ProtocolManager.broadcast(new WrapperPlayServerEntityMetadata(ghost.getEntityId(),
                Collections.singletonList(new EntityData<>(HAND_STATES_INDEX, EntityDataTypes.BYTE, bitmask))));
    }
}
