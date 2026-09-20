package com.worldql.mammoth.minecraft_serialization;

import com.worldql.mammoth.MammothPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Base64;
import java.util.logging.Level;

/**
 * Moves whole entities between servers.
 * <p>
 * This used to poke at NBT through server internals, which tied Mammoth to one exact Minecraft
 * build. Bukkit's own entity serialization does the same job and keeps working across releases,
 * and it round-trips the entity's real type, so a chest boat stays a chest boat instead of coming
 * back as a plain one.
 */
public final class EntitySerialization {

    private EntitySerialization() {
    }

    /** Serializes an entity to a Base64 string that can be sent over redis or WorldQL. */
    @SuppressWarnings("deprecation") // getUnsafe() is the only API that can serialize a whole entity.
    public static String serialize(Entity entity) {
        return Base64.getEncoder().encodeToString(Bukkit.getUnsafe().serializeEntity(entity));
    }

    /**
     * Recreates a previously serialized entity and spawns it.
     *
     * @return the spawned entity, or null if the data could not be read.
     */
    @SuppressWarnings("deprecation") // getUnsafe() is the only API that can deserialize a whole entity.
    public static Entity spawn(String serialized, Location location) {
        if (serialized == null || location.getWorld() == null) {
            return null;
        }

        try {
            byte[] data = Base64.getDecoder().decode(serialized);
            Entity entity = Bukkit.getUnsafe().deserializeEntity(data, location.getWorld());
            if (!entity.spawnAt(location, CreatureSpawnEvent.SpawnReason.CUSTOM)) {
                return null;
            }
            return entity;
        } catch (IllegalArgumentException e) {
            MammothPlugin.getPluginInstance().getLogger()
                    .log(Level.WARNING, "Failed to deserialize a transferred entity.", e);
            return null;
        }
    }
}
