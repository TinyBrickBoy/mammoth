package com.worldql.mammoth;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Draws a curtain of particles along nearby server borders so players can see where a slice ends
 * before they walk into the DMZ (issue #63).
 * <p>
 * The particles are sent only to the player they are drawn for, so a busy border does not spam
 * everyone else's client.
 */
public class BorderParticleTask implements Runnable {
    /** How far from a border a player has to be for the wall to show. */
    private static final int VIEW_DISTANCE = 12;
    /** How far along the wall the curtain is drawn either side of the player. */
    private static final int WALL_RADIUS = 8;
    /** Half the height of the drawn curtain, in blocks. */
    private static final int HEIGHT = 4;
    /** Spacing between particles along and up the wall. Kept coarse to stay cheap. */
    private static final double STEP = 2.0;

    @Override
    public void run() {
        if (!Slices.enabled) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation();
            if (Slices.getDistanceFromSliceBoundary(location) > VIEW_DISTANCE) {
                continue;
            }

            for (double borderX : Slices.nearbyBorderPlanesX(location, VIEW_DISTANCE)) {
                drawWall(player, location, borderX, true);
            }
            for (double borderZ : Slices.nearbyBorderPlanesZ(location, VIEW_DISTANCE)) {
                drawWall(player, location, borderZ, false);
            }
        }
    }

    /**
     * Draws one vertical plane. {@code alongX} tells us whether the wall runs across the X axis
     * (so the plane sits at a fixed X and extends along Z) or the other way round.
     */
    private static void drawWall(Player player, Location center, double border, boolean alongX) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        double from = (alongX ? center.getZ() : center.getX()) - WALL_RADIUS;
        double to = from + WALL_RADIUS * 2;

        for (double offset = Math.floor(from); offset <= to; offset += STEP) {
            for (double y = center.getY() - HEIGHT; y <= center.getY() + HEIGHT; y += STEP) {
                Location particle = alongX
                        ? new Location(world, border, y, offset)
                        : new Location(world, offset, y, border);
                player.spawnParticle(Particle.END_ROD, particle, 1, 0, 0, 0, 0);
            }
        }
    }
}
