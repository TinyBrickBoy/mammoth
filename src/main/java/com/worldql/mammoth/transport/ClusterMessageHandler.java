package com.worldql.mammoth.transport;

import com.worldql.mammoth.worldql_serialization.Record;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Receives what the rest of the cluster sends. Implemented by Mammoth, called by the transport.
 * <p>
 * Callbacks arrive on the transport's own thread, never on the server thread.
 */
public interface ClusterMessageHandler {

    /**
     * @param scope    how the message was addressed
     * @param message  the message
     * @param fromSelf true when this server sent it and asked to receive its own copy
     */
    void onClusterMessage(@NotNull MessageScope scope, @NotNull ClusterMessage message, boolean fromSelf);

    /** Stored world changes coming back from a {@link RecordStore#requestRegion} call. */
    void onStoredRecords(@NotNull List<Record> records);

    /** The transport reached the rest of the cluster. May fire again after a reconnect. */
    default void onConnected() {
    }
}
