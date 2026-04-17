package com.sanguine.gitbuild;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * Manages ghost block visualization.
 * Uses translucent blocks for clone preview and
 * colored dust particles for diff visualization.
 */
public class GhostBlockManager {

    // Particle refresh interval in ticks (0.25 seconds - faster refresh for continuous visibility)
    private static final int PARTICLE_REFRESH_INTERVAL = 5;
    // Maximum distance to render particles
    private static final double MAX_RENDER_DISTANCE = 64.0;
    // Distance for full detail rendering (corners + edges)
    private static final double FULL_DETAIL_DISTANCE = 16.0;

    // Color definitions for particles (RGB values 0.0-1.0)
    private static final Vector3f COLOR_NEW = new Vector3f(0.3f, 1.0f, 0.3f);        // Lime green
    private static final Vector3f COLOR_MODIFIED = new Vector3f(1.0f, 1.0f, 0.3f);   // Yellow
    private static final Vector3f COLOR_DELETED = new Vector3f(1.0f, 0.3f, 0.3f);     // Red
    private static final Vector3f COLOR_MISSING = new Vector3f(0.3f, 0.5f, 1.0f);     // Blue
    private static final Vector3f COLOR_CLONE_CORRECT = new Vector3f(0.3f, 0.8f, 1.0f); // Cyan
    private static final Vector3f COLOR_CLONE_WRONG = new Vector3f(1.0f, 0.5f, 0.2f);  // Orange

    // Ghost blocks for missing blocks (commit restoration)
    private static final Map<UUID, Map<BlockPos, GhostBlock>> playerGhosts = new HashMap<>();
    
    // Diff ghosts per player
    private static final Map<UUID, Map<BlockPos, DiffGhost>> playerDiffGhosts = new HashMap<>();
    
    // Clone preview ghosts per player
    private static final Map<UUID, List<ClonePreviewGhost>> playerClonePreviews = new HashMap<>();
    
    // Proximity tracking for smart particle visibility
    // If player is within 5 blocks of anchor for 5+ seconds, hide particles
    private static final double PROXIMITY_HIDE_DISTANCE = 5.0;
    private static final int PROXIMITY_HIDE_TICKS = 100; // 5 seconds at 20 tps
    private static final Map<UUID, ProximityData> playerProximity = new HashMap<>();
    
    private static class ProximityData {
        long enteredProximityTick = -1;
        boolean isHidden = false;
    }
    
    // Tick counter for particle refresh
    private static int tickCounter = 0;

    public enum GhostType {
        MISSING,
        CLONE_PREVIEW_CORRECT,
        CLONE_PREVIEW_WRONG
    }

    public enum DiffType {
        NEW,
        MODIFIED,
        DELETED
    }

    public record GhostBlock(BlockPos pos, BlockState state, GhostType type) {}

    public record DiffGhost(BlockPos pos, BlockState state, DiffType diffType) {}

    public record ClonePreviewGhost(BlockPos pos, BlockState state, String blockName, boolean isWrongBlock) {}

    // ==================== GHOST BLOCK METHODS ====================

    public static void addGhostBlock(UUID playerUUID, BlockPos pos, BlockState state) {
        playerGhosts.computeIfAbsent(playerUUID, k -> new HashMap<>())
                   .put(pos, new GhostBlock(pos, state, GhostType.MISSING));
    }

    public static void removeGhostBlock(UUID playerUUID, BlockPos pos) {
        Map<BlockPos, GhostBlock> ghosts = playerGhosts.get(playerUUID);
        if (ghosts != null) {
            ghosts.remove(pos);
            if (ghosts.isEmpty()) {
                playerGhosts.remove(playerUUID);
            }
        }
    }

    public static Map<BlockPos, BlockState> getGhostBlocks(UUID playerUUID) {
        Map<BlockPos, BlockState> result = new HashMap<>();
        Map<BlockPos, GhostBlock> ghosts = playerGhosts.get(playerUUID);
        if (ghosts != null) {
            for (GhostBlock ghost : ghosts.values()) {
                result.put(ghost.pos, ghost.state);
            }
        }
        return result;
    }

    public static void clearGhosts(UUID playerUUID) {
        playerGhosts.remove(playerUUID);
    }

    // ==================== DIFF GHOST METHODS ====================

    public static void addDiffGhost(ServerPlayer player, DiffGhost ghost) {
        playerDiffGhosts.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                       .put(ghost.pos, ghost);
    }

    public static void clearDiffGhosts(ServerPlayer player) {
        playerDiffGhosts.remove(player.getUUID());
    }

    // ==================== CLONE PREVIEW METHODS ====================

    public static void addClonePreviewGhost(ServerPlayer player, ClonePreviewGhost ghost) {
        playerClonePreviews.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(ghost);
    }

    public static void clearClonePreviews(ServerPlayer player) {
        playerClonePreviews.remove(player.getUUID());
    }

    public static void sendClonePreviewsToPlayer(ServerPlayer player) {
        // Clone previews are rendered via particles, so this triggers a refresh
        renderClonePreviews(player);
    }

    // ==================== PARTICLE RENDERING ====================

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        tickCounter++;
        if (tickCounter % PARTICLE_REFRESH_INTERVAL != 0) {
            return;
        }

        // Check proximity to instance anchor for smart visibility
        boolean shouldHide = checkProximityAndShouldHide(player);
        if (shouldHide) {
            return; // Skip all particle rendering
        }

        // Render all visualization types
        renderGhostBlocks(player);
        renderDiffGhosts(player);
        renderClonePreviews(player);
    }

    /**
     * Checks if player is within 5 blocks of their current instance anchor.
     * If within proximity for 5+ seconds, returns true to hide particles.
     */
    private static boolean checkProximityAndShouldHide(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        PlayerSession session = SessionManager.getSession(player);
        
        if (session == null) {
            return false;
        }
        
        BuildInstance currentInstance = session.getCurrentInstance();
        if (currentInstance == null) {
            // No instance, reset proximity data
            playerProximity.remove(playerUUID);
            return false;
        }
        
        BlockPos anchor = currentInstance.getAnchorPos();
        double distance = Math.sqrt(player.distanceToSqr(
            anchor.getX() + 0.5,
            anchor.getY() + 0.5,
            anchor.getZ() + 0.5
        ));
        
        ProximityData data = playerProximity.computeIfAbsent(playerUUID, k -> new ProximityData());
        
        if (distance <= PROXIMITY_HIDE_DISTANCE) {
            // Player is within proximity zone
            if (data.enteredProximityTick < 0) {
                // Just entered proximity
                data.enteredProximityTick = tickCounter;
                data.isHidden = false;
            } else {
                // Check if been in proximity for 5+ seconds
                long ticksInProximity = tickCounter - data.enteredProximityTick;
                if (ticksInProximity >= PROXIMITY_HIDE_TICKS) {
                    data.isHidden = true;
                }
            }
        } else {
            // Player moved away, reset
            data.enteredProximityTick = -1;
            data.isHidden = false;
        }
        
        return data.isHidden;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Clean up player's ghost data
            playerGhosts.remove(player.getUUID());
            playerDiffGhosts.remove(player.getUUID());
            playerClonePreviews.remove(player.getUUID());
            playerProximity.remove(player.getUUID());
        }
    }

    private static void renderGhostBlocks(ServerPlayer player) {
        Map<BlockPos, GhostBlock> ghosts = playerGhosts.get(player.getUUID());
        if (ghosts == null || ghosts.isEmpty()) {
            return;
        }

        for (GhostBlock ghost : ghosts.values()) {
            double distance = player.distanceToSqr(
                ghost.pos.getX() + 0.5,
                ghost.pos.getY() + 0.5,
                ghost.pos.getZ() + 0.5
            );

            if (distance > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
                continue;
            }

            boolean fullDetail = distance <= FULL_DETAIL_DISTANCE * FULL_DETAIL_DISTANCE;
            spawnOutlineParticles(player, ghost.pos, COLOR_MISSING, fullDetail);
        }
    }

    private static void renderDiffGhosts(ServerPlayer player) {
        Map<BlockPos, DiffGhost> ghosts = playerDiffGhosts.get(player.getUUID());
        if (ghosts == null || ghosts.isEmpty()) {
            return;
        }

        for (DiffGhost ghost : ghosts.values()) {
            double distance = player.distanceToSqr(
                ghost.pos.getX() + 0.5,
                ghost.pos.getY() + 0.5,
                ghost.pos.getZ() + 0.5
            );

            if (distance > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
                continue;
            }

            Vector3f color = switch (ghost.diffType) {
                case NEW -> COLOR_NEW;
                case MODIFIED -> COLOR_MODIFIED;
                case DELETED -> COLOR_DELETED;
            };

            boolean fullDetail = distance <= FULL_DETAIL_DISTANCE * FULL_DETAIL_DISTANCE;
            spawnOutlineParticles(player, ghost.pos, color, fullDetail);
        }
    }

    private static void renderClonePreviews(ServerPlayer player) {
        List<ClonePreviewGhost> previews = playerClonePreviews.get(player.getUUID());
        if (previews == null || previews.isEmpty()) {
            return;
        }

        for (ClonePreviewGhost preview : previews) {
            double distance = player.distanceToSqr(
                preview.pos.getX() + 0.5,
                preview.pos.getY() + 0.5,
                preview.pos.getZ() + 0.5
            );

            if (distance > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
                continue;
            }

            Vector3f color = preview.isWrongBlock ? COLOR_CLONE_WRONG : COLOR_CLONE_CORRECT;
            boolean fullDetail = distance <= FULL_DETAIL_DISTANCE * FULL_DETAIL_DISTANCE;
            
            // Clone previews get a slightly different visual - box outline
            spawnOutlineParticles(player, preview.pos, color, fullDetail);
        }
    }

    /**
     * Spawns dust particles forming a box outline around the given position.
     */
    private static void spawnOutlineParticles(ServerPlayer player, BlockPos pos, Vector3f color, boolean fullDetail) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();

        float r = color.x();
        float g = color.y();
        float b = color.z();

        if (fullDetail) {
            // Full box outline with more particles
            // Bottom face
            for (int i = 0; i <= 4; i++) {
                double offset = i * 0.25;
                sendDustParticle(level, x + offset, y, z, r, g, b);
                sendDustParticle(level, x + offset, y, z + 1, r, g, b);
                sendDustParticle(level, x, y, z + offset, r, g, b);
                sendDustParticle(level, x + 1, y, z + offset, r, g, b);
            }
            // Top face
            for (int i = 0; i <= 4; i++) {
                double offset = i * 0.25;
                sendDustParticle(level, x + offset, y + 1, z, r, g, b);
                sendDustParticle(level, x + offset, y + 1, z + 1, r, g, b);
                sendDustParticle(level, x, y + 1, z + offset, r, g, b);
                sendDustParticle(level, x + 1, y + 1, z + offset, r, g, b);
            }
            // Vertical edges
            for (int i = 0; i <= 4; i++) {
                double offset = i * 0.25;
                sendDustParticle(level, x, y + offset, z, r, g, b);
                sendDustParticle(level, x + 1, y + offset, z, r, g, b);
                sendDustParticle(level, x, y + offset, z + 1, r, g, b);
                sendDustParticle(level, x + 1, y + offset, z + 1, r, g, b);
            }
        } else {
            // Simplified - just corners for distant blocks
            // 8 corners
            sendDustParticle(level, x, y, z, r, g, b);
            sendDustParticle(level, x + 1, y, z, r, g, b);
            sendDustParticle(level, x, y + 1, z, r, g, b);
            sendDustParticle(level, x + 1, y + 1, z, r, g, b);
            sendDustParticle(level, x, y, z + 1, r, g, b);
            sendDustParticle(level, x + 1, y, z + 1, r, g, b);
            sendDustParticle(level, x, y + 1, z + 1, r, g, b);
            sendDustParticle(level, x + 1, y + 1, z + 1, r, g, b);
        }

        // Center particle for all distances (helps identify block position)
        sendDustParticle(level, x + 0.5, y + 0.5, z + 0.5, r, g, b);
    }

    /**
     * Sends a colored dust particle to the level.
     * Converts RGB float values (0.0-1.0) to packed int format (0xFFRRGGBB).
     */
    private static void sendDustParticle(ServerLevel level, double x, double y, double z, float r, float g, float b) {
        int red = (int) (r * 255) & 0xFF;
        int green = (int) (g * 255) & 0xFF;
        int blue = (int) (b * 255) & 0xFF;
        int packedColor = (0xFF << 24) | (red << 16) | (green << 8) | blue;
        DustParticleOptions options = new DustParticleOptions(packedColor, 1.0f);
        level.sendParticles(options, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
