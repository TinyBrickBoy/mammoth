package com.worldql.mammoth.transport;

import com.worldql.mammoth.worldql_serialization.Vec3D;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Carries messages between the servers of a Mammoth cluster.
 * <p>
 * Everything Mammoth needs from a message broker is behind this interface, so swapping the broker
 * out means writing one implementation rather than touching every listener. The WorldQL/ZeroMQ
 * implementation lives in {@code transport.worldql}.
 */
public interface MammothTransport extends AutoCloseable {

    /** Connects and begins delivering incoming messages to the handler. */
    void start(@NotNull ClusterMessageHandler handler);

    /** Sends to every other server in the cluster. */
    void broadcast(@NotNull ClusterMessage message);

    /** Sends to every server in the cluster, this one included. */
    void broadcastIncludingSelf(@NotNull ClusterMessage message);

    /** Sends to the other servers watching the region the message sits in. */
    void publishToRegion(@NotNull ClusterMessage message);

    /** Sends to every server watching the region, this one included. */
    void publishToRegionIncludingSelf(@NotNull ClusterMessage message);

    /** Starts receiving region scoped messages for the region containing this position. */
    void subscribeToRegion(@NotNull String worldName, @NotNull Vec3D position);

    /** Stops receiving region scoped messages for the region containing this position. */
    void unsubscribeFromRegion(@NotNull String worldName, @NotNull Vec3D position);

    /** This server's identity within the cluster. */
    @NotNull UUID localServerId();

    /** @return true while the transport has heard from the rest of the cluster recently. */
    boolean isConnected();

    @Override
    void close();
}
