package com.worldql.mammoth.listeners.explosions;

import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.Slices;
import com.worldql.mammoth.transport.ClusterMessage;
import com.worldql.mammoth.worldql_serialization.Record;
import com.worldql.mammoth.worldql_serialization.Vec3D;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles explosions whose source is a block rather than an entity: respawn anchors detonating
 * outside the nether, beds detonating in the nether, and anything else that raises
 * {@link BlockExplodeEvent}.
 */
public class BlockExplodeEventListener implements Listener {
    @EventHandler
    public void onBlockExplodeEvent(BlockExplodeEvent e) {
        Vec3D origin = new Vec3D(e.getBlock().getLocation());
        String world = e.getBlock().getWorld().getName();

        if (Slices.enabled && Slices.isDMZ(e.getBlock().getLocation())) {
            e.setCancelled(true);
            // The exploding block itself is still consumed, so tell the cluster it is gone.
            MammothPlugin.transport().broadcast(ClusterMessage.of(
                    world, origin, "MinecraftBlockUpdate", List.of(airRecord(e.getBlock()))));
            return;
        }

        // Outside the DMZ the explosion is allowed to happen, but the blocks it destroys have to be
        // replicated the same way entity explosions are, otherwise a respawn anchor blast is only
        // visible on the server that ran it (issue #47).
        List<Record> brokenBlocks = new ArrayList<>();
        brokenBlocks.add(airRecord(e.getBlock()));
        for (Block block : e.blockList()) {
            brokenBlocks.add(airRecord(block));
        }

        MammothPlugin.records().saveAndPublish(
                ClusterMessage.of(world, origin, "MinecraftBlockUpdate", brokenBlocks));
    }

    private static Record airRecord(Block block) {
        return new Record(
                UUID.nameUUIDFromBytes(block.getLocation().toString().getBytes(StandardCharsets.UTF_8)),
                new Vec3D(block.getLocation()),
                block.getWorld().getName(),
                "minecraft:air",
                null
        );
    }
}
