package com.sanguine.gitbuild;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GhostBlockManager {

    // Map of player UUID to their ghost blocks (position -> block state)
    private static final Map<UUID, Map<BlockPos, BlockState>> playerGhostBlocks = new ConcurrentHashMap<>();

    /**
     * Add a ghost block for a specific player
     */
    public static void addGhostBlock(UUID playerUuid, BlockPos pos, BlockState blockState) {
        playerGhostBlocks.computeIfAbsent(playerUuid, k -> new HashMap<>())
                         .put(pos.immutable(), blockState);
    }

    /**
     * Remove a ghost block for a specific player
     */
    public static void removeGhostBlock(UUID playerUuid, BlockPos pos) {
        Map<BlockPos, BlockState> blocks = playerGhostBlocks.get(playerUuid);
        if (blocks != null) {
            blocks.remove(pos);
            if (blocks.isEmpty()) {
                playerGhostBlocks.remove(playerUuid);
            }
        }
    }

    /**
     * Get all ghost blocks for a player
     */
    public static Map<BlockPos, BlockState> getGhostBlocks(UUID playerUuid) {
        return playerGhostBlocks.getOrDefault(playerUuid, new HashMap<>());
    }

    /**
     * Clear all ghost blocks for a player (on logout)
     */
    public static void clearGhostBlocks(UUID playerUuid) {
        playerGhostBlocks.remove(playerUuid);
    }

    /**
     * Resend all ghost blocks to a player (on login or chunk load)
     */
    public static void sendGhostBlocksToPlayer(ServerPlayer player) {
        Map<BlockPos, BlockState> blocks = getGhostBlocks(player.getUUID());
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(entry.getKey(), entry.getValue());
            player.connection.send(packet);
        }
    }

    /**
     * Send ghost blocks that are within a specific chunk
     */
    public static void sendGhostBlocksInChunk(ServerPlayer player, int chunkX, int chunkZ) {
        Map<BlockPos, BlockState> blocks = getGhostBlocks(player.getUUID());
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            int blockChunkX = pos.getX() >> 4;
            int blockChunkZ = pos.getZ() >> 4;
            if (blockChunkX == chunkX && blockChunkZ == chunkZ) {
                ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(pos, entry.getValue());
                player.connection.send(packet);
            }
        }
    }

    /**
     * Event handler for when a player logs out - clean up their ghost blocks
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            clearGhostBlocks(serverPlayer.getUUID());
            GitBuildMod.LOGGER.info("Cleared ghost blocks for player: {}", serverPlayer.getName().getString());
        }
    }

    /**
     * Event handler for when a player logs in - resend ghost blocks
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // Small delay to ensure chunks are loaded
            serverPlayer.level().getServer().execute(() -> sendGhostBlocksToPlayer(serverPlayer));
            GitBuildMod.LOGGER.info("Sent ghost blocks to player: {}", serverPlayer.getName().getString());
        }
    }

    /**
     * Event handler for when a chunk is loaded - send ghost blocks in that chunk
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) return;

        ChunkPos chunkPos = event.getChunk().getPos();
        int chunkX = chunkPos.x();
        int chunkZ = chunkPos.z();

        // Try to get the server from the level
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        // Send ghost blocks to all players who have ghost blocks in this chunk
        for (Map.Entry<UUID, Map<BlockPos, BlockState>> entry : playerGhostBlocks.entrySet()) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                sendGhostBlocksInChunk(player, chunkX, chunkZ);
            }
        }
    }
}