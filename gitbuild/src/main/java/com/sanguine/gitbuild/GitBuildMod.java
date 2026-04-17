package com.sanguine.gitbuild;
import com.sanguine.gitbuild.command.GitCommand;
import com.sanguine.gitbuild.command.PingCommand;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(GitBuildMod.MODID)
public class GitBuildMod {
    public static final String MODID = "gitbuild";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GitBuildMod(IEventBus modEventBus, net.neoforged.fml.ModContainer modContainer) {
        // Register ourselves for game events
        NeoForge.EVENT_BUS.register(this);
        // Register SessionManager for player login/logout
        NeoForge.EVENT_BUS.register(SessionManager.class);
        // Register GhostBlockManager for chunk loading and player events
        NeoForge.EVENT_BUS.register(GhostBlockManager.class);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        PingCommand.register(event.getDispatcher());
        GitCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        BlockPos pos = event.getPos();

        // Auto-add functionality
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSession session = SessionManager.getSession(player);
            if (session != null && session.hasActiveRepo() && session.isAutoAdd()) {
                // Update instance context based on current world/dimension/position
                updateInstanceContext(player, session, pos);

                BlockState newState = event.getPlacedBlock();

                // Check if there's a ghost block at this position (player is restoring)
                if (GhostBlockManager.getGhostBlocks(player.getUUID()).containsKey(pos)) {
                    // Remove the ghost block - player has built here
                    // Particles will stop rendering automatically
                    GhostBlockManager.removeGhostBlock(player.getUUID(), pos);
                }

                // If there was a staged entry (e.g., "removed"), unstage it first
                // Then stage the new block - status will compare against ghost/HEAD
                session.unstageBlock(pos);
                session.stageBlock(pos, null, newState, PlayerSession.ChangeType.ADD);
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockPos pos = event.getPos();

        // Auto-rm functionality - stage broken blocks as removed
        if (event.getPlayer() instanceof ServerPlayer player) {
            PlayerSession session = SessionManager.getSession(player);
            if (session != null && session.hasActiveRepo() && session.isAutoRm()) {
                // Update instance context based on current world/dimension/position
                updateInstanceContext(player, session, pos);

                BlockState oldState = event.getState();
                BlockState airState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

                // Stage the block as removed (will be excluded from commit snapshot)
                session.stageBlock(pos, oldState, airState, PlayerSession.ChangeType.REMOVE);
            }
        }
    }

    /**
     * Updates the player's current build instance context based on their world, dimension, and position.
     * This allows the same repo to be used across different worlds, dimensions, and locations.
     */
    private void updateInstanceContext(ServerPlayer player, PlayerSession session, BlockPos pos) {
        // Get world ID from the level's server world name
        String worldId = "world";
        if (player.level().getServer() != null) {
            worldId = player.level().getServer().getWorldData().getLevelName();
        }

        // Get dimension ID (e.g., "minecraft:overworld", "minecraft:the_nether")
        // dimension() returns a ResourceKey<Level>
        String dimensionId = player.level().dimension().toString();

        // Update the instance context - this will auto-detect or create a new instance
        session.updateInstanceContext(worldId, dimensionId, pos);
    }
}