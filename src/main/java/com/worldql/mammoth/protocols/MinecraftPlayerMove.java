package com.worldql.mammoth.protocols;

import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.google.flatbuffers.FlexBuffers;
import com.worldql.mammoth.ghost.GhostPlayer;
import com.worldql.mammoth.worldql_serialization.Message;

public class MinecraftPlayerMove {

    public static void process(Message state, GhostPlayer ghost) {
        FlexBuffers.Map playerMessageMap = FlexBuffers.getRoot(state.flex()).asMap();
        float yaw = (float) playerMessageMap.get("yaw").asFloat();
        float pitch = (float) playerMessageMap.get("pitch").asFloat();

        ghost.setPosition(state.position().x(), state.position().y(), state.position().z(), yaw, pitch);

        ProtocolManager.broadcast(new WrapperPlayServerEntityTeleport(
                ghost.getEntityId(),
                new Vector3d(ghost.getX(), ghost.getY(), ghost.getZ()),
                yaw,
                pitch,
                true));
        ProtocolManager.broadcast(new WrapperPlayServerEntityHeadLook(ghost.getEntityId(), yaw));
    }
}
