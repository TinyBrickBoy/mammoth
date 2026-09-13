package com.worldql.mammoth.events;

import com.worldql.mammoth.ghost.GhostPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class OutgoingPlayerHitEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final GhostPlayer receiver;
    private final Player attacker;

    // TODO Add attackers equipment/tools, add receivers equipment, add vector of attacker
    public OutgoingPlayerHitEvent(Player attacker, GhostPlayer receiver) {
        this.receiver = receiver;
        this.attacker = attacker;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public GhostPlayer getReceiver() {
        return receiver;
    }

    /** The uuid of the real player the attacked ghost is mimicking. */
    public UUID getUUID() {
        return receiver.getUuid();
    }

    public Player getAttacker() {
        return attacker;
    }
}
