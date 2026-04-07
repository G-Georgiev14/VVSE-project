package com.sanguine.gitbuild;

import com.sanguine.gitbuild.command.PingCommand;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        PingCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();

        LOGGER.info("Block placed: {} by {} at position ({}, {}, {}) in dimension {}",
                event.getPlacedBlock().getBlock(),
                event.getEntity() != null ? event.getEntity().getName().getString() : "unknown",
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                level.dimension());
    }
}