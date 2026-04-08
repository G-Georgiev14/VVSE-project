package com.sanguine.gitbuild;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private static final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    public static PlayerSession getSession(ServerPlayer player) {
        return sessions.computeIfAbsent(player.getUUID(), PlayerSession::new);
    }

    public static PlayerSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public static void removeSession(UUID uuid) {
        sessions.remove(uuid);
    }

    public static boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            removeSession(serverPlayer.getUUID());
            GitBuildMod.LOGGER.info("Cleared session for player: {}", serverPlayer.getName().getString());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // Initialize session for player
            getSession(serverPlayer);
            GitBuildMod.LOGGER.info("Initialized session for player: {}", serverPlayer.getName().getString());
        }
    }
}
