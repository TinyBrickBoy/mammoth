package com.worldql.mammoth.listeners.world;

import com.worldql.mammoth.transport.ClusterMessage;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.listeners.utils.BlockTools;
import com.worldql.mammoth.worldql_serialization.*;
import com.worldql.mammoth.worldql_serialization.Record;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class PortalCreateEventListener implements Listener {
    @EventHandler
    public void onPortalCreate(PortalCreateEvent e) {
        if (e.getBlocks().size() < 1) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                List<Record> portalBlocks = new ArrayList<>();
                for (BlockState b : e.getBlocks()) {
                    if (b.getBlockData().getMaterial().equals(Material.OBSIDIAN) || b.getBlockData().getMaterial().equals(Material.NETHER_PORTAL)) {
                        portalBlocks.add(BlockTools.serializeBlock(b.getBlock()));
                    }
                }
                Location l = e.getBlocks().get(0).getLocation();

                // Create a WorldQL Message containing the broken blocks.
                // Get a copy of the message we just sent, but with the Instruction type LocalMessage.
                // This Message notifies other Minecraft servers of the block change immediately (if they are subscribed to the region)
                // Record changes aren't loaded until the chunk is loaded.
                MammothPlugin.transport().broadcast(
                        ClusterMessage.of(ClusterMessage.ANY_WORLD, new Vec3D(l), "MinecraftBlockUpdate", portalBlocks));
            }
        }.runTaskLater(MammothPlugin.pluginInstance, 1);


    }
}
