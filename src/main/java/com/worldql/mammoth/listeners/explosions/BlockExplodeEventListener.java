package com.worldql.mammoth.listeners.explosions;

import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.Slices;
import com.worldql.mammoth.worldql_serialization.*;
import com.worldql.mammoth.worldql_serialization.Record;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import zmq.ZMQ;

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
        if (Slices.enabled && Slices.isDMZ(e.getBlock().getLocation())) {
            e.setCancelled(true);
            // The exploding block itself is still consumed, so tell the cluster it is gone.
            Message message = blockUpdate(e, List.of(airRecord(e.getBlock())));
            MammothPlugin.getPluginInstance().getPushSocket()
                    .send(message.withInstruction(Instruction.GlobalMessage).encode(), ZMQ.ZMQ_DONTWAIT);
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

        Message message = blockUpdate(e, brokenBlocks);
        MammothPlugin.getPluginInstance().getPushSocket().send(message.encode(), ZMQ.ZMQ_DONTWAIT);
        // Recorded changes are only replayed when a chunk loads, so also notify subscribed servers now.
        MammothPlugin.getPluginInstance().getPushSocket()
                .send(message.withInstruction(Instruction.LocalMessage).encode(), ZMQ.ZMQ_DONTWAIT);
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

    private static Message blockUpdate(BlockExplodeEvent e, List<Record> records) {
        return new Message(
                Instruction.RecordCreate,
                MammothPlugin.worldQLClientId,
                e.getBlock().getWorld().getName(),
                Replication.ExceptSelf,
                // This field isn't really used since the Records also contain the position
                // of the changed block(s).
                new Vec3D(e.getBlock().getLocation()),
                records,
                null,
                "MinecraftBlockUpdate",
                null
        );
    }
}
