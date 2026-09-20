package com.worldql.mammoth.listeners;

import com.worldql.mammoth.Slices;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Keeps block state that Mammoth cannot synchronise out of the DMZ.
 * <p>
 * A block on a server boundary is mirrored onto two servers, so anything holding state that is not
 * replicated - an inventory, a note block's pitch, a jukebox's disc - can be read on one side and
 * changed on the other, which duplicates items and desyncs the two copies. Those blocks are simply
 * not usable there (issues #32, #35 and #49).
 */
public class DmzProtectionListener implements Listener {

    private static final Component CANNOT_PLACE =
            Component.text("You cannot place this here.", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text(" Move further from a server border.", NamedTextColor.RED));
    private static final Component CANNOT_USE =
            Component.text("You cannot use this here.", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text(" Move further from a server border.", NamedTextColor.RED));

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!isProtected(e.getBlockPlaced())) {
            return;
        }
        e.setCancelled(true);
        e.getPlayer().sendMessage(CANNOT_PLACE);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        if (!isProtected(e.getClickedBlock())) {
            return;
        }
        e.setCancelled(true);
        e.getPlayer().sendMessage(CANNOT_USE);
    }

    private static boolean isProtected(Block block) {
        if (!Slices.enabled) {
            return false;
        }
        Location location = block.getLocation();
        return Slices.isDMZ(location) && holdsUnsyncedState(block);
    }

    private static boolean holdsUnsyncedState(Block block) {
        Material type = block.getType();
        // Note blocks store their pitch in the block data and jukeboxes hold a disc; both are
        // changed by right-clicking, so two mirrored copies drift apart immediately.
        if (type == Material.NOTE_BLOCK || type == Material.JUKEBOX) {
            return true;
        }
        if (Tag.SHULKER_BOXES.isTagged(type)) {
            return true;
        }
        // getState() reads the block entity, so this covers chests, furnaces, hoppers, barrels,
        // droppers, dispensers and anything else holding an inventory.
        return block.getState(false) instanceof Container;
    }
}
