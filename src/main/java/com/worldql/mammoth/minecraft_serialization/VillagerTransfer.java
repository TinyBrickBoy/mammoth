package com.worldql.mammoth.minecraft_serialization;

import com.google.flatbuffers.FlexBuffers;
import com.google.flatbuffers.FlexBuffersBuilder;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.worldql_serialization.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import zmq.ZMQ;

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

        Message message = new Message(
                Instruction.GlobalMessage,
                MammothPlugin.worldQLClientId,
                "@global",
                Replication.ExceptSelf,
                new Vec3D(p.getLocation()),
                null,
                null,
                "MinecraftVillagerTransfer",
                bb
        );

        MammothPlugin.getPluginInstance().getPushSocket().send(message.encode(), ZMQ.ZMQ_DONTWAIT);
    }
    public static void handleIncomingVillager(Message incoming) {
        FlexBuffers.Map villagerMessageMap = FlexBuffers.getRoot(incoming.flex()).asMap();
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
