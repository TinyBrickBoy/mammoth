package com.worldql.mammoth.commands;

import com.worldql.mammoth.transport.ClusterMessage;
import com.worldql.mammoth.MammothPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

public class CommandTeleportRequestAccept implements CommandExecutor {
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player target)) {
            // Must be used by a player
            return false;
        }

        ByteBuffer bb = CommandTeleportRequest.pendingTeleportRequests.get(target.getUniqueId());
        if (bb != null) {
            MammothPlugin.transport().broadcastIncludingSelf(
                    ClusterMessage.anywhere("MinecraftTeleport", bb));
            target.sendMessage(Component.text("Teleport request accepted!", NamedTextColor.GREEN));
            return true;
        } else {
            target.sendMessage(Component.text("You do not have any pending teleport requests.", NamedTextColor.RED));
            return false;
        }
    }
}
