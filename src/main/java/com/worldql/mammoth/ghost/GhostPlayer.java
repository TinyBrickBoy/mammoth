package com.worldql.mammoth.ghost;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Instant;
import java.util.UUID;

/**
 * A player that is really connected to a different server in the cluster, mirrored onto this one.
 * <p>
 * Ghosts are not Bukkit entities: they exist only as packets sent to the players connected here.
 * That means they carry their own entity id and position instead of wrapping a server-side entity,
 * which also keeps the whole ghost system free of version specific server internals.
 */
public class GhostPlayer {
    private final UUID uuid;
    private final String name;
    private final int entityId;

    private volatile String worldName;
    private volatile double x;
    private volatile double y;
    private volatile double z;
    private volatile float yaw;
    private volatile float pitch;

    private volatile long lastAccessed;

    public GhostPlayer(UUID uuid, String name, int entityId, Location location) {
        this.uuid = uuid;
        this.name = name;
        this.entityId = entityId;
        this.lastAccessed = Instant.now().getEpochSecond();
        setPosition(location);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public int getEntityId() {
        return entityId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPosition(Location location) {
        this.worldName = location.getWorld() == null ? null : location.getWorld().getName();
        setPosition(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public void setPosition(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        touch();
    }

    /**
     * @return the ghost's last known location, or null if the world it was last seen in is not
     * loaded on this server.
     */
    public Location getLocation() {
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    /** Marks the ghost as still alive so the expiry sweep leaves it alone. */
    public void touch() {
        lastAccessed = Instant.now().getEpochSecond();
    }

    /**
     * Players who walk out of this server's subscription range stop being updated without ever
     * sending a disconnect, so ghosts that went quiet are swept up instead of lingering forever.
     */
    public boolean shouldExpire() {
        return Instant.now().getEpochSecond() - lastAccessed > 120;
    }
}
