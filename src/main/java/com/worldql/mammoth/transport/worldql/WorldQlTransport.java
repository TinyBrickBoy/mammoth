package com.worldql.mammoth.transport.worldql;

import com.worldql.mammoth.transport.ClusterMessage;
import com.worldql.mammoth.transport.ClusterMessageHandler;
import com.worldql.mammoth.transport.MammothTransport;
import com.worldql.mammoth.transport.MessageScope;
import com.worldql.mammoth.transport.RecordStore;
import com.worldql.mammoth.worldql_serialization.Instruction;
import com.worldql.mammoth.worldql_serialization.Message;
import com.worldql.mammoth.worldql_serialization.Replication;
import com.worldql.mammoth.worldql_serialization.Vec3D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Talks to a WorldQL server over ZeroMQ.
 * <p>
 * This is the only place left that knows about WorldQL's instruction codes, its replication flags
 * and its two sockets. Everything above it speaks {@link ClusterMessage}.
 */
public class WorldQlTransport implements MammothTransport, RecordStore {

    /** How long without a sign of life before we assume the connection is gone. */
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(5);
    private static final int LISTEN_PORT_MIN = 29000;
    private static final int LISTEN_PORT_MAX = 30000;

    private final Logger logger;
    private final UUID serverId = UUID.randomUUID();

    private final String brokerHost;
    private final int brokerPort;
    /** The address the rest of the cluster is told to reach us on. May be a DNS name. */
    private final String advertisedHost;
    /** The local interface to listen on. ZeroMQ can only bind to an address, never to a name. */
    private final String bindAddress;

    private ZContext context;
    private ZMQ.Socket pushSocket;
    private Thread receiveThread;
    private Thread heartbeatThread;

    private volatile ClusterMessageHandler handler;
    private volatile int listenPort;
    private volatile long lastContactMillis;
    private volatile boolean running;

    public WorldQlTransport(Logger logger, String brokerHost, int brokerPort,
                            String advertisedHost, String bindAddress) {
        this.logger = logger;
        this.brokerHost = brokerHost;
        this.brokerPort = brokerPort;
        this.advertisedHost = advertisedHost;
        this.bindAddress = bindAddress;
    }

    @Override
    public void start(@NotNull ClusterMessageHandler handler) {
        this.handler = handler;
        this.running = true;
        this.lastContactMillis = Instant.now().toEpochMilli();

        context = new ZContext();
        pushSocket = context.createSocket(SocketType.PUSH);
        logger.info("Attempting to connect to WorldQL server.");
        pushSocket.connect("tcp://%s:%d".formatted(brokerHost, brokerPort));

        receiveThread = new Thread(this::receiveLoop, "Mammoth WorldQL receive");
        receiveThread.start();

        heartbeatThread = new Thread(this::heartbeatLoop, "Mammoth WorldQL heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    // region: sending

    @Override
    public void broadcast(@NotNull ClusterMessage message) {
        send(message, Instruction.GlobalMessage, Replication.ExceptSelf);
    }

    @Override
    public void broadcastIncludingSelf(@NotNull ClusterMessage message) {
        send(message, Instruction.GlobalMessage, Replication.IncludingSelf);
    }

    @Override
    public void publishToRegion(@NotNull ClusterMessage message) {
        send(message, Instruction.LocalMessage, Replication.ExceptSelf);
    }

    @Override
    public void publishToRegionIncludingSelf(@NotNull ClusterMessage message) {
        send(message, Instruction.LocalMessage, Replication.IncludingSelf);
    }

    @Override
    public void save(@NotNull ClusterMessage message) {
        send(message, Instruction.RecordCreate, Replication.ExceptSelf);
    }

    @Override
    public void saveAndPublish(@NotNull ClusterMessage message) {
        // WorldQL models this as two messages carrying the same records: one to store, one to
        // deliver to the servers currently watching that region.
        send(message, Instruction.RecordCreate, Replication.ExceptSelf);
        send(message, Instruction.LocalMessage, Replication.ExceptSelf);
    }

    @Override
    public void requestRegion(@NotNull String worldName, @NotNull Vec3D position, @Nullable String since) {
        send(new ClusterMessage(worldName, position, since, null, null),
                Instruction.RecordRead, Replication.ExceptSelf);
    }

    @Override
    public void subscribeToRegion(@NotNull String worldName, @NotNull Vec3D position) {
        send(new ClusterMessage(worldName, position, null, null, null),
                Instruction.AreaSubscribe, Replication.ExceptSelf);
    }

    @Override
    public void unsubscribeFromRegion(@NotNull String worldName, @NotNull Vec3D position) {
        send(new ClusterMessage(worldName, position, null, null, null),
                Instruction.AreaUnsubscribe, Replication.ExceptSelf);
    }

    private void send(ClusterMessage message, Instruction instruction, Replication replication) {
        ZMQ.Socket socket = pushSocket;
        if (socket == null) {
            return;
        }

        Message encoded = new Message(
                instruction,
                serverId,
                message.worldName(),
                replication,
                message.position(),
                message.records(),
                null,
                message.parameter(),
                message.payload()
        );

        try {
            socket.send(encoded.encode(), zmq.ZMQ.ZMQ_DONTWAIT);
        } catch (ZMQException e) {
            // The socket is closed during shutdown; nothing useful to do about it here.
            if (running) {
                logger.log(Level.WARNING, "Failed to send a message to the WorldQL server.", e);
            }
        }
    }

    // endregion

    // region: receiving

    private void receiveLoop() {
        ZMQ.Socket socket = context.createSocket(SocketType.PULL);
        listenPort = socket.bindToRandomPort("tcp://" + bindAddress, LISTEN_PORT_MIN, LISTEN_PORT_MAX);
        sendHandshake();

        while (running) {
            try {
                byte[] reply = socket.recv(0);
                if (reply == null) {
                    continue;
                }
                dispatch(Message.decode(ByteBuffer.wrap(reply)));
            } catch (ZMQException e) {
                if (e.getErrorCode() == ZMQ.Error.ETERM.getCode()) {
                    logger.info("Caught ZeroMQ ETERM exception!");
                    break;
                }
                logger.log(Level.WARNING, "Error while receiving from the WorldQL server.", e);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to handle a message from the WorldQL server.", e);
            }
        }

        socket.setLinger(0);
        socket.close();
    }

    private void dispatch(Message incoming) {
        lastContactMillis = Instant.now().toEpochMilli();
        boolean fromSelf = incoming.senderUuid().equals(serverId);

        switch (incoming.instruction()) {
            case Handshake -> {
                logger.info("Got successful handshake response from WorldQL server!");
                handler.onConnected();
            }
            case Heartbeat -> {
                // Nothing to do; touching lastContactMillis above is the whole point.
            }
            case GlobalMessage -> handler.onClusterMessage(MessageScope.CLUSTER, toClusterMessage(incoming), fromSelf);
            case LocalMessage -> handler.onClusterMessage(MessageScope.REGION, toClusterMessage(incoming), fromSelf);
            case RecordReply -> {
                List<com.worldql.mammoth.worldql_serialization.Record> records = incoming.records();
                if (records != null && !records.isEmpty()) {
                    handler.onStoredRecords(records);
                }
            }
            default -> {
                // Instructions Mammoth never asks for, such as PeerConnect.
            }
        }
    }

    private static ClusterMessage toClusterMessage(Message incoming) {
        return new ClusterMessage(
                incoming.worldName(),
                incoming.position(),
                incoming.parameter(),
                incoming.records(),
                incoming.flex()
        );
    }

    // endregion

    // region: liveness

    private void heartbeatLoop() {
        while (running) {
            try {
                Thread.sleep(HEARTBEAT_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!running) {
                return;
            }

            send(ClusterMessage.anywhere(null, null), Instruction.Heartbeat, Replication.ExceptSelf);

            if (!isConnected()) {
                logger.warning("Haven't received a heartbeat from WorldQL in over "
                        + CONNECTION_TIMEOUT.toSeconds() + " seconds! Attempting to reconnect.");
                sendHandshake();
            }
        }
    }

    private void sendHandshake() {
        send(new ClusterMessage(ClusterMessage.ANY_WORLD, null,
                        advertisedHost + ":" + listenPort, null, null),
                Instruction.Handshake, Replication.ExceptSelf);
    }

    @Override
    public boolean isConnected() {
        return Instant.now().toEpochMilli() - lastContactMillis <= CONNECTION_TIMEOUT.toMillis();
    }

    // endregion

    @Override
    public @NotNull UUID localServerId() {
        return serverId;
    }

    @Override
    public void close() {
        running = false;

        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
        if (context != null) {
            logger.info("Shutting down ZeroMQ thread.");
            context.close();
        }
        if (receiveThread != null) {
            try {
                receiveThread.interrupt();
                receiveThread.join();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        pushSocket = null;
    }
}
