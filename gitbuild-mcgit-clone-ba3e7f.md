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

## Implementation Phases

1. **Backend Foundation** - Extend API for all operations
2. **Repository & Staging** - Mod commands for init, add, status
3. **Commit & History** - Commit, log, revert implementation
4. **Remote Operations** - Push, pull, clone, fetch
5. **Integration** - Connect mod to backend, test end-to-end

## Key Technical Decisions

- Block changes stored as (x,y,z,block_name,block_state)
- Staging area is client-side only (server just receives final commits)
- Revert uses setblock commands (requires gamemaster permissions)
- Clone/fetch create ghost blocks first, `/git put` applies them
- Auto-add/rm track via BlockEvent handlers
