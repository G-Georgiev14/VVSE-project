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
import com.sanguine.gitbuild.BuildInstance.CommitData;
import com.sanguine.gitbuild.BuildInstance.RelativeBlockData;
import com.sanguine.gitbuild.PlayerSession.BlockChange;
import com.sanguine.gitbuild.PlayerSession.ChangeType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.security.MessageDigest;
import java.util.*;

public class GitCommand {

    // Authentication requirement predicate - hides commands from unauthenticated players
    private static final java.util.function.Predicate<CommandSourceStack> IS_AUTHENTICATED = source -> {
        ServerPlayer player = source.getPlayer();
        if (player == null) return false;
        PlayerSession session = SessionManager.getSession(player);
        return session.isAuthenticated();
    };

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
                            String shortHash = hash.substring(0, 8);
                            String message = c.has("message") ? c.get("message").getAsString() : "";
                            builder.suggest(shortHash, () -> message);
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
                // Help - available to all players
                .then(Commands.literal("help")
                    .executes(GitCommand::executeHelp)
                )
                // Repository management
                .then(Commands.literal("init").requires(IS_AUTHENTICATED)
                    .then(Commands.literal("public")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(context -> executeInit(context, "public"))
                        )
                    )
                    .then(Commands.literal("private")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(context -> executeInit(context, "private"))
                        )
                    )
                )
                .then(Commands.literal("activate").requires(IS_AUTHENTICATED)
                    .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(REPO_SUGGESTIONS)
                        .executes(GitCommand::executeActivate)
                    )
                )
                .then(Commands.literal("repoList").requires(IS_AUTHENTICATED)
                    .executes(GitCommand::executeRepoList)
                )
                // Staging
                .then(Commands.literal("add").requires(IS_AUTHENTICATED)
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
                .then(Commands.literal("rm").requires(IS_AUTHENTICATED)
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
                .then(Commands.literal("unstage").requires(IS_AUTHENTICATED)
                    .then(Commands.argument("from", BlockPosArgument.blockPos())
                        .executes(GitCommand::executeUnstageSingle)
                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                            .executes(GitCommand::executeUnstageArea)
                        )
                    )
                )
                .then(Commands.literal("autoadd").requires(IS_AUTHENTICATED)
                    .executes(GitCommand::executeAutoAddStatus)
                    .then(Commands.literal("on")
                        .executes(GitCommand::executeAutoAddOn)
                    )
                    .then(Commands.literal("off")
                        .executes(GitCommand::executeAutoAddOff)
                    )
                )
                .then(Commands.literal("autorm").requires(IS_AUTHENTICATED)
                    .executes(GitCommand::executeAutoRmStatus)
                    .then(Commands.literal("on")
                        .executes(GitCommand::executeAutoRmOn)
                    )
                    .then(Commands.literal("off")
                        .executes(GitCommand::executeAutoRmOff)
                    )
                )
                // Commit
                .then(Commands.literal("commit").requires(IS_AUTHENTICATED)
                    .then(Commands.literal("-m")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(GitCommand::executeCommit)
                        )
                    )
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(GitCommand::executeCommit)
                    )
                )
                .then(Commands.literal("status").requires(IS_AUTHENTICATED)
                    .executes(GitCommand::executeStatus)
                )
                .then(Commands.literal("diff").requires(IS_AUTHENTICATED)
                    .executes(GitCommand::executeDiff)
                    .then(Commands.literal("head")
                        .executes(GitCommand::executeDiffHead)
                    )
                    .then(Commands.argument("commit", StringArgumentType.word())
                        .executes(GitCommand::executeDiffAgainstCommit)
                    )
                    .then(Commands.literal("clear")
                        .executes(GitCommand::executeDiffClear)
                    )
                )
                // History
                .then(Commands.literal("log").requires(IS_AUTHENTICATED)
                    .executes(GitCommand::executeLog)
                )
                .then(Commands.literal("commitList").requires(IS_AUTHENTICATED)
                    .executes(GitCommand::executeLog)
                )
                .then(Commands.literal("revert").requires(IS_AUTHENTICATED)
                    .executes(GitCommand::executeRevertHead)
                    .then(Commands.argument("hash", StringArgumentType.word())
                        .suggests(COMMIT_SUGGESTIONS)
                        .executes(GitCommand::executeRevert)
                    )
                )
                .then(Commands.literal("reset").requires(IS_AUTHENTICATED)
                    .executes(GitCommand::executeResetHead)
                    .then(Commands.argument("hash", StringArgumentType.word())
                        .suggests(COMMIT_SUGGESTIONS)
                        .executes(GitCommand::executeReset)
                    )
                )
                .then(Commands.literal("push").requires(IS_AUTHENTICATED)
                    .executes(GitCommand::executePush)
                )
                .then(Commands.literal("pull").requires(IS_AUTHENTICATED)
                    .then(Commands.argument("source", StringArgumentType.greedyString())
                        .executes(GitCommand::executePull)
                    )
                )
                .then(Commands.literal("clone").requires(IS_AUTHENTICATED)
                    // /git clone - Start preview with active repo at player position
                    .executes(GitCommand::executeClonePreview)
                    // /git clone anchor - Set anchor at target block
                    .then(Commands.literal("anchor")
                        .executes(GitCommand::executeCloneAnchor)
                    )
                    // /git clone rotate <angle>
                    .then(Commands.literal("rotate")
                        .then(Commands.argument("angle", StringArgumentType.word())
                            .executes(GitCommand::executeCloneRotate)
                        )
                    )
                    // /git clone confirm - Paste blocks (OP only)
                    .then(Commands.literal("confirm")
                        .executes(GitCommand::executeCloneConfirm)
                    )
                    // /git clone cancel - Clear preview
                    .then(Commands.literal("cancel")
                        .executes(GitCommand::executeCloneCancel)
                    )
                    // Legacy: /git clone <name> <source> - Immediate clone
                    .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("source", StringArgumentType.greedyString())
                            .executes(GitCommand::executeCloneLegacy)
                        )
                    )
                )
                // Instance management
                .then(Commands.literal("instance").requires(IS_AUTHENTICATED)
                    // /git instance list - Show instances in current dimension
                    .then(Commands.literal("list")
                        .executes(GitCommand::executeInstanceList)
                    )
                    // /git instance highlight [id|nearest] - Show beacon beam at anchor
                    .then(Commands.literal("highlight")
                        .executes(GitCommand::executeInstanceHighlightNearest)
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                            .executes(GitCommand::executeInstanceHighlight)
                        )
                    )
                    // /git instance select <id> - Switch to instance
                    .then(Commands.literal("select")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                            .executes(GitCommand::executeInstanceSelect)
                        )
                    )
                    // /git instance new [name] - Force new instance
                    .then(Commands.literal("new")
                        .executes(GitCommand::executeInstanceNew)
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(GitCommand::executeInstanceNewNamed)
                        )
                    )
                    // /git instance info - Current instance details
                    .then(Commands.literal("info")
                        .executes(GitCommand::executeInstanceInfo)
                    )
                    // /git instance clearhighlight - Remove beacon beams
                    .then(Commands.literal("clearhighlight")
                        .executes(GitCommand::executeInstanceClearHighlight)
                    )
                    // /git instance autodetect - Control auto-detection
                    .then(Commands.literal("autodetect")
                        .executes(GitCommand::executeInstanceAutoDetectStatus)
                        .then(Commands.literal("on")
                            .executes(GitCommand::executeInstanceAutoDetectOn)
                        )
                        .then(Commands.literal("off")
                            .executes(GitCommand::executeInstanceAutoDetectOff)
                        )
                    )
                )
                // Deactivate current repo (pause tracking)
                .then(Commands.literal("deactivate").requires(IS_AUTHENTICATED)
                    .executes(GitCommand::executeDeactivate)
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

    private static void updateInstanceContext(ServerPlayer player, PlayerSession session, BlockPos pos) {
        // Get world ID from the level's server world name
        String worldId = "world";
        if (player.level().getServer() != null) {
            worldId = player.level().getServer().getWorldData().getLevelName();
        }

        // Get dimension ID
        String dimensionId = player.level().dimension().toString();

        // Update the instance context
        session.updateInstanceContext(worldId, dimensionId, pos);
    }

    // Help command - shows different content based on authentication status
    private static int executeHelp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        
        StringBuilder help = new StringBuilder();
        
        if (!session.isAuthenticated()) {
            // Unauthenticated help
            help.append("\u00a76========== GitBuild Mod ==========\n");
            help.append("\u00a77Welcome to GitBuild! This mod brings version control to your Minecraft builds.\n\n");
            help.append("\u00a76Available Commands:\n");
            help.append("  \u00a7e/git auth <username> [password]\u00a77 - Authenticate with the GitBuild server\n");
            help.append("  \u00a7e/git help\u00a77 - Shows this help message\n\n");
            help.append("\u00a77Authenticate to see all available commands for repository management.\n");
        } else {
            // Authenticated help - full command list
            help.append("\u00a76========== GitBuild Mod - Full Command Reference ==========\n\n");
            
            help.append("\u00a76[Authentication]\n");
            help.append("  \u00a7e/git auth <username> [password]\u00a77 - Authenticate with server\n");
            
            help.append("\u00a76[Repository]\n");
            help.append("  \u00a7e/git init <public|private> <name>\u00a77 - Create new repository\n");
            help.append("  \u00a7e/git activate <name>\u00a77 - Switch to repository\n");
            help.append("  \u00a7e/git repoList\u00a77 - List your repositories\n");
            help.append("  \u00a7e/git deactivate\u00a77 - Pause tracking on current repo\n");
            
            help.append("\u00a76[Staging]\n");
            help.append("  \u00a7e/git add <from> [to] [hollow|outline]\u00a77 - Stage blocks\n");
            help.append("  \u00a7e/git rm <from> [to] [hollow|outline]\u00a77 - Remove and stage\n");
            help.append("  \u00a7e/git unstage <from> [to]\u00a77 - Unstage blocks\n");
            help.append("  \u00a7e/git autoadd [on|off]\u00a77 - Toggle auto-staging additions\n");
            help.append("  \u00a7e/git autorm [on|off]\u00a77 - Toggle auto-staging removals\n");
            
            help.append("\u00a76[Commit]\n");
            help.append("  \u00a7e/git commit <message>\u00a77 - Commit staged changes\n");
            help.append("  \u00a7e/git status\u00a77 - Show staged changes\n");
            help.append("  \u00a7e/git diff [head|<commit>|clear]\u00a77 - Show differences\n");
            help.append("  \u00a7e/git log\u00a77 - View commit history\n");
            
            help.append("\u00a76[History]\n");
            help.append("  \u00a7e/git revert [hash]\u00a77 - Revert commit (new commit)\n");
            help.append("  \u00a7e/git reset [hash]\u00a77 - Reset to commit (destructive)\n");
            help.append("  \u00a7e/git push\u00a77 - Push commits to remote\n");
            help.append("  \u00a7e/git pull <source>\u00a77 - Pull from remote repository\n");
            
            help.append("\u00a76[Clone/Build]\n");
            help.append("  \u00a7e/git clone\u00a77 - Start clone preview\n");
            help.append("  \u00a7e/git clone anchor\u00a77 - Set clone anchor\n");
            help.append("  \u00a7e/git clone rotate <angle>\u00a77 - Rotate preview\n");
            help.append("  \u00a7e/git clone confirm\u00a77 - Confirm clone (OP only)\n");
            help.append("  \u00a7e/git clone cancel\u00a77 - Cancel clone preview\n");
            
            help.append("\u00a76[Instance]\n");
            help.append("  \u00a7e/git instance list\u00a77 - List build instances\n");
            help.append("  \u00a7e/git instance highlight [id]\u00a77 - Highlight instance\n");
            help.append("  \u00a7e/git instance select <id>\u00a77 - Switch to instance\n");
            help.append("  \u00a7e/git instance new [name]\u00a77 - Create new instance\n");
            help.append("  \u00a7e/git instance info\u00a77 - Show current instance\n");
            help.append("  \u00a7e/git instance clearhighlight\u00a77 - Remove highlights\n");
            help.append("  \u00a7e/git instance autodetect [on|off]\u00a77 - Toggle auto-detection\n");
            
            help.append("\u00a76[Help]\n");
            help.append("  \u00a7e/git help\u00a77 - Show this help message\n");
        }
        
        final String message = help.toString();
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
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
            // Resync command tree to show authenticated commands
            ((net.minecraft.server.level.ServerLevel)player.level()).getServer().getCommands().sendCommands(player);
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
            // Resync command tree to show authenticated commands
            ((net.minecraft.server.level.ServerLevel)player.level()).getServer().getCommands().sendCommands(player);
            return 1;
        }

        context.getSource().sendFailure(Component.literal("§cAuthentication failed: " + response.message));
        return 0;
    }

    // Repository management
    private static int executeInit(CommandContext<CommandSourceStack> context, String visibility) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        String repoName = StringArgumentType.getString(context, "name");

        checkServerOnline(context.getSource());
        checkAuthenticated(session, context.getSource());

        ApiResponse response = BackendApiClient.createRepo(session.getUsername(), repoName, session.getUuid(), visibility);
        if (response.success) {
            session.setCurrentRepo(repoName);
            String visibilityLabel = visibility.equals("public") ? "§a[public]" : "§c[private]";
            context.getSource().sendSuccess(() ->
                Component.literal("§aInitialized repository: " + repoName + " " + visibilityLabel), false);
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

                // Load HEAD commit and check for missing blocks
                int missingCount = loadAndCheckHeadCommit(player, session);

                context.getSource().sendSuccess(() ->
                    Component.literal("§aSwitched to repository: " + repoName +
                        (missingCount > 0 ? " §7(" + missingCount + " blocks to restore)" : "")), false);
                return 1;
            }
        }

        context.getSource().sendFailure(Component.literal("§cRepository not found: " + repoName));
        return 0;
    }

    private static int loadAndCheckHeadCommit(ServerPlayer player, PlayerSession session) {
        if (!BackendApiClient.isServerOnline() || !session.hasActiveRepo()) {
            return 0;
        }

        int missingCount = 0;
        BlockState airState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

        // Get current instance anchor - blocks are stored relative to this
        BuildInstance currentInstance = session.getCurrentInstance();
        if (currentInstance == null) {
            // No instance yet - can't determine relative positions
            return 0;
        }
        BlockPos anchor = currentInstance.getAnchorPos();

        // Get HEAD commit blocks
        ApiResponse logResponse = BackendApiClient.getLog(session.getUsername(), session.getCurrentRepo());
        if (logResponse.success && logResponse.data != null) {
            JsonArray commits = logResponse.data.getAsJsonArray();
            if (commits.size() > 0) {
                String headHash = commits.get(0).getAsJsonObject().get("commit_hash").getAsString();
                ApiResponse blocksResponse = BackendApiClient.getCommitBlocks(
                    session.getUsername(), session.getCurrentRepo(), headHash);

                if (blocksResponse.success && blocksResponse.data != null) {
                    JsonArray headBlocks = blocksResponse.data.getAsJsonArray();

                    for (JsonElement element : headBlocks) {
                        JsonObject block = element.getAsJsonObject();
                        String blockName = block.get("block_name").getAsString();

                        // Skip AIR blocks
                        if (blockName.equals("minecraft:air") || blockName.equals("air")) {
                            continue;
                        }

                        int x = block.get("x").getAsInt();
                        int y = block.get("y").getAsInt();
                        int z = block.get("z").getAsInt();
                        
                        // Convert absolute commit position to relative (based on anchor)
                        // Then convert relative back to absolute world position using current anchor
                        BlockPos relativePos = new BlockPos(
                            x - anchor.getX(),
                            y - anchor.getY(),
                            z - anchor.getZ()
                        );
                        BlockPos worldPos = currentInstance.toAbsolute(relativePos);

                        // Check if block exists in world at the anchor-relative position
                        BlockState worldState = player.level().getBlockState(worldPos);
                        String worldBlockName = BuiltInRegistries.BLOCK.getKey(worldState.getBlock()).toString();

                        if (!worldBlockName.equals(blockName)) {
                            // Block is missing or different at this anchor-relative position
                            missingCount++;

                            // Add ghost block at the world position (anchor-relative)
                            // Particle visualization will be handled by GhostBlockManager
                            BlockState ghostState = parseBlockState(blockName);
                            if (ghostState != null) {
                                GhostBlockManager.addGhostBlock(player.getUUID(), worldPos, ghostState);
                            }

                            // Stage as removed (so placing it back will show no change)
                            session.stageBlock(worldPos, ghostState, airState, PlayerSession.ChangeType.REMOVE);
                        }
                    }
                }
            }
        }

        return missingCount;
    }

    private static BlockState parseBlockState(String blockName) {
        try {
            for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
                if (entry.getKey().toString().equals(blockName)) {
                    return entry.getValue().defaultBlockState();
                }
            }
        } catch (Exception e) {
            GitBuildMod.LOGGER.warn("Failed to parse block: {}", blockName, e);
        }
        return null;
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
        
        // Update instance context for this position
        updateInstanceContext(player, session, pos);
        
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
        
        // Update instance context for this position
        updateInstanceContext(player, session, pos);
        
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
        
        // Update instance context for this position
        updateInstanceContext(player, session, pos);
        
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
        
        // Update instance context using the 'from' position as anchor
        updateInstanceContext(player, session, from);
        
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
        
        // Update instance context using the 'from' position as anchor
        updateInstanceContext(player, session, from);
        
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
        
        // Update instance context using the 'from' position as anchor
        updateInstanceContext(player, session, from);
        
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
    private static int executeAutoAddStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        boolean enabled = session.isAutoAdd();
        context.getSource().sendSuccess(() ->
            Component.literal("§7Auto-add is currently " + (enabled ? "§aON" : "§cOFF") +
                "§7. Use §6/git autoadd on§7 or §6/git autoadd off§7 to change."), false);
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
        context.getSource().sendSuccess(() -> Component.literal("§cAuto-add disabled"), false);
        return 1;
    }

    private static int executeAutoRmStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        boolean enabled = session.isAutoRm();
        context.getSource().sendSuccess(() ->
            Component.literal("§7Auto-rm is currently " + (enabled ? "§aON" : "§cOFF") +
                "§7. Use §6/git autorm on§7 or §6/git autorm off§7 to change."), false);
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
        context.getSource().sendSuccess(() -> Component.literal("§cAuto-rm disabled"), false);
        return 1;
    }

    // Commit
    private static int executeCommit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        String message = StringArgumentType.getString(context, "message");

        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());

        Map<BlockPos, BlockChange> staged = session.getStagingArea();

        // Get previous commit blocks (if any) for full snapshot mode
        List<BlockData> allBlocks = new ArrayList<>();
        Set<String> existingPositions = new HashSet<>();

        // Load pending commits to build base snapshot
        List<CommitData> pendingCommits = session.getPendingCommitsForCurrent();

        // Check server online for remote commits, but don't fail if offline
        if (BackendApiClient.isServerOnline()) {
            ApiResponse logResponse = BackendApiClient.getLog(session.getUsername(), session.getCurrentRepo());

            if (logResponse.success && logResponse.data != null) {
                JsonArray commits = logResponse.data.getAsJsonArray();
                if (commits.size() > 0) {
                    String headHash = commits.get(0).getAsJsonObject().get("commit_hash").getAsString();
                    ApiResponse blocksResponse = BackendApiClient.getCommitBlocks(
                        session.getUsername(), session.getCurrentRepo(), headHash);

                    if (blocksResponse.success && blocksResponse.data != null) {
                        // Add previous blocks (excluding ones we're replacing)
                        JsonArray prevBlocks = blocksResponse.data.getAsJsonArray();
                        Set<String> stagedPositions = new HashSet<>();

                        // First, collect positions of staged changes
                        for (Map.Entry<BlockPos, BlockChange> entry : staged.entrySet()) {
                            BlockPos pos = entry.getKey();
                            stagedPositions.add(pos.getX() + "," + pos.getY() + "," + pos.getZ());
                        }

                        // Add previous blocks that aren't being replaced
                        for (JsonElement element : prevBlocks) {
                            JsonObject block = element.getAsJsonObject();
                            String blockName = block.get("block_name").getAsString();

                            // Skip AIR blocks
                            if (blockName.equals("minecraft:air") || blockName.equals("air")) {
                                continue;
                            }

                            String posKey = block.get("x").getAsInt() + "," +
                                            block.get("y").getAsInt() + "," +
                                            block.get("z").getAsInt();

                            if (!stagedPositions.contains(posKey)) {
                                allBlocks.add(new BlockData(
                                    block.get("x").getAsInt(),
                                    block.get("y").getAsInt(),
                                    block.get("z").getAsInt(),
                                    blockName,
                                    block.get("block_state").getAsString()
                                ));
                                existingPositions.add(posKey);
                            }
                        }
                    }
                }
            }
        }

        // Apply pending commits on top (if any)
        if (!pendingCommits.isEmpty()) {
            // Use the most recent pending commit as base
            CommitData lastPending = pendingCommits.get(pendingCommits.size() - 1);

            // Track which positions are already in our snapshot
            for (BlockData block : allBlocks) {
                existingPositions.add(block.x + "," + block.y + "," + block.z);
            }

            // Add pending commit blocks, replacing existing ones
            for (BlockData block : lastPending.toAbsoluteBlocks(session.getCurrentInstance().getAnchorPos())) {
                String posKey = block.x + "," + block.y + "," + block.z;
                if (block.block_name.equals("minecraft:air") || block.block_name.equals("air")) {
                    // Remove if it exists
                    allBlocks.removeIf(b -> (b.x + "," + b.y + "," + b.z).equals(posKey));
                    existingPositions.remove(posKey);
                } else {
                    if (existingPositions.contains(posKey)) {
                        // Replace existing
                        allBlocks.removeIf(b -> (b.x + "," + b.y + "," + b.z).equals(posKey));
                    }
                    allBlocks.add(block);
                    existingPositions.add(posKey);
                }
            }
        }

        // Add staged changes (new/modified/removed blocks)
        StringBuilder commitData = new StringBuilder();
        for (Map.Entry<BlockPos, BlockChange> entry : staged.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockChange change = entry.getValue();

            // Skip entries with null newState (can happen after deserialization)
            if (change.newState == null) continue;

            String blockName = BuiltInRegistries.BLOCK.getKey(change.newState.getBlock()).toString();

            // Convert relative position to absolute for storage in commit
            BlockPos absPos = session.getCurrentInstance().toAbsolute(pos);
            String posKey = absPos.getX() + "," + absPos.getY() + "," + absPos.getZ();

            if (blockName.equals("minecraft:air")) {
                // Actually remove the block from the list (not just skip adding)
                allBlocks.removeIf(b -> (b.x + "," + b.y + "," + b.z).equals(posKey));
                existingPositions.remove(posKey);
            } else {
                // Add or replace the block
                if (existingPositions.contains(posKey)) {
                    allBlocks.removeIf(b -> (b.x + "," + b.y + "," + b.z).equals(posKey));
                }
                allBlocks.add(new BlockData(absPos.getX(), absPos.getY(), absPos.getZ(), blockName, change.newState.toString()));
                existingPositions.add(posKey);
                commitData.append(pos.toString()).append(blockName);
            }
        }

        // Check for empty commit
        if (allBlocks.isEmpty()) {
            context.getSource().sendFailure(Component.literal("§cNothing to commit - all blocks would be empty."));
            return 0;
        }

        // Generate commit hash
        String commitHash = generateHash(session.getUsername() + session.getCurrentRepo() + message + commitData.toString() + System.currentTimeMillis());
        String commitName = "commit-" + commitHash.substring(0, 8);

        // Save commit locally (do not send to backend yet)
        // Convert to relative blocks for instance storage
        CommitData pendingCommit = CommitData.fromAbsolute(
            commitHash,
            commitName,
            message,
            System.currentTimeMillis(),
            allBlocks,
            session.getCurrentInstance().getAnchorPos()
        );
        session.savePendingCommitToCurrent(pendingCommit);
        session.saveInstances();

        session.clearStaging();
        int pendingCount = session.getPendingCommitCount();
        context.getSource().sendSuccess(() ->
            Component.literal("§aCommitted locally: " + message + " §7(" + commitHash.substring(0, 8) + ") §8[§e" + pendingCount + " pending§8]"), false);
        return 1;
    }

    // Helper class for status entries
    private static class StatusEntry {
        final BlockPos pos;
        final String description;

        StatusEntry(BlockPos pos, String description) {
            this.pos = pos;
            this.description = description;
        }
    }

    // Helper class to store head block info
    private static class HeadBlock {
        final String blockName;
        final String blockState;

        HeadBlock(String blockName, String blockState) {
            this.blockName = blockName;
            this.blockState = blockState;
        }
    }

    private static int executeStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkRepoActive(session, context.getSource());

        Map<BlockPos, BlockChange> staged = session.getStagingArea();

        // Fetch HEAD commit blocks for comparison
        Map<String, HeadBlock> headBlocks = new HashMap<>();
        if (BackendApiClient.isServerOnline()) {
            GitBuildMod.LOGGER.warn("Status: Fetching log for {}/{}", session.getUsername(), session.getCurrentRepo());
            ApiResponse logResponse = BackendApiClient.getLog(session.getUsername(), session.getCurrentRepo());
            GitBuildMod.LOGGER.warn("Status: logResponse.success={}, data={}", logResponse.success, logResponse.data);
            if (logResponse.success && logResponse.data != null) {
                JsonArray commits = logResponse.data.getAsJsonArray();
                GitBuildMod.LOGGER.warn("Status: commits.size={}", commits.size());
                if (commits.size() > 0) {
                    String headHash = commits.get(0).getAsJsonObject().get("commit_hash").getAsString();
                    GitBuildMod.LOGGER.warn("Status: headHash={}", headHash);
                    ApiResponse blocksResponse = BackendApiClient.getCommitBlocks(
                        session.getUsername(), session.getCurrentRepo(), headHash);
                    GitBuildMod.LOGGER.warn("Status: blocksResponse.success={}, data={}", blocksResponse.success, blocksResponse.data);

                    if (blocksResponse.success && blocksResponse.data != null) {
                        JsonArray prevBlocks = blocksResponse.data.getAsJsonArray();
                        GitBuildMod.LOGGER.warn("Status: prevBlocks.size={}", prevBlocks.size());
                        
                        // Get anchor for relative position calculation
                        BuildInstance currentInstance = session.getCurrentInstance();
                        BlockPos anchor = (currentInstance != null) ? currentInstance.getAnchorPos() : BlockPos.ZERO;
                        
                        for (JsonElement element : prevBlocks) {
                            JsonObject block = element.getAsJsonObject();
                            int x = block.get("x").getAsInt();
                            int y = block.get("y").getAsInt();
                            int z = block.get("z").getAsInt();
                            
                            // Convert absolute commit position to anchor-relative
                            BlockPos relativePos = new BlockPos(
                                x - anchor.getX(),
                                y - anchor.getY(),
                                z - anchor.getZ()
                            );
                            String posKey = relativePos.getX() + "," + relativePos.getY() + "," + relativePos.getZ();
                            
                            headBlocks.put(posKey, new HeadBlock(
                                block.get("block_name").getAsString(),
                                block.get("block_state").getAsString()
                            ));
                        }
                    }
                }
            }
        }

        // Apply pending commits on top of HEAD (if any)
        // This ensures status shows changes relative to the true base state including local commits
        List<CommitData> pendingCommits = session.getPendingCommitsForCurrent();
        if (!pendingCommits.isEmpty()) {
            // Use the most recent pending commit as the base
            CommitData lastPending = pendingCommits.get(pendingCommits.size() - 1);
            for (RelativeBlockData block : lastPending.blocks) {
                String posKey = block.rel_x + "," + block.rel_y + "," + block.rel_z;
                // Skip air blocks (they represent removals)
                if (block.block_name.equals("minecraft:air") || block.block_name.equals("air")) {
                    headBlocks.remove(posKey);
                } else {
                    headBlocks.put(posKey, new HeadBlock(block.block_name, block.block_state));
                }
            }
            GitBuildMod.LOGGER.warn("Status: Applied {} pending commits, base now has {} blocks", 
                pendingCommits.size(), headBlocks.size());
        }

        // Get ghost blocks (loaded commit state) for comparison
        Map<BlockPos, BlockState> ghostBlocks = GhostBlockManager.getGhostBlocks(player.getUUID());
        GitBuildMod.LOGGER.warn("Status: headBlocks={}, ghostBlocks={}, staged={}",
            headBlocks.size(), ghostBlocks.size(), staged.size());

        // Categorize staged changes
        List<StatusEntry> newBlocks = new ArrayList<>();
        List<StatusEntry> removedBlocks = new ArrayList<>();
        List<StatusEntry> modifiedBlocks = new ArrayList<>();

        for (Map.Entry<BlockPos, BlockChange> entry : staged.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockChange change = entry.getValue();
            
            // Skip entries with null newState (can happen after deserialization)
            if (change.newState == null) continue;
            
            String posKey = pos.getX() + "," + pos.getY() + "," + pos.getZ();
            String blockName = BuiltInRegistries.BLOCK.getKey(change.newState.getBlock()).toString();
            String blockState = change.newState.toString();

            // Check ghost blocks first (loaded commit state) - this is the "expected" state
            if (ghostBlocks.containsKey(pos)) {
                BlockState ghostState = ghostBlocks.get(pos);
                String ghostName = BuiltInRegistries.BLOCK.getKey(ghostState.getBlock()).toString();
                String ghostStateStr = ghostState.toString();
                GitBuildMod.LOGGER.warn("Ghost check at {}: staged={}/{}, ghost={}/{}",
                    posKey, blockName, blockState, ghostName, ghostStateStr);
                if (blockName.equals(ghostName) && blockState.equals(ghostStateStr)) {
                    GitBuildMod.LOGGER.warn("  -> Skipping, matches ghost block (loaded commit)");
                    continue; // Identical to loaded commit, don't show as change
                }
            }

            // Also check HEAD as fallback
            if (headBlocks.containsKey(posKey)) {
                HeadBlock headBlock = headBlocks.get(posKey);
                GitBuildMod.LOGGER.warn("HEAD check at {}: staged={}/{}, head={}/{}",
                    posKey, blockName, blockState, headBlock.blockName, headBlock.blockState);
                if (blockName.equals(headBlock.blockName) &&
                    blockState.equals(headBlock.blockState)) {
                    GitBuildMod.LOGGER.warn("  -> Skipping, matches HEAD");
                    continue; // Identical to HEAD, don't show as change
                }
            }

            if (headBlocks.containsKey(posKey)) {
                HeadBlock headBlock = headBlocks.get(posKey);
                if (blockName.equals("minecraft:air")) {
                    // Block removed
                    removedBlocks.add(new StatusEntry(pos, headBlock.blockName));
                } else if (!blockName.equals(headBlock.blockName)) {
                    // Block type changed
                    modifiedBlocks.add(new StatusEntry(pos, headBlock.blockName + " → " + blockName));
                } else {
                    // Same block type - check state
                    if (!blockState.equals(headBlock.blockState)) {
                        modifiedBlocks.add(new StatusEntry(pos, headBlock.blockName + " (state changed)"));
                    }
                    // If identical, don't show (block deleted and placed back)
                }
            } else {
                // New block
                if (!blockName.equals("minecraft:air")) {
                    newBlocks.add(new StatusEntry(pos, blockName));
                }
            }
        }

        // Build output with colors
        StringBuilder sb = new StringBuilder();

        int totalChanges = newBlocks.size() + removedBlocks.size() + modifiedBlocks.size();

        // Repository name with summary counts on same line
        sb.append("§aRepository: §f").append(session.getCurrentRepo());
        if (totalChanges > 0) {
            sb.append(" §7(");
            sb.append("§a+").append(newBlocks.size()).append("§7, ");
            sb.append("§c-").append(removedBlocks.size()).append("§7, ");
            sb.append("§e~").append(modifiedBlocks.size());
            sb.append("§7)");
        }
        sb.append("\n");

        // Show current instance info
        BuildInstance currentInstance = session.getCurrentInstance();
        if (currentInstance != null) {
            BlockPos anchor = currentInstance.getAnchorPos();
            sb.append("§aInstance: §f").append(currentInstance.getInstanceId()).append("\n");
            sb.append("§7  Anchor: ").append(formatPos(anchor)).append("\n");
        }

        if (totalChanges == 0) {
            sb.append("§aNo changes\n");
        } else {

            // Show max 8 changes total
            final int MAX_DISPLAY = 8;
            int displayed = 0;
            int skipped = 0;

            for (StatusEntry entry : newBlocks) {
                if (displayed >= MAX_DISPLAY) {
                    skipped++;
                    continue;
                }
                sb.append("  §a+ ").append(formatPos(entry.pos)).append(" §7").append(entry.description).append("\n");
                displayed++;
            }
            for (StatusEntry entry : removedBlocks) {
                if (displayed >= MAX_DISPLAY) {
                    skipped++;
                    continue;
                }
                sb.append("  §c- ").append(formatPos(entry.pos)).append(" §7").append(entry.description).append("\n");
                displayed++;
            }
            for (StatusEntry entry : modifiedBlocks) {
                if (displayed >= MAX_DISPLAY) {
                    skipped++;
                    continue;
                }
                sb.append("  §e~ ").append(formatPos(entry.pos)).append(" §7").append(entry.description).append("\n");
                displayed++;
            }

            if (skipped > 0) {
                sb.append("§7...").append(skipped).append(" more\n");
            }
        }

        // Show pending commits info
        int pendingCount = session.getPendingCommitCount();
        if (pendingCount > 0) {
            sb.append("\n§ePending commits: §f").append(pendingCount).append(" §8(run /git push to upload)");
        }

        sb.append("\n§aAuto-add: §f").append(session.isAutoAdd() ? "on" : "off");
        sb.append(" §aAuto-rm: §f").append(session.isAutoRm() ? "on" : "off");

        final String message = sb.toString();
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    // Diff visualization - compares HEAD with HEAD~1 (last two commits)
    private static int executeDiff(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkRepoActive(session, context.getSource());
        checkServerOnline(context.getSource());

        // Clear any existing diff ghosts
        GhostBlockManager.clearDiffGhosts(player);

        // Get commit log
        ApiResponse logResponse = BackendApiClient.getLog(session.getUsername(), session.getCurrentRepo());
        if (!logResponse.success || logResponse.data == null) {
            context.getSource().sendFailure(Component.literal("§cFailed to get commit log"));
            return 0;
        }

        JsonArray commits = logResponse.data.getAsJsonArray();
        if (commits.size() < 2) {
            context.getSource().sendFailure(Component.literal("§cNeed at least 2 commits to diff. Only found " + commits.size()));
            return 0;
        }

        // Get HEAD (most recent) and HEAD~1 (previous) commit hashes
        String headHash = commits.get(0).getAsJsonObject().get("commit_hash").getAsString();
        String prevHash = commits.get(1).getAsJsonObject().get("commit_hash").getAsString();
        String headMsg = commits.get(0).getAsJsonObject().has("message")
            ? commits.get(0).getAsJsonObject().get("message").getAsString()
            : "HEAD";
        String prevMsg = commits.get(1).getAsJsonObject().has("message")
            ? commits.get(1).getAsJsonObject().get("message").getAsString()
            : "HEAD~1";

        // Fetch HEAD blocks
        ApiResponse headResponse = BackendApiClient.getCommitBlocks(
            session.getUsername(), session.getCurrentRepo(), headHash);
        if (!headResponse.success || headResponse.data == null) {
            context.getSource().sendFailure(Component.literal("§cFailed to get HEAD commit blocks"));
            return 0;
        }

        // Fetch HEAD~1 blocks
        ApiResponse prevResponse = BackendApiClient.getCommitBlocks(
            session.getUsername(), session.getCurrentRepo(), prevHash);
        if (!prevResponse.success || prevResponse.data == null) {
            context.getSource().sendFailure(Component.literal("§cFailed to get previous commit blocks"));
            return 0;
        }

        JsonArray headBlocks = headResponse.data.getAsJsonArray();
        JsonArray prevBlocks = prevResponse.data.getAsJsonArray();

        // Build map of HEAD~1 (previous) blocks
        Map<String, HeadBlock> prevBlockMap = new HashMap<>();
        for (JsonElement element : prevBlocks) {
            JsonObject block = element.getAsJsonObject();
            String posKey = block.get("x").getAsInt() + "," +
                          block.get("y").getAsInt() + "," +
                          block.get("z").getAsInt();
            prevBlockMap.put(posKey, new HeadBlock(
                block.get("block_name").getAsString(),
                block.has("block_state") ? block.get("block_state").getAsString() : ""
            ));
        }


        int newCount = 0;
        int deletedCount = 0;
        int modifiedCount = 0;

        // Process HEAD blocks - compare against HEAD~1
        for (JsonElement element : headBlocks) {
            JsonObject block = element.getAsJsonObject();
            int x = block.get("x").getAsInt();
            int y = block.get("y").getAsInt();
            int z = block.get("z").getAsInt();
            String posKey = x + "," + y + "," + z;
            String blockName = block.get("block_name").getAsString();
            String blockState = block.has("block_state") ? block.get("block_state").getAsString() : "";

            BlockPos absPos = new BlockPos(x, y, z);  // Backend stores absolute coordinates
            BlockState headState = parseBlockState(blockName, blockState);

            if (prevBlockMap.containsKey(posKey)) {
                HeadBlock prevBlock = prevBlockMap.get(posKey);
                if (!blockName.equals(prevBlock.blockName)) {
                    // Modified - different block type
                    if (headState != null) {
                        GhostBlockManager.addDiffGhost(player,
                            new GhostBlockManager.DiffGhost(absPos, headState, GhostBlockManager.DiffType.MODIFIED));
                        modifiedCount++;
                    }
                } else if (!blockState.equals(prevBlock.blockState)) {
                    // Same block type, different state
                    if (headState != null) {
                        GhostBlockManager.addDiffGhost(player,
                            new GhostBlockManager.DiffGhost(absPos, headState, GhostBlockManager.DiffType.MODIFIED));
                        modifiedCount++;
                    }
                }
                // If identical, no diff needed
            } else {
                // New block in HEAD that wasn't in HEAD~1
                if (headState != null && !blockName.equals("minecraft:air")) {
                    GhostBlockManager.addDiffGhost(player,
                        new GhostBlockManager.DiffGhost(absPos, headState, GhostBlockManager.DiffType.NEW));
                    newCount++;
                }
            }
        }

        // Find blocks that were in HEAD~1 but not in HEAD (deleted)
        for (JsonElement element : prevBlocks) {
            JsonObject block = element.getAsJsonObject();
            int x = block.get("x").getAsInt();
            int y = block.get("y").getAsInt();
            int z = block.get("z").getAsInt();

            if (!block.has("block_name")) continue;
            String blockName = block.get("block_name").getAsString();

            // Skip air blocks
            if (blockName.equals("minecraft:air") || blockName.equals("air")) continue;

            // Check if this position exists in HEAD
            boolean existsInHead = false;
            for (JsonElement headElement : headBlocks) {
                JsonObject headBlock = headElement.getAsJsonObject();
                int hx = headBlock.get("x").getAsInt();
                int hy = headBlock.get("y").getAsInt();
                int hz = headBlock.get("z").getAsInt();
                if (hx == x && hy == y && hz == z) {
                    existsInHead = true;
                    break;
                }
            }

            if (!existsInHead) {
                // Block was deleted - show red ghost
                String blockState = block.has("block_state") ? block.get("block_state").getAsString() : "";
                BlockPos absPos = new BlockPos(x, y, z);  // Backend stores absolute coordinates
                BlockState prevState = parseBlockState(blockName, blockState);
                if (prevState != null) {
                    GhostBlockManager.addDiffGhost(player,
                        new GhostBlockManager.DiffGhost(absPos, prevState, GhostBlockManager.DiffType.DELETED));
                    deletedCount++;
                }
            }
        }

        int total = newCount + deletedCount + modifiedCount;
        if (total == 0) {
            context.getSource().sendSuccess(() ->
                Component.literal("§aNo differences between commits\n§7" +
                    headHash.substring(0, 8) + " " + headMsg + "\n§7and\n§7" +
                    prevHash.substring(0, 8) + " " + prevMsg), false);
        } else {
            final int fNew = newCount;
            final int fDel = deletedCount;
            final int fMod = modifiedCount;
            final String fHead = headHash.substring(0, 8);
            final String fPrev = prevHash.substring(0, 8);
            context.getSource().sendSuccess(() ->
                Component.literal("§aDiff: §f" + fHead + " §7vs §f" + fPrev +
                    "\n§a" + fNew + " new§7, §c" + fDel + " deleted§7, §e" + fMod + " modified" +
                    "\n§7Green = added in HEAD, Red = removed from HEAD~1, Yellow = changed" +
                    "\n§7Use /git diff clear to remove"), false);
        }

        return 1;
    }

    private static int executeDiffClear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);

        GhostBlockManager.clearDiffGhosts(player);
        context.getSource().sendSuccess(() -> Component.literal("§aDiff visualization cleared"), false);
        return 1;
    }

    // Diff visualization - compares current world state against HEAD (shows uncommitted changes)
    private static int executeDiffHead(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkRepoActive(session, context.getSource());
        checkServerOnline(context.getSource());

        // Clear any existing diff ghosts
        GhostBlockManager.clearDiffGhosts(player);

        // Get HEAD commit blocks
        ApiResponse headResponse = BackendApiClient.getHeadBlocks(session.getUsername(), session.getCurrentRepo());
        if (!headResponse.success || headResponse.data == null) {
            context.getSource().sendFailure(Component.literal("§cFailed to get HEAD commit blocks"));
            return 0;
        }

        JsonObject headData = headResponse.data.getAsJsonObject();
        String headHash = headData.get("commit_hash").getAsString();
        String headMsg = headData.has("message") ? headData.get("message").getAsString() : "HEAD";
        JsonArray headBlocks = headData.getAsJsonArray("blocks");

        // Build map of HEAD blocks
        Map<String, HeadBlock> headBlockMap = new HashMap<>();
        for (JsonElement element : headBlocks) {
            JsonObject block = element.getAsJsonObject();
            String posKey = block.get("x").getAsInt() + "," +
                          block.get("y").getAsInt() + "," +
                          block.get("z").getAsInt();
            headBlockMap.put(posKey, new HeadBlock(
                block.get("block_name").getAsString(),
                block.has("block_state") ? block.get("block_state").getAsString() : ""
            ));
        }

        // Get current instance for coordinate conversion and bounds
        BuildInstance currentInstance = session.getCurrentInstance();
        if (currentInstance == null) {
            context.getSource().sendFailure(Component.literal("§cNo active build instance"));
            return 0;
        }

        // Scan current world blocks in the instance area
        ServerLevel level = (ServerLevel) player.level();
        BlockPos anchor = currentInstance.getAnchorPos();
        int minX = anchor.getX();
        int minY = anchor.getY();
        int minZ = anchor.getZ();

        // Determine bounds from HEAD blocks or use default size
        int maxX = minX + 32;
        int maxY = minY + 32;
        int maxZ = minZ + 32;

        for (JsonElement element : headBlocks) {
            JsonObject block = element.getAsJsonObject();
            int x = block.get("x").getAsInt();
            int y = block.get("y").getAsInt();
            int z = block.get("z").getAsInt();
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        int newCount = 0;
        int deletedCount = 0;
        int modifiedCount = 0;

        // Scan world and compare against HEAD
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos worldPos = new BlockPos(x, y, z);
                    BlockState worldState = level.getBlockState(worldPos);
                    String worldBlockName = worldState.isAir() ? "minecraft:air" : level.getBlockState(worldPos).getBlock().toString();

                    // Convert to relative position for comparison
                    BlockPos relPos = currentInstance.toRelative(worldPos);
                    String posKey = relPos.getX() + "," + relPos.getY() + "," + relPos.getZ();

                    if (headBlockMap.containsKey(posKey)) {
                        HeadBlock headBlock = headBlockMap.get(posKey);
                        String headBlockName = headBlock.blockName;

                        // Normalize world block name to match HEAD format
                        if (worldBlockName.startsWith("Block{")) {
                            worldBlockName = worldBlockName.substring(6, worldBlockName.length() - 1);
                        }

                        if (!worldBlockName.equals(headBlockName)) {
                            // Modified - different block type
                            if (!worldState.isAir() || !headBlockName.equals("minecraft:air")) {
                                GhostBlockManager.addDiffGhost(player,
                                    new GhostBlockManager.DiffGhost(worldPos, worldState, GhostBlockManager.DiffType.MODIFIED));
                                modifiedCount++;
                            }
                        }
                        // If identical, no diff needed
                        headBlockMap.remove(posKey); // Mark as processed
                    } else {
                        // New block in world that wasn't in HEAD
                        if (!worldState.isAir()) {
                            GhostBlockManager.addDiffGhost(player,
                                new GhostBlockManager.DiffGhost(worldPos, worldState, GhostBlockManager.DiffType.NEW));
                            newCount++;
                        }
                    }
                }
            }
        }

        // Remaining blocks in headBlockMap are deleted (in HEAD but not in world)
        for (Map.Entry<String, HeadBlock> entry : headBlockMap.entrySet()) {
            String posKey = entry.getKey();
            HeadBlock headBlock = entry.getValue();

            if (headBlock.blockName.equals("minecraft:air") || headBlock.blockName.equals("air")) {
                continue;
            }

            String[] coords = posKey.split(",");
            int x = Integer.parseInt(coords[0]);
            int y = Integer.parseInt(coords[1]);
            int z = Integer.parseInt(coords[2]);

            BlockPos absPos = new BlockPos(x, y, z);  // Backend stores absolute coordinates
            BlockState prevState = parseBlockState(headBlock.blockName, headBlock.blockState);

            if (prevState != null) {
                GhostBlockManager.addDiffGhost(player,
                    new GhostBlockManager.DiffGhost(absPos, prevState, GhostBlockManager.DiffType.DELETED));
                deletedCount++;
            }
        }

        int total = newCount + deletedCount + modifiedCount;
        if (total == 0) {
            context.getSource().sendSuccess(() ->
                Component.literal("§aNo uncommitted changes - world matches HEAD\n§7" +
                    headHash.substring(0, 8) + " " + headMsg), false);
        } else {
            final int fNew = newCount;
            final int fDel = deletedCount;
            final int fMod = modifiedCount;
            final String fHead = headHash.substring(0, 8);
            context.getSource().sendSuccess(() ->
                Component.literal("§aUncommitted changes vs HEAD (" + fHead + ")" +
                    "\n§a" + fNew + " new§7, §c" + fDel + " deleted§7, §e" + fMod + " modified" +
                    "\n§7Green = added in world, Red = removed from HEAD, Yellow = changed" +
                    "\n§7Use /git diff clear to remove"), false);
        }

        return 1;
    }

    // Diff visualization - compares current world state against a specific commit
    private static int executeDiffAgainstCommit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkRepoActive(session, context.getSource());
        checkServerOnline(context.getSource());

        // Get the commit hash argument
        String commitInput = StringArgumentType.getString(context, "commit");

        // Clear any existing diff ghosts
        GhostBlockManager.clearDiffGhosts(player);

        // Get commit log to find the full hash from partial input
        ApiResponse logResponse = BackendApiClient.getLog(session.getUsername(), session.getCurrentRepo());
        if (!logResponse.success || logResponse.data == null) {
            context.getSource().sendFailure(Component.literal("§cFailed to get commit log"));
            return 0;
        }

        JsonArray commits = logResponse.data.getAsJsonArray();
        String targetHash = null;
        String targetMsg = null;

        // Find matching commit by partial hash
        for (JsonElement element : commits) {
            JsonObject commit = element.getAsJsonObject();
            String hash = commit.get("commit_hash").getAsString();
            if (hash.startsWith(commitInput.toLowerCase())) {
                targetHash = hash;
                targetMsg = commit.has("message") ? commit.get("message").getAsString() : "unknown";
                break;
            }
        }

        if (targetHash == null) {
            context.getSource().sendFailure(Component.literal("§cNo commit found matching '" + commitInput + "'"));
            return 0;
        }

        // Make final copies for lambda usage
        final String finalTargetHash = targetHash;
        final String finalTargetMsg = targetMsg;

        // Fetch target commit blocks
        ApiResponse commitResponse = BackendApiClient.getCommitBlocks(
            session.getUsername(), session.getCurrentRepo(), finalTargetHash);
        if (!commitResponse.success || commitResponse.data == null) {
            context.getSource().sendFailure(Component.literal("§cFailed to get commit blocks for " + targetHash.substring(0, 8)));
            return 0;
        }

        JsonArray commitBlocks = commitResponse.data.getAsJsonArray();

        // Build map of commit blocks
        Map<String, HeadBlock> commitBlockMap = new HashMap<>();
        for (JsonElement element : commitBlocks) {
            JsonObject block = element.getAsJsonObject();
            String posKey = block.get("x").getAsInt() + "," +
                          block.get("y").getAsInt() + "," +
                          block.get("z").getAsInt();
            commitBlockMap.put(posKey, new HeadBlock(
                block.get("block_name").getAsString(),
                block.has("block_state") ? block.get("block_state").getAsString() : ""
            ));
        }

        // Get current instance for coordinate conversion and bounds
        BuildInstance currentInstance = session.getCurrentInstance();
        if (currentInstance == null) {
            context.getSource().sendFailure(Component.literal("§cNo active build instance"));
            return 0;
        }

        // Scan current world blocks in the instance area
        ServerLevel level = (ServerLevel) player.level();
        BlockPos anchor = currentInstance.getAnchorPos();
        int minX = anchor.getX();
        int minY = anchor.getY();
        int minZ = anchor.getZ();

        // Determine bounds from commit blocks or use default size
        int maxX = minX + 32;
        int maxY = minY + 32;
        int maxZ = minZ + 32;

        for (JsonElement element : commitBlocks) {
            JsonObject block = element.getAsJsonObject();
            int x = block.get("x").getAsInt();
            int y = block.get("y").getAsInt();
            int z = block.get("z").getAsInt();
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        int newCount = 0;
        int deletedCount = 0;
        int modifiedCount = 0;

        // Scan world and compare against commit
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos worldPos = new BlockPos(x, y, z);
                    BlockState worldState = level.getBlockState(worldPos);
                    String worldBlockName = worldState.isAir() ? "minecraft:air" : level.getBlockState(worldPos).getBlock().toString();

                    // Convert to relative position for comparison
                    BlockPos relPos = currentInstance.toRelative(worldPos);
                    String posKey = relPos.getX() + "," + relPos.getY() + "," + relPos.getZ();

                    if (commitBlockMap.containsKey(posKey)) {
                        HeadBlock commitBlock = commitBlockMap.get(posKey);
                        String commitBlockName = commitBlock.blockName;

                        // Normalize world block name to match commit format
                        if (worldBlockName.startsWith("Block{")) {
                            worldBlockName = worldBlockName.substring(6, worldBlockName.length() - 1);
                        }

                        if (!worldBlockName.equals(commitBlockName)) {
                            // Modified - different block type
                            if (!worldState.isAir() || !commitBlockName.equals("minecraft:air")) {
                                GhostBlockManager.addDiffGhost(player,
                                    new GhostBlockManager.DiffGhost(worldPos, worldState, GhostBlockManager.DiffType.MODIFIED));
                                modifiedCount++;
                            }
                        }
                        // If identical, no diff needed
                        commitBlockMap.remove(posKey); // Mark as processed
                    } else {
                        // New block in world that wasn't in commit
                        if (!worldState.isAir()) {
                            GhostBlockManager.addDiffGhost(player,
                                new GhostBlockManager.DiffGhost(worldPos, worldState, GhostBlockManager.DiffType.NEW));
                            newCount++;
                        }
                    }
                }
            }
        }

        // Remaining blocks in commitBlockMap are deleted (in commit but not in world)
        for (Map.Entry<String, HeadBlock> entry : commitBlockMap.entrySet()) {
            String posKey = entry.getKey();
            HeadBlock commitBlock = entry.getValue();

            if (commitBlock.blockName.equals("minecraft:air") || commitBlock.blockName.equals("air")) {
                continue;
            }

            String[] coords = posKey.split(",");
            int x = Integer.parseInt(coords[0]);
            int y = Integer.parseInt(coords[1]);
            int z = Integer.parseInt(coords[2]);

            BlockPos absPos = new BlockPos(x, y, z);  // Backend stores absolute coordinates
            BlockState prevState = parseBlockState(commitBlock.blockName, commitBlock.blockState);

            if (prevState != null) {
                GhostBlockManager.addDiffGhost(player,
                    new GhostBlockManager.DiffGhost(absPos, prevState, GhostBlockManager.DiffType.DELETED));
                deletedCount++;
            }
        }

        int total = newCount + deletedCount + modifiedCount;
        if (total == 0) {
            context.getSource().sendSuccess(() ->
                Component.literal("§aNo differences - world matches commit\n§7" +
                    finalTargetHash.substring(0, 8) + " " + finalTargetMsg), false);
        } else {
            final int fNew = newCount;
            final int fDel = deletedCount;
            final int fMod = modifiedCount;
            final String fHash = finalTargetHash.substring(0, 8);
            context.getSource().sendSuccess(() ->
                Component.literal("§aWorld vs commit " + fHash + " (" + finalTargetMsg + ")" +
                    "\n§a" + fNew + " new§7, §c" + fDel + " deleted§7, §e" + fMod + " modified" +
                    "\n§7Green = added in world, Red = removed from commit, Yellow = changed" +
                    "\n§7Use /git diff clear to remove"), false);
        }

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

    private static int executePush(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());
        checkServerOnline(context.getSource());

        // Get pending commits and send them to backend
        List<CommitData> pendingCommits = session.getPendingCommitsForCurrent();

        if (pendingCommits.isEmpty()) {
            context.getSource().sendFailure(Component.literal("§cNothing to push - no pending commits. Use /git commit first."));
            return 0;
        }

        BuildInstance currentInstance = session.getCurrentInstance();

        // Send all pending commits to backend
        int pushedCount = 0;
        for (CommitData commit : pendingCommits) {
            ApiResponse response = BackendApiClient.createCommit(
                session.getUsername(),
                session.getCurrentRepo(),
                session.getUuid(),
                commit.commitName,
                commit.commitHash,
                commit.message,
                commit.toAbsoluteBlocks(currentInstance.getAnchorPos())
            );

            if (!response.success) {
                context.getSource().sendFailure(Component.literal(
                    "§cFailed to push commit " + commit.commitName + ": " + response.message));
                return 0;
            }
            pushedCount++;
        }

        if (pushedCount > 0) {
            session.clearPendingCommitsForCurrent();
            final int finalPushedCount = pushedCount;
            context.getSource().sendSuccess(() ->
                Component.literal("§aPushed " + finalPushedCount + " commit" + (finalPushedCount > 1 ? "s" : "") + " to server"), false);
            return 1;
        }

        return 0;
    }

    private static int executePull(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);
        String source = StringArgumentType.getString(context, "source");
        
        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());
        checkServerOnline(context.getSource());
        
        // Parse source (format: username/repo)
        String[] parts = source.split("/");
        if (parts.length != 2) {
            context.getSource().sendFailure(Component.literal("§cInvalid source format. Use: username/repo"));
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
                Component.literal("§aPulled from " + source + " (" + finalAdded + " commits)"), false);
            return 1;
        }
        
        context.getSource().sendFailure(Component.literal("§cPull failed: " + response.message));
        return 0;
    }

    // Legacy clone - immediate clone without preview
    private static int executeCloneLegacy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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

            // Optionally apply to world (like /git revert)
            context.getSource().sendSuccess(() ->
                Component.literal("§aCloned " + source + " to " + newRepoName + " (use /git revert to apply)"), false);
            return 1;
        }

        context.getSource().sendFailure(Component.literal("§cClone failed: " + response.message));
        return 0;
    }

    // New clone preview system - Litematica style
    private static int executeClonePreview(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());
        checkServerOnline(context.getSource());

        // Clear any existing preview
        clearClonePreview(player, session);

        // Get world and dimension info
        String worldId = "world";
        if (player.level().getServer() != null) {
            worldId = player.level().getServer().getWorldData().getLevelName();
        }
        String dimensionId = player.level().dimension().toString();

        // Set anchor at player position (rounded to block)
        BlockPos anchor = player.blockPosition();

        // Get current instance for source
        BuildInstance currentInstance = session.getCurrentInstance();
        String sourceInstanceId = currentInstance != null ? currentInstance.getInstanceId() : null;

        // Initialize clone preview in session
        session.startClonePreview(session.getCurrentRepo(), sourceInstanceId, anchor, worldId, dimensionId);
        session.saveInstances(); // Persist clone preview state

        // Load preview from HEAD commit
        loadClonePreviewFromHead(player, session);

        context.getSource().sendSuccess(() ->
            Component.literal("§aClone preview started at " + formatPos(anchor) + ". Use §6/git clone anchor §ato adjust, §6/git clone rotate §ato rotate, §6/git clone confirm §ato paste, or §6/git clone cancel §ato clear."), false);
        return 1;
    }

    private static int executeCloneAnchor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());

        if (!session.isClonePreviewActive()) {
            context.getSource().sendFailure(Component.literal("§cNo active clone preview. Use /git clone first."));
            return 0;
        }

        // Get the block the player is looking at (up to 5 blocks away)
        BlockPos targetPos = getTargetBlockPos(player, 5);
        if (targetPos == null) {
            context.getSource().sendFailure(Component.literal("§cLook at a block to set as anchor."));
            return 0;
        }

        // Update anchor and reload preview
        session.setClonePreviewAnchor(targetPos);
        session.saveInstances(); // Persist updated anchor
        loadClonePreviewFromHead(player, session);

        context.getSource().sendSuccess(() ->
            Component.literal("§aClone anchor set to " + formatPos(targetPos) + ". Preview updated."), false);
        return 1;
    }

    private static int executeCloneRotate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());

        if (!session.isClonePreviewActive()) {
            context.getSource().sendFailure(Component.literal("§cNo active clone preview. Use /git clone first."));
            return 0;
        }

        String angleStr = StringArgumentType.getString(context, "angle");
        int angle;
        try {
            angle = Integer.parseInt(angleStr);
            if (angle != 0 && angle != 90 && angle != 180 && angle != 270) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("§cInvalid angle. Use: 0, 90, 180, or 270"));
            return 0;
        }

        // Update rotation and reload preview
        session.setClonePreviewRotation(angle);
        session.saveInstances(); // Persist rotation
        loadClonePreviewFromHead(player, session);

        context.getSource().sendSuccess(() ->
            Component.literal("§aClone preview rotated to " + angle + "°"), false);
        return 1;
    }

    private static int executeCloneConfirm(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());

        if (!session.isClonePreviewActive()) {
            context.getSource().sendFailure(Component.literal("§cNo active clone preview. Use /git clone first."));
            return 0;
        }

        // Check OP permission via command source permissions
        var perms = context.getSource().permissions();
        if (!(perms instanceof net.minecraft.server.permissions.LevelBasedPermissionSet levelPerms) ||
            !levelPerms.level().isEqualOrHigherThan(net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS)) {
            context.getSource().sendFailure(Component.literal("§cYou need OP permission to paste builds."));
            return 0;
        }

        // Place actual blocks
        Map<BlockPos, PlayerSession.ClonePreviewBlock> previewBlocks = session.getClonePreviewBlocks();
        int placed = 0;
        int skipped = 0;

        for (PlayerSession.ClonePreviewBlock preview : previewBlocks.values()) {
            BlockPos pos = preview.targetPos;
            Block block = parseBlock(preview.targetBlock);

            if (block == null) {
                skipped++;
                continue;
            }

            // Parse block state from the stored string
            BlockState state = parseBlockState(preview.targetBlock, preview.targetBlockState);
            if (state == null) {
                skipped++;
                continue;
            }

            // Only place if the current block is different
            BlockState currentState = player.level().getBlockState(pos);
            if (!currentState.equals(state)) {
                player.level().setBlock(pos, state, 3);
                placed++;
            } else {
                skipped++;
            }
        }

        // Clear preview
        clearClonePreview(player, session);

        // Create new instance for this paste
        String worldId = session.getClonePreviewWorldId();
        String dimensionId = session.getClonePreviewDimensionId();
        BlockPos anchor = session.getClonePreviewAnchor();

        if (anchor == null) {
            context.getSource().sendFailure(Component.literal("§cNo clone preview anchor found. Start a new clone with /git clone"));
            return 0;
        }

        // Get or create instance at this location
        BuildInstance pasteInstance = session.getOrCreateInstance(worldId, dimensionId, anchor);
        if (pasteInstance == null) {
            context.getSource().sendFailure(Component.literal("§cFailed to create instance for paste"));
            return 0;
        }
        session.setCurrentInstance(pasteInstance);

        final int finalPlaced = placed;
        final int finalSkipped = skipped;
        context.getSource().sendSuccess(() ->
            Component.literal("§aBuild pasted! Placed " + finalPlaced + " blocks, skipped " + finalSkipped + " (already correct)."), false);
        return 1;
    }

    private static int executeCloneCancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());

        if (!session.isClonePreviewActive()) {
            context.getSource().sendFailure(Component.literal("§cNo active clone preview."));
            return 0;
        }

        clearClonePreview(player, session);
        context.getSource().sendSuccess(() -> Component.literal("§aClone preview cleared."), false);
        return 1;
    }

    // Helper to clear clone preview
    private static void clearClonePreview(ServerPlayer player, PlayerSession session) {
        // Clear ghost blocks from GhostBlockManager
        GhostBlockManager.clearClonePreviews(player);

        // Clear session preview state
        // Note: Particles stop automatically when cleared from GhostBlockManager
        session.clearClonePreview();
        session.saveInstances();
    }

    // Helper to load clone preview from HEAD commit
    private static void loadClonePreviewFromHead(ServerPlayer player, PlayerSession session) {
        // Clear existing preview ghosts
        GhostBlockManager.clearClonePreviews(player);

        // Get HEAD commit blocks
        ApiResponse logResponse = BackendApiClient.getLog(session.getUsername(), session.getClonePreviewSourceRepo());
        if (!logResponse.success || logResponse.data == null) {
            return;
        }

        JsonArray commits = logResponse.data.getAsJsonArray();
        if (commits.size() == 0) {
            return;
        }

        String headHash = commits.get(0).getAsJsonObject().get("commit_hash").getAsString();
        ApiResponse blocksResponse = BackendApiClient.getCommitBlocks(
            session.getUsername(),
            session.getClonePreviewSourceRepo(),
            headHash
        );

        if (!blocksResponse.success || blocksResponse.data == null) {
            return;
        }

        JsonArray blocks = blocksResponse.data.getAsJsonArray();
        BlockPos anchor = session.getClonePreviewAnchor();
        // Rotation is applied via session.rotatePosition() which uses getClonePreviewRotation()
        String sourceInstanceId = session.getClonePreviewSourceInstanceId();

        // Get source instance for coordinate conversion
        BuildInstance sourceInstance = sourceInstanceId != null ?
            session.getAllInstances().stream()
                .filter(i -> i.getInstanceId().equals(sourceInstanceId))
                .findFirst()
                .orElse(null) : null;

        // If no source instance, calculate bounding box to make positions relative
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        if (sourceInstance == null) {
            for (JsonElement elem : blocks) {
                JsonObject block = elem.getAsJsonObject();
                String blockName = block.get("block_name").getAsString();
                // Skip air blocks for bounding box calculation
                if (blockName.equals("minecraft:air") || blockName.equals("air")) {
                    continue;
                }
                int x = block.get("x").getAsInt();
                int y = block.get("y").getAsInt();
                int z = block.get("z").getAsInt();
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
            }
            // If no valid blocks found, default to 0
            if (minX == Integer.MAX_VALUE) {
                minX = minY = minZ = 0;
            }
        }

        Map<BlockPos, PlayerSession.ClonePreviewBlock> previewBlocks = new HashMap<>();

        for (JsonElement elem : blocks) {
            JsonObject block = elem.getAsJsonObject();
            int x = block.get("x").getAsInt();
            int y = block.get("y").getAsInt();
            int z = block.get("z").getAsInt();
            String blockName = block.get("block_name").getAsString();

            // Skip air blocks
            if (blockName.equals("minecraft:air") || blockName.equals("air")) {
                continue;
            }

            // Convert to relative if from source instance
            BlockPos relativePos;
            if (sourceInstance != null) {
                BlockPos absPos = new BlockPos(x, y, z);
                relativePos = sourceInstance.toRelative(absPos);
            } else {
                // Make relative to bounding box minimum so build appears at anchor
                relativePos = new BlockPos(x - minX, y - minY, z - minZ);
            }

            // Apply rotation
            BlockPos rotatedPos = session.rotatePosition(relativePos);

            // Calculate absolute position from anchor
            BlockPos targetPos = new BlockPos(
                anchor.getX() + rotatedPos.getX(),
                anchor.getY() + rotatedPos.getY(),
                anchor.getZ() + rotatedPos.getZ()
            );

            // Check current block at target position
            BlockState currentState = player.level().getBlockState(targetPos);
            String currentBlockName = BuiltInRegistries.BLOCK.getKey(currentState.getBlock()).toString();

            // Determine if it's a wrong block
            boolean isWrongBlock = !currentBlockName.equals(blockName) && !currentState.isAir();
            boolean shouldShowGhost = isWrongBlock || currentState.isAir();

            // Get block state string from backend data
            String blockStateStr = block.has("block_state") ? block.get("block_state").getAsString() : "";

            if (shouldShowGhost) {
                // Add ghost block with proper state
                BlockState ghostState = parseBlockState(blockName, blockStateStr);
                if (ghostState != null) {
                    GhostBlockManager.ClonePreviewGhost ghost = new GhostBlockManager.ClonePreviewGhost(
                        targetPos,
                        ghostState,
                        blockName,
                        isWrongBlock
                    );
                    GhostBlockManager.addClonePreviewGhost(player, ghost);
                    GhostBlockManager.sendClonePreviewsToPlayer(player);
                }
            }

            // Store preview data
            previewBlocks.put(targetPos, new PlayerSession.ClonePreviewBlock(
                targetPos,
                blockName,
                blockStateStr,
                currentBlockName,
                isWrongBlock
            ));
        }

        session.setClonePreviewBlocks(previewBlocks);
        session.saveInstances();
    }

    // Helper to get target block player is looking at
    private static BlockPos getTargetBlockPos(ServerPlayer player, int maxDistance) {
        // Simple raycast - get block at player's eye position + look direction * distance
        net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition(1.0f);
        net.minecraft.world.phys.Vec3 lookDir = player.getViewVector(1.0f);

        for (int i = 1; i <= maxDistance * 10; i++) {
            double distance = i / 10.0;
            net.minecraft.world.phys.Vec3 checkPos = eyePos.add(lookDir.x * distance, lookDir.y * distance, lookDir.z * distance);
            BlockPos blockPos = BlockPos.containing(checkPos);
            BlockState state = player.level().getBlockState(blockPos);
            if (!state.isAir()) {
                return blockPos;
            }
        }
        return null;
    }

    // ==================== INSTANCE MANAGEMENT COMMANDS ====================

    private static int executeInstanceList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());

        Collection<BuildInstance> allInstances = session.getAllInstances();
        if (allInstances.isEmpty()) {
            context.getSource().sendFailure(Component.literal("§cNo build instances found. Use §6/git instance new §cto create one."));
            return 0;
        }

        // Get current dimension
        String currentDimension = player.level().dimension().toString();
        BlockPos playerPos = player.blockPosition();

        // Separate instances by dimension
        List<BuildInstance> currentDimInstances = new ArrayList<>();
        Map<String, Integer> otherDimCounts = new HashMap<>();

        for (BuildInstance instance : allInstances) {
            if (instance.getDimensionId().equals(currentDimension)) {
                currentDimInstances.add(instance);
            } else {
                String dimId = instance.getDimensionId();
                otherDimCounts.put(dimId, otherDimCounts.getOrDefault(dimId, 0) + 1);
            }
        }

        // Sort current dimension instances by distance
        currentDimInstances.sort(Comparator.comparingDouble(i -> i.distanceToAnchor(playerPos)));

        // Build output
        StringBuilder sb = new StringBuilder();
        sb.append("§aBuild instances for §f").append(session.getCurrentRepo()).append("§a in §f").append(formatDimensionName(currentDimension)).append(":\n");

        // Show auto-detection status
        boolean autoDetect = session.isAutoDetectionEnabled();
        sb.append(autoDetect ? "§7Auto-detection: §aON §7(/git instance autodetect off to disable)\n" :
                              "§7Auto-detection: §cOFF §7(manual control enabled)\n");
        sb.append("§7Use §6/git instance select <ID>§7 with the ID shown above\n");

        String currentInstanceId = session.getCurrentInstanceId();

        if (currentDimInstances.isEmpty()) {
            sb.append("§cNo instances in this dimension. Use §6/git instance new §cto create one.\n");
        } else {
            for (BuildInstance instance : currentDimInstances) {
                boolean isCurrent = instance.getInstanceId().equals(currentInstanceId);
                double distance = instance.distanceToAnchor(playerPos);
                BlockPos anchor = instance.getAnchorPos();
                int pendingCount = instance.getPendingCommits().size();

                // Generate fresh compact ID for display
                String compactId = BuildInstance.generateInstanceId(
                    instance.getWorldId(), instance.getDimensionId(), anchor);
                sb.append(isCurrent ? "§2" : "§7").append(compactId).append(" ");
                sb.append("§f").append(formatPos(anchor)).append(" ");
                sb.append(isCurrent ? "§2" : "§a").append(formatDistance(distance)).append(" away");
                if (isCurrent) sb.append(" §2(current)");
                sb.append("\n");

                if (pendingCount > 0) {
                    sb.append("  §7Pending: ").append(pendingCount).append(" commits\n");
                }
            }
        }

        // Show other dimensions summary
        if (!otherDimCounts.isEmpty()) {
            sb.append("\n§7Other dimensions:\n");
            for (Map.Entry<String, Integer> entry : otherDimCounts.entrySet()) {
                sb.append("§7  ").append(formatDimensionName(entry.getKey()))
                  .append(": ").append(entry.getValue()).append(" instance(s)\n");
            }
        }

        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int executeInstanceHighlightNearest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());

        String currentDimension = player.level().dimension().toString();
        BlockPos playerPos = player.blockPosition();

        // Find nearest instance in current dimension
        BuildInstance nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (BuildInstance instance : session.getAllInstances()) {
            if (instance.getDimensionId().equals(currentDimension)) {
                double dist = instance.distanceToAnchor(playerPos);
                if (dist < nearestDistance) {
                    nearestDistance = dist;
                    nearest = instance;
                }
            }
        }

        if (nearest == null) {
            context.getSource().sendFailure(Component.literal("§cNo instances found in this dimension."));
            return 0;
        }

        spawnBeaconBeam(player, nearest.getAnchorPos(), nearest.getInstanceId().equals(session.getCurrentInstanceId()));

        final BuildInstance finalNearest = nearest;
        final double finalDistance = nearestDistance;
        context.getSource().sendSuccess(() ->
            Component.literal("§aBeacon beam at " + formatPos(finalNearest.getAnchorPos()) +
                " §7(" + formatDistance(finalDistance) + " away)"), false);
        return 1;
    }

    private static int executeInstanceHighlight(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());

        String idStr = StringArgumentType.getString(context, "id");

        // Parse the ID (format: #1, #2, etc. or full instance ID)
        BuildInstance targetInstance = findInstanceById(session, player, idStr);

        if (targetInstance == null) {
            context.getSource().sendFailure(Component.literal("§cInstance not found: " + idStr));
            return 0;
        }

        // Check if in same dimension
        String currentDimension = player.level().dimension().toString();
        if (!targetInstance.getDimensionId().equals(currentDimension)) {
            context.getSource().sendFailure(Component.literal("§cInstance is in " +
                formatDimensionName(targetInstance.getDimensionId()) + ". You must be in the same dimension to highlight it."));
            return 0;
        }

        spawnBeaconBeam(player, targetInstance.getAnchorPos(),
            targetInstance.getInstanceId().equals(session.getCurrentInstanceId()));

        final BlockPos anchor = targetInstance.getAnchorPos();
        context.getSource().sendSuccess(() ->
            Component.literal("§aBeacon beam at " + formatPos(anchor)), false);
        return 1;
    }

    private static int executeInstanceSelect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());

        String idStr = StringArgumentType.getString(context, "id");

        BuildInstance targetInstance = findInstanceById(session, player, idStr);

        if (targetInstance == null) {
            context.getSource().sendFailure(Component.literal("§cInstance not found: " + idStr));
            return 0;
        }

        // Check if in same world/dimension
        String worldId = "world";
        if (player.level().getServer() != null) {
            worldId = player.level().getServer().getWorldData().getLevelName();
        }
        String dimensionId = player.level().dimension().toString();

        if (!targetInstance.isInSameContext(worldId, dimensionId)) {
            context.getSource().sendFailure(Component.literal("§cInstance is in " +
                formatDimensionName(targetInstance.getDimensionId()) +
                " (world: " + targetInstance.getWorldId() + "). You must be in the same world and dimension."));
            return 0;
        }

        session.setCurrentInstance(targetInstance);
        session.saveInstances();

        final BlockPos anchor = targetInstance.getAnchorPos();
        context.getSource().sendSuccess(() ->
            Component.literal("§aSwitched to instance at " + formatPos(anchor)), false);
        return 1;
    }

    private static int executeInstanceNew(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeInstanceNewInternal(context, null);
    }

    private static int executeInstanceNewNamed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        return executeInstanceNewInternal(context, name);
    }

    private static int executeInstanceNewInternal(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());

        // Get world and dimension
        String worldId = "world";
        if (player.level().getServer() != null) {
            worldId = player.level().getServer().getWorldData().getLevelName();
        }
        String dimensionId = player.level().dimension().toString();
        BlockPos playerPos = player.blockPosition();

        // Check for nearby instances
        double[] nearestDist = { Double.MAX_VALUE };
        for (BuildInstance instance : session.getAllInstances()) {
            if (instance.isInSameContext(worldId, dimensionId)) {
                double dist = instance.distanceToAnchor(playerPos);
                if (dist < nearestDist[0]) {
                    nearestDist[0] = dist;
                }
            }
        }

        // Create new instance at player position using forceCreateInstance
        BuildInstance newInstance = session.forceCreateInstance(worldId, dimensionId, playerPos);
        session.setCurrentInstance(newInstance);
        session.saveInstances();

        final double finalNearestDist = nearestDist[0];
        context.getSource().sendSuccess(() ->
            Component.literal("§aNew instance created at " + formatPos(playerPos) +
                (finalNearestDist < 1000 ? " §7(warning: " + formatDistance(finalNearestDist) + " from nearest instance)" : "")), false);
        return 1;
    }

    private static int executeInstanceInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());
        checkRepoActive(session, context.getSource());

        BuildInstance current = session.getCurrentInstance();
        if (current == null) {
            context.getSource().sendFailure(Component.literal("§cNo current instance. Use /git instance list and /git instance select."));
            return 0;
        }

        BlockPos anchor = current.getAnchorPos();
        int pendingCount = current.getPendingCommits().size();
        double distance = current.distanceToAnchor(player.blockPosition());

        StringBuilder sb = new StringBuilder();
        sb.append("§aCurrent Instance Info:\n");
        sb.append("§7ID: §f").append(current.getInstanceId()).append("\n");
        sb.append("§7World: §f").append(current.getWorldId()).append("\n");
        sb.append("§7Dimension: §f").append(formatDimensionName(current.getDimensionId())).append("\n");
        sb.append("§7Anchor: §f").append(formatPos(anchor)).append("\n");
        sb.append("§7Distance: §f").append(formatDistance(distance)).append("\n");
        sb.append("§7Pending commits: §f").append(pendingCount).append("\n");
        sb.append("§7Total instances: §f").append(session.getAllInstances().size());

        context.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int executeInstanceClearHighlight(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        // Clear any active beacon beams by sending empty packets or letting them timeout
        // The beacon beams are particles that fade, so we just stop sending new ones
        // For now, just acknowledge
        context.getSource().sendSuccess(() -> Component.literal("§aBeacon beams cleared (they fade automatically)."), false);
        return 1;
    }

    private static int executeInstanceAutoDetectOn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());

        session.setAutoDetectionEnabled(true);
        session.saveInstances();

        context.getSource().sendSuccess(() ->
            Component.literal("§aAuto-detection §aENABLED§a. The system will automatically switch instances based on your location."), false);
        return 1;
    }

    private static int executeInstanceAutoDetectOff(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());

        session.setAutoDetectionEnabled(false);
        session.saveInstances();

        context.getSource().sendSuccess(() ->
            Component.literal("§cAuto-detection DISABLED§a. Use §6/git instance select §ato manually switch instances."), false);
        return 1;
    }

    private static int executeInstanceAutoDetectStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());

        boolean enabled = session.isAutoDetectionEnabled();
        context.getSource().sendSuccess(() ->
            Component.literal("§7Auto-detection is currently " + (enabled ? "§aON" : "§cOFF") +
                "§7. Use §6/git instance autodetect on§7 or §6/git instance autodetect off§7 to change."), false);
        return 1;
    }

    // Helper methods for instance management
    private static BuildInstance findInstanceById(PlayerSession session, ServerPlayer player, String idStr) {
        // Search by compact format ID only
        for (BuildInstance instance : session.getAllInstances()) {
            String compactId = BuildInstance.generateInstanceId(
                instance.getWorldId(), instance.getDimensionId(), instance.getAnchorPos());
            if (compactId.equals(idStr)) {
                return instance;
            }
        }
        return null;
    }

    private static int executeDeactivate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(context);
        PlayerSession session = SessionManager.getSession(player);

        checkAuthenticated(session, context.getSource());

        String deactivatedRepo = session.getCurrentRepo();
        session.deactivate();
        session.saveInstances();

        final String repoName = deactivatedRepo;
        context.getSource().sendSuccess(() ->
            Component.literal("§aDeactivated '§f" + repoName + "§a'. GitBuild tracking paused.\n" +
                "§7Ghost blocks remain visible. Use §6/git activate <repo> §7to resume."), false);
        return 1;
    }

    private static void spawnBeaconBeam(ServerPlayer player, BlockPos anchor, boolean isCurrentInstance) {
        // Spawn particles in a vertical beam
        // Use happy villager particles for normal, witch particles for current
        net.minecraft.core.particles.ParticleOptions particle = isCurrentInstance ?
            net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER :
            net.minecraft.core.particles.ParticleTypes.WITCH;

        ServerLevel level = (ServerLevel) player.level();

        // Spawn particles from anchor up to 50 blocks high
        for (int i = 1; i <= 50; i++) {
            double x = anchor.getX() + 0.5;
            double y = anchor.getY() + i;
            double z = anchor.getZ() + 0.5;

            level.sendParticles(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        // Also spawn a ring of particles at the base
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double x = anchor.getX() + 0.5 + Math.cos(angle) * 0.5;
            double z = anchor.getZ() + 0.5 + Math.sin(angle) * 0.5;
            level.sendParticles(particle, x, anchor.getY() + 1.1, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static String formatDimensionName(String dimensionId) {
        return switch (dimensionId) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> dimensionId.replace("minecraft:", "");
        };
    }

    private static String formatDistance(double distance) {
        if (distance < 10) {
            return String.format("%.1f blocks", distance);
        } else if (distance < 1000) {
            return String.format("%.0f blocks", distance);
        } else {
            return String.format("%.1f km", distance / 1000);
        }
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

    /**
     * Parse a block with its full state string.
     * Handles formats like: "minecraft:stairs[facing=east,half=bottom]" or "minecraft:redstone_torch[lit=true]"
     */
    private static BlockState parseBlockState(String blockName, String stateString) {
        Block block = parseBlock(blockName);
        if (block == null) {
            return null;
        }

        BlockState state = block.defaultBlockState();

        // If no state string or empty, return default
        if (stateString == null || stateString.isEmpty() || stateString.equals("{}")) {
            return state;
        }

        try {
            // Parse state string - format: "[key=value,key2=value2]" or "Block{key=value,key2=value2}"
            String cleanState = stateString;

            // Extract just the properties part if wrapped in Block{} or []
            if (cleanState.contains("{")) {
                cleanState = cleanState.substring(cleanState.indexOf('{') + 1);
                if (cleanState.endsWith("}")) {
                    cleanState = cleanState.substring(0, cleanState.length() - 1);
                }
            } else if (cleanState.startsWith("[")) {
                cleanState = cleanState.substring(1);
                if (cleanState.endsWith("]")) {
                    cleanState = cleanState.substring(0, cleanState.length() - 1);
                }
            }

            // Parse each property
            if (!cleanState.isEmpty()) {
                String[] pairs = cleanState.split(",");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim();
                        String value = keyValue[1].trim();

                        // Find the property and set it
                        state = setBlockStateProperty(state, key, value);
                    }
                }
            }
        } catch (Exception e) {
            GitBuildMod.LOGGER.warn("Failed to parse block state '{}': {}", stateString, e.getMessage());
        }

        return state;
    }

    /**
     * Set a specific property on a BlockState
     */
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState setBlockStateProperty(BlockState state, String propertyName, String value) {
        net.minecraft.world.level.block.state.properties.Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
        if (property == null) {
            return state; // Property doesn't exist for this block
        }

        try {
            // Parse the value based on property type
            Optional<?> parsedValue = property.getValue(value);
            if (parsedValue.isPresent()) {
                return state.setValue((net.minecraft.world.level.block.state.properties.Property<T>) property, (T) parsedValue.get());
            }
        } catch (Exception e) {
            GitBuildMod.LOGGER.debug("Failed to set property {}={}: {}", propertyName, value, e.getMessage());
        }

        return state;
    }
}
