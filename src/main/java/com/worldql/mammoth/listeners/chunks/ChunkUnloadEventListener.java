package com.worldql.mammoth.listeners.chunks;

import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.worldql_serialization.Vec3D;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkUnloadEventListener implements Listener {
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        if (MammothPlugin.processGhosts) {
            Chunk chunk = e.getChunk();

            // Multiply coords by 16
            int x = chunk.getX() << 4;
            int z = chunk.getZ() << 4;

            int min_height = chunk.getWorld().getMinHeight();
            int max_height = chunk.getWorld().getMaxHeight();

            for (int i = min_height; i <= max_height; i += 16) {
                MammothPlugin.transport().unsubscribeFromRegion(chunk.getWorld().getName(), new Vec3D(x, i, z));
            }
        }
    }
}
