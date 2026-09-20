package com.worldql.mammoth.listeners.chunks;

import com.worldql.mammoth.Slices;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.worldql_serialization.Vec3D;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ChunkLoadEventListener implements Listener {
    private static final Map<Chunk, Long> seenChunks = Collections.synchronizedMap(new HashMap<>());

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk chunk = e.getChunk();

        // Multiply coords by 16
        int x = chunk.getX() << 4;
        int z = chunk.getZ() << 4;

        int min_height = chunk.getWorld().getMinHeight();
        int max_height = chunk.getWorld().getMaxHeight();

        if (!Slices.enabled) {
            String parameter = null;
            if (seenChunks.containsKey(chunk)) {
                long ts = seenChunks.get(chunk);
                parameter = Long.toString(ts);
            }

            String world = chunk.getWorld().getName();
            MammothPlugin.records().requestRegion(world, new Vec3D(x, 0, z), parameter);

            // Handle Y=-1 to Y=-256
            if (min_height < 0) {
                MammothPlugin.records().requestRegion(world, new Vec3D(x, -1, z), parameter);
            }

            // Handle Y=256 to Y=511
            if (max_height > 256) {
                MammothPlugin.records().requestRegion(world, new Vec3D(x, 256, z), parameter);
            }

            seenChunks.put(chunk, System.currentTimeMillis());
        }

        if (MammothPlugin.processGhosts) {
            for (int i = min_height; i <= max_height; i += 16) {
                MammothPlugin.transport().subscribeToRegion(chunk.getWorld().getName(), new Vec3D(x, i, z));
            }
        }
    }
}
