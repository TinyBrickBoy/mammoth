package com.worldql.mammoth;

import com.google.flatbuffers.FlexBuffers;
import com.worldql.mammoth.commands.CommandTeleportRequest;
import com.worldql.mammoth.commands.CommandTeleportTo;
import com.worldql.mammoth.ghost.PlayerGhostManager;
import com.worldql.mammoth.listeners.player.PlayerChatListener;
import com.worldql.mammoth.listeners.player.PlayerDeathListener;
import com.worldql.mammoth.listeners.utils.BlockTools;
import com.worldql.mammoth.minecraft_serialization.VillagerTransfer;
import com.worldql.mammoth.transport.ClusterMessage;
import com.worldql.mammoth.transport.ClusterMessageHandler;
import com.worldql.mammoth.transport.MessageScope;
import com.worldql.mammoth.worldql_serialization.Record;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Decides what Mammoth does with each message the rest of the cluster sends.
 * <p>
 * This is the domain half of what used to be the ZeroMQ receive loop: the transport now owns
 * sockets and framing, and everything left here is about Minecraft.
 */
public class ClusterMessageDispatcher implements ClusterMessageHandler {

    @Override
    public void onClusterMessage(@NotNull MessageScope scope, @NotNull ClusterMessage message, boolean fromSelf) {
        if (message.parameter() == null) {
            return;
        }

        switch (scope) {
            case CLUSTER -> handleClusterWide(message, fromSelf);
            case REGION -> handleRegional(message, fromSelf);
        }
    }

    @Override
    public void onStoredRecords(@NotNull List<Record> records) {
        BlockTools.setRecords(records, false);
    }

    private void handleClusterWide(ClusterMessage message, boolean fromSelf) {
        String parameter = message.parameter();

        if (parameter.startsWith("MinecraftRPC")) {
            String[] parts = parameter.split(">", 2);
            if (parts.length == 2) {
                String command = parts[1];
                Bukkit.getScheduler().runTask(MammothPlugin.getPluginInstance(),
                        () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
            }
            return;
        }

        switch (parameter) {
            case "MinecraftNightSkip" -> Bukkit.getScheduler().runTask(MammothPlugin.getPluginInstance(), () -> {
                World world = Bukkit.getWorld(MammothPlugin.worldName);
                if (world != null) {
                    world.setTime(0);
                }
            });
            case "MinecraftPlayerChat" -> PlayerChatListener.relayChat(message);
            case "MinecraftPlayerDeath" -> PlayerDeathListener.handleIncomingDeath(message, fromSelf);
            case "MinecraftBlockUpdate" -> {
                if (!fromSelf) {
                    BlockTools.setRecords(message.recordsOrEmpty(), false);
                }
            }
            case "MinecraftTeleportRequest" -> CommandTeleportRequest.handlePositionLookup(message);
            case "MinecraftTeleportPositionLookup" -> CommandTeleportTo.handlePositionLookup(message);
            case "MinecraftTeleport" -> CommandTeleportRequest.handleTeleport(message);
            case "MinecraftVillagerTransfer" -> VillagerTransfer.handleIncomingVillager(message);
            default -> {
                // A message type this server does not know about; newer senders are not an error.
            }
        }
    }

    private void handleRegional(ClusterMessage message, boolean fromSelf) {
        String parameter = message.parameter();

        if (parameter.equals("MinecraftBlockUpdate")) {
            BlockTools.setRecords(message.recordsOrEmpty(), fromSelf);
            return;
        }
        if (parameter.equals("MinecraftEndCrystalCreate")) {
            BlockTools.createEndCrystal(message.position(), message.worldName());
            return;
        }
        if (parameter.startsWith("MinecraftPlayer")) {
            PlayerGhostManager.updateNPC(message);
        }
        if (parameter.equals("MinecraftExplosion")) {
            float radius = (float) FlexBuffers.getRoot(message.payload()).asMap().get("radius").asFloat();
            BlockTools.createExplosion(message.position(), message.worldName(), radius);
        }
        if (parameter.equals("MinecraftPrimeTNT")) {
            BlockTools.createPrimedTNT(message.position(), message.worldName());
        }
    }
}
