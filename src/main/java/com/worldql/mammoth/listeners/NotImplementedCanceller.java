package com.worldql.mammoth.listeners;

import com.worldql.mammoth.Slices;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.world.StructureGrowEvent;

public class NotImplementedCanceller implements Listener {
    @EventHandler
    public void onRedstoneCurrent(BlockRedstoneEvent e) {
        if (Slices.enabled && Slices.isDMZ(e.getBlock().getLocation())) {
            e.setNewCurrent(0);
        }
        if (!Slices.enabled) {
            e.setNewCurrent(0);
        }
    }

    @EventHandler
    public void onLiquid(BlockFromToEvent e){
        if (Slices.enabled && Slices.isDMZ(e.getToBlock().getLocation())) {
            e.setCancelled(true);
        }
        if (!Slices.enabled) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBurn(BlockSpreadEvent e) {
        if (Slices.enabled && Slices.isDMZ(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
        if (!Slices.enabled) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onStateChange(EntityChangeBlockEvent e) {
        if (Slices.enabled && Slices.isDMZ(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
        if (!Slices.enabled) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBonemeal(StructureGrowEvent e) {
        if (shouldCancel(e.getLocation())) {
            e.setCancelled(true);
        }
    }

    /**
     * StructureGrowEvent only covers bonemeal that grows a tree or a big mushroom. Everything else
     * bonemeal does (crops, grass, sugar cane, moss) raises BlockFertilizeEvent, which was never
     * cancelled, so bonemealing inside the DMZ desynced the servers (issue #48).
     */
    @EventHandler
    public void onFertilize(BlockFertilizeEvent e) {
        if (shouldCancel(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    private static boolean shouldCancel(org.bukkit.Location location) {
        return !Slices.enabled || Slices.isDMZ(location);
    }
}
