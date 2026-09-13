package com.worldql.mammoth.listeners.player;

import com.google.flatbuffers.FlexBuffers;
import com.google.flatbuffers.FlexBuffersBuilder;
import com.worldql.mammoth.MammothPlugin;
import com.worldql.mammoth.worldql_serialization.*;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import zmq.ZMQ;

import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.UUID;

public class PlayerChatListener implements Listener {
    /**
     * Stands in for the chat message while the format is being built. The message text is inserted
     * afterwards as plain text, so players cannot smuggle colour codes or placeholders into it.
     * PlaceholderAPI leaves this shape alone because it is not wrapped in percent signs.
     */
    private static final String MESSAGE_PLACEHOLDER = "{{mammoth-message}}";

    public static String chatFormat;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncChatEvent e) {
        if (!MammothPlugin.enableChatRelay) {
            return;
        }

        String messageText = PlainTextComponentSerializer.plainText().serialize(e.message());

        // Render local chat with the configured format too, so a message reads the same whichever
        // server in the cluster you happen to be standing on.
        e.renderer((source, sourceDisplayName, message, viewer) ->
                format(source, source.getName(), messageText));

        // Send chat message to other clients
        FlexBuffersBuilder b = Codec.getFlexBuilder();
        int pmap = b.startMap();
        b.putString("username", e.getPlayer().getName());
        b.putString("message", messageText);
        b.putString("uuid", e.getPlayer().getUniqueId().toString());
        b.endMap(null, pmap);
        ByteBuffer bb = b.finish();

        Message message = new Message(
                Instruction.GlobalMessage,
                MammothPlugin.worldQLClientId,
                "@global",
                Replication.ExceptSelf,
                null,
                null,
                null,
                "MinecraftPlayerChat",
                bb
        );

        MammothPlugin.getPluginInstance().getPushSocket().send(message.encode(), ZMQ.ZMQ_DONTWAIT);
    }

    public static void relayChat(@NotNull Message message) {
        FlexBuffers.Map map = FlexBuffers.getRoot(message.flex()).asMap();

        String playerName = map.get("username").asString();
        UUID uuid = UUID.fromString(map.get("uuid").asString());
        String messageText = map.get("message").asString();

        // The sender is connected to another server in the cluster, so only their offline profile
        // is available here. PlaceholderAPI resolves most placeholders from that just fine.
        Bukkit.getServer().sendMessage(format(Bukkit.getOfflinePlayer(uuid), playerName, messageText));
    }

    /**
     * Builds a chat line from the configured chat-format. Index 0 is the player's name and index 1
     * the message, ampersand codes style the format, and PlaceholderAPI placeholders are filled in
     * when that plugin is installed (issue #66).
     */
    private static Component format(OfflinePlayer player, String playerName, String messageText) {
        String template = MessageFormat.format(chatFormat, playerName, MESSAGE_PLACEHOLDER);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            template = PlaceholderAPI.setPlaceholders(player, template);
        }

        return LegacyComponentSerializer.legacyAmpersand().deserialize(template)
                .replaceText(builder -> builder
                        .matchLiteral(MESSAGE_PLACEHOLDER)
                        .replacement(Component.text(messageText)));
    }
}
