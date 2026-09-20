package com.worldql.mammoth.listeners.explosions;

import com.worldql.mammoth.transport.ClusterMessage;
import com.google.flatbuffers.FlexBuffersBuilder;
import com.worldql.mammoth.Slices;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.worldql_serialization.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExplosionPrimeEvent;

import java.nio.ByteBuffer;

public class ExplosionPrimeEventListener implements Listener {
    @EventHandler
    public void onExplosionPrime(ExplosionPrimeEvent e) {
        // Send a message to create an explosion effect on the other server too :)
        // This is another LocalMessage to be passed to other MinecraftServers, but with the parameter
        // "MinecraftExplosion" instead of "MinecraftBlockUpdate". Parameters do not define behavior in any
        // special way. They are simply passed to the client and can be processed like any string.

        if (Slices.enabled) {
            return;
        }

        FlexBuffersBuilder b = Codec.getFlexBuilder();
        int pmap = b.startMap();
        b.putFloat("radius", e.getRadius());
        b.endMap(null, pmap);
        ByteBuffer bb = b.finish();

        MammothPlugin.transport().publishToRegion(
                ClusterMessage.at(e.getEntity().getWorld().getName(), new Vec3D(e.getEntity().getLocation()), "MinecraftExplosion", bb));
    }
}
