package com.sanguine.gitbuild.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sanguine.gitbuild.GhostBlockManager;
import com.sanguine.gitbuild.GitBuildMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public class GhostBlockCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ghostblock")
                .requires(source -> {
                    var perms = source.permissions();
                    if (perms instanceof LevelBasedPermissionSet levelPerms) {
                        return levelPerms.level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS);
                    }
                    return false;
                })
                .then(Commands.literal("place")
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                            .then(Commands.argument("z", IntegerArgumentType.integer())
                                .then(Commands.argument("block", StringArgumentType.string())
                                    // Version without player argument - defaults to command executor (self)
                                    .executes(GhostBlockCommand::executePlaceGhostBlockSelf)
                                    // Version with player argument
                                    .then(Commands.argument("player", EntityArgument.player())
                                        .executes(GhostBlockCommand::executePlaceGhostBlock)
                                    )
                                )
                            )
                        )
                    )
                )
                .then(Commands.literal("remove")
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                            .then(Commands.argument("z", IntegerArgumentType.integer())
                                // Version without player argument - defaults to command executor (self)
                                .executes(GhostBlockCommand::executeRemoveGhostBlockSelf)
                                // Version with player argument
                                .then(Commands.argument("player", EntityArgument.player())
                                    .executes(GhostBlockCommand::executeRemoveGhostBlock)
                                )
                            )
                        )
                    )
                )
                .then(Commands.literal("clear")
                    // Version without player argument - defaults to command executor (self)
                    .executes(GhostBlockCommand::executeClearGhostBlocksSelf)
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(GhostBlockCommand::executeClearGhostBlocks)
                    )
                )
                .then(Commands.literal("list")
                    // Version without player argument - defaults to command executor (self)
                    .executes(GhostBlockCommand::executeListGhostBlocksSelf)
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(GhostBlockCommand::executeListGhostBlocks)
                    )
                )
                // Backwards compatibility - allow /ghostblock x y z block player without "place" subcommand
                .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .then(Commands.argument("block", StringArgumentType.string())
                                .then(Commands.argument("player", EntityArgument.player())
                                    .executes(GhostBlockCommand::executePlaceGhostBlock)
                                )
                            )
                        )
                    )
                )
        );
    }

    // Execute for specified target player (old behavior)
    private static int executePlaceGhostBlock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executePlaceGhostBlockForTarget(context, EntityArgument.getPlayer(context, "player"));
    }

    // Execute for command executor (self) - like Litematica
    private static int executePlaceGhostBlockSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        String blockName = StringArgumentType.getString(context, "block");
        ServerPlayer targetPlayer = getCommandExecutor(context);
        return executePlaceGhostBlockForTarget(context, targetPlayer);
    }

    // Shared implementation
    private static int executePlaceGhostBlockForTarget(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) throws CommandSyntaxException {
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        String blockName = StringArgumentType.getString(context, "block");

        BlockPos pos = new BlockPos(x, y, z);

        // Parse block name
        Block block = parseBlock(blockName);
        if (block == null) {
            context.getSource().sendFailure(
                Component.literal("§cUnknown block: " + blockName + ". Use format: stone, diamond_block, etc.")
            );
            return 0;
        }

        // Store ghost block for this player
        GhostBlockManager.addGhostBlock(targetPlayer.getUUID(), pos, block.defaultBlockState());

        // Send block update packet only to the target player
        ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(pos, block.defaultBlockState());
        targetPlayer.connection.send(packet);

        // Send success message to command executor
        String message;
        if (targetPlayer != context.getSource().getPlayer()) {
            message = "§aPlaced ghost block '" + blockName + "' at (" + x + ", " + y + ", " + z + ") visible only to " + targetPlayer.getName().getString();
        } else {
            message = "§aPlaced ghost block '" + blockName + "' at (" + x + ", " + y + ", " + z + ") - only you can see it!";
        }
        context.getSource().sendSuccess(() -> Component.literal(message), false);

        // Send message to target player (only if it's not the executor)
        if (targetPlayer != context.getSource().getPlayer()) {
            targetPlayer.sendSystemMessage(
                Component.literal("§eA ghost block (" + blockName + ") has been placed at (" + x + ", " + y + ", " + z + ") - only you can see it!")
            );
        }

        return 1;
    }

    // Execute for specified target player (old behavior)
    private static int executeRemoveGhostBlock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeRemoveGhostBlockForTarget(context, EntityArgument.getPlayer(context, "player"));
    }

    // Execute for command executor (self) - like Litematica
    private static int executeRemoveGhostBlockSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        ServerPlayer targetPlayer = getCommandExecutor(context);
        return executeRemoveGhostBlockForTarget(context, targetPlayer);
    }

    // Shared implementation
    private static int executeRemoveGhostBlockForTarget(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) throws CommandSyntaxException {
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");

        BlockPos pos = new BlockPos(x, y, z);

        // Get the real block at that position
        BlockState realBlockState = targetPlayer.level().getBlockState(pos);

        // Remove ghost block from tracking
        GhostBlockManager.removeGhostBlock(targetPlayer.getUUID(), pos);

        // Send the real block state to the player to "hide" the ghost block
        ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(pos, realBlockState);
        targetPlayer.connection.send(packet);

        // Send success message
        String message;
        if (targetPlayer != context.getSource().getPlayer()) {
            message = "§aRemoved ghost block at (" + x + ", " + y + ", " + z + ") for " + targetPlayer.getName().getString();
        } else {
            message = "§aRemoved ghost block at (" + x + ", " + y + ", " + z + ")";
        }
        context.getSource().sendSuccess(() -> Component.literal(message), false);

        // Send message to target player (only if it's not the executor)
        if (targetPlayer != context.getSource().getPlayer()) {
            targetPlayer.sendSystemMessage(
                Component.literal("§eA ghost block at (" + x + ", " + y + ", " + z + ") has been removed!")
            );
        }

        return 1;
    }

    // Execute for specified target player (old behavior)
    private static int executeClearGhostBlocks(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeClearGhostBlocksForTarget(context, EntityArgument.getPlayer(context, "player"));
    }

    // Execute for command executor (self) - like Litematica
    private static int executeClearGhostBlocksSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = getCommandExecutor(context);
        return executeClearGhostBlocksForTarget(context, targetPlayer);
    }

    // Shared implementation
    private static int executeClearGhostBlocksForTarget(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) throws CommandSyntaxException {
        final int finalCount = GhostBlockManager.getGhostBlocks(targetPlayer.getUUID()).size();
        final String playerName = targetPlayer.getName().getString();

        // Send real block state for all ghost blocks
        for (BlockPos pos : GhostBlockManager.getGhostBlocks(targetPlayer.getUUID()).keySet()) {
            BlockState realBlockState = targetPlayer.level().getBlockState(pos);
            ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(pos, realBlockState);
            targetPlayer.connection.send(packet);
        }

        // Clear all ghost blocks
        GhostBlockManager.clearGhostBlocks(targetPlayer.getUUID());

        String message;
        if (targetPlayer != context.getSource().getPlayer()) {
            message = "§aCleared " + finalCount + " ghost block(s) for " + playerName;
        } else {
            message = "§aCleared " + finalCount + " ghost block(s) from your view";
        }
        context.getSource().sendSuccess(() -> Component.literal(message), false);

        // Send message to target player (only if it's not the executor)
        if (targetPlayer != context.getSource().getPlayer()) {
            targetPlayer.sendSystemMessage(
                Component.literal("§eAll your ghost blocks have been cleared!")
            );
        }

        return 1;
    }

    // Execute for specified target player (old behavior)
    private static int executeListGhostBlocks(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeListGhostBlocksForTarget(context, EntityArgument.getPlayer(context, "player"));
    }

    // Execute for command executor (self) - like Litematica
    private static int executeListGhostBlocksSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer targetPlayer = getCommandExecutor(context);
        return executeListGhostBlocksForTarget(context, targetPlayer);
    }

    // Shared implementation
    private static int executeListGhostBlocksForTarget(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer) throws CommandSyntaxException {
        Map<BlockPos, BlockState> ghostBlocks = GhostBlockManager.getGhostBlocks(targetPlayer.getUUID());

        if (ghostBlocks.isEmpty()) {
            String message;
            if (targetPlayer == context.getSource().getPlayer()) {
                message = "§eYou have no ghost blocks.";
            } else {
                message = "§e" + targetPlayer.getName().getString() + " has no ghost blocks.";
            }
            context.getSource().sendSuccess(() -> Component.literal(message), false);
            return 1;
        }

        // Build the message using Component appending
        StringBuilder sb = new StringBuilder();
        if (targetPlayer == context.getSource().getPlayer()) {
            sb.append("Your ghost blocks:");
        } else {
            sb.append("Ghost blocks for ").append(targetPlayer.getName().getString()).append(":");
        }
        for (Map.Entry<BlockPos, BlockState> entry : ghostBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            String blockName = BuiltInRegistries.BLOCK.getKey(entry.getValue().getBlock()).toString();
            sb.append("\n  - (").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ()).append(") - ").append(blockName);
        }
        
        final String message = sb.toString();
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static ServerPlayer getCommandExecutor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    private static Block parseBlock(String blockName) {
        // Handle names with or without namespace
        String fullName = blockName.contains(":") ? blockName : "minecraft:" + blockName;

        try {
            // Try to get block from registry using the registry's built-in lookup
            for (Block block : BuiltInRegistries.BLOCK) {
                String key = BuiltInRegistries.BLOCK.getKey(block).toString();
                if (key.equalsIgnoreCase(fullName) || key.equalsIgnoreCase("minecraft:" + blockName) || key.endsWith(":" + blockName)) {
                    return block;
                }
            }
        } catch (Exception e) {
            GitBuildMod.LOGGER.warn("Error parsing block name: {}", blockName, e);
        }
        return null;
    }
}