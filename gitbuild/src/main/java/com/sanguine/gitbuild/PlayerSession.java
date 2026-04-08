package com.sanguine.gitbuild;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class PlayerSession {
    private final UUID playerUUID;
    private String username;
    private String uuid;
    private String currentRepo;
    private boolean autoAdd = true;  // Enabled by default in creative
    private boolean autoRm = true;   // Enabled by default in creative
    private final Map<BlockPos, BlockChange> stagingArea = new HashMap<>();
    private final Map<String, RemoteCredentials> remotes = new HashMap<>();

    public enum ChangeType {
        ADD, REMOVE, MODIFY
    }

    public static class BlockChange {
        public final BlockPos pos;
        public final BlockState oldState;
        public final BlockState newState;
        public final ChangeType type;

        public BlockChange(BlockPos pos, BlockState oldState, BlockState newState, ChangeType type) {
            this.pos = pos.immutable();
            this.oldState = oldState;
            this.newState = newState;
            this.type = type;
        }
    }

    public static class RemoteCredentials {
        public String username;
        public String password;

        public RemoteCredentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    public PlayerSession(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    // User authentication
    public void setAuthenticated(String username, String uuid) {
        this.username = username;
        this.uuid = uuid;
    }

    public boolean isAuthenticated() {
        return username != null && uuid != null;
    }

    public String getUsername() {
        return username;
    }

    public String getUuid() {
        return uuid;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    // Repository management
    public void setCurrentRepo(String repoName) {
        this.currentRepo = repoName;
    }

    public String getCurrentRepo() {
        return currentRepo;
    }

    public boolean hasActiveRepo() {
        return currentRepo != null && !currentRepo.isEmpty();
    }

    // Staging area
    public void stageBlock(BlockPos pos, BlockState oldState, BlockState newState, ChangeType type) {
        stagingArea.put(pos.immutable(), new BlockChange(pos, oldState, newState, type));
    }

    public void unstageBlock(BlockPos pos) {
        stagingArea.remove(pos.immutable());
    }

    public void clearStaging() {
        stagingArea.clear();
    }

    public Map<BlockPos, BlockChange> getStagingArea() {
        return new HashMap<>(stagingArea);
    }

    public boolean isStaged(BlockPos pos) {
        return stagingArea.containsKey(pos.immutable());
    }

    public int getStagedCount() {
        return stagingArea.size();
    }

    // Auto tracking
    public void setAutoAdd(boolean enabled) {
        this.autoAdd = enabled;
    }

    public void setAutoRm(boolean enabled) {
        this.autoRm = enabled;
    }

    public boolean isAutoAdd() {
        return autoAdd;
    }

    public boolean isAutoRm() {
        return autoRm;
    }

    public void toggleAutoAdd() {
        this.autoAdd = !this.autoAdd;
    }

    public void toggleAutoRm() {
        this.autoRm = !this.autoRm;
    }

    // Remote credentials
    public void addRemote(String name, String username, String password) {
        remotes.put(name, new RemoteCredentials(username, password));
    }

    public RemoteCredentials getRemote(String name) {
        return remotes.get(name);
    }

    public Map<String, RemoteCredentials> getAllRemotes() {
        return new HashMap<>(remotes);
    }

    public void removeRemote(String name) {
        remotes.remove(name);
    }
}
