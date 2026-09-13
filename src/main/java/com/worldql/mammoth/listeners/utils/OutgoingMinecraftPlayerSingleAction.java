package com.worldql.mammoth.listeners.utils;

import com.worldql.mammoth.transport.ClusterMessage;
import com.google.flatbuffers.FlexBuffersBuilder;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.worldql_serialization.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.nio.ByteBuffer;

public class OutgoingMinecraftPlayerSingleAction {
    public static void sendPacket(Location playerLocation, Player player, String action) {
        FlexBuffersBuilder b = Codec.getFlexBuilder();
        int pmap = b.startMap();
        b.putString("action", action);
        b.putString("username", player.getName());
        b.putString("uuid", player.getUniqueId().toString());
        b.endMap(null, pmap);
        ByteBuffer bb = b.finish();

        MammothPlugin.transport().publishToRegion(
                ClusterMessage.at(player.getWorld().getName(), new Vec3D(playerLocation), "MinecraftPlayerSingleAction", bb));
    }
    // Used for placing entities like end crystals.
    public static void sendPlaceEndCrystalPacket(World world, Location targetLocation) {
        MammothPlugin.transport().publishToRegion(
                ClusterMessage.at(world.getName(), new Vec3D(targetLocation), "MinecraftEndCrystalCreate", null));
    }
}
