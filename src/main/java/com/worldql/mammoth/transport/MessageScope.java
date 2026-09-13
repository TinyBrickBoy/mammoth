package com.worldql.mammoth.transport;

/** How widely a {@link ClusterMessage} is delivered. */
public enum MessageScope {
    /** Every server in the cluster, wherever it is in the world. */
    CLUSTER,
    /** Only the servers that subscribed to the region the message sits in. */
    REGION
}
