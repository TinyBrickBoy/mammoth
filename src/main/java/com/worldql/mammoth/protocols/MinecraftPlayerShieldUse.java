package com.worldql.mammoth.protocols;

import com.worldql.mammoth.transport.ClusterMessage;
import com.google.flatbuffers.FlexBuffers;
import com.worldql.mammoth.ghost.GhostPlayer;

public class MinecraftPlayerShieldUse {

    public static void process(ClusterMessage state, GhostPlayer ghost) {
        FlexBuffers.Map playerMessageMap = FlexBuffers.getRoot(state.payload()).asMap();
        HandState.broadcast(ghost,
                playerMessageMap.get("blocking").asBoolean(),
                playerMessageMap.get("offhand").asBoolean());
    }
}
