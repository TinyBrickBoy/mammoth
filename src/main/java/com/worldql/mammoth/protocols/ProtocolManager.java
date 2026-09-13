package com.worldql.mammoth.protocols;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHurtAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.events.OutgoingPlayerHitEvent;
import com.worldql.mammoth.ghost.GhostPlayer;
import com.worldql.mammoth.ghost.PlayerGhostManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends and receives the raw packets the ghost system needs.
 * <p>
 * Everything here goes through PacketEvents, which speaks the wire protocol directly. That is what
 * lets ghosts keep working across Minecraft releases: Mammoth never touches server internals.
 */
public final class ProtocolManager {
    /** Metadata index of the player's displayed skin parts. */
    private static final int SKIN_PARTS_INDEX = 17;
    /** Every skin overlay (cape, jacket, sleeves, trouser legs and hat) turned on. */
    private static final byte ALL_SKIN_PARTS = 0x7F;

    private static PacketListenerAbstract listener;

    private ProtocolManager() {
    }

    /**
     * Starts listening for attacks against ghosts. Must be called before PacketEvents is
     * initialized, which is the order PacketEvents expects listeners to be registered in.
     *
     * @return false if PacketEvents is not loaded, in which case ghosts cannot run.
     */
    public static boolean read() {
        if (!isPacketEventsLoaded()) {
            return false;
        }

        listener = new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
                    return;
                }

                WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
                if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                    return;
                }

                GhostPlayer ghost = PlayerGhostManager.getGhostByEntityId(interact.getEntityId());
                if (ghost == null) {
                    return;
                }

                Player attacker = event.getPlayer();
                if (attacker == null) {
                    return;
                }

                // Bukkit events must be fired on the main thread; packets arrive on a netty thread.
                Bukkit.getScheduler().runTask(MammothPlugin.getPluginInstance(),
                        () -> Bukkit.getPluginManager().callEvent(new OutgoingPlayerHitEvent(attacker, ghost)));

                sendTo(attacker, new WrapperPlayServerHurtAnimation(ghost.getEntityId(), ghost.getYaw()));
            }
        };

        PacketEvents.getAPI().getEventManager().registerListener(listener);
        return true;
    }

    public static void close() {
        if (listener != null && isPacketEventsLoaded()) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        }
        listener = null;
    }

    /** PacketEvents is an optional dependency, so its classes may not be on the classpath at all. */
    public static boolean isPacketEventsLoaded() {
        try {
            return PacketEvents.getAPI() != null && PacketEvents.getAPI().isLoaded();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isPacketEventsInitialized() {
        try {
            return PacketEvents.getAPI() != null && PacketEvents.getAPI().isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void sendTo(Player player, PacketWrapper<?> packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    public static void broadcast(PacketWrapper<?> packet) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendTo(player, packet);
        }
    }

    public static void sendJoinPacket(GhostPlayer ghost) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendJoinPacket(ghost, player);
        }
    }

    public static void sendJoinPacket(GhostPlayer ghost, Player player) {
        if (player == null) {
            return;
        }
        for (PacketWrapper<?> packet : buildSpawnPackets(ghost)) {
            sendTo(player, packet);
        }
    }

    public static void sendLeavePacket(GhostPlayer ghost) {
        broadcast(new WrapperPlayServerPlayerInfoRemove(ghost.getUuid()));
        broadcast(new WrapperPlayServerDestroyEntities(ghost.getEntityId()));
    }

    public static void sendLeavePacket(GhostPlayer ghost, Player player) {
        if (player == null) {
            return;
        }
        sendTo(player, new WrapperPlayServerPlayerInfoRemove(ghost.getUuid()));
        sendTo(player, new WrapperPlayServerDestroyEntities(ghost.getEntityId()));
    }

    private static List<PacketWrapper<?>> buildSpawnPackets(GhostPlayer ghost) {
        UserProfile profile = new UserProfile(ghost.getUuid(), ghost.getName());
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile, true, 0, GameMode.SURVIVAL, null, null);

        return List.of(
                new WrapperPlayServerPlayerInfoUpdate(
                        EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                        info),
                new WrapperPlayServerSpawnEntity(
                        ghost.getEntityId(),
                        Optional.of(ghost.getUuid()),
                        EntityTypes.PLAYER,
                        new Vector3d(ghost.getX(), ghost.getY(), ghost.getZ()),
                        ghost.getPitch(),
                        ghost.getYaw(),
                        ghost.getYaw(),
                        0,
                        Optional.of(new Vector3d(0, 0, 0))),
                new WrapperPlayServerEntityHeadLook(ghost.getEntityId(), ghost.getYaw()),
                // Without this the ghost renders with no cape, jacket, sleeves or hat.
                new WrapperPlayServerEntityMetadata(ghost.getEntityId(),
                        Collections.singletonList(
                                new EntityData<>(SKIN_PARTS_INDEX, EntityDataTypes.BYTE, ALL_SKIN_PARTS))));
    }

    /** Removes a ghost from every player's tab list without despawning its entity. */
    public static void removeFromTabList(UUID uuid) {
        broadcast(new WrapperPlayServerPlayerInfoRemove(uuid));
    }
}
