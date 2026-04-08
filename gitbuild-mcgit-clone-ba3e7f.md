# GitBuild MCGit Clone Implementation Plan

Implement a Git-like version control system for Minecraft builds, modeled after MCGit, with all features except branching.

## Architecture Overview

**Backend (FastAPI + SQLite):**
- User management (auth, UUID)
- Repository metadata storage
- Commit history per repo
- Block data per commit (serialized chunks)
- Remote operations support (push/pull/clone)

**Minecraft Mod (NeoForge):**
- Git commands (init, add, commit, revert, etc.)
- Block change tracking (auto-add, auto-rm)
- HTTP client for backend communication
- Staging area management (client-side)
- World modification capabilities for revert/clone

**Website (HTML/CSS/JS):**
- User authentication (login/signup)
- Repository browser
- Commit history viewer
- 3D block visualization (three.js)
- Public repository exploration

## Backend API Extensions Needed

### Repository Management
- `POST /repos/{username}` - Create new repo
- `GET /repos/{username}` - List all repos for user
- `GET /repos/{username}/{repo_name}/exists` - Check if repo exists

### Commit Operations
- `POST /repos/{username}/{repo_name}/commits` - Create commit (exists, needs blocks)
- `GET /repos/{username}/{repo_name}/commits` - Get commit history
- `GET /repos/{username}/{repo_name}/commits/{hash}` - Get specific commit blocks

### Remote Operations
- `POST /repos/{username}/{repo_name}/push` - Push commits to remote
- `POST /repos/{username}/{repo_name}/pull` - Pull commits from remote
- `POST /repos/{username}/clone` - Clone remote repo
- `POST /repos/{username}/{repo_name}/fetch` - Fetch without merging

## Mod Commands to Implement

### Repository Commands
- `/git init <name>` - Initialize new repo (backend + local tracking)
- `/git activate <name>` - Switch active repo
- `/git repoList` - List all repos

### Staging Commands
- `/git add <x> <y> <z>` - Add single block
- `/git add <x1> <y1> <z1> <x2> <y2> <z2> [hollow|outline]` - Add cuboid
- `/git rm <x> <y> <z>` - Remove block from staging and revert
- `/git unstage ...` - Remove from staging without reverting
- `/git autoadd [on|off|toggle]` - Auto-track placed blocks
- `/git autorm [on|off|toggle]` - Auto-track broken blocks

### Commit Commands
- `/git commit [-m] <message>` - Commit staged changes
- `/git status` - Show staged/unstaged changes

### History Commands
- `/git log` - Show commit history
- `/git revert [hash]` - Revert world to commit
- `/git reset [hash]` - Reset HEAD without changing world

### Remote Commands
- `/git remote add <name> <url>` - Add remote
- `/git push [remote] [branch]` - Push to remote
- `/git pull [remote] [branch]` - Pull from remote
- `/git fetch [remote] [branch]` - Fetch from remote
- `/git clone <name> <url>` - Clone repo and apply to world
- `/git auth <username> [password]` - Store credentials

### Clone with Ghost Preview (TODO)
**Feature:** `/git clone <name> <url>` should show a ghost preview before applying

**Implementation Steps:**

1. **Modify clone command flow:**
   - Parse `username/repo` from URL
   - Fetch HEAD commit blocks from backend API
   - Don't clone repo metadata yet - just preview

2. **Calculate bounding box & offset:**
   ```java
   // Find min/max coordinates from commit blocks
   int minX = blocks.stream().mapToInt(b -> b.x).min();
   int minZ = blocks.stream().mapToInt(b -> b.z).min();
   
   // Calculate offset to center on player
   int offsetX = playerX - minX - (width / 2);
   int offsetY = playerY; // Keep Y at player level
   int offsetZ = playerZ - minZ - (depth / 2);
   ```

3. **Create ghost blocks for preview:**
   - Use existing `GhostBlockManager.addGhostBlock()`
   - make the blocks semi-transparent (0.5 alpha)
   - orange if block is in wrong place (overlapping existing blocks)
   - none if block is in correct place
   - Transform all block positions by offset
   - Store original positions in session for later apply
   - Send `ClientboundBlockUpdatePacket` to player only

4. **Player confirmation system:**
   - Show message: "§eGhost preview active. Type /git confirm to apply or /git cancel to abort"
   - Store pending clone in `PlayerSession.pendingClone`
   - Include: source username, source repo, new repo name, block offsets

5. **New commands:**
   - `/git confirm` - Apply the ghost preview to world
     - Clone repo to backend
     - Set blocks in world using stored offsets
     - Clear ghost blocks
   - `/git cancel` - Cancel ghost preview
     - Clear ghost blocks from `GhostBlockManager`
     - Clear pending clone from session

6. **Backend API changes:**
   - Add `GET /repos/{username}/{repo_name}/head-blocks` - Get latest commit blocks without cloning
   - Returns same format as `/commits/{hash}/blocks`

**Files to modify:**
- `GitCommand.java` - Add `executeClone()` ghost logic, new `/git confirm` and `/git cancel` commands
- `PlayerSession.java` - Add `pendingClone` field with offset data
- `BackendApiClient.java` - Add `getHeadBlocks()` method
- `main.py` - Add `GET /repos/{username}/{repo_name}/head-blocks` endpoint

## Data Models

### Client-Side (Minecraft)
```java
// Active session data
- currentRepo: String
- userUUID: String  
- username: String
- stagingArea: Map<BlockPos, BlockChange>
- autoAdd: boolean
- autoRm: boolean
- credentials: Map<remote, Credentials>
```

### Backend (SQLite)
```sql
-- Users (exists)
-- Repo metadata per user (exists)
-- Commits per repo (exists)
-- Need: Remotes table for remote URLs
-- Need: Better block storage (compressed chunks)
```

## Website Requirements

**Existing Pages:**
- `@c:\Users\leoaq\Documents\GitHub\VVSE-project\website\login\login.html` - User login
- `@c:\Users\leoaq\Documents\GitHub\VVSE-project\website\signup\signup.html` - User registration
- `@c:\Users\leoaq\Documents\GitHub\VVSE-project\website\homepage\homepage.html` - User dashboard

**Pages to Add:**

### Repository Browser
- List all user repositories (like GitHub profile)
- Show repository stats (commits, last modified)
- Create new repository button
- Delete repository option
- Clone repository button (url to copy into minecraft client)

### Commit History Viewer
- `/repo/{username}/{repo_name}` - Repository detail page
- List all commits with hash, message, timestamp
- Click commit to view 3D block preview
- Download/clone button

<!--not needed for now 
### Commit 3D Viewer
- `/commit/{username}/{repo_name}/{commit_hash}`
- Render blocks in 3D (three.js or similar)
- Rotate, zoom, pan controls
- Block count, dimensions info
- Export to schematic/Litematica -->

### Public Repository Search
- `/explore` - Browse public repositories
- Search by username/repo name
- Filter by most recent, most blocks, etc.


**Frontend Features:**
- JavaScript `checks.js` connects to `http://127.0.0.1:8000`
- Add repository API calls to `checks.js`:
  - `createRepo()`, `listRepos()`, `deleteRepo()`
  - `getCommits()`, `getCommitBlocks()`
  - `pushRepo()`, `pullRepo()`, `cloneRepo()`

## Implementation Phases

1. **Backend Foundation** - Extend API for all operations
2. **Repository & Staging** - Mod commands for init, add, status
3. **Commit & History** - Commit, log, revert implementation
4. **Remote Operations** - Push, pull, clone, fetch
5. **Website - Repository Browser** - Add repo list and create pages
6. **Website - Commit Viewer** - 3D block visualization
7. **Integration** - Connect mod to backend, test end-to-end

## Key Technical Decisions

- Block changes stored as (x,y,z,block_name,block_state)
- Staging area is client-side only (server just receives final commits)
- Revert uses setblock commands (requires gamemaster permissions)
- Clone/fetch create ghost blocks first, `/git put` applies them
- Auto-add/rm track via BlockEvent handlers
