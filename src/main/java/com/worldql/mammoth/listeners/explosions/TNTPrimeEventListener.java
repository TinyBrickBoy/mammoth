package com.worldql.mammoth.listeners.explosions;

import com.worldql.mammoth.Slices;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.worldql_serialization.*;
import com.worldql.mammoth.transport.ClusterMessage;
import com.worldql.mammoth.worldql_serialization.Record;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.TNTPrimeEvent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class TNTPrimeEventListener implements Listener {
    @EventHandler
    public void onTNTPrime(TNTPrimeEvent e) {
        UUID blockUuid = UUID.nameUUIDFromBytes(e.getBlock().getLocation().toString().getBytes(StandardCharsets.UTF_8));

        Record airBlock = new Record(
                blockUuid,
                new Vec3D(e.getBlock().getLocation()),
                e.getBlock().getWorld().getName(),
                "minecraft:air",
                null
        );

        // This position isn't really used since the Record also contains the position
        // of the changed block(s).
        ClusterMessage primed = ClusterMessage.of(
                e.getBlock().getWorld().getName(),
                new Vec3D(e.getBlock().getLocation()),
                "MinecraftPrimeTNT",
                List.of(airBlock));

        if (Slices.enabled && Slices.isDMZ(e.getBlock().getLocation())) {
            MammothPlugin.transport().broadcast(primed.withParameter("MinecraftBlockUpdate"));
            return;
        }

        MammothPlugin.records().saveAndPublish(primed);
    }
}
