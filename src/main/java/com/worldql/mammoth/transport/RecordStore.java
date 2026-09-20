package com.worldql.mammoth.transport;

import com.worldql.mammoth.worldql_serialization.Vec3D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Where permanent world changes live.
 * <p>
 * Separate from {@link MammothTransport} because the two are not the same problem: messages are
 * transient and want low latency, records are durable and want a database. WorldQL happened to do
 * both, so its implementation implements both, but a future backend can split them.
 */
public interface RecordStore {

    /** Records a permanent change to the world, without notifying anyone. */
    void save(@NotNull ClusterMessage message);

    /**
     * Records a permanent change and tells the servers watching that region straight away.
     * Stored changes on their own are only replayed when a chunk loads.
     */
    void saveAndPublish(@NotNull ClusterMessage message);

    /**
     * Asks for the stored changes in the region around this position. The answer arrives
     * asynchronously on {@link ClusterMessageHandler#onStoredRecords}.
     *
     * @param since only return changes newer than this, as epoch milliseconds; null for all of them
     */
    void requestRegion(@NotNull String worldName, @NotNull Vec3D position, @Nullable String since);
}
