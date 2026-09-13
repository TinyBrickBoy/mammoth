package com.worldql.mammoth;

import com.worldql.mammoth.transport.ClusterMessage;
import com.worldql.mammoth.worldql_serialization.*;
import com.worldql.mammoth.worldql_serialization.Record;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class MinecraftUtil {
    public static String itemStackArrayToBase64(ItemStack[] items) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }

    public static ItemStack[] itemStackArrayFromBase64(String data) {
        return ItemStack.deserializeItemsFromBytes(Base64.getMimeDecoder().decode(data));
    }
    public static void setBed(Block head, BlockFace facing, Material material) {
        for (Bed.Part part : Bed.Part.values()) {
            Bed bedData = (Bed) Bukkit.createBlockData(material, (data) -> {
                ((Bed) data).setPart(part);
                ((Bed) data).setFacing(facing);
            });
            head.setBlockData(bedData);
            head = head.getRelative(facing.getOppositeFace());
        }
    }

    public static void sendAirBlock(Location l) {
        Record airBlock = new Record(
                UUID.nameUUIDFromBytes(l.toString().getBytes(StandardCharsets.UTF_8)),
                new Vec3D(l),
                l.getWorld().getName(),
                "minecraft:air",
                null
        );
        MammothPlugin.transport().broadcastIncludingSelf(
                ClusterMessage.of(ClusterMessage.ANY_WORLD, new Vec3D(l), "MinecraftBlockUpdate", List.of(airBlock)));
    }
    public static void breakConnectedBlock(Block b) {
        BlockData bd = b.getBlockData();
        if (bd instanceof Door door) {
            Location target;
            if (door.getHalf().equals(Bisected.Half.BOTTOM)) {
                target = b.getRelative(BlockFace.UP).getLocation();
            } else {
                target = b.getRelative(BlockFace.DOWN).getLocation();
            }
            sendAirBlock(target);
            return;
        }
        if (bd instanceof Bed) {
            Bed bed = (Bed) bd;
            Location target;
            target = b.getRelative(bed.getFacing().getOppositeFace()).getLocation();
            sendAirBlock(target);
            return;
        }
    }
}
