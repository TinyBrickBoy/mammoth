package com.worldql.mammoth;

import com.worldql.mammoth.commands.CommandTeleportRequest;
import com.worldql.mammoth.commands.CommandTeleportRequestAccept;
import com.worldql.mammoth.commands.CommandTeleportTo;
import com.worldql.mammoth.commands.CommandUnstuck;
import com.worldql.mammoth.listeners.DmzProtectionListener;
import com.worldql.mammoth.listeners.NotImplementedCanceller;
import com.worldql.mammoth.listeners.OutgoingPlayerHitListener;
import com.worldql.mammoth.listeners.chunks.ChunkLoadEventListener;
import com.worldql.mammoth.listeners.chunks.ChunkUnloadEventListener;
import com.worldql.mammoth.listeners.explosions.BlockExplodeEventListener;
import com.worldql.mammoth.listeners.explosions.EntityExplodeEventListener;
import com.worldql.mammoth.listeners.explosions.ExplosionPrimeEventListener;
import com.worldql.mammoth.listeners.explosions.TNTPrimeEventListener;
import com.worldql.mammoth.listeners.player.*;
import com.worldql.mammoth.listeners.world.*;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import com.worldql.mammoth.ghost.PlayerGhostManager;
import com.worldql.mammoth.minecraft_serialization.SaveLoadPlayerFromRedis;
import com.worldql.mammoth.protocols.ProtocolManager;
import com.worldql.mammoth.worldql_serialization.Instruction;
import com.worldql.mammoth.worldql_serialization.Message;
import com.worldql.mammoth.worldql_serialization.Replication;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisException;

import java.time.Instant;
import java.util.UUID;

public class MammothPlugin extends JavaPlugin {
    public static boolean disabling;
    public static MammothPlugin pluginInstance;
    public static UUID worldQLClientId;
    public static RedisClient redis;
    public static int mammothServerId;
    private Thread zeroMQThread;
    private ZContext context;
    private ZMQ.Socket pushSocket;
    public static boolean processGhosts;
    public static boolean syncPlayerInventory;
    public static boolean syncPlayerHealthXPHunger;
    public static boolean syncPlayerEffects;
    public static boolean avoidSlicingOrigin;
    public static int originRadius;
    public static PlayerDataSavingManager playerDataSavingManager;
    public static long timestampOfLastHeartbeat;
    public static String worldName;
    public static boolean enableChatRelay;
    static int zeroMQServerPort;
    public static String serverPrefix;

    @Override
    public void onLoad() {
        // PacketEvents has to be loaded before any player can connect, so it cannot wait for onEnable.
        if (getConfig().getBoolean("ghosts", false) && isPacketEventsInstalled()) {
            PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
            PacketEvents.getAPI().load();
        }
    }

    private boolean isPacketEventsInstalled() {
        return getServer().getPluginManager().getPlugin("packetevents") != null;
    }

    @Override
    public void onEnable() {
        disabling = false;
        pluginInstance = this;
        getLogger().info("Initializing Mammoth v" + getPluginMeta().getVersion());
        saveDefaultConfig();

        // RedisClient pools connections internally and is safe to share between threads.
        redis = RedisClient.create(getConfig().getString("redis.host", "localhost"), getConfig().getInt("redis.port", 6379));
        // Make sure we're connected to redis.
        try {
            redis.ping();
            getLogger().info("Redis connection successful.");
        } catch (JedisException e) {
            getLogger().warning("Failed to connect to Redis. Both WorldQL and Redis are required for Mammoth. Stopping server...");
            Bukkit.getServer().shutdown();
            return;
        }

        mammothServerId = Bukkit.getServer().getPort() - getConfig().getInt("starting-port");
        worldQLClientId = java.util.UUID.randomUUID();
        context = new ZContext();
        pushSocket = context.createSocket(SocketType.PUSH);
        processGhosts = getConfig().getBoolean("ghosts", false);
        syncPlayerInventory = getConfig().getBoolean("sync-player-inventory", true);
        syncPlayerHealthXPHunger = getConfig().getBoolean("sync-player-health-xp-hunger", true);
        syncPlayerEffects = getConfig().getBoolean("sync-player-effects", true);
        avoidSlicingOrigin = getConfig().getBoolean("avoid-slicing-origin", false);
        originRadius = getConfig().getInt("origin-radius", 256);
        playerDataSavingManager = new PlayerDataSavingManager();
        timestampOfLastHeartbeat = Instant.now().toEpochMilli();
        worldName = getConfig().getString("world-name", "world");
        enableChatRelay = getConfig().getBoolean("chat-relay", true);
        serverPrefix = getConfig().getString("server-prefix", "mammoth_");
        PlayerChatListener.chatFormat = getConfig().getString("chat-format", "<{0}> {1}");

        String worldqlHost = getConfig().getString("worldql.host", "127.0.0.1");
        int worldqlPushPort = getConfig().getInt("worldql.push-port", 5555);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        // "host" is what the rest of the cluster is told to reach us on, so it may be a DNS name.
        // ZeroMQ cannot bind to a name, so the listening interface is configured separately.
        String selfHostname = getConfig().getString("host", "127.0.0.1");
        String bindAddress = getConfig().getString("bind-address", "0.0.0.0");

        // Connect to the WorldQL server.
        getLogger().info("Attempting to connect to WorldQL server.");
        pushSocket.connect("tcp://%s:%d".formatted(worldqlHost, worldqlPushPort));

        Slices.enabled = getConfig().getBoolean("slice-mode");
        if (Slices.enabled) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Slices.numServers = getConfig().getInt("num-servers");
                Slices.worldDiameter = getConfig().getInt("world-diameter");
                Slices.sliceWidth = getConfig().getInt("slice-width");
                Slices.dmzSize = getConfig().getInt("dmz-size");
                int worldDiameter = getConfig().getInt("world-diameter");
                try {
                    World world = Bukkit.getWorld(worldName);
                    World nether = Bukkit.getWorld(worldName + "_nether");
                    World end = Bukkit.getWorld(worldName + "_the_end");

                    WorldBorder wb = world.getWorldBorder();
                    wb.setCenter(0, 0);
                    wb.setSize(worldDiameter);

                    wb = nether.getWorldBorder();
                    wb.setCenter(0, 0);
                    wb.setSize(worldDiameter);

                    wb = end.getWorldBorder();
                    wb.setCenter(0, 0);
                    wb.setSize(worldDiameter);

                    world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
                    nether.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
                    end.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 20);
        }

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            Message message = new Message(
                    Instruction.Heartbeat,
                    MammothPlugin.worldQLClientId,
                    "@global"
            );

            pushSocket.send(message.encode(), zmq.ZMQ.ZMQ_DONTWAIT);

            long now = Instant.now().toEpochMilli();
            if (now - timestampOfLastHeartbeat > 15000) {
                getLogger().warning("Haven't received a heartbeat from WorldQL in over 15 seconds! Attempting to reconnect.");
                Message reconnectMessage = new Message(
                        Instruction.Handshake,
                        MammothPlugin.worldQLClientId,
                        "@global",
                        Replication.ExceptSelf,
                        null,
                        null,
                        null,
                        selfHostname + ":" + MammothPlugin.zeroMQServerPort,
                        null
                );

                MammothPlugin.getPluginInstance().getPushSocket().send(reconnectMessage.encode(), ZMQ.DONTWAIT);
            }
        }, 5L, 20L * 5L);

        if (Slices.enabled && getConfig().getBoolean("border-particles", true)) {
            Bukkit.getScheduler().runTaskTimer(this, new BorderParticleTask(), 20L, 10L);
        }

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            getLogger().info("One minute has passed, saving players...");
            for (Player player : getServer().getOnlinePlayers()) {
                SaveLoadPlayerFromRedis.savePlayerToRedis(player, false, false);
            }
        }, 20L * 60, 20L * 60);

        // TODO: Remove this command after we figure out the cause of players being spawned in the ground.
        getCommand("unstuck").setExecutor(new CommandUnstuck());
        getCommand("mtpa").setExecutor(new CommandTeleportRequest());
        getCommand("mtpaccept").setExecutor(new CommandTeleportRequestAccept());
        getCommand("mtp").setExecutor(new CommandTeleportTo());


        getServer().getPluginManager().registerEvents(new PlayerServerTransferJoinLeave(), this);
        // Handles server transfers and the movement component of ghosts.
        getServer().getPluginManager().registerEvents(new PlayerMoveAndLookHandler(), this);
        getServer().getPluginManager().registerEvents(new PlayerInventoryOpenEventListener(), this);

        // For ghosts.
        if (processGhosts && !startGhostSystem()) {
            processGhosts = false;
        }
        if (processGhosts) {
            getServer().getPluginManager().registerEvents(new PlayerCrouchListener(), this);
            getServer().getPluginManager().registerEvents(new PlayerInteractEventListener(), this);
            getServer().getPluginManager().registerEvents(new PlayerArmorEditListener(), this);
            getServer().getPluginManager().registerEvents(new PlayerHeldItemListener(), this);
            getServer().getPluginManager().registerEvents(new PlayerShieldInteractListener(), this);
            getServer().getPluginManager().registerEvents(new PlayerTeleportEventListener(), this);
            getServer().getPluginManager().registerEvents(new PlayerShootBowListener(), this);
        }

        // To sub/unsub from regions of the world.
        if (processGhosts || !Slices.enabled) {
            getServer().getPluginManager().registerEvents(new ChunkLoadEventListener(), this);
            getServer().getPluginManager().registerEvents(new ChunkUnloadEventListener(), this);
        }

        // Sync broken and placed blocks.
        getServer().getPluginManager().registerEvents(new PlayerBreakBlockListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerPlaceBlockListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerEditSignListener(), this);
        getServer().getPluginManager().registerEvents(new NoteBlockListener(), this);
        getServer().getPluginManager().registerEvents(new PortalCreateEventListener(), this);
        getServer().getPluginManager().registerEvents(new TimeSkipEventListener(), this);

        // For explosions.
        getServer().getPluginManager().registerEvents(new TNTPrimeEventListener(), this);
        getServer().getPluginManager().registerEvents(new ExplosionPrimeEventListener(), this);
        getServer().getPluginManager().registerEvents(new EntityExplodeEventListener(), this);
        getServer().getPluginManager().registerEvents(new BlockExplodeEventListener(), this);

        // Chat sync
        getServer().getPluginManager().registerEvents(new PlayerChatListener(), this);
        // Death drop mechanics.
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(), this);

        // Cancel events that can cause desync in any mode.
        getServer().getPluginManager().registerEvents(new NotImplementedCanceller(), this);
        getServer().getPluginManager().registerEvents(new DmzProtectionListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerDropItemListener(), this);
        // Custom listeners.
        getServer().getPluginManager().registerEvents(new OutgoingPlayerHitListener(), this);

        zeroMQThread = new Thread(new ZeroMQServer(this, context, selfHostname, bindAddress));
        zeroMQThread.start();
    }

    /**
     * Ghosts are drawn entirely with packets, which needs PacketEvents.
     *
     * @return false if the ghost system could not be started, in which case ghosts stay off.
     */
    private boolean startGhostSystem() {
        if (!ProtocolManager.isPacketEventsLoaded()) {
            getLogger().warning("Ghosts are enabled but PacketEvents is not installed. "
                    + "Install PacketEvents (https://modrinth.com/plugin/packetevents) or set ghosts: false. "
                    + "Continuing with ghosts disabled.");
            return false;
        }

        // PacketEvents wants its listeners registered before it is initialized.
        if (!ProtocolManager.read()) {
            getLogger().warning("Could not register the PacketEvents listener; continuing with ghosts disabled.");
            return false;
        }
        PacketEvents.getAPI().init();

        // Players who walk out of subscription range never send a quit message, so ghosts that
        // stopped being updated are swept up here instead of lingering in the tab list.
        Bukkit.getScheduler().runTaskTimer(this, PlayerGhostManager::expireStaleGhosts, 20L * 30, 20L * 30);
        return true;
    }

    @Override
    public void onDisable() {
        disabling = true;
        if (processGhosts) {
            PlayerGhostManager.removeAll();
            ProtocolManager.close();
        }
        for (Player player : getServer().getOnlinePlayers()) {
            SaveLoadPlayerFromRedis.savePlayerToRedis(player, true, false);
        }
        if (redis != null) {
            redis.close();
        }
        if (ProtocolManager.isPacketEventsInitialized()) {
            PacketEvents.getAPI().terminate();
        }
        if (context != null && zeroMQThread != null) {
            getLogger().info("Shutting down ZeroMQ thread.");
            context.close();
            try {
                zeroMQThread.interrupt();
                zeroMQThread.join();
            } catch (InterruptedException ignored) {
            }
        }
    }

    public static MammothPlugin getPluginInstance() {
        return pluginInstance;
    }

    public ZMQ.Socket getPushSocket() {
        return pushSocket;
    }
}
