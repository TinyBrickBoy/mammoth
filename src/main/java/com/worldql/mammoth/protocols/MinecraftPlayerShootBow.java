package com.worldql.mammoth.protocols;

import com.worldql.mammoth.transport.ClusterMessage;
import com.google.flatbuffers.FlexBuffers;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.ghost.GhostPlayer;
import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class MinecraftPlayerShootBow {

    public static void process(ClusterMessage state, GhostPlayer ghost) {
        FlexBuffers.Map playerMessageMap = FlexBuffers.getRoot(state.payload()).asMap();
        boolean charging = playerMessageMap.get("charging").asBoolean();

        HandState.broadcast(ghost, charging, playerMessageMap.get("offhand").asBoolean());

        if (charging) {
            return;
        }

        // Releasing the string: fire a real arrow so it can actually hit players on this server.
        Location origin = ghost.getLocation();
        if (origin == null) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                Location loc = origin.clone().add(0, 1.4, 0);
                Vector direction = origin.getDirection().normalize();

                Arrow arrow = loc.getWorld().spawnArrow(loc, direction, 1, 0);
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrow.setVelocity(direction);
            }
        }.runTask(MammothPlugin.getPluginInstance());
    }
}
