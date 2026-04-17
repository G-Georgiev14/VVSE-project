package com.sanguine.gitbuild;

import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class BackendApiClient {
    private static final String BASE_URL = "http://127.0.0.1:8000";
    private static final int TIMEOUT_SECONDS = 10;
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static class ApiResponse {
        public final boolean success;
        public final String message;
        public final JsonElement data;

        public ApiResponse(boolean success, String message, JsonElement data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
    }

    public static class BlockData {
        public int x;
        public int y;
        public int z;
        public String block_name;
        public String block_state;

        public BlockData(int x, int y, int z, String blockName, String blockState) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block_name = blockName;
            this.block_state = blockState;
        }
    }

    // Helper method for GET requests
    private static ApiResponse get(String endpoint) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + endpoint))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonElement data = JsonParser.parseString(response.body());
                return new ApiResponse(true, "Success", data);
            } else {
                return new ApiResponse(false, "HTTP " + response.statusCode() + ": " + response.body(), null);
            }
        } catch (Exception e) {
            GitBuildMod.LOGGER.error("API GET request failed: {}", endpoint, e);
            return new ApiResponse(false, "Error: " + e.getMessage(), null);
        }
    }

    // Helper method for POST requests
    private static ApiResponse post(String endpoint, Object body) {
        try {
            String jsonBody = gson.toJson(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + endpoint))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                JsonElement data = response.body().isEmpty() ? null : JsonParser.parseString(response.body());
                return new ApiResponse(true, "Success", data);
            } else {
                return new ApiResponse(false, "HTTP " + response.statusCode() + ": " + response.body(), null);
            }
        } catch (Exception e) {
            GitBuildMod.LOGGER.error("API POST request failed: {}", endpoint, e);
            return new ApiResponse(false, "Error: " + e.getMessage(), null);
        }
    }

    // User authentication
    public static ApiResponse login(String username, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        return post("/login", body);
    }

    // Repository operations
    public static ApiResponse createRepo(String username, String repoName, String uuid, String visibility) {
        String endpoint = String.format("/repos/%s?repo_name=%s&uuid=%s&visibility=%s", username, repoName, uuid, visibility);
        return post(endpoint, new HashMap<>());
    }

    public static ApiResponse listRepos(String username, String uuid) {
        return get(String.format("/repos/%s?uuid=%s", username, uuid));
    }

    public static ApiResponse repoExists(String username, String repoName) {
        return get(String.format("/repos/%s/%s/exists", username, repoName));
    }

    // Commit operations
    public static ApiResponse createCommit(String username, String repoName, String uuid, 
                                              String commitName, String commitHash, 
                                              String message, List<BlockData> blocks) {
        String endpoint = String.format("/users/%s/%s/commit?uuid=%s&commit_name=%s&commit_hash=%s&message=%s",
                username, repoName, uuid, commitName, commitHash, message);
        return post(endpoint, blocks);
    }

    public static ApiResponse getLog(String username, String repoName) {
        return get(String.format("/users/%s/%s/log", username, repoName));
    }

    public static ApiResponse getCommitBlocks(String username, String repoName, String commitHash) {
        return get(String.format("/repos/%s/%s/commits/%s/blocks", username, repoName, commitHash));
    }

    public static ApiResponse getHeadBlocks(String username, String repoName) {
        return get(String.format("/repos/%s/%s/head-blocks", username, repoName));
    }

    public static ApiResponse hardReset(String username, String repoName, String targetHash) {
        String endpoint = String.format("/users/%s/%s/reset-hard?target_hash=%s", username, repoName, targetHash);
        return post(endpoint, new HashMap<>());
    }

    // Remote operations
    public static ApiResponse push(String username, String repoName, String uuid,
                                    String remoteUsername, String remoteRepo) {
        Map<String, String> body = new HashMap<>();
        body.put("remote_username", remoteUsername);
        body.put("remote_repo", remoteRepo);
        body.put("uuid", uuid);
        return post(String.format("/repos/%s/%s/push", username, repoName), body);
    }

    public static ApiResponse pull(String username, String repoName, String uuid,
                                    String remoteUsername, String remoteRepo) {
        Map<String, String> body = new HashMap<>();
        body.put("remote_username", remoteUsername);
        body.put("remote_repo", remoteRepo);
        body.put("uuid", uuid);
        return post(String.format("/repos/%s/%s/pull", username, repoName), body);
    }

    public static ApiResponse fetch(String username, String repoName, String uuid,
                                     String remoteUsername, String remoteRepo) {
        Map<String, String> body = new HashMap<>();
        body.put("remote_username", remoteUsername);
        body.put("remote_repo", remoteRepo);
        body.put("uuid", uuid);
        return post(String.format("/repos/%s/%s/fetch", username, repoName), body);
    }

    public static ApiResponse cloneRepo(String username, String uuid,
                                        String sourceUsername, String sourceRepo, String newRepoName) {
        Map<String, String> body = new HashMap<>();
        body.put("source_username", sourceUsername);
        body.put("source_repo", sourceRepo);
        body.put("new_repo_name", newRepoName);
        body.put("uuid", uuid);
        return post(String.format("/repos/%s/clone", username), body);
    }

    // Health check
    public static boolean isServerOnline() {
        try {
            ApiResponse response = get("/db-check");
            return response.success;
        } catch (Exception e) {
            return false;
        }
    }
}
