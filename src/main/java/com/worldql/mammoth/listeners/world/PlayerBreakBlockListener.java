package com.worldql.mammoth.listeners.world;

import com.worldql.mammoth.MinecraftUtil;
import com.worldql.mammoth.Slices;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.listeners.utils.BlockTools;
import com.worldql.mammoth.transport.ClusterMessage;
import com.worldql.mammoth.worldql_serialization.Record;
import com.worldql.mammoth.worldql_serialization.*;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class PlayerBreakBlockListener implements Listener {
    public static final ItemStack[] NO_DROPS = new ItemStack[0];
    public static final Set<UUID> pendingDrops = Collections.synchronizedSet(new HashSet<>());

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerBreakBlockEvent(BlockBreakEvent e) {
        if (Slices.enabled) {
            // When slices are enabled, ignore the complex block drop logic and just forward a global message if it's in the DMZ.
            if (Slices.isDMZ(e.getBlock().getLocation())) {
                Record airBlock = new Record(
                        UUID.nameUUIDFromBytes(e.getBlock().getLocation().toString().getBytes(StandardCharsets.UTF_8)),
                        new Vec3D(e.getBlock().getLocation()),
                        e.getBlock().getWorld().getName(),
                        "minecraft:air",
                        null
                );
                MammothPlugin.transport().broadcastIncludingSelf(ClusterMessage.of(
                        ClusterMessage.ANY_WORLD,
                        new Vec3D(e.getBlock().getLocation()),
                        "MinecraftBlockUpdate",
                        List.of(airBlock)));
                MinecraftUtil.breakConnectedBlock(e.getBlock());
            }
            return;
        }




        if (e.isCancelled()) {
            return;
        }
        ItemStack[] drops;
        if (e.isDropItems() && !e.getPlayer().getGameMode().equals(GameMode.CREATIVE)) {
            drops = e.getBlock().getDrops().toArray(new ItemStack[0]);
            e.setDropItems(false);
        } else {
            drops = NO_DROPS;
        }

        UUID blockUuid = UUID.nameUUIDFromBytes(e.getBlock().getLocation().toString().getBytes(StandardCharsets.UTF_8));
        pendingDrops.add(blockUuid);

        Record airBlock = BlockTools.airBlock(e.getBlock().getLocation(), drops);
        Vec3D position = new Vec3D(e.getBlock().getLocation());
        String world = e.getPlayer().getWorld().getName();

        MammothPlugin.transport().publishToRegionIncludingSelf(
                ClusterMessage.of(world, position, "MinecraftBlockUpdate", List.of(airBlock)));

        // Don't pass drops flex to DB
        MammothPlugin.records().save(
                ClusterMessage.of(world, position, null, List.of(airBlock.withFlex(null))));
    }

}
