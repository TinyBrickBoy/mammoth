package com.worldql.mammoth.listeners.utils;

import org.bukkit.inventory.ItemStack;

import java.nio.ByteBuffer;

/**
 * Item serialization for the WorldQL wire format.
 * <p>
 * Bukkit's object streams were replaced with the NBT based item serialization: it survives
 * Minecraft data upgrades, so items keep their meaning when a cluster is updated.
 */
public class ItemTools {
    public static byte[] serializeItemStack(ItemStack[] items) {
        return ItemStack.serializeItemsAsBytes(items);
    }

    public static ItemStack[] deserializeItemStack(ByteBuffer buf) {
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return ItemStack.deserializeItemsFromBytes(bytes);
    }
}
