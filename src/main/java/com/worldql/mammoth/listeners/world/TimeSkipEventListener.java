package com.worldql.mammoth.listeners.world;

import com.worldql.mammoth.transport.ClusterMessage;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.worldql_serialization.Vec3D;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.TimeSkipEvent;

public class TimeSkipEventListener implements Listener {
    @EventHandler
    public void onTimeSkip(TimeSkipEvent e) {
        if (e.getSkipReason().equals(TimeSkipEvent.SkipReason.NIGHT_SKIP)) {
            MammothPlugin.transport().broadcast(
                    ClusterMessage.at(ClusterMessage.ANY_WORLD, new Vec3D(0,0,0), "MinecraftNightSkip", null));
        }
    }
}
