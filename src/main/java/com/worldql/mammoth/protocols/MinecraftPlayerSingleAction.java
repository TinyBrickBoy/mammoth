package com.worldql.mammoth.protocols;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.google.flatbuffers.FlexBuffers;
import com.worldql.mammoth.ghost.GhostPlayer;
import com.worldql.mammoth.worldql_serialization.Message;

import java.util.Collections;

public class MinecraftPlayerSingleAction {
    /** Metadata index of an entity's pose. */
    private static final int POSE_INDEX = 6;

    public static void process(Message state, GhostPlayer ghost) {
        FlexBuffers.Map playerMessageMap = FlexBuffers.getRoot(state.flex()).asMap();

        switch (playerMessageMap.get("action").asString()) {
            case "crouch" -> sendPose(ghost, EntityPose.CROUCHING);
            case "uncrouch" -> sendPose(ghost, EntityPose.STANDING);
            case "punch" -> ProtocolManager.broadcast(new WrapperPlayServerEntityAnimation(
                    ghost.getEntityId(), WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM));
            default -> {
                // Unknown actions are ignored so newer senders don't break older receivers.
            }
        }
    }

    private static void sendPose(GhostPlayer ghost, EntityPose pose) {
        ProtocolManager.broadcast(new WrapperPlayServerEntityMetadata(ghost.getEntityId(),
                Collections.singletonList(new EntityData<>(POSE_INDEX, EntityDataTypes.ENTITY_POSE, pose))));
    }
}
