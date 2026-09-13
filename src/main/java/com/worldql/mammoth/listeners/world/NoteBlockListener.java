package com.worldql.mammoth.listeners.world;

import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.Slices;
import com.worldql.mammoth.listeners.utils.BlockTools;
import com.worldql.mammoth.worldql_serialization.Instruction;
import com.worldql.mammoth.worldql_serialization.Message;
import com.worldql.mammoth.worldql_serialization.Record;
import com.worldql.mammoth.worldql_serialization.Replication;
import com.worldql.mammoth.worldql_serialization.Vec3D;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import zmq.ZMQ;

import java.util.List;

/**
 * Keeps note blocks in tune across the cluster.
 * <p>
 * Right clicking a note block changes its pitch, which lives in the block data. That is a block
 * change like any other, but nothing was raising it as one, so a tuned note block played a
 * different note on every server (issue #49).
 */
public class NoteBlockListener implements Listener {

    @EventHandler
    public void onNoteBlockTune(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }

        Block block = e.getClickedBlock();
        if (block.getType() != Material.NOTE_BLOCK) {
            return;
        }
        // Note blocks in the DMZ are not usable at all; see DmzProtectionListener.
        if (Slices.enabled && Slices.isDMZ(block.getLocation())) {
            return;
        }

        // The pitch only changes after the event, so read the block back on the next tick.
        Bukkit.getScheduler().runTask(MammothPlugin.getPluginInstance(), () -> broadcast(block));
    }

    private static void broadcast(Block block) {
        if (block.getType() != Material.NOTE_BLOCK) {
            return;
        }

        Record record = BlockTools.serializeBlock(block);
        Message message = new Message(
                Instruction.RecordCreate,
                MammothPlugin.worldQLClientId,
                block.getWorld().getName(),
                Replication.ExceptSelf,
                new Vec3D(block.getLocation()),
                List.of(record),
                null,
                "MinecraftBlockUpdate",
                null
        );

        MammothPlugin.getPluginInstance().getPushSocket().send(message.encode(), ZMQ.ZMQ_DONTWAIT);
        MammothPlugin.getPluginInstance().getPushSocket()
                .send(message.withInstruction(Instruction.LocalMessage).encode(), ZMQ.ZMQ_DONTWAIT);
    }
}
