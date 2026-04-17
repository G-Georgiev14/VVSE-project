package com.sanguine.gitbuild;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class PlayerSession {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PENDING_COMMITS_DIR = "gitbuild-commits";
    private static final double INSTANCE_DETECTION_RADIUS = 1000.0; // Blocks

    private final UUID playerUUID;
    private String username;
    private String uuid;
    private String currentRepo;
    private boolean autoAdd = true;
    private boolean autoRm = true;

    // Staging area - relative coordinates keyed by relative position
    private final Map<BlockPos, BlockChange> stagingArea = new HashMap<>();

    // Build instances: instanceId -> BuildInstance
    private final Map<String, BuildInstance> instances = new HashMap<>();
    private String currentInstanceId = null;

    // Clone preview state
    public static class ClonePreviewBlock {
        public final BlockPos targetPos;
        public final String targetBlock;
        public final String targetBlockState;
        public final String currentBlockAtPos;
        public final boolean isWrongBlock;

        public ClonePreviewBlock(BlockPos targetPos, String targetBlock, String targetBlockState,
                                  String currentBlockAtPos, boolean isWrongBlock) {
            this.targetPos = targetPos;
            this.targetBlock = targetBlock;
            this.targetBlockState = targetBlockState;
            this.currentBlockAtPos = currentBlockAtPos;
            this.isWrongBlock = isWrongBlock;
        }
    }

    private Map<BlockPos, ClonePreviewBlock> clonePreviewBlocks = new HashMap<>();
    private BlockPos clonePreviewAnchor = null;
    private int clonePreviewRotation = 0; // 0, 90, 180, 270
    private String clonePreviewSourceRepo = null;
    private String clonePreviewSourceInstanceId = null;
    private boolean clonePreviewActive = false;
    private String clonePreviewWorldId = null;
    private String clonePreviewDimensionId = null;

    // Auto-detection setting - when false, player manually controls instance selection
    private boolean autoDetectionEnabled = true;

    public enum ChangeType {
        ADD, REMOVE, MODIFY
    }

    public static class BlockChange {
        public final BlockPos relPos;  // Relative to instance anchor
        public final BlockState oldState;
        public final BlockState newState;
        public final ChangeType type;

        public BlockChange(BlockPos relPos, BlockState oldState, BlockState newState, ChangeType type) {
            this.relPos = relPos.immutable();
            this.oldState = oldState;
            this.newState = newState;
            this.type = type;
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
        loadInstances();
        currentInstanceId = null;
    }

    public String getCurrentRepo() {
        return currentRepo;
    }

    public boolean hasActiveRepo() {
        return currentRepo != null && !currentRepo.isEmpty();
    }

    // Instance management
    public BuildInstance getCurrentInstance() {
        return currentInstanceId != null ? instances.get(currentInstanceId) : null;
    }

    public String getCurrentInstanceId() {
        return currentInstanceId;
    }

    public Collection<BuildInstance> getAllInstances() {
        return instances.values();
    }

    public BuildInstance getOrCreateInstance(String worldId, String dimensionId, BlockPos playerPos) {
        if (playerPos == null) {
            return null;
        }

        // First, check if we're close to an existing instance in the same context
        for (BuildInstance instance : instances.values()) {
            if (instance.isInSameContext(worldId, dimensionId)) {
                if (instance.distanceToAnchor(playerPos) <= INSTANCE_DETECTION_RADIUS) {
                    return instance;
                }
            }
        }

        // Create new instance with anchor at player position rounded to chunk boundaries
        BlockPos anchor = new BlockPos(
            (playerPos.getX() >> 4) << 4,
            playerPos.getY(),
            (playerPos.getZ() >> 4) << 4
        );
        BuildInstance newInstance = new BuildInstance(worldId, dimensionId, anchor);
        instances.put(newInstance.getInstanceId(), newInstance);
        return newInstance;
    }

    public void setCurrentInstance(BuildInstance instance) {
        if (instance != null) {
            currentInstanceId = instance.getInstanceId();
        } else {
            currentInstanceId = null;
        }
    }

    public void updateInstanceContext(String worldId, String dimensionId, BlockPos playerPos) {
        if (!hasActiveRepo()) return;

        // If auto-detection is disabled, stay on current instance
        // Don't auto-create - player must use /git instance new
        if (!autoDetectionEnabled) {
            return;
        }

        // Auto-detection ON: Find closest instance in current dimension
        BuildInstance closest = null;
        double closestDist = Double.MAX_VALUE;

        for (BuildInstance instance : instances.values()) {
            if (instance.isInSameContext(worldId, dimensionId)) {
                double dist = instance.distanceToAnchor(playerPos);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = instance;
                }
            }
        }

        if (closest != null) {
            setCurrentInstance(closest);
        } else if (instances.isEmpty()) {
            // No instances exist at all - auto-create first one at player position
            BuildInstance newInstance = forceCreateInstance(worldId, dimensionId, playerPos);
            setCurrentInstance(newInstance);
        }
        // If instances exist but none in this dimension, do nothing - player must use /git instance new
    }

    // Deactivate current repo (pause GitBuild tracking)
    public void deactivate() {
        currentRepo = null;
        currentInstanceId = null;
        // Note: Ghost blocks remain visible, instances data is preserved
    }

    // Reactivate a repo
    public boolean activateRepo(String repoName) {
        if (repoName != null && !repoName.isEmpty()) {
            this.currentRepo = repoName;
            return true;
        }
        return false;
    }

    // Auto-detection control
    public boolean isAutoDetectionEnabled() {
        return autoDetectionEnabled;
    }

    public void setAutoDetectionEnabled(boolean enabled) {
        this.autoDetectionEnabled = enabled;
    }

    public void toggleAutoDetection() {
        this.autoDetectionEnabled = !this.autoDetectionEnabled;
    }

    // Get last active repo name (for reactivation)
    public String getLastActiveRepo() {
        // Could be stored in a field, for now just return current or null
        return currentRepo;
    }

    // Staging area - uses relative coordinates
    public void stageBlock(BlockPos absolutePos, BlockState oldState, BlockState newState, ChangeType type) {
        BuildInstance instance = getCurrentInstance();
        if (instance == null) return;

        BlockPos relPos = instance.toRelative(absolutePos);
        stagingArea.put(relPos, new BlockChange(relPos, oldState, newState, type));
    }

    public void unstageBlock(BlockPos absolutePos) {
        BuildInstance instance = getCurrentInstance();
        if (instance == null) return;

        BlockPos relPos = instance.toRelative(absolutePos);
        stagingArea.remove(relPos);
    }

    public void clearStaging() {
        stagingArea.clear();
    }

    public Map<BlockPos, BlockChange> getStagingArea() {
        return new HashMap<>(stagingArea);
    }

    public boolean isStaged(BlockPos absolutePos) {
        BuildInstance instance = getCurrentInstance();
        if (instance == null) return false;

        BlockPos relPos = instance.toRelative(absolutePos);
        return stagingArea.containsKey(relPos);
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

    // Instance storage
    public File getInstancesFile() {
        if (username == null || currentRepo == null) {
            return null;
        }
        return new File(PENDING_COMMITS_DIR, username + "/" + currentRepo + "/instances.json");
    }

    public void loadInstances() {
        instances.clear();
        currentInstanceId = null;
        clearClonePreview();

        File file = getInstancesFile();
        if (file == null || !file.exists()) {
            // Try to migrate from old format (individual commit files)
            migrateFromOldFormat();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            JsonArray instancesArray = json.getAsJsonArray("instances");

            for (JsonElement elem : instancesArray) {
                BuildInstance instance = BuildInstance.fromJson(elem.getAsJsonObject());
                instances.put(instance.getInstanceId(), instance);
            }

            // Load clone preview if exists
            if (json.has("clonePreview")) {
                loadClonePreviewFromJson(json.getAsJsonObject("clonePreview"));
            }

            // Load staging area
            if (json.has("stagingArea")) {
                stagingArea.clear();
                JsonArray stagingArray = json.getAsJsonArray("stagingArea");
                for (JsonElement elem : stagingArray) {
                    JsonObject changeJson = elem.getAsJsonObject();
                    BlockPos pos = new BlockPos(
                        changeJson.get("x").getAsInt(),
                        changeJson.get("y").getAsInt(),
                        changeJson.get("z").getAsInt()
                    );
                    BlockState oldState = blockStateFromString(changeJson.get("oldState").getAsString());
                    BlockState newState = blockStateFromString(changeJson.get("newState").getAsString());
                    ChangeType type = ChangeType.valueOf(changeJson.get("type").getAsString());
                    stagingArea.put(pos, new BlockChange(pos, oldState, newState, type));
                }
            }

            // Load auto-detection setting (default to true if not present)
            if (json.has("autoDetectionEnabled")) {
                autoDetectionEnabled = json.get("autoDetectionEnabled").getAsBoolean();
            } else {
                autoDetectionEnabled = true;
            }
        } catch (IOException e) {
            GitBuildMod.LOGGER.warn("Failed to load instances: {}", e.getMessage());
        }
    }

    private void migrateFromOldFormat() {
        // Look for old-style commit files and migrate them to a default instance
        File dir = getPendingCommitsDir();
        if (dir == null || !dir.exists()) {
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json") && !name.equals("instances.json"));
        if (files == null || files.length == 0) {
            return;
        }

        // Create a default instance for migration (overworld at 0,0,0)
        BuildInstance defaultInstance = new BuildInstance("world", "minecraft:overworld", BlockPos.ZERO);

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                String commitHash = json.get("commitHash").getAsString();
                String commitName = json.get("commitName").getAsString();
                String message = json.get("message").getAsString();
                long timestamp = json.get("timestamp").getAsLong();

                List<BuildInstance.RelativeBlockData> blocks = new ArrayList<>();
                JsonArray blocksArray = json.getAsJsonArray("blocks");
                for (int i = 0; i < blocksArray.size(); i++) {
                    JsonObject block = blocksArray.get(i).getAsJsonObject();
                    blocks.add(new BuildInstance.RelativeBlockData(
                        block.get("x").getAsInt(),
                        block.get("y").getAsInt(),
                        block.get("z").getAsInt(),
                        block.get("block_name").getAsString(),
                        block.get("block_state").getAsString()
                    ));
                }

                BuildInstance.CommitData commit = new BuildInstance.CommitData(
                    commitHash, commitName, message, timestamp, blocks
                );
                defaultInstance.savePendingCommit(commit);

                // Delete old file
                file.delete();
            } catch (IOException e) {
                GitBuildMod.LOGGER.warn("Failed to migrate commit file {}: {}", file.getName(), e.getMessage());
            }
        }

        if (!defaultInstance.getPendingCommits().isEmpty()) {
            instances.put(defaultInstance.getInstanceId(), defaultInstance);
            saveInstances();
        }
    }

    public void saveInstances() {
        File file = getInstancesFile();
        if (file == null) {
            return;
        }

        File dir = file.getParentFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        JsonObject json = new JsonObject();
        JsonArray instancesArray = new JsonArray();

        for (BuildInstance instance : instances.values()) {
            instancesArray.add(instance.toJson());
        }

        json.add("instances", instancesArray);

        // Save clone preview state if active
        JsonObject clonePreviewJson = saveClonePreviewToJson();
        if (clonePreviewJson != null) {
            json.add("clonePreview", clonePreviewJson);
        }

        // Save staging area
        JsonArray stagingArray = new JsonArray();
        for (Map.Entry<BlockPos, BlockChange> entry : stagingArea.entrySet()) {
            JsonObject changeJson = new JsonObject();
            BlockPos pos = entry.getKey();
            BlockChange change = entry.getValue();
            changeJson.addProperty("x", pos.getX());
            changeJson.addProperty("y", pos.getY());
            changeJson.addProperty("z", pos.getZ());
            changeJson.addProperty("oldState", blockStateToString(change.oldState));
            changeJson.addProperty("newState", blockStateToString(change.newState));
            changeJson.addProperty("type", change.type.name());
            stagingArray.add(changeJson);
        }
        json.add("stagingArea", stagingArray);

        // Save auto-detection setting
        json.addProperty("autoDetectionEnabled", autoDetectionEnabled);

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            GitBuildMod.LOGGER.error("Failed to save instances: {}", e.getMessage());
        }
    }

    public void savePendingCommitToCurrent(BuildInstance.CommitData commit) {
        BuildInstance instance = getCurrentInstance();
        if (instance == null) return;

        instance.savePendingCommit(commit);
        saveInstances();
    }

    public List<BuildInstance.CommitData> getPendingCommitsForCurrent() {
        BuildInstance instance = getCurrentInstance();
        if (instance == null) return new ArrayList<>();
        return instance.getPendingCommits();
    }

    public int getPendingCommitCount() {
        BuildInstance instance = getCurrentInstance();
        if (instance == null) return 0;
        return instance.getPendingCommits().size();
    }

    public void clearPendingCommitsForCurrent() {
        BuildInstance instance = getCurrentInstance();
        if (instance == null) return;

        instance.clearPendingCommits();
        saveInstances();
    }

    // Clone preview methods
    public void startClonePreview(String sourceRepo, String sourceInstanceId, BlockPos anchor,
                                   String worldId, String dimensionId) {
        this.clonePreviewSourceRepo = sourceRepo;
        this.clonePreviewSourceInstanceId = sourceInstanceId;
        this.clonePreviewAnchor = anchor;
        this.clonePreviewWorldId = worldId;
        this.clonePreviewDimensionId = dimensionId;
        this.clonePreviewRotation = 0;
        this.clonePreviewActive = true;
        this.clonePreviewBlocks.clear();
    }

    public void setClonePreviewBlocks(Map<BlockPos, ClonePreviewBlock> blocks) {
        this.clonePreviewBlocks = new HashMap<>(blocks);
    }

    public Map<BlockPos, ClonePreviewBlock> getClonePreviewBlocks() {
        return new HashMap<>(clonePreviewBlocks);
    }

    public void clearClonePreview() {
        clonePreviewBlocks.clear();
        clonePreviewAnchor = null;
        clonePreviewRotation = 0;
        clonePreviewSourceRepo = null;
        clonePreviewSourceInstanceId = null;
        clonePreviewActive = false;
        clonePreviewWorldId = null;
        clonePreviewDimensionId = null;
    }

    // Force create instance at exact position (bypasses auto-detection radius)
    public BuildInstance forceCreateInstance(String worldId, String dimensionId, BlockPos anchorPos) {
        BuildInstance newInstance = new BuildInstance(worldId, dimensionId, anchorPos);
        instances.put(newInstance.getInstanceId(), newInstance);
        return newInstance;
    }

    public BlockPos getClonePreviewAnchor() {
        return clonePreviewAnchor;
    }

    public void setClonePreviewAnchor(BlockPos anchor) {
        this.clonePreviewAnchor = anchor;
    }

    public int getClonePreviewRotation() {
        return clonePreviewRotation;
    }

    public void setClonePreviewRotation(int rotation) {
        this.clonePreviewRotation = rotation % 360;
    }

    public String getClonePreviewSourceRepo() {
        return clonePreviewSourceRepo;
    }

    public String getClonePreviewSourceInstanceId() {
        return clonePreviewSourceInstanceId;
    }

    public boolean isClonePreviewActive() {
        return clonePreviewActive;
    }

    public String getClonePreviewWorldId() {
        return clonePreviewWorldId;
    }

    public String getClonePreviewDimensionId() {
        return clonePreviewDimensionId;
    }

    // Rotate a position around the anchor based on current rotation
    public BlockPos rotatePosition(BlockPos relativePos) {
        int x = relativePos.getX();
        int z = relativePos.getZ();
        int y = relativePos.getY();

        switch (clonePreviewRotation) {
            case 90:
                return new BlockPos(-z, y, x);
            case 180:
                return new BlockPos(-x, y, -z);
            case 270:
                return new BlockPos(z, y, -x);
            case 0:
            default:
                return relativePos;
        }
    }

    // Save clone preview state to JSON for persistence
    public JsonObject saveClonePreviewToJson() {
        if (!clonePreviewActive) return null;

        JsonObject json = new JsonObject();
        json.addProperty("active", true);
        json.addProperty("sourceRepo", clonePreviewSourceRepo);
        json.addProperty("sourceInstanceId", clonePreviewSourceInstanceId);
        json.addProperty("anchorX", clonePreviewAnchor.getX());
        json.addProperty("anchorY", clonePreviewAnchor.getY());
        json.addProperty("anchorZ", clonePreviewAnchor.getZ());
        json.addProperty("rotation", clonePreviewRotation);
        json.addProperty("worldId", clonePreviewWorldId);
        json.addProperty("dimensionId", clonePreviewDimensionId);

        JsonArray blocksArray = new JsonArray();
        for (ClonePreviewBlock block : clonePreviewBlocks.values()) {
            JsonObject blockObj = new JsonObject();
            blockObj.addProperty("x", block.targetPos.getX());
            blockObj.addProperty("y", block.targetPos.getY());
            blockObj.addProperty("z", block.targetPos.getZ());
            blockObj.addProperty("targetBlock", block.targetBlock);
            blockObj.addProperty("targetBlockState", block.targetBlockState);
            blockObj.addProperty("currentBlock", block.currentBlockAtPos);
            blockObj.addProperty("isWrong", block.isWrongBlock);
            blocksArray.add(blockObj);
        }
        json.add("blocks", blocksArray);

        return json;
    }

    // Load clone preview state from JSON
    public void loadClonePreviewFromJson(JsonObject json) {
        if (json == null || !json.has("active") || !json.get("active").getAsBoolean()) {
            return;
        }

        clonePreviewActive = true;
        clonePreviewSourceRepo = json.get("sourceRepo").getAsString();
        clonePreviewSourceInstanceId = json.get("sourceInstanceId").getAsString();
        clonePreviewAnchor = new BlockPos(
            json.get("anchorX").getAsInt(),
            json.get("anchorY").getAsInt(),
            json.get("anchorZ").getAsInt()
        );
        clonePreviewRotation = json.get("rotation").getAsInt();
        clonePreviewWorldId = json.get("worldId").getAsString();
        clonePreviewDimensionId = json.get("dimensionId").getAsString();

        clonePreviewBlocks.clear();
        if (json.has("blocks")) {
            JsonArray blocksArray = json.getAsJsonArray("blocks");
            for (JsonElement elem : blocksArray) {
                JsonObject blockObj = elem.getAsJsonObject();
                BlockPos pos = new BlockPos(
                    blockObj.get("x").getAsInt(),
                    blockObj.get("y").getAsInt(),
                    blockObj.get("z").getAsInt()
                );
                ClonePreviewBlock block = new ClonePreviewBlock(
                    pos,
                    blockObj.get("targetBlock").getAsString(),
                    blockObj.get("targetBlockState").getAsString(),
                    blockObj.get("currentBlock").getAsString(),
                    blockObj.get("isWrong").getAsBoolean()
                );
                clonePreviewBlocks.put(pos, block);
            }
        }
    }

    // Legacy methods for backward compatibility
    public File getPendingCommitsDir() {
        if (username == null || currentRepo == null) {
            return null;
        }
        return new File(PENDING_COMMITS_DIR, username + "/" + currentRepo);
    }

    // BlockState serialization helpers
    private String blockStateToString(BlockState state) {
        if (state == null) return "null";
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private BlockState blockStateFromString(String str) {
        if (str == null || str.equals("null")) return null;
        try {
            for (var entry : net.minecraft.core.registries.BuiltInRegistries.BLOCK.entrySet()) {
                if (entry.getKey().toString().equals(str)) {
                    return entry.getValue().defaultBlockState();
                }
            }
        } catch (Exception e) {
            GitBuildMod.LOGGER.warn("Failed to parse block state: {}", str);
        }
        return null;
    }
}
