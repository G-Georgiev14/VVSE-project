package com.sanguine.gitbuild;

import com.sanguine.gitbuild.command.GhostBlockCommand;
import com.sanguine.gitbuild.command.GitCommand;
import com.sanguine.gitbuild.command.PingCommand;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
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
        GhostBlockCommand.register(event.getDispatcher());
        GitCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();

        LOGGER.debug("Block placed: {} by {} at position ({}, {}, {}) in dimension {}",
                event.getPlacedBlock().getBlock(),
                event.getEntity() != null ? event.getEntity().getName().getString() : "unknown",
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                level.dimension());

        // Auto-add functionality
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerSession session = SessionManager.getSession(player);
            if (session != null && session.hasActiveRepo() && session.isAutoAdd()) {
                BlockState newState = event.getPlacedBlock();
                session.stageBlock(pos, null, newState, PlayerSession.ChangeType.ADD);
                LOGGER.debug("Auto-added block at {} for player {}", pos, player.getName().getString());
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockPos pos = event.getPos();
        
        // Auto-rm functionality - removes blocks from staging when broken
        if (event.getPlayer() instanceof ServerPlayer player) {
            PlayerSession session = SessionManager.getSession(player);
            if (session != null && session.hasActiveRepo() && session.isAutoRm()) {
                // If block was staged, unstage it (don't commit this change)
                if (session.isStaged(pos)) {
                    session.unstageBlock(pos);
                    LOGGER.debug("Auto-unstaged block at {} for player {}", pos, player.getName().getString());
                }
            }
        }
    }
}