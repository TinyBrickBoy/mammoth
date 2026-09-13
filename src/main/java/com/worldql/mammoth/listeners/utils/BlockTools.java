package com.worldql.mammoth.listeners.utils;

import com.google.flatbuffers.FlexBuffers;
import com.google.flatbuffers.FlexBuffersBuilder;
import com.worldql.mammoth.MinecraftUtil;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.listeners.world.PlayerBreakBlockListener;
import com.worldql.mammoth.worldql_serialization.Codec;
import com.worldql.mammoth.worldql_serialization.Record;
import com.worldql.mammoth.worldql_serialization.Vec3D;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class BlockTools {
    public static Record serializeBlock(@NotNull Block block) {
        FlexBuffersBuilder b = Codec.getFlexBuilder();
        int pmap = b.startMap();

        Location location = block.getLocation();
        World world = location.getWorld();

        // Save the block entity, if this block has one. Wrapping the state in the block's item form
        // is what lets us serialize it through the API instead of reaching into server internals:
        // an item carrying a BlockStateMeta round-trips a chest's contents, a sign's text and so on.
        byte[] tile = serializeTileState(block);
        if (tile != null) {
            b.putBoolean("isTile", true);
            b.putBlob("tile", tile);
        } else {
            b.putBoolean("isTile", false);
        }

        b.endMap(null, pmap);
        ByteBuffer bb = b.finish();

        return new Record(
                UUID.nameUUIDFromBytes(location.toString().getBytes(StandardCharsets.UTF_8)),
                new Vec3D(location),
                world.getName(),
                block.getBlockData().getAsString(),
                bb
        );
    }

    public static Record airBlock(@NotNull Location location, @Nullable ItemStack[] drops) {
        FlexBuffersBuilder b = Codec.getFlexBuilder();
        int pmap = b.startMap();

        // Save drops
        if (drops != null) {
            b.putBlob("drops", ItemTools.serializeItemStack(drops));
        }

        b.endMap(null, pmap);
        ByteBuffer bb = b.finish();

        return new Record(
                UUID.nameUUIDFromBytes(location.toString().getBytes(StandardCharsets.UTF_8)),
                new Vec3D(location),
                location.getWorld().getName(),
                "minecraft:air",
                bb
        );
    }

    public static void setRecords(List<Record> records, boolean isSelf) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Record record : records) {
                    setRecord(record, isSelf);
                }
            }

        }.runTask(MammothPlugin.pluginInstance);
    }

    private static void setRecord(@NotNull Record record, boolean isSelf) {

        if (record.data().startsWith("minecraft:fire")) {
            return;
        }

        Vec3D p = record.position();
        Block b = Bukkit.getWorld(record.worldName()).getBlockAt((int) p.x(), (int) p.y(), (int) p.z());

        BlockData bd = Bukkit.createBlockData(record.data());

        if (Tag.BEDS.isTagged(bd.getMaterial())) {
            b.setBlockData(bd, false);
            Bed bed = (Bed) bd;
            // get the location of the head of the bed
            Location l = b.getLocation().add(bed.getFacing().getDirection());
            MinecraftUtil.setBed(l.getBlock(), bed.getFacing(), bd.getMaterial());
        } else if (Tag.DOORS.isTagged(bd.getMaterial())) {
            b.setBlockData(bd, false);
            Door door = (Door) bd;
            if (door.getHalf().equals(Bisected.Half.BOTTOM)) {
                Block top = b.getRelative(BlockFace.UP);
                Door doorData = (Door) Bukkit.createBlockData(bd.getMaterial(), (data) -> {
                    ((Door) data).setHalf(Bisected.Half.TOP);
                    ((Door) data).setOpen(((Door) bd).isOpen());
                    ((Door) data).setHinge(((Door) bd).getHinge());
                    ((Door) data).setFacing(((Door) bd).getFacing());
                });
                top.setBlockData(doorData, false);
            }
        } else {
            // We want block physics because it makes things like glass panes sync right.
            b.setBlockData(bd, true);
        }

        if (record.flex() != null) {
            FlexBuffers.Map map = FlexBuffers.getRoot(record.flex()).asMap();

            // Handle drops
            if (!map.get("drops").isNull() && isSelf && PlayerBreakBlockListener.pendingDrops.contains(record.uuid())) {
                PlayerBreakBlockListener.pendingDrops.remove(record.uuid());

                Location blockCenter = b.getLocation().add(0.5, 0.5, 0.5);
                for (ItemStack item : ItemTools.deserializeItemStack(map.get("drops").asBlob().data())) {
                    b.getWorld().dropItem(blockCenter, item);
                }
            }

            // Handle block entity data
            if (!map.get("isTile").isNull() && map.get("isTile").asBoolean() && !map.get("tile").isNull()) {
                applyTileState(b, map.get("tile").asBlob().getBytes());
            }
        }
    }

    /**
     * @return the block entity at this block serialized as bytes, or null if the block has no
     * block entity (or one that cannot be carried in item form).
     */
    private static byte[] serializeTileState(@NotNull Block block) {
        BlockState state = block.getState();
        if (!(state instanceof TileState)) {
            return null;
        }

        ItemStack carrier = new ItemStack(block.getType());
        if (!(carrier.getItemMeta() instanceof BlockStateMeta meta)) {
            return null;
        }

        meta.setBlockState(state);
        carrier.setItemMeta(meta);
        return carrier.serializeAsBytes();
    }

    private static void applyTileState(@NotNull Block block, byte[] serialized) {
        try {
            ItemStack carrier = ItemStack.deserializeBytes(serialized);
            if (carrier.getItemMeta() instanceof BlockStateMeta meta && meta.hasBlockState()) {
                meta.getBlockState().copy(block.getLocation()).update(true, false);
            }
        } catch (IllegalArgumentException e) {
            MammothPlugin.getPluginInstance().getLogger()
                    .warning("Failed to apply synced block entity data at " + block.getLocation() + ".");
        }
    }

    public static void createExplosion(Vec3D position, String worldName, float radius) {
        new BukkitRunnable() {
            @Override
            public void run() {
                World w = Bukkit.getWorld(worldName);
                w.createExplosion(
                        position.x(), position.y(), position.z(),
                        radius, false, false);

            }
        }.runTask(MammothPlugin.pluginInstance);
    }

    public static void createPrimedTNT(Vec3D position, String worldName) {
        new BukkitRunnable() {
            @Override
            public void run() {
                World w = Bukkit.getWorld(worldName);
                Location tntLocation = new Location(w, position.x(), position.y(), position.z());
                TNTPrimed tnt = w.spawn(tntLocation, TNTPrimed.class);
                w.getBlockAt(tntLocation).setType(Material.AIR);

            }
        }.runTask(MammothPlugin.pluginInstance);
    }

    public static void createEndCrystal(Vec3D position, String worldName) {
        new BukkitRunnable() {
            @Override
            public void run() {
                World w = Bukkit.getWorld(worldName);
                if (w == null) {
                    return;
                }
                Location location = new Location(w, position.x(), position.y(), position.z());

                // Two servers can both see the same placement (the placing server spawns one
                // naturally, and overlapping subscription regions can deliver the message twice),
                // which used to leave a stack of crystals on one block (issue #46).
                for (Entity nearby : w.getNearbyEntities(location, 0.6, 0.6, 0.6)) {
                    if (nearby instanceof EnderCrystal) {
                        return;
                    }
                }

                w.spawn(location, EnderCrystal.class);
            }
        }.runTask(MammothPlugin.pluginInstance);
    }
}
