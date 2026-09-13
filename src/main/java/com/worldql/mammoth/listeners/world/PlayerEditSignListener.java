package com.worldql.mammoth.listeners.world;

import com.worldql.mammoth.Slices;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.listeners.utils.BlockTools;
import com.worldql.mammoth.transport.ClusterMessage;
import com.worldql.mammoth.worldql_serialization.Vec3D;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.SignSide;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

import java.util.List;

public class PlayerEditSignListener implements Listener {
    @EventHandler
    public void onSignEdit(SignChangeEvent e) {
        if (e.getBlock().getState() instanceof Sign sign) {
            // The state is a snapshot taken before the edit, so copy the new lines onto it before
            // serializing, otherwise the other servers receive the sign's previous text.
            SignSide side = sign.getSide(e.getSide());
            List<Component> lines = e.lines();
            for (int i = 0; i < lines.size(); i++) {
                side.line(i, lines.get(i));
            }
            sign.update();
        }
        // This field isn't really used since the Record also contains the position
        // of the changed block(s).
        ClusterMessage update = ClusterMessage.of(
                e.getPlayer().getWorld().getName(),
                new Vec3D(e.getBlock().getLocation()),
                "MinecraftBlockUpdate",
                List.of(BlockTools.serializeBlock(e.getBlock())));

        if (!Slices.enabled) {
            MammothPlugin.records().saveAndPublish(update);
        } else if (Slices.isDMZ(e.getBlock().getLocation())) {
            MammothPlugin.transport().publishToRegion(update);
        }
    }
}
