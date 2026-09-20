package com.worldql.mammoth.listeners.player;

import com.worldql.mammoth.transport.ClusterMessage;
import com.google.flatbuffers.FlexBuffersBuilder;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.worldql_serialization.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.nio.ByteBuffer;

public class PlayerTeleportEventListener implements Listener {

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null) return;
        if (MammothPlugin.processGhosts) {
            FlexBuffersBuilder b = Codec.getFlexBuilder();
            int pmap = b.startMap();
            b.putFloat("pitch", e.getTo().getPitch());
            b.putFloat("yaw", e.getTo().getYaw());
            b.putString("username", e.getPlayer().getName());
            b.putString("uuid", e.getPlayer().getUniqueId().toString());
            b.endMap(null, pmap);
            ByteBuffer bb = b.finish();

            MammothPlugin.transport().publishToRegion(
                    ClusterMessage.at(e.getPlayer().getWorld().getName(), new Vec3D(e.getTo()), "MinecraftPlayerMove", bb));
        }
    }
}
