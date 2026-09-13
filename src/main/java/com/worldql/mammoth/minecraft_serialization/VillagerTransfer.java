package com.worldql.mammoth.minecraft_serialization;

import com.worldql.mammoth.transport.ClusterMessage;
import com.google.flatbuffers.FlexBuffers;
import com.google.flatbuffers.FlexBuffersBuilder;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.worldql_serialization.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.nio.ByteBuffer;
import java.util.UUID;

public class VillagerTransfer {
    public static void sendVillagerTransferMessage(Player p, String serializedVillager) {
        FlexBuffersBuilder b = Codec.getFlexBuilder();
        int pmap = b.startMap();
        b.putString("villagernbt", serializedVillager);
        b.putString("uuid", p.getUniqueId().toString());
        b.endMap(null, pmap);
        ByteBuffer bb = b.finish();

        MammothPlugin.transport().broadcast(
                ClusterMessage.at(ClusterMessage.ANY_WORLD, new Vec3D(p.getLocation()), "MinecraftVillagerTransfer", bb));
    }
    public static void handleIncomingVillager(ClusterMessage incoming) {
        FlexBuffers.Map villagerMessageMap = FlexBuffers.getRoot(incoming.payload()).asMap();
        String serializedVillager = villagerMessageMap.get("villagernbt").asString();
        Bukkit.getScheduler().runTask(MammothPlugin.getPluginInstance(), () -> {
            Player p = Bukkit.getPlayer(UUID.fromString(villagerMessageMap.get("uuid").asString()));
            if (p == null) {
                return;
            }
            Entity v = EntitySerialization.spawn(serializedVillager, p.getLocation());
            if (v == null) {
                return;
            }
            Bukkit.getScheduler().runTaskLater(MammothPlugin.getPluginInstance(), () -> {
                v.teleport(p);
                if (p.isInsideVehicle()) {
                    p.getVehicle().addPassenger(v);
                }
            }, 5L);
        });
    }
}
