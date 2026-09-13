package com.worldql.mammoth.protocols;

import com.google.flatbuffers.FlexBuffers;
import com.worldql.mammoth.ghost.GhostPlayer;
import com.worldql.mammoth.worldql_serialization.Message;

public class MinecraftPlayerShieldUse {

    public static void process(Message state, GhostPlayer ghost) {
        FlexBuffers.Map playerMessageMap = FlexBuffers.getRoot(state.flex()).asMap();
        HandState.broadcast(ghost,
                playerMessageMap.get("blocking").asBoolean(),
                playerMessageMap.get("offhand").asBoolean());
    }
}
