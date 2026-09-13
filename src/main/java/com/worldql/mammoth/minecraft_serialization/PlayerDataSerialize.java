package com.worldql.mammoth.minecraft_serialization;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Base64;

/**
 * Turns inventories into Base64 strings so they can live in redis.
 * <p>
 * This used to go through Bukkit's object streams, which serialize Java objects and break as soon
 * as the server updates. The NBT based item serialization runs Minecraft's own data upgrader
 * instead, so an inventory saved before a version bump still loads afterwards.
 */
public class PlayerDataSerialize {
    /**
     * Converts the player inventory to a String array of Base64 strings. First string is the content and second string is the armor.
     *
     * @param playerInventory to turn into an array of strings.
     * @return Array of strings: [ main content, armor content ]
     */
    public static String[] playerInventoryToBase64(PlayerInventory playerInventory) {
        // getContents() already includes the armor slots, but they are stored separately so the
        // two halves can be restored independently.
        String content = toBase64(playerInventory);
        String armor = itemStackArrayToBase64(playerInventory.getArmorContents());

        return new String[]{content, armor};
    }

    /**
     * A method to serialize an {@link ItemStack} array to a Base64 String.
     *
     * @param items to turn into a Base64 String.
     * @return Base64 string of the items.
     */
    public static String itemStackArrayToBase64(ItemStack[] items) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }

    /**
     * A method to serialize an inventory to a Base64 string.
     *
     * @param inventory to serialize
     * @return Base64 string of the provided inventory
     */
    public static String toBase64(Inventory inventory) {
        return itemStackArrayToBase64(inventory.getContents());
    }

    /**
     * Gets an array of ItemStacks from a Base64 string.
     *
     * @param data Base64 string to convert to ItemStack array.
     * @return ItemStack array created from the Base64 string.
     */
    public static ItemStack[] itemStackArrayFromBase64(String data) {
        // The MIME decoder also accepts the line-wrapped Base64 older versions wrote.
        return ItemStack.deserializeItemsFromBytes(Base64.getMimeDecoder().decode(data));
    }
}
