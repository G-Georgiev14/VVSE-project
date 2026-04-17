package com.sanguine.gitbuild;

import com.google.gson.*;
import com.sanguine.gitbuild.BackendApiClient.BlockData;
import net.minecraft.core.BlockPos;

import java.util.*;

public class BuildInstance {
    private final String instanceId;
    private final String worldId;
    private final String dimensionId;
    private final BlockPos anchorPos;
    private final List<CommitData> pendingCommits = new ArrayList<>();

    public static class CommitData {
        public String commitHash;
        public String commitName;
        public String message;
        public long timestamp;
        public List<RelativeBlockData> blocks;

        public CommitData(String commitHash, String commitName, String message, long timestamp, List<RelativeBlockData> blocks) {
            this.commitHash = commitHash;
            this.commitName = commitName;
            this.message = message;
            this.timestamp = timestamp;
            this.blocks = blocks;
        }

        public static CommitData fromAbsolute(String commitHash, String commitName, String message, long timestamp,
                                               List<BlockData> absoluteBlocks, BlockPos anchor) {
            List<RelativeBlockData> relativeBlocks = new ArrayList<>();
            for (BlockData block : absoluteBlocks) {
                relativeBlocks.add(RelativeBlockData.fromAbsolute(block, anchor));
            }
            return new CommitData(commitHash, commitName, message, timestamp, relativeBlocks);
        }

        public List<BlockData> toAbsoluteBlocks(BlockPos anchor) {
            List<BlockData> absoluteBlocks = new ArrayList<>();
            for (RelativeBlockData block : blocks) {
                absoluteBlocks.add(block.toAbsolute(anchor));
            }
            return absoluteBlocks;
        }
    }

    public static class RelativeBlockData {
        public int rel_x;
        public int rel_y;
        public int rel_z;
        public String block_name;
        public String block_state;

        public RelativeBlockData(int rel_x, int rel_y, int rel_z, String block_name, String block_state) {
            this.rel_x = rel_x;
            this.rel_y = rel_y;
            this.rel_z = rel_z;
            this.block_name = block_name;
            this.block_state = block_state;
        }

        public static RelativeBlockData fromAbsolute(BlockData absolute, BlockPos anchor) {
            return new RelativeBlockData(
                absolute.x - anchor.getX(),
                absolute.y - anchor.getY(),
                absolute.z - anchor.getZ(),
                absolute.block_name,
                absolute.block_state
            );
        }

        public BlockData toAbsolute(BlockPos anchor) {
            return new BlockData(
                rel_x + anchor.getX(),
                rel_y + anchor.getY(),
                rel_z + anchor.getZ(),
                block_name,
                block_state
            );
        }
    }

    public BuildInstance(String worldId, String dimensionId, BlockPos anchorPos) {
        this.worldId = worldId;
        this.dimensionId = dimensionId;
        this.anchorPos = anchorPos.immutable();
        this.instanceId = generateInstanceId(worldId, dimensionId, anchorPos);
    }

    private BuildInstance(String instanceId, String worldId, String dimensionId, BlockPos anchorPos) {
        this.instanceId = instanceId;
        this.worldId = worldId;
        this.dimensionId = dimensionId;
        this.anchorPos = anchorPos;
    }

    public static String generateInstanceId(String worldId, String dimensionId, BlockPos anchor) {
        // Parse dimension ID from ResourceKey format: ResourceKey[minecraft:dimension / minecraft:overworld]
        String dimName = parseDimensionName(dimensionId);
        String dimPrefix = switch (dimName) {
            case "overworld" -> "ow";
            case "the_nether" -> "n";
            case "the_end" -> "end";
            default -> dimName.replace(":", "_");
        };
        return dimPrefix + "_" + formatCoord(anchor.getX()) + "_" + 
               formatCoord(anchor.getY()) + "_" + formatCoord(anchor.getZ());
    }

    private static String parseDimensionName(String dimensionId) {
        // Handle ResourceKey[dimension / overworld] format
        if (dimensionId.contains("/")) {
            String afterSlash = dimensionId.substring(dimensionId.lastIndexOf("/") + 1).trim();
            // Remove trailing ] if present
            if (afterSlash.endsWith("]")) {
                afterSlash = afterSlash.substring(0, afterSlash.length() - 1);
            }
            // Remove minecraft: prefix
            if (afterSlash.startsWith("minecraft:")) {
                afterSlash = afterSlash.substring("minecraft:".length());
            }
            return afterSlash;
        }
        // Handle plain format: minecraft:overworld
        if (dimensionId.startsWith("minecraft:")) {
            return dimensionId.substring("minecraft:".length());
        }
        return dimensionId;
    }

    private static String formatCoord(int coord) {
        return (coord >= 0 ? "p" : "n") + Math.abs(coord);
    }

    public String getInstanceId() { return instanceId; }
    public String getWorldId() { return worldId; }
    public String getDimensionId() { return dimensionId; }
    public BlockPos getAnchorPos() { return anchorPos; }

    public boolean isInSameContext(String worldId, String dimensionId) {
        return this.worldId.equals(worldId) && this.dimensionId.equals(dimensionId);
    }

    public double distanceToAnchor(BlockPos pos) {
        return Math.sqrt(
            Math.pow(pos.getX() - anchorPos.getX(), 2) +
            Math.pow(pos.getY() - anchorPos.getY(), 2) +
            Math.pow(pos.getZ() - anchorPos.getZ(), 2)
        );
    }

    public BlockPos toRelative(BlockPos absolute) {
        return new BlockPos(
            absolute.getX() - anchorPos.getX(),
            absolute.getY() - anchorPos.getY(),
            absolute.getZ() - anchorPos.getZ()
        );
    }

    public BlockPos toAbsolute(BlockPos relative) {
        return new BlockPos(
            relative.getX() + anchorPos.getX(),
            relative.getY() + anchorPos.getY(),
            relative.getZ() + anchorPos.getZ()
        );
    }

    public void savePendingCommit(CommitData commit) {
        pendingCommits.add(commit);
        pendingCommits.sort(Comparator.comparingLong(c -> c.timestamp));
    }

    public List<CommitData> getPendingCommits() {
        return new ArrayList<>(pendingCommits);
    }

    public void clearPendingCommits() {
        pendingCommits.clear();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("instanceId", instanceId);
        json.addProperty("worldId", worldId);
        json.addProperty("dimensionId", dimensionId);
        json.addProperty("anchorX", anchorPos.getX());
        json.addProperty("anchorY", anchorPos.getY());
        json.addProperty("anchorZ", anchorPos.getZ());

        JsonArray commitsArray = new JsonArray();
        for (CommitData commit : pendingCommits) {
            JsonObject commitJson = new JsonObject();
            commitJson.addProperty("commitHash", commit.commitHash);
            commitJson.addProperty("commitName", commit.commitName);
            commitJson.addProperty("message", commit.message);
            commitJson.addProperty("timestamp", commit.timestamp);

            JsonArray blocksArray = new JsonArray();
            for (RelativeBlockData block : commit.blocks) {
                JsonObject blockObj = new JsonObject();
                blockObj.addProperty("rel_x", block.rel_x);
                blockObj.addProperty("rel_y", block.rel_y);
                blockObj.addProperty("rel_z", block.rel_z);
                blockObj.addProperty("block_name", block.block_name);
                blockObj.addProperty("block_state", block.block_state);
                blocksArray.add(blockObj);
            }
            commitJson.add("blocks", blocksArray);
            commitsArray.add(commitJson);
        }
        json.add("pendingCommits", commitsArray);

        return json;
    }

    public static BuildInstance fromJson(JsonObject json) {
        String instanceId = json.get("instanceId").getAsString();
        String worldId = json.get("worldId").getAsString();
        String dimensionId = json.get("dimensionId").getAsString();
        BlockPos anchor = new BlockPos(
            json.get("anchorX").getAsInt(),
            json.get("anchorY").getAsInt(),
            json.get("anchorZ").getAsInt()
        );

        BuildInstance instance = new BuildInstance(instanceId, worldId, dimensionId, anchor);

        if (json.has("pendingCommits")) {
            JsonArray commitsArray = json.getAsJsonArray("pendingCommits");
            for (JsonElement elem : commitsArray) {
                JsonObject commitJson = elem.getAsJsonObject();
                String commitHash = commitJson.get("commitHash").getAsString();
                String commitName = commitJson.get("commitName").getAsString();
                String message = commitJson.get("message").getAsString();
                long timestamp = commitJson.get("timestamp").getAsLong();

                List<RelativeBlockData> blocks = new ArrayList<>();
                JsonArray blocksArray = commitJson.getAsJsonArray("blocks");
                for (JsonElement blockElem : blocksArray) {
                    JsonObject blockObj = blockElem.getAsJsonObject();
                    blocks.add(new RelativeBlockData(
                        blockObj.get("rel_x").getAsInt(),
                        blockObj.get("rel_y").getAsInt(),
                        blockObj.get("rel_z").getAsInt(),
                        blockObj.get("block_name").getAsString(),
                        blockObj.get("block_state").getAsString()
                    ));
                }

                instance.pendingCommits.add(new CommitData(commitHash, commitName, message, timestamp, blocks));
            }
        }

        return instance;
    }
}
