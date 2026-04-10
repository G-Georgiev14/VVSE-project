package com.sanguine.gitbuild;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GhostBlockManager {

    // Diff ghost types for coloring
    public enum DiffType {
        NEW,      // Green - newly added blocks
        DELETED,  // Red - deleted blocks
        MODIFIED  // Yellow - modified blocks
    }

    // Diff ghost block storage
    public static class DiffGhost {
        public final BlockPos pos;
        public final BlockState originalState;
        public final DiffType type;

        public DiffGhost(BlockPos pos, BlockState originalState, DiffType type) {
            this.pos = pos.immutable();
            this.originalState = originalState;
            this.type = type;
        }
    }

    // Clone preview ghost blocks - stores target state and render type
    private static final Map<UUID, Map<BlockPos, ClonePreviewGhost>> playerClonePreviews = new ConcurrentHashMap<>();

    // Diff ghost blocks - stores colored diff visualization
    private static final Map<UUID, Map<BlockPos, DiffGhost>> playerDiffGhosts = new ConcurrentHashMap<>();

    public static class ClonePreviewGhost {
        public final BlockPos pos;
        public final BlockState targetState;
        public final boolean isWrongBlock;
        public final String targetBlockName;

        public ClonePreviewGhost(BlockPos pos, BlockState targetState, BlockState highlightState, boolean isWrongBlock, String targetBlockName) {
            this.pos = pos;
            this.targetState = targetState;
            this.isWrongBlock = isWrongBlock;
            this.targetBlockName = targetBlockName;
        }
    }

    // Legacy ghost block storage
    private static final Map<UUID, Map<BlockPos, BlockState>> playerGhostBlocks = new ConcurrentHashMap<>();

    public static void addGhostBlock(UUID playerUuid, BlockPos pos, BlockState blockState) {
        playerGhostBlocks.computeIfAbsent(playerUuid, k -> new HashMap<>()).put(pos.immutable(), blockState);
    }

    public static void removeGhostBlock(UUID playerUuid, BlockPos pos) {
        Map<BlockPos, BlockState> blocks = playerGhostBlocks.get(playerUuid);
        if (blocks != null) {
            blocks.remove(pos);
            if (blocks.isEmpty()) {
                playerGhostBlocks.remove(playerUuid);
            }
        }
    }

    public static Map<BlockPos, BlockState> getGhostBlocks(UUID playerUuid) {
        return playerGhostBlocks.getOrDefault(playerUuid, new HashMap<>());
    }

    public static void clearGhostBlocks(UUID playerUuid) {
        playerGhostBlocks.remove(playerUuid);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.level().getServer().execute(() -> {
                PlayerSession session = SessionManager.getSession(serverPlayer);
                if (session != null && session.isClonePreviewActive()) {
                    sendClonePreviewsToPlayer(serverPlayer);
                    serverPlayer.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                            "You have an unfinished clone preview. Use /git clone confirm to paste or /git clone cancel to clear."
                        )
                    );
                }
            });
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        ChunkPos chunkPos = event.getChunk().getPos();
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        for (Map.Entry<UUID, Map<BlockPos, ClonePreviewGhost>> entry : playerClonePreviews.entrySet()) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                sendClonePreviewInChunk(player, chunkPos.x(), chunkPos.z());
            }
        }
        // Also resend diff ghosts in loaded chunks
        for (Map.Entry<UUID, Map<BlockPos, DiffGhost>> entry : playerDiffGhosts.entrySet()) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                sendDiffGhostsInChunk(player, chunkPos.x(), chunkPos.z());
            }
        }
    }

    public static void addClonePreviewGhost(ServerPlayer player, ClonePreviewGhost ghost) {
        UUID playerUuid = player.getUUID();
        playerClonePreviews.computeIfAbsent(playerUuid, k -> new HashMap<>()).put(ghost.pos.immutable(), ghost);
        sendGhostBlockPacket(player, ghost);
    }

    public static void removeClonePreviewGhost(UUID playerUuid, BlockPos pos) {
        Map<BlockPos, ClonePreviewGhost> previews = playerClonePreviews.get(playerUuid);
        if (previews != null) {
            previews.remove(pos);
            if (previews.isEmpty()) {
                playerClonePreviews.remove(playerUuid);
            }
        }
    }

    public static void clearClonePreviews(ServerPlayer player) {
        playerClonePreviews.remove(player.getUUID());
    }

    public static void sendClonePreviewsToPlayer(ServerPlayer player) {
        Map<BlockPos, ClonePreviewGhost> previews = playerClonePreviews.get(player.getUUID());
        if (previews == null || previews.isEmpty()) return;
        for (ClonePreviewGhost ghost : previews.values()) {
            sendGhostBlockPacket(player, ghost);
        }
    }

    public static void sendClonePreviewInChunk(ServerPlayer player, int chunkX, int chunkZ) {
        Map<BlockPos, ClonePreviewGhost> previews = playerClonePreviews.get(player.getUUID());
        if (previews == null) return;
        for (ClonePreviewGhost ghost : previews.values()) {
            int blockChunkX = ghost.pos.getX() >> 4;
            int blockChunkZ = ghost.pos.getZ() >> 4;
            if (blockChunkX == chunkX && blockChunkZ == chunkZ) {
                sendGhostBlockPacket(player, ghost);
            }
        }
    }

    private static void sendGhostBlockPacket(ServerPlayer player, ClonePreviewGhost ghost) {
        BlockState ghostVisual = Blocks.STRUCTURE_VOID.defaultBlockState();
        ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(ghost.pos, ghostVisual);
        player.connection.send(packet);
    }

    // ==================== Diff Ghost Block Methods ====================

    public static void addDiffGhost(ServerPlayer player, DiffGhost ghost) {
        UUID playerUuid = player.getUUID();
        playerDiffGhosts.computeIfAbsent(playerUuid, k -> new HashMap<>()).put(ghost.pos, ghost);
        sendDiffGhostPacket(player, ghost);
    }

    public static void clearDiffGhosts(ServerPlayer player) {
        // Restore real blocks before clearing
        Map<BlockPos, DiffGhost> ghosts = playerDiffGhosts.get(player.getUUID());
        if (ghosts != null) {
            for (DiffGhost ghost : ghosts.values()) {
                BlockState realState = player.level().getBlockState(ghost.pos);
                ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(ghost.pos, realState);
                player.connection.send(packet);
            }
        }
        playerDiffGhosts.remove(player.getUUID());
    }

    public static void sendDiffGhostsToPlayer(ServerPlayer player) {
        Map<BlockPos, DiffGhost> ghosts = playerDiffGhosts.get(player.getUUID());
        if (ghosts == null || ghosts.isEmpty()) return;
        for (DiffGhost ghost : ghosts.values()) {
            sendDiffGhostPacket(player, ghost);
        }
    }

    public static void sendDiffGhostsInChunk(ServerPlayer player, int chunkX, int chunkZ) {
        Map<BlockPos, DiffGhost> ghosts = playerDiffGhosts.get(player.getUUID());
        if (ghosts == null) return;
        for (DiffGhost ghost : ghosts.values()) {
            int blockChunkX = ghost.pos.getX() >> 4;
            int blockChunkZ = ghost.pos.getZ() >> 4;
            if (blockChunkX == chunkX && blockChunkZ == chunkZ) {
                sendDiffGhostPacket(player, ghost);
            }
        }
    }

    private static void sendDiffGhostPacket(ServerPlayer player, DiffGhost ghost) {
        BlockState visualState;
        switch (ghost.type) {
            case NEW:
                // Green for new blocks
                visualState = Blocks.LIME_STAINED_GLASS.defaultBlockState();
                break;
            case DELETED:
                // Red for deleted blocks
                visualState = Blocks.RED_STAINED_GLASS.defaultBlockState();
                break;
            case MODIFIED:
                // Yellow for modified blocks
                visualState = Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
                break;
            default:
                visualState = Blocks.STRUCTURE_VOID.defaultBlockState();
        }
        ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(ghost.pos, visualState);
        player.connection.send(packet);
    }

    public static Map<BlockPos, DiffGhost> getDiffGhosts(UUID playerUuid) {
        return playerDiffGhosts.getOrDefault(playerUuid, new HashMap<>());
    }

    public static boolean hasDiffGhosts(UUID playerUuid) {
        Map<BlockPos, DiffGhost> ghosts = playerDiffGhosts.get(playerUuid);
        return ghosts != null && !ghosts.isEmpty();
    }

    public static Map<BlockPos, ClonePreviewGhost> getClonePreviews(UUID playerUuid) {
        return playerClonePreviews.getOrDefault(playerUuid, new HashMap<>());
    }

    public static boolean hasClonePreviews(UUID playerUuid) {
        Map<BlockPos, ClonePreviewGhost> previews = playerClonePreviews.get(playerUuid);
        return previews != null && !previews.isEmpty();
    }
}
