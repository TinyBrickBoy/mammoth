package com.worldql.mammoth.listeners.player;

import com.google.flatbuffers.FlexBuffers;
import com.google.flatbuffers.FlexBuffersBuilder;
import com.worldql.mammoth.Slices;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.ghost.PlayerGhostManager;
import com.worldql.mammoth.listeners.utils.ItemTools;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.worldql.mammoth.ghost.GhostPlayer;
import com.worldql.mammoth.protocols.ProtocolManager;
import com.worldql.mammoth.worldql_serialization.Codec;
import com.worldql.mammoth.worldql_serialization.Instruction;
import com.worldql.mammoth.worldql_serialization.Message;
import com.worldql.mammoth.worldql_serialization.Replication;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zmq.ZMQ;

import java.nio.ByteBuffer;
import java.util.UUID;

public class PlayerDeathListener implements Listener {
    public static final ItemStack[] EMPTY_DROPS = new ItemStack[0];

    private static String serializeDeathMessage(@Nullable Component message) {
        return message == null ? "" : GsonComponentSerializer.gson().serialize(message);
    }

    private static @Nullable Component deserializeDeathMessage(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return null;
        }
        return GsonComponentSerializer.gson().deserialize(serialized);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        // Drop items normally when players die with slice mode enabled.
        // Also delete their record from redis.
        if (Slices.enabled) {
            Bukkit.getScheduler().runTaskAsynchronously(MammothPlugin.getPluginInstance(), () -> {
                MammothPlugin.playerDataSavingManager.markSavedForDebounce(e.getEntity().getPlayer());
                MammothPlugin.redis.set("player-" + e.getEntity().getUniqueId(), "dead");
            });
            return;
        }


        String killerUuid = null;
        if (e.getEntity().getKiller() != null) {
            killerUuid = e.getEntity().getKiller().getUniqueId().toString();
        }

        ItemStack[] drops = EMPTY_DROPS;
        if (killerUuid != null) {
            drops = e.getDrops().toArray(new ItemStack[0]);
        }

        // Send death event to other servers
        FlexBuffersBuilder b = Codec.getFlexBuilder();
        int pmap = b.startMap();
        b.putString("uuid", e.getEntity().getUniqueId().toString());
        if (killerUuid != null) b.putString("killer", killerUuid);
        b.putString("message", serializeDeathMessage(e.deathMessage()));
        b.putBlob("drops", ItemTools.serializeItemStack(drops));
        b.putInt("xp", e.getDroppedExp());
        b.putFloat("x", e.getEntity().getLocation().getX());
        b.putFloat("y", e.getEntity().getLocation().getY());
        b.putFloat("z", e.getEntity().getLocation().getZ());
        b.putString("world", e.getEntity().getWorld().getName());
        b.endMap(null, pmap);
        ByteBuffer bb = b.finish();

        Message message = new Message(
                Instruction.GlobalMessage,
                MammothPlugin.worldQLClientId,
                "@global",
                Replication.IncludingSelf,
                null,
                null,
                null,
                "MinecraftPlayerDeath",
                bb
        );

        MammothPlugin.getPluginInstance().getPushSocket().send(message.encode(), ZMQ.ZMQ_DONTWAIT);

        // Stop drops from dropping if killed by a player
        if (killerUuid != null) {
            e.getDrops().clear();
        }
    }

    public static void handleIncomingDeath(@NotNull Message message, boolean isSelf) {
        FlexBuffers.Map map = FlexBuffers.getRoot(message.flex()).asMap();
        Server server = MammothPlugin.getPluginInstance().getServer();

        // Broadcast death message
        if (!isSelf) {
            Component deathMsg = deserializeDeathMessage(map.get("message").asString());
            if (deathMsg != null) {
                server.broadcast(deathMsg);
            }
        }

        UUID killerUuid = null;
        if (!map.get("killer").isNull()) {
            killerUuid = UUID.fromString(map.get("killer").asString());
        }

        // yeet the player below the map so it looks like they "died"
        // TODO: Maybe play a death animation.
        GhostPlayer ghost = PlayerGhostManager.getGhost(UUID.fromString(map.get("uuid").asString()));
        if (ghost != null) {
            ghost.setPosition(0.0, -50.0, 0.0, 0, 0);
            ProtocolManager.broadcast(new WrapperPlayServerEntityTeleport(
                    ghost.getEntityId(), new Vector3d(0.0, -50.0, 0.0), 0, 0, false));
        }

        if (killerUuid != null) {
            // For lambda
            UUID finalKillerUuid = killerUuid;
            boolean killerPresent = server.getOnlinePlayers().stream().anyMatch(p -> p.getUniqueId().equals(finalKillerUuid));

            // Only drop if the killer is on the current server
            if (killerPresent) {
                double x = map.get("x").asFloat();
                double y = map.get("y").asFloat();
                double z = map.get("z").asFloat();
                String worldName = map.get("world").asString();

                World world = Bukkit.getWorld(worldName);
                Location location = new Location(world, x, y, z);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (ItemStack item : ItemTools.deserializeItemStack(map.get("drops").asBlob().data())) {
                            world.dropItem(location, item);
                        }

                        ExperienceOrb orb = world.spawn(location, ExperienceOrb.class);
                        orb.setExperience(map.get("xp").asInt());
                    }
                }.runTask(MammothPlugin.pluginInstance);
            }
        }
    }
}
