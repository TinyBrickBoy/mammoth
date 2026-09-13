package com.worldql.mammoth.ghost;

import com.google.flatbuffers.FlexBuffers;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.protocols.*;
import com.worldql.mammoth.worldql_serialization.Message;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerGhostManager {

    private static final Map<UUID, GhostPlayer> ghostsByUuid = new ConcurrentHashMap<>();
    private static final Map<Integer, GhostPlayer> ghostsByEntityId = new ConcurrentHashMap<>();

    public static void updateNPC(Message state) {
        if (!MammothPlugin.processGhosts) {
            return;
        }

        FlexBuffers.Map playerMessageMap = FlexBuffers.getRoot(state.flex()).asMap();

        UUID playerUUID = UUID.fromString(playerMessageMap.get("uuid").asString());

        if (state.parameter().equals("MinecraftPlayerDamage")) {
            GhostPlayer attacker = ghostsByUuid.get(UUID.fromString(playerMessageMap.get("uuidofattacker").asString()));
            MinecraftPlayerDamage.process(state, Bukkit.getPlayer(playerUUID), attacker);
            return;
        }

        World world = Bukkit.getServer().getWorld(Objects.requireNonNull(state.worldName()));
        if (world == null) {
            return;
        }

        GhostPlayer ghost = ghostsByUuid.get(playerUUID);
        if (ghost == null) {
            ghost = createGhost(playerMessageMap.get("username").asString(), playerUUID,
                    new Location(world, state.position().x(), state.position().y(), state.position().z()));
            ghostsByUuid.put(playerUUID, ghost);
            ghostsByEntityId.put(ghost.getEntityId(), ghost);
            ProtocolManager.sendJoinPacket(ghost);
        } else {
            ghost.touch();
        }

        if (state.parameter().equals("MinecraftPlayerQuit")) {
            remove(playerUUID);
            return;
        }
        processPacket(state, ghost);
    }

    /**
     * This gets the UUID of a player from the entity id its ghost was given on this server.
     *
     * @param id - entity id
     * @return - the uuid of the player it's mimicking, or null if there is no such ghost.
     */
    public static UUID getUUIDfromID(int id) {
        GhostPlayer ghost = ghostsByEntityId.get(id);
        return ghost == null ? null : ghost.getUuid();
    }

    public static GhostPlayer getGhostByEntityId(int id) {
        return ghostsByEntityId.get(id);
    }

    public static GhostPlayer getGhost(UUID uuid) {
        return ghostsByUuid.get(uuid);
    }

    private static GhostPlayer createGhost(String name, UUID uuid, Location location) {
        return new GhostPlayer(uuid, name, SpigotReflectionUtil.generateEntityId(), location);
    }

    /** Despawns a ghost and stops tracking it. */
    public static void remove(UUID uuid) {
        GhostPlayer ghost = ghostsByUuid.remove(uuid);
        if (ghost == null) {
            return;
        }
        ghostsByEntityId.remove(ghost.getEntityId());
        ProtocolManager.sendLeavePacket(ghost);
    }

    /**
     * Ghosts of players who wandered out of this server's subscription range stop being updated
     * without ever sending a quit message, so they are swept up on a timer. Without this they stay
     * in the tab list and in the world forever (issue #50).
     */
    public static void expireStaleGhosts() {
        Iterator<Map.Entry<UUID, GhostPlayer>> iterator = ghostsByUuid.entrySet().iterator();
        while (iterator.hasNext()) {
            GhostPlayer ghost = iterator.next().getValue();
            if (!ghost.shouldExpire()) {
                continue;
            }
            iterator.remove();
            ghostsByEntityId.remove(ghost.getEntityId());
            ProtocolManager.sendLeavePacket(ghost);
        }
    }

    /** Spawns every known ghost for a player who just joined or respawned. */
    public static void ensurePlayerHasJoinPackets(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        for (GhostPlayer ghost : ghostsByUuid.values()) {
            ProtocolManager.sendJoinPacket(ghost, player);
        }
    }

    /** Despawns every ghost, used on shutdown so nothing is left in players' tab lists. */
    public static void removeAll() {
        for (GhostPlayer ghost : ghostsByUuid.values()) {
            ProtocolManager.sendLeavePacket(ghost);
        }
        ghostsByUuid.clear();
        ghostsByEntityId.clear();
    }

    public static void processPacket(Message state, GhostPlayer ghost) {
        switch (state.parameter()) {
            case "MinecraftPlayerMove" -> MinecraftPlayerMove.process(state, ghost);
            case "MinecraftPlayerSingleAction" -> MinecraftPlayerSingleAction.process(state, ghost);
            case "MinecraftPlayerEquipmentEdit" -> MinecraftPlayerEquipmentEdit.process(state, ghost);
            case "MinecraftPlayerShieldUse" -> MinecraftPlayerShieldUse.process(state, ghost);
            case "MinecraftPlayerShootBow" -> MinecraftPlayerShootBow.process(state, ghost);
            default -> {
                // Not every MinecraftPlayer* message drives a ghost; the rest are handled elsewhere.
            }
        }
    }

}
