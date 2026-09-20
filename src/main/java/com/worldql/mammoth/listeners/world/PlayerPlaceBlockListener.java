package com.worldql.mammoth.listeners.world;

import com.worldql.mammoth.Slices;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.events.PlayerHoldEvent;
import com.worldql.mammoth.listeners.utils.BlockTools;
import com.worldql.mammoth.transport.ClusterMessage;
import com.worldql.mammoth.worldql_serialization.Vec3D;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class PlayerPlaceBlockListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPlaceBlockEvent(BlockPlaceEvent e) {
        if (e.isCancelled() || e.getBlockPlaced().getType().equals(Material.FIRE)) {
            return;
        }

        if (Slices.enabled) {
            // Blocks holding state we cannot sync are rejected in the DMZ by DmzProtectionListener.
            if (Slices.isDMZ(e.getBlockPlaced().getLocation())) {
                // TODO: Handle compound blocks (beds, doors) and joined blocks (fences, glass panes)
                MammothPlugin.transport().broadcast(ClusterMessage.of(
                        ClusterMessage.ANY_WORLD,
                        new Vec3D(e.getBlockPlaced().getLocation()),
                        "MinecraftBlockUpdate",
                        List.of(BlockTools.serializeBlock(e.getBlockPlaced()))));
            }
        } else {
            MammothPlugin.records().saveAndPublish(ClusterMessage.of(
                    e.getPlayer().getWorld().getName(),
                    new Vec3D(e.getBlockPlaced().getLocation()),
                    "MinecraftBlockUpdate",
                    List.of(BlockTools.serializeBlock(e.getBlockPlaced()))));
        }

        // Update hand visual if they ran out of blocks in their hand.
        if (e.getPlayer().getInventory().getItemInMainHand().getAmount() - 1 <= 0) {
            Bukkit.getPluginManager().callEvent(new PlayerHoldEvent(e.getPlayer(), new ItemStack(Material.AIR), PlayerHoldEvent.HandType.MAINHAND));
        }
    }
}
