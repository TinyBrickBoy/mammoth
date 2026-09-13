package com.worldql.mammoth.transport;

import com.worldql.mammoth.worldql_serialization.Record;
import com.worldql.mammoth.worldql_serialization.Vec3D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Something one server in the cluster wants to tell the others.
 * <p>
 * This is deliberately free of any broker's vocabulary: no instruction codes, no replication flags,
 * no socket. A {@link MammothTransport} decides how to put it on the wire, which is what makes the
 * broker underneath replaceable.
 *
 * @param worldName the world the message belongs to, or {@link #ANY_WORLD} for cluster wide news
 * @param position  where in the world this happened, used to route region scoped messages
 * @param parameter the message type, such as "MinecraftPlayerMove"
 * @param records   world changes to persist or apply, if any
 * @param payload   the FlexBuffer body, if any
 */
public record ClusterMessage(
        @NotNull String worldName,
        @Nullable Vec3D position,
        @Nullable String parameter,
        @Nullable List<Record> records,
        @Nullable ByteBuffer payload
) {
    /** Stands in for the world name on messages that are not tied to one world. */
    public static final String ANY_WORLD = "@global";

    /** A message with a body, tied to a place in the world. */
    public static ClusterMessage at(@NotNull String worldName, @Nullable Vec3D position,
                                    @Nullable String parameter, @Nullable ByteBuffer payload) {
        return new ClusterMessage(worldName, position, parameter, null, payload);
    }

    /** A message with a body that is not tied to any particular place. */
    public static ClusterMessage anywhere(@Nullable String parameter, @Nullable ByteBuffer payload) {
        return new ClusterMessage(ANY_WORLD, null, parameter, null, payload);
    }

    /** A message carrying world changes. */
    public static ClusterMessage of(@NotNull String worldName, @Nullable Vec3D position,
                                    @Nullable String parameter, @Nullable List<Record> records) {
        return new ClusterMessage(worldName, position, parameter, records, null);
    }

    public ClusterMessage withParameter(@Nullable String parameter) {
        return new ClusterMessage(worldName, position, parameter, records, payload);
    }

    public ClusterMessage withWorldName(@NotNull String worldName) {
        return new ClusterMessage(worldName, position, parameter, records, payload);
    }

    /** @return the records, never null, so callers can iterate without a null check. */
    public @NotNull List<Record> recordsOrEmpty() {
        return records == null ? List.of() : records;
    }

    public boolean hasParameter(@NotNull String expected) {
        return expected.equals(parameter);
    }
}
