package com.worldql.mammoth.listeners.player;

import com.worldql.mammoth.transport.ClusterMessage;
import com.google.flatbuffers.FlexBuffersBuilder;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.worldql_serialization.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.nio.ByteBuffer;

public class PlayerShootBowListener implements Listener {


    private static void pushCommand(Player player, boolean charging) {
        FlexBuffersBuilder b = Codec.getFlexBuilder();
        int pmap = b.startMap();

        b.putBoolean("charging", charging); // true when they are drawing the bow back.
        b.putString("username", player.getName());
        b.putString("uuid", player.getUniqueId().toString());
        b.endMap(null, pmap);
        ByteBuffer bb = b.finish();

        MammothPlugin.transport().publishToRegion(
                ClusterMessage.at(player.getWorld().getName(), new Vec3D(player.getLocation()), "MinecraftPlayerShootBow", bb));
    }

    @EventHandler
    public void onPlayerShooting(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;
        pushCommand(player, false);
    }

    @EventHandler
    public void onPlayerShooting(PlayerInteractEvent event) {
        if (!event.hasItem())
            return;
        if (!event.getItem().getType().equals(Material.BOW))
            return;
        if (!event.getAction().name().contains("RIGHT"))
            return;

        pushCommand(event.getPlayer(), true);
    }
}
