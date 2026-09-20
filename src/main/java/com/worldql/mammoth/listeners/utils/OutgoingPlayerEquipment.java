package com.worldql.mammoth.listeners.utils;

import com.worldql.mammoth.transport.ClusterMessage;
import com.google.flatbuffers.FlexBuffersBuilder;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.worldql_serialization.Codec;
import com.worldql.mammoth.worldql_serialization.Vec3D;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.nio.ByteBuffer;

/**
 * Tells the rest of the cluster what a player is wearing and holding.
 * <p>
 * Equipment is normally only sent when it changes, so a player who reconnects looked naked to
 * everyone on the other servers until they took a piece off and put it back on. Broadcasting the
 * full loadout once on join fixes that (issue #52).
 */
public final class OutgoingPlayerEquipment {

    private OutgoingPlayerEquipment() {
    }

    /** Sends every equipment slot of this player, so remote ghosts render them correctly. */
    public static void broadcastAll(Player player) {
        PlayerInventory inventory = player.getInventory();
        send(player, "head", inventory.getHelmet());
        send(player, "chest", inventory.getChestplate());
        send(player, "legs", inventory.getLeggings());
        send(player, "feet", inventory.getBoots());
        send(player, "mainhand", inventory.getItemInMainHand());
        send(player, "offhand", inventory.getItemInOffHand());
    }

    private static void send(Player player, String slot, ItemStack item) {
        // An empty slot is sent as air so ghosts that still show an old piece drop it.
        Material material = item == null ? Material.AIR : item.getType();
        boolean enchanted = item != null && !item.getEnchantments().isEmpty();

        FlexBuffersBuilder b = Codec.getFlexBuilder();
        int pmap = b.startMap();
        b.putBoolean("enchanted", enchanted);
        b.putString("material", material.name());
        b.putString("type", slot);
        b.putString("username", player.getName());
        b.putString("uuid", player.getUniqueId().toString());
        b.endMap(null, pmap);
        ByteBuffer bb = b.finish();

        MammothPlugin.transport().publishToRegion(
                ClusterMessage.at(player.getWorld().getName(), new Vec3D(player.getLocation()), "MinecraftPlayerEquipmentEdit", bb));
    }
}
