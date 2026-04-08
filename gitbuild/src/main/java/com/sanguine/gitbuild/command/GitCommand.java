package com.sanguine.gitbuild.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.sanguine.gitbuild.*;
import com.sanguine.gitbuild.BackendApiClient.ApiResponse;
import com.sanguine.gitbuild.BackendApiClient.BlockData;
import com.sanguine.gitbuild.PlayerSession.BlockChange;
import com.sanguine.gitbuild.PlayerSession.ChangeType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.security.MessageDigest;
import java.util.*;

public class GitCommand {

    // Suggestion providers
    private static final SuggestionProvider<CommandSourceStack> REPO_SUGGESTIONS = (context, builder) -> {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            PlayerSession session = SessionManager.getSession(player);
            if (session.isAuthenticated()) {
                ApiResponse response = BackendApiClient.listRepos(session.getUsername(), session.getUuid());
                if (response.success && response.data != null) {
                    JsonObject data = response.data.getAsJsonObject();
                    if (data.has("repos")) {
                        JsonArray repos = data.getAsJsonArray("repos");
                        for (JsonElement repo : repos) {
                            builder.suggest(repo.getAsString());
                        }
                    }
                }
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> COMMIT_SUGGESTIONS = (context, builder) -> {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            PlayerSession session = SessionManager.getSession(player);
            if (session.isAuthenticated() && session.hasActiveRepo()) {
                ApiResponse response = BackendApiClient.getLog(session.getUsername(), session.getCurrentRepo());
                if (response.success && response.data != null) {
                    JsonArray commits = response.data.getAsJsonArray();
                    for (JsonElement commit : commits) {
                        JsonObject c = commit.getAsJsonObject();
                        if (c.has("commit_hash")) {
                            String hash = c.get("commit_hash").getAsString();
                            String message = c.has("message") ? c.get("message").getAsString() : "";
                            builder.suggest(hash, () -> message);
                        }
                    }
                }
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("git")
                // Authentication
                .then(Commands.literal("auth")
                    .then(Commands.argument("username", StringArgumentType.word())
                        .executes(GitCommand::executeAuthNoPassword)
                        .then(Commands.argument("password", StringArgumentType.greedyString())
                            .executes(GitCommand::executeAuth)
                        )
                    )
                )
                // Repository management
                .then(Commands.literal("init")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(GitCommand::executeInit)
                    )
                )
                .then(Commands.literal("activate")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(REPO_SUGGESTIONS)
                        .executes(GitCommand::executeActivate)
                    )
                )
                .then(Commands.literal("repoList")
                    .executes(GitCommand::executeRepoList)
                )
                // Staging
                .then(Commands.literal("add")
                    .then(Commands.argument("from", BlockPosArgument.blockPos())
                        .executes(GitCommand::executeAddSingle)
                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                            .executes(GitCommand::executeAddArea)
                            .then(Commands.literal("hollow")
                                .executes(GitCommand::executeAddAreaHollow)
                            )
                            .then(Commands.literal("outline")
                                .executes(GitCommand::executeAddAreaOutline)
                            )
                        )
                    )
                )
                .then(Commands.literal("rm")
                    .then(Commands.argument("from", BlockPosArgument.blockPos())
                        .executes(GitCommand::executeRmSingle)
                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                            .executes(GitCommand::executeRmArea)
                            .then(Commands.literal("hollow")
                                .executes(GitCommand::executeRmAreaHollow)
                            )
                            .then(Commands.literal("outline")
                                .executes(GitCommand::executeRmAreaOutline)
                            )
                        )
                    )
                )
                .then(Commands.literal("unstage")
                    .then(Commands.argument("from", BlockPosArgument.blockPos())
                        .executes(GitCommand::executeUnstageSingle)
                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                            .executes(GitCommand::executeUnstageArea)
                        )
                    )
                )
                .then(Commands.literal("autoadd")
                    .executes(GitCommand::executeAutoAddToggle)
                    .then(Commands.literal("on")
                        .executes(GitCommand::executeAutoAddOn)
                    )
                    .then(Commands.literal("off")
                        .executes(GitCommand::executeAutoAddOff)
                    )
                    .then(Commands.literal("toggle")
                        .executes(GitCommand::executeAutoAddToggle)
                    )
                )
                .then(Commands.literal("autorm")
                    .executes(GitCommand::executeAutoRmToggle)
                    .then(Commands.literal("on")
                        .executes(GitCommand::executeAutoRmOn)
                    )
                    .then(Commands.literal("off")
                        .executes(GitCommand::executeAutoRmOff)
                    )
                    .then(Commands.literal("toggle")
                        .executes(GitCommand::executeAutoRmToggle)
                    )
                )
                // Commit
                .then(Commands.literal("commit")
                    .then(Commands.literal("-m")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(GitCommand::executeCommit)
                        )
                    )
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(GitCommand::executeCommit)
                    )
                )
                .then(Commands.literal("status")
                    .executes(GitCommand::executeStatus)
                )
                // History
                .then(Commands.literal("log")
                    .executes(GitCommand::executeLog)
                )
                .then(Commands.literal("commitList")
                    .executes(GitCommand::executeLog)
                )
                .then(Commands.literal("revert")
                    .executes(GitCommand::executeRevertHead)
                    .then(Commands.argument("hash", StringArgumentType.word())
                        .suggests(COMMIT_SUGGESTIONS)
                        .executes(GitCommand::executeRevert)
                    )
                )
                .then(Commands.literal("reset")
                    .executes(GitCommand::executeResetHead)
                    .then(Commands.argument("hash", StringArgumentType.word())
                        .suggests(COMMIT_SUGGESTIONS)
                        .executes(GitCommand::executeReset)
                    )
                )
                // Remote
                .then(Commands.literal("remote")
                    .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .then(Commands.argument("url", StringArgumentType.greedyString())
                                .executes(GitCommand::executeRemoteAdd)
                            )
                        )
                    )
                    .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(GitCommand::executeRemoteRemove)
                        )
                    )
                    .then(Commands.literal("list")
                        .executes(GitCommand::executeRemoteList)
                    )
                )
                .then(Commands.literal("push")
                    .executes(GitCommand::executePushDefault)
                    .then(Commands.argument("remote", StringArgumentType.word())
                        .executes(GitCommand::executePush)
                    )
                )
                .then(Commands.literal("pull")
                    .executes(GitCommand::executePullDefault)
                    .then(Commands.argument("remote", StringArgumentType.word())
                        .executes(GitCommand::executePull)
                    )
                )
                .then(Commands.literal("fetch")
                    .executes(GitCommand::executeFetchDefault)
                    .then(Commands.argument("remote", StringArgumentType.word())
                        .executes(GitCommand::executeFetch)
                    )
                )
                .then(Commands.literal("clone")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("source", StringArgumentType.greedyString())
                            .executes(GitCommand::executeClone)
                        )
                    )
                )
        );
    }

    // Helper methods
    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    private static String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            // Return full 64-character hash to match backend
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private static void checkAuthenticated(PlayerSession session, CommandSourceStack source) throws CommandSyntaxException {
        if (!session.isAuthenticated()) {
            source.sendFailure(Component.literal("§cNot authenticated. Use /git auth <username> [password]"));
            throw new CommandSyntaxException(null, Component.literal("Not authenticated"));
        }
    }

    private static void checkRepoActive(PlayerSession session, CommandSourceStack source) throws CommandSyntaxException {
        if (!session.hasActiveRepo()) {
            source.sendFailure(Component.literal("§cNo active repository. Use /git activate <name>"));
            throw new CommandSyntaxException(null, Component.literal("No active repo"));
        }
    }

    private static void checkServerOnline(CommandSourceStack source) throws CommandSyntaxException {
        if (!BackendApiClient.isServerOnline()) {
            source.sendFailure(Component.literal("§cBackend server is offline!"));
            throw new CommandSyntaxException(null, Component.literal("Server offline"));
        }
    }

    // Authentication
    private static int executeAuthNoPassword(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        String username = StringArgumentType.getString(context, "username");
        
        checkServerOnline(context.getSource());
        
        // Try login without password
        ApiResponse response = BackendApiClient.login(username, "");
        if (response.success && response.data != null) {
            JsonObject data = response.data.getAsJsonObject();
            String uuid = data.get("uuid").getAsString();
            String mcUsername = data.get("minecraft_username").getAsString();
            session.setAuthenticated(username, uuid);
            context.getSource().sendSuccess(() -> 
                Component.literal("§aAuthenticated as " + username + " (MC: " + mcUsername + ")"), false);
            return 1;
        }
        
        context.getSource().sendFailure(Component.literal("§cAuthentication failed. Password required."));
        return 0;
    }

    private static int executeAuth(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        String username = StringArgumentType.getString(context, "username");
        String password = StringArgumentType.getString(context, "password");
        
        checkServerOnline(context.getSource());
        
        // Hash password (simple SHA-256)
        String hashedPassword = generateHash(username + password);   
        
        ApiResponse response = BackendApiClient.login(username, hashedPassword);
        if (response.success && response.data != null) {
            JsonObject data = response.data.getAsJsonObject();
            String uuid = data.get("uuid").getAsString();
            String mcUsername = data.get("minecraft_username").getAsString();
            session.setAuthenticated(username, uuid);
            context.getSource().sendSuccess(() -> 
                Component.literal("§aAuthenticated as " + username + " (MC: " + mcUsername + ")"), false);
            return 1;
        }
        
        context.getSource().sendFailure(Component.literal("§cAuthentication failed: " + response.message));
        return 0;
    }

    // Repository management
    private static int executeInit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        String repoName = StringArgumentType.getString(context, "name");
        
        checkServerOnline(context.getSource());
        checkAuthenticated(session, context.getSource());
        
        ApiResponse response = BackendApiClient.createRepo(session.getUsername(), repoName, session.getUuid());
        if (response.success) {
            session.setCurrentRepo(repoName);
            context.getSource().sendSuccess(() -> 
                Component.literal("§aInitialized repository: " + repoName), false);
            return 1;
        }
        
        context.getSource().sendFailure(Component.literal("§cFailed to create repo: " + response.message));
        return 0;
    }

    private static int executeActivate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        String repoName = StringArgumentType.getString(context, "name");
        
        checkAuthenticated(session, context.getSource());
        
        // Check if repo exists
        ApiResponse response = BackendApiClient.repoExists(session.getUsername(), repoName);
        if (response.success && response.data != null) {
            JsonObject data = response.data.getAsJsonObject();
            if (data.has("exists") && data.get("exists").getAsBoolean()) {
                session.setCurrentRepo(repoName);
                context.getSource().sendSuccess(() -> 
                    Component.literal("§aSwitched to repository: " + repoName), false);
                return 1;
            }
        }
        
        context.getSource().sendFailure(Component.literal("§cRepository not found: " + repoName));
        return 0;
    }

    private static int executeRepoList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        
        checkAuthenticated(session, context.getSource());
        
        ApiResponse response = BackendApiClient.listRepos(session.getUsername(), session.getUuid());
        if (response.success && response.data != null) {
            JsonObject data = response.data.getAsJsonObject();
            if (data.has("repos")) {
                JsonArray repos = data.getAsJsonArray("repos");
                StringBuilder sb = new StringBuilder("§aRepositories:\n");
                for (JsonElement repo : repos) {
                    String repoName = repo.getAsString();
                    String marker = repoName.equals(session.getCurrentRepo()) ? " §e[active]" : "";
                    sb.append("  §7- §f").append(repoName).append(marker).append("\n");
                }
                if (repos.size() == 0) {
                    sb.append("  §7No repositories found");
                }
                final String message = sb.toString();
                context.getSource().sendSuccess(() -> Component.literal(message), false);
                return 1;
            }
        }
        
        context.getSource().sendFailure(Component.literal("§cFailed to list repos: " + response.message));
        return 0;
    }

    // Staging - Single block
    private static int executeAddSingle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        BlockPos pos = BlockPosArgument.getBlockPos(context, "from");
        
        checkRepoActive(session, context.getSource());
        
        BlockState currentState = player.level().getBlockState(pos);
        String blockName = BuiltInRegistries.BLOCK.getKey(currentState.getBlock()).toString();
        
        session.stageBlock(pos, null, currentState, ChangeType.ADD);
        context.getSource().sendSuccess(() -> 
            Component.literal("§aStaged: " + blockName + " at " + formatPos(pos)), false);
        return 1;
    }

    private static int executeRmSingle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        BlockPos pos = BlockPosArgument.getBlockPos(context, "from");
        
        checkRepoActive(session, context.getSource());
        
        BlockState currentState = player.level().getBlockState(pos);
        BlockState airState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        
        // Stage the removal
        session.stageBlock(pos, currentState, airState, ChangeType.REMOVE);
        
        // Actually remove the block (requires setblock permission)
        player.level().setBlock(pos, airState, 3);
        
        context.getSource().sendSuccess(() -> 
            Component.literal("§aRemoved and staged: block at " + formatPos(pos)), false);
        return 1;
    }

    private static int executeUnstageSingle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        BlockPos pos = BlockPosArgument.getBlockPos(context, "from");
        
        session.unstageBlock(pos);
        context.getSource().sendSuccess(() -> 
            Component.literal("§aUnstaged block at " + formatPos(pos)), false);
        return 1;
    }

    // Staging - Area
    private static int executeAddArea(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeAddAreaInternal(context, false, false);
    }

    private static int executeAddAreaHollow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeAddAreaInternal(context, true, false);
    }

    private static int executeAddAreaOutline(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeAddAreaInternal(context, false, true);
    }

    private static int executeAddAreaInternal(CommandContext<CommandSourceStack> context, boolean hollow, boolean outline) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        BlockPos from = BlockPosArgument.getBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getBlockPos(context, "to");
        
        checkRepoActive(session, context.getSource());
        
        int count = 0;
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean isEdge = (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ);
                    
                    if (hollow && !isEdge) continue;
                    if (outline && !isEdge) continue;
                    
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = player.level().getBlockState(pos);
                    if (!state.isAir()) {
                        session.stageBlock(pos, null, state, ChangeType.ADD);
                        count++;
                    }
                }
            }
        }
        
        final int finalCount = count;
        context.getSource().sendSuccess(() -> 
            Component.literal("§aStaged " + finalCount + " blocks"), false);
        return count;
    }

    private static int executeRmArea(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeRmAreaInternal(context, false, false);
    }

    private static int executeRmAreaHollow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeRmAreaInternal(context, true, false);
    }

    private static int executeRmAreaOutline(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeRmAreaInternal(context, false, true);
    }

    private static int executeRmAreaInternal(CommandContext<CommandSourceStack> context, boolean hollow, boolean outline) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        BlockPos from = BlockPosArgument.getBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getBlockPos(context, "to");
        
        checkRepoActive(session, context.getSource());
        
        int count = 0;
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());
        
        BlockState airState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean isEdge = (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ);
                    
                    if (hollow && !isEdge) continue;
                    if (outline && !isEdge) continue;
                    
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = player.level().getBlockState(pos);
                    if (!state.isAir()) {
                        session.stageBlock(pos, state, airState, ChangeType.REMOVE);
                        player.level().setBlock(pos, airState, 3);
                        count++;
                    }
                }
            }
        }
        
        final int finalCount = count;
        context.getSource().sendSuccess(() -> 
            Component.literal("§aRemoved and staged " + finalCount + " blocks"), false);
        return count;
    }

    private static int executeUnstageArea(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        BlockPos from = BlockPosArgument.getBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getBlockPos(context, "to");
        
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());
        
        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    session.unstageBlock(pos);
                    count++;
                }
            }
        }
        
        final int finalCount = count;
        context.getSource().sendSuccess(() -> 
            Component.literal("§aUnstaged " + finalCount + " positions"), false);
        return count;
    }

    // Auto tracking
    private static int executeAutoAddToggle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        session.toggleAutoAdd();
        boolean enabled = session.isAutoAdd();
        context.getSource().sendSuccess(() -> 
            Component.literal("§aAuto-add " + (enabled ? "enabled" : "disabled")), false);
        return 1;
    }

    private static int executeAutoAddOn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        session.setAutoAdd(true);
        context.getSource().sendSuccess(() -> Component.literal("§aAuto-add enabled"), false);
        return 1;
    }

    private static int executeAutoAddOff(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        session.setAutoAdd(false);
        context.getSource().sendSuccess(() -> Component.literal("§aAuto-add disabled"), false);
        return 1;
    }

    private static int executeAutoRmToggle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        session.toggleAutoRm();
        boolean enabled = session.isAutoRm();
        context.getSource().sendSuccess(() -> 
            Component.literal("§aAuto-rm " + (enabled ? "enabled" : "disabled")), false);
        return 1;
    }

    private static int executeAutoRmOn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        session.setAutoRm(true);
        context.getSource().sendSuccess(() -> Component.literal("§aAuto-rm enabled"), false);
        return 1;
    }

    private static int executeAutoRmOff(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        session.setAutoRm(false);
        context.getSource().sendSuccess(() -> Component.literal("§aAuto-rm disabled"), false);
        return 1;
    }

    // Commit
    private static int executeCommit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        String message = StringArgumentType.getString(context, "message");
        
        checkServerOnline(context.getSource());
        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());
        
        Map<BlockPos, BlockChange> staged = session.getStagingArea();
        if (staged.isEmpty()) {
            context.getSource().sendFailure(Component.literal("§cNothing to commit. Use /git add to stage changes."));
            return 0;
        }
        
        // Convert staged changes to block data
        List<BlockData> blocks = new ArrayList<>();
        StringBuilder commitData = new StringBuilder();
        for (Map.Entry<BlockPos, BlockChange> entry : staged.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockChange change = entry.getValue();
            String blockName = BuiltInRegistries.BLOCK.getKey(change.newState.getBlock()).toString();
            String blockState = change.newState.toString();
            blocks.add(new BlockData(pos.getX(), pos.getY(), pos.getZ(), blockName, blockState));
            commitData.append(pos.toString()).append(blockName);
        }
        
        // Generate commit hash
        String commitHash = generateHash(session.getUsername() + session.getCurrentRepo() + message + commitData.toString() + System.currentTimeMillis());
        String commitName = "commit-" + commitHash.substring(0, 8);
        
        ApiResponse response = BackendApiClient.createCommit(
            session.getUsername(), 
            session.getCurrentRepo(), 
            session.getUuid(),
            commitName, 
            commitHash, 
            message, 
            blocks
        );
        
        if (response.success) {
            session.clearStaging();
            context.getSource().sendSuccess(() -> 
                Component.literal("§aCommitted: " + message + " §7(" + commitHash.substring(0, 8) + ")"), false);
            return 1;
        }
        
        context.getSource().sendFailure(Component.literal("§cCommit failed: " + response.message));
        return 0;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        
        checkRepoActive(session, context.getSource());
        
        Map<BlockPos, BlockChange> staged = session.getStagingArea();
        StringBuilder sb = new StringBuilder();
        
        sb.append("§aRepository: §f").append(session.getCurrentRepo()).append("\n");
        sb.append("§aStaged changes: §f").append(staged.size()).append(" blocks\n");
        
        if (!staged.isEmpty()) {
            sb.append("§7Staged blocks:\n");
            int count = 0;
            for (Map.Entry<BlockPos, BlockChange> entry : staged.entrySet()) {
                if (count++ >= 10) {
                    sb.append("  §7... and ").append(staged.size() - 10).append(" more\n");
                    break;
                }
                BlockPos pos = entry.getKey();
                BlockChange change = entry.getValue();
                String type = change.type == ChangeType.ADD ? "+" : change.type == ChangeType.REMOVE ? "-" : "~";
                sb.append("  §7").append(type).append(" ").append(formatPos(pos)).append("\n");
            }
        }
        
        sb.append("\n§aAuto-add: §f").append(session.isAutoAdd() ? "on" : "off");
        sb.append(" §aAuto-rm: §f").append(session.isAutoRm() ? "on" : "off");
        
        final String message = sb.toString();
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    // History
    private static int executeLog(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        
        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());
        checkServerOnline(context.getSource());
        
        ApiResponse response = BackendApiClient.getLog(session.getUsername(), session.getCurrentRepo());
        if (response.success && response.data != null) {
            JsonArray commits = response.data.getAsJsonArray();
            StringBuilder sb = new StringBuilder("§aCommit history:\n");
            
            int count = 0;
            for (JsonElement element : commits) {
                if (count++ >= 20) {
                    sb.append("§7... and more\n");
                    break;
                }
                JsonObject commit = element.getAsJsonObject();
                String hash = commit.get("commit_hash").getAsString();
                String shortHash = hash.substring(0, 8);
                String message = commit.get("message").getAsString();
                boolean isActive = commit.has("is_active") && commit.get("is_active").getAsBoolean();
                
                String marker = isActive ? "§e* " : "§7  ";
                sb.append(marker).append("§f").append(shortHash).append(" §7").append(message).append("\n");
            }
            
            if (commits.size() == 0) {
                sb.append("§7No commits yet");
            }
            
            final String msg = sb.toString();
            context.getSource().sendSuccess(() -> Component.literal(msg), false);
            return 1;
        }
        
        context.getSource().sendFailure(Component.literal("§cFailed to get log: " + response.message));
        return 0;
    }

    private static int executeRevertHead(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        
        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());
        checkServerOnline(context.getSource());
        
        // Get the most recent commit
        ApiResponse logResponse = BackendApiClient.getLog(session.getUsername(), session.getCurrentRepo());
        if (logResponse.success && logResponse.data != null) {
            JsonArray commits = logResponse.data.getAsJsonArray();
            if (commits.size() > 0) {
                JsonObject headCommit = commits.get(0).getAsJsonObject();
                String hash = headCommit.get("commit_hash").getAsString();
                return revertToCommit(context, session, player, hash);
            }
        }
        
        context.getSource().sendFailure(Component.literal("§cNo commits to revert to"));
        return 0;
    }

    private static int executeRevert(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        String hash = StringArgumentType.getString(context, "hash");
        
        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());
        checkServerOnline(context.getSource());
        
        return revertToCommit(context, session, player, hash);
    }

    private static int revertToCommit(CommandContext<CommandSourceStack> context, PlayerSession session, ServerPlayer player, String hash) {
        ApiResponse response = BackendApiClient.getCommitBlocks(session.getUsername(), session.getCurrentRepo(), hash);
        if (response.success && response.data != null) {
            JsonArray blocks = response.data.getAsJsonArray();
            int count = 0;
            for (JsonElement element : blocks) {
                JsonObject block = element.getAsJsonObject();
                int x = block.get("x").getAsInt();
                int y = block.get("y").getAsInt();
                int z = block.get("z").getAsInt();
                String blockName = block.get("block_name").getAsString();
                
                BlockPos pos = new BlockPos(x, y, z);
                
                // Parse and set block
                try {
                    Block blockType = parseBlock(blockName);
                    if (blockType != null) {
                        player.level().setBlock(pos, blockType.defaultBlockState(), 3);
                        count++;
                    }
                } catch (Exception e) {
                    GitBuildMod.LOGGER.warn("Could not set block: {}", blockName);
                }
            }
            
            final int finalCount = count;
            context.getSource().sendSuccess(() -> 
                Component.literal("§aReverted to " + hash.substring(0, 8) + " - set " + finalCount + " blocks"), false);
            return 1;
        }
        
        context.getSource().sendFailure(Component.literal("§cRevert failed: " + response.message));
        return 0;
    }

    private static int executeResetHead(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        
        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());
        
        session.clearStaging();
        context.getSource().sendSuccess(() -> 
            Component.literal("§aReset staging area (HEAD unchanged)"), false);
        return 1;
    }

    private static int executeReset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        String hash = StringArgumentType.getString(context, "hash");
        
        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());
        checkServerOnline(context.getSource());
        
        ApiResponse response = BackendApiClient.hardReset(session.getUsername(), session.getCurrentRepo(), hash);
        if (response.success) {
            session.clearStaging();
            context.getSource().sendSuccess(() -> 
                Component.literal("§aHard reset to " + hash.substring(0, 8)), false);
            return 1;
        }
        
        context.getSource().sendFailure(Component.literal("§cReset failed: " + response.message));
        return 0;
    }

    // Remote
    private static int executeRemoteAdd(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        String name = StringArgumentType.getString(context, "name");
        String url = StringArgumentType.getString(context, "url");
        
        // Parse URL (format: username/repo or full URL)
        String[] parts = url.split("/");
        if (parts.length >= 2) {
            String remoteUser = parts[parts.length - 2];
            String remoteRepo = parts[parts.length - 1].replace(".git", "");
            session.addRemote(name, remoteUser + "/" + remoteRepo, "");
            context.getSource().sendSuccess(() -> 
                Component.literal("§aAdded remote '" + name + "' -> " + remoteUser + "/" + remoteRepo), false);
            return 1;
        }
        
        context.getSource().sendFailure(Component.literal("§cInvalid remote URL format. Use: username/repo"));
        return 0;
    }

    private static int executeRemoteRemove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        String name = StringArgumentType.getString(context, "name");
        
        session.removeRemote(name);
        context.getSource().sendSuccess(() -> Component.literal("§aRemoved remote '" + name + "'"), false);
        return 1;
    }

    private static int executeRemoteList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        
        Map<String, PlayerSession.RemoteCredentials> remotes = session.getAllRemotes();
        StringBuilder sb = new StringBuilder("§aRemotes:\n");
        for (Map.Entry<String, PlayerSession.RemoteCredentials> entry : remotes.entrySet()) {
            sb.append("  §7").append(entry.getKey()).append(" -> §f").append(entry.getValue().username).append("\n");
        }
        if (remotes.isEmpty()) {
            sb.append("  §7No remotes configured");
        }
        
        final String message = sb.toString();
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int executePushDefault(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executePushInternal(context, "origin");
    }

    private static int executePush(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String remote = StringArgumentType.getString(context, "remote");
        return executePushInternal(context, remote);
    }

    private static int executePushInternal(CommandContext<CommandSourceStack> context, String remoteName) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        
        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());
        checkServerOnline(context.getSource());
        
        PlayerSession.RemoteCredentials remote = session.getRemote(remoteName);
        if (remote == null) {
            context.getSource().sendFailure(Component.literal("§cRemote '" + remoteName + "' not found"));
            return 0;
        }
        
        String[] parts = remote.username.split("/");
        if (parts.length != 2) {
            context.getSource().sendFailure(Component.literal("§cInvalid remote format"));
            return 0;
        }
        
        ApiResponse response = BackendApiClient.push(
            session.getUsername(), 
            session.getCurrentRepo(), 
            session.getUuid(),
            parts[0], 
            parts[1]
        );
        
        if (response.success) {
            context.getSource().sendSuccess(() -> 
                Component.literal("§aPushed to " + remoteName), false);
            return 1;
        }
        
        context.getSource().sendFailure(Component.literal("§cPush failed: " + response.message));
        return 0;
    }

    private static int executePullDefault(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executePullInternal(context, "origin");
    }

    private static int executePull(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String remote = StringArgumentType.getString(context, "remote");
        return executePullInternal(context, remote);
    }

    private static int executePullInternal(CommandContext<CommandSourceStack> context, String remoteName) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        
        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());
        checkServerOnline(context.getSource());
        
        PlayerSession.RemoteCredentials remote = session.getRemote(remoteName);
        if (remote == null) {
            context.getSource().sendFailure(Component.literal("§cRemote '" + remoteName + "' not found"));
            return 0;
        }
        
        String[] parts = remote.username.split("/");
        if (parts.length != 2) {
            context.getSource().sendFailure(Component.literal("§cInvalid remote format"));
            return 0;
        }
        
        ApiResponse response = BackendApiClient.pull(
            session.getUsername(), 
            session.getCurrentRepo(), 
            session.getUuid(),
            parts[0], 
            parts[1]
        );
        
        if (response.success) {
            JsonObject data = response.data.getAsJsonObject();
            int added = data.has("commits_added") ? data.get("commits_added").getAsInt() : 0;
            final int finalAdded = added;
            context.getSource().sendSuccess(() -> 
                Component.literal("§aPulled from " + remoteName + " (" + finalAdded + " commits)"), false);
            return 1;
        }
        
        context.getSource().sendFailure(Component.literal("§cPull failed: " + response.message));
        return 0;
    }

    private static int executeFetchDefault(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeFetchInternal(context, "origin");
    }

    private static int executeFetch(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String remote = StringArgumentType.getString(context, "remote");
        return executeFetchInternal(context, remote);
    }

    private static int executeFetchInternal(CommandContext<CommandSourceStack> context, String remoteName) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        
        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());
        checkServerOnline(context.getSource());
        
        PlayerSession.RemoteCredentials remote = session.getRemote(remoteName);
        if (remote == null) {
            context.getSource().sendFailure(Component.literal("§cRemote '" + remoteName + "' not found"));
            return 0;
        }
        
        String[] parts = remote.username.split("/");
        if (parts.length != 2) {
            context.getSource().sendFailure(Component.literal("§cInvalid remote format"));
            return 0;
        }
        
        ApiResponse response = BackendApiClient.fetch(
            session.getUsername(), 
            session.getCurrentRepo(), 
            session.getUuid(),
            parts[0], 
            parts[1]
        );
        
        if (response.success) {
            context.getSource().sendSuccess(() -> 
                Component.literal("§aFetched from " + remoteName), false);
            return 1;
        }
        
        context.getSource().sendFailure(Component.literal("§cFetch failed: " + response.message));
        return 0;
    }

    private static int executeClone(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        String newRepoName = StringArgumentType.getString(context, "name");
        String source = StringArgumentType.getString(context, "source");
        
        checkAuthenticated(session, context.getSource());
        checkServerOnline(context.getSource());
        
        // Parse source (format: username/repo)
        String[] parts = source.split("/");
        if (parts.length != 2) {
            context.getSource().sendFailure(Component.literal("§cInvalid source format. Use: username/repo"));
            return 0;
        }
        
        String sourceUser = parts[0];
        String sourceRepo = parts[1];
        
        ApiResponse response = BackendApiClient.cloneRepo(
            session.getUsername(), 
            session.getUuid(),
            sourceUser, 
            sourceRepo, 
            newRepoName
        );
        
        if (response.success) {
            // Set as active repo
            session.setCurrentRepo(newRepoName);
            
            // Optionally apply to world (like /git put)
            context.getSource().sendSuccess(() -> 
                Component.literal("§aCloned " + source + " to " + newRepoName + " (use /git revert to apply)"), false);
            return 1;
        }
        
        context.getSource().sendFailure(Component.literal("§cClone failed: " + response.message));
        return 0;
    }

    // Utility
    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private static net.minecraft.world.level.block.Block parseBlock(String blockName) {
        // Handle names with or without namespace
        String fullName = blockName.contains(":") ? blockName : "minecraft:" + blockName;

        try {
            // Try to get block from registry using the registry's built-in lookup
            for (net.minecraft.world.level.block.Block block : BuiltInRegistries.BLOCK) {
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
