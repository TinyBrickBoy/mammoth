package com.worldql.mammoth.listeners.player;

import com.worldql.mammoth.MinecraftUtil;
import com.worldql.mammoth.MammothPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;

public class PlayerInventoryOpenEventListener implements Listener {
    @EventHandler
    public void onEnderChestOpen(InventoryOpenEvent e) {
        if (e.getInventory().getType().equals(InventoryType.ENDER_CHEST)) {
            try {
                String data = MammothPlugin.redis.get("player-" + e.getPlayer().getUniqueId() + "-enderchest");
                if (data != null) {
                    e.getInventory().setContents(MinecraftUtil.itemStackArrayFromBase64(data));
                }
            } catch (Exception exception) {
                exception.printStackTrace();;
            }
        }
    }
    @EventHandler
    public void onEnderChestClose(InventoryCloseEvent event) {
        if (event.getInventory().getType().equals(InventoryType.ENDER_CHEST)) {
            String serializedInventory = MinecraftUtil.itemStackArrayToBase64(event.getInventory().getContents());
            try {
                MammothPlugin.redis.set("player-" + event.getPlayer().getUniqueId() + "-enderchest", serializedInventory);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

}
