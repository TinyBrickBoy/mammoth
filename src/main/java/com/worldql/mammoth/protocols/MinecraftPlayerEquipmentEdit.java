package com.worldql.mammoth.protocols;

import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.google.flatbuffers.FlexBuffers;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.ghost.GhostPlayer;
import com.worldql.mammoth.worldql_serialization.Message;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Locale;

public class MinecraftPlayerEquipmentEdit {

    public static void process(Message state, GhostPlayer ghost) {
        FlexBuffers.Map playerMessageMap = FlexBuffers.getRoot(state.flex()).asMap();

        EquipmentSlot slot = parseSlot(playerMessageMap.get("type").asString());
        if (slot == null) {
            return;
        }

        Material material = Material.matchMaterial(playerMessageMap.get("material").asString());
        if (material == null) {
            return;
        }

        ItemStack item = new ItemStack(material);
        // Only the glint matters here; the ghost's item is cosmetic.
        if (playerMessageMap.get("enchanted").asBoolean()) {
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        }

        ProtocolManager.broadcast(new WrapperPlayServerEntityEquipment(ghost.getEntityId(),
                Collections.singletonList(new Equipment(slot, SpigotConversionUtil.fromBukkitItemStack(item)))));
    }

    private static EquipmentSlot parseSlot(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "mainhand", "main_hand", "hand" -> EquipmentSlot.MAIN_HAND;
            case "offhand", "off_hand" -> EquipmentSlot.OFF_HAND;
            case "feet", "boots" -> EquipmentSlot.BOOTS;
            case "legs", "leggings" -> EquipmentSlot.LEGGINGS;
            case "chest", "chestplate" -> EquipmentSlot.CHEST_PLATE;
            case "head", "helmet" -> EquipmentSlot.HELMET;
            default -> {
                MammothPlugin.getPluginInstance().getLogger()
                        .warning("Received equipment update for unknown slot '" + name + "'.");
                yield null;
            }
        };
    }
}
