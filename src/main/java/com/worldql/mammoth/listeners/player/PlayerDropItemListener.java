package com.worldql.mammoth.listeners.player;

import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.Slices;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

public class PlayerDropItemListener implements Listener {
    @EventHandler
    public void onPlayerDropEvent(PlayerDropItemEvent e) {
        if (!MammothPlugin.playerDataSavingManager.isFullySynced(e.getPlayer()) || MammothPlugin.playerDataSavingManager.getMsSinceLogin(e.getPlayer()) < 8000) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(
                    Component.text("You can't move items right now. Please wait a moment...", NamedTextColor.RED));
            return;
        }
        if (Slices.getDistanceFromSliceBoundary(e.getPlayer().getLocation()) < 5) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Component.text("You don't want to drop items on the ground here!", NamedTextColor.GOLD)
                    .append(Component.text(" Move further from a server border.", NamedTextColor.GOLD, TextDecoration.BOLD)));
        }
    }
}
