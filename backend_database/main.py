import uvicorn
import os
import hashlib
from fastapi import FastAPI, Depends, HTTPException, Body
from sqlalchemy import exists, and_
from sqlalchemy.orm import Session
from pydantic import BaseModel, EmailStr
from typing import List
import uuid
from fastapi.middleware.cors import CORSMiddleware

import database
import models

class BlockSchema(BaseModel):
    x: int
    y: int
    z: int
    block_name: str
    block_state: str

class UserCreate(BaseModel):
    username: str
    email: str
    password: str
    minecraft_username: str

class LoginRequest(BaseModel):
    username: str
    password: str

class CheckRequest(BaseModel):
    username: str
    email: str

app = FastAPI()

# Simple CORS configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Allow all origins for development
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

try:
    print("Initializing database...")
    database.init_global_database()
    print("Database initialized successfully!")
except Exception as e:
    print(f"Database initialization error: {e}")
    raise


def generate_hash(username: str, password: str) -> str:
    """Generate SHA-256 hash of username + password (same as frontend)"""
    data = (username + password).encode('utf-8')
    hash_object = hashlib.sha256(data)
    return hash_object.hexdigest()


@app.get("/db-check")
def db_check():
    return {"server": True}

@app.get("/test")
def test_endpoint():
    return {"message": "Server is working correctly", "cors": "enabled"}


def get_database():
    db = database.GlobalSessionLocal()
    try:
        yield db
    finally:
        db.close()


@app.post("/users")
def create_user(user_data: UserCreate,
                db: Session = Depends(get_database)):

    existing_user = db.query(models.User).filter(
        (models.User.username == user_data.username) |
        (models.User.email == user_data.email)
    ).first()
    
    if existing_user:
        raise HTTPException(status_code=400, detail="Username or Email already exist")
    
    new_uuid = str(uuid.uuid4())
    while db.query(exists().where(models.User.uuid == new_uuid)).scalar():
        new_uuid = str(uuid.uuid4())

    hashed_password = generate_hash(user_data.username, user_data.password)
    
    user = models.User(
        username=user_data.username,
        email=user_data.email,
        password=hashed_password, 
        minecraft_username=user_data.minecraft_username,
        uuid=new_uuid
    )

    db.add(user)
    db.commit()
    db.refresh(user)

    return {"status": "success", "username": user.username, "minecraft_username": user.minecraft_username, "uuid": user.uuid}


@app.post("/users/{username}/{repo_name}/commit")
def create_commit(username: str,
                  uuid: str, 
                  repo_name: str,
                  commit_name: str, 
                  commit_hash: str,
                  message: str, 
                  blocks: List[BlockSchema] = Body(...),
                  db: Session = Depends(get_database)):
    
    user_check = db.query(exists().where(and_(models.User.uuid == uuid, models.User.username == username))).scalar()

    if not user_check:
        raise HTTPException(status_code=404, detail="User doesn't exist")
    
    commit_database = database.get_commit_database_session(username, repo_name, commit_hash)

    new_block = [models.Commit(**b.model_dump()) for b in blocks]
    commit_database.add_all(new_block)
    commit_database.commit()
    commit_database.close()

    meta_database = database.get_repo_metadata_session(username, repo_name)

    meta_database.query(models.RepoMetadata).update({models.RepoMetadata.is_active:False})

    new_history = models.RepoMetadata(
        commit_name = commit_name,
        commit_hash = commit_hash,
        message = message,
        is_active = True
    )

    meta_database.add(new_history)
    meta_database.commit()

    meta_database.close()

    return {"status": "committed", "commit_name": commit_name, "hash": commit_hash}


@app.post("/users/{username}/{repo_name}/reset-hard")
def reset_hard(username: str,
               repo_name: str, 
               target_hash: str):
    
    meta_database = database.get_repo_metadata_session(username, repo_name)

    target = meta_database.query(models.RepoMetadata).filter_by(commit_hash=target_hash).first()
    if not target:
        raise HTTPException(status_code=404, detail="Commit not found")
    
    to_delete = meta_database.query(models.RepoMetadata).filter(models.RepoMetadata.time_stamp > target.time_stamp).all()

    for item in to_delete:
        file_path = os.path.join(database.DATA_ROOT, username, repo_name, f"{item.commit_hash}.db")
        if os.path.exists(file_path):
            os.remove(file_path)
        meta_database.delete(item)
    
    meta_database.query(models.RepoMetadata).update({models.RepoMetadata.is_active: False})
    target.is_active = True

    meta_database.commit()
    meta_database.close()

    return {"message": f"Hard reset to {target_hash}"}


@app.get("/users/{username}/{repo_name}/log")
def get_log(username: str, repo_name: str):

    meta_database = database.get_repo_metadata_session(username, repo_name)
    history = meta_database.query(models.RepoMetadata).order_by(models.RepoMetadata.time_stamp.desc()).all()
    meta_database.close()

    return [
        {
            "commit_name": h.commit_name,
            "commit_hash": h.commit_hash,
            "message": h.message,
            "time_stamp": h.time_stamp.isoformat() if h.time_stamp else None,
            "is_active": h.is_active
        }
        for h in history
    ]


# Repository Management Endpoints

@app.post("/repos/{username}")
def create_repo(username: str, repo_name: str, uuid: str, visibility: str = 'public', db: Session = Depends(get_database)):
    """Create a new repository"""
    # Verify user exists and owns the repository
    user_check = db.query(exists().where(and_(models.User.uuid == uuid, models.User.username == username))).scalar()
    if not user_check:
        raise HTTPException(status_code=404, detail="User doesn't exist")
    
    # Get the user ID
    user = db.query(models.User).filter(models.User.uuid == uuid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    # Check if repository already exists
    existing_repo = db.query(models.Repo).filter(and_(
        models.Repo.name == repo_name,
        models.Repo.creator_id == user.id
    )).first()
    
    if existing_repo:
        raise HTTPException(status_code=400, detail="Repository already exists")
    
    # Create repository record in database
    new_repo = models.Repo(
        name=repo_name,
        creator_id=user.id,
        visibility=visibility
    )
    
    db.add(new_repo)
    db.commit()
    db.refresh(new_repo)
    
    # Create repo directory structure
    import os
    repo_path = os.path.join(database.DATA_ROOT, username, repo_name)
    os.makedirs(repo_path, exist_ok=True)
    
    # Initialize metadata database
    meta_db = database.get_repo_metadata_session(username, repo_name)
    meta_db.close()
    
    return {"status": "success", "repo_name": repo_name, "message": "Repository initialized", "repo_id": new_repo.id}


@app.get("/repos/{username}")
def list_repos(username: str, uuid: str, db: Session = Depends(get_database)):
    """List all repositories for a user"""
    user_check = db.query(exists().where(and_(models.User.uuid == uuid, models.User.username == username))).scalar()
    if not user_check:
        raise HTTPException(status_code=404, detail="User doesn't exist")
    
    # Get the user ID
    user = db.query(models.User).filter(models.User.uuid == uuid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    # Get repositories from database
    repos = db.query(models.Repo).filter(models.Repo.creator_id == user.id).all()
    
    return {"repos": [{"name": repo.name, "visibility": repo.visibility, "stars": repo.stars} for repo in repos]}


@app.get("/public-repos")
def list_all_public_repos(db: Session = Depends(get_database)):
    """List all public repositories from all users (no authentication required)"""
    # Get all public repositories from database
    public_repos = db.query(models.Repo).filter(models.Repo.visibility == 'public').all()
    
    # Get creator usernames for each repo
    result = []
    for repo in public_repos:
        creator = db.query(models.User).filter(models.User.id == repo.creator_id).first()
        if creator:
            result.append({
                "name": repo.name,
                "visibility": repo.visibility,
                "username": creator.username,
                "stars": repo.stars
            })

    return {"repos": result}


@app.get("/repos/{username}/{repo_name}/exists")
def repo_exists(username: str, repo_name: str):
    """Check if a repository exists"""
    import os
    repo_path = os.path.join(database.DATA_ROOT, username, repo_name)
    exists_flag = os.path.exists(repo_path) and os.path.exists(os.path.join(repo_path, "metadata.db"))
    return {"exists": exists_flag}


@app.delete("/repos/{username}/{repo_name}")
def delete_repo(username: str, repo_name: str, uuid: str, db: Session = Depends(get_database)):
    """Delete a repository and all its data"""
    # Verify user exists and owns the repository
    user_check = db.query(exists().where(and_(models.User.uuid == uuid, models.User.username == username))).scalar()
    if not user_check:
        raise HTTPException(status_code=404, detail="User doesn't exist")
    
    # Get the user ID
    user = db.query(models.User).filter(models.User.uuid == uuid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    # Find the repository
    repo = db.query(models.Repo).filter(
        and_(models.Repo.name == repo_name, models.Repo.creator_id == user.id)
    ).first()
    
    if not repo:
        raise HTTPException(status_code=404, detail="Repository not found")
    
    import os
    import shutil
    import gc
    import time
    
    repo_path = os.path.join(database.DATA_ROOT, username, repo_name)
    
    try:
        # Close all database connections for this repository first
        # This is crucial to prevent file locking issues
        close_repository_connections(username, repo_name)
        
        # Force garbage collection to ensure connections are closed
        gc.collect()
        
        # Multiple attempts with increasing delays
        max_attempts = 3
        for attempt in range(max_attempts):
            try:
                # Additional delay for subsequent attempts
                if attempt > 0:
                    time.sleep(0.5 * attempt)
                
                # Try to delete the repository directory
                if os.path.exists(repo_path):
                    shutil.rmtree(repo_path)
                
                # If we get here, deletion succeeded
                break
                
            except (OSError, PermissionError) as e:
                if attempt == max_attempts - 1:
                    # Last attempt failed, try alternative method
                    try:
                        force_delete_repository(repo_path)
                    except Exception as alt_e:
                        raise Exception(f"Failed to delete repository after {max_attempts} attempts: {str(e)}. Alternative method also failed: {str(alt_e)}")
                else:
                    # Retry after closing connections again
                    close_repository_connections(username, repo_name)
                    gc.collect()
        
        # Delete from database
        db.delete(repo)
        db.commit()
        
        return {"status": "deleted", "repo_name": repo_name, "message": "Repository deleted successfully"}
        
    except Exception as e:
        db.rollback()
        raise HTTPException(status_code=500, detail=f"Failed to delete repository: {str(e)}")

def force_delete_repository(repo_path):
    """Force delete repository using alternative methods"""
    import stat
    import tempfile
    
    if not os.path.exists(repo_path):
        return
    
    # Method 1: Try to change file permissions and delete
    try:
        for root, dirs, files in os.walk(repo_path):
            for file in files:
                file_path = os.path.join(root, file)
                try:
                    # Remove read-only attribute
                    os.chmod(file_path, stat.S_IWRITE | stat.S_IREAD)
                except:
                    pass
        shutil.rmtree(repo_path)
        return
    except:
        pass
    
    # Method 2: Try moving files to temp directory first
    try:
        temp_dir = tempfile.mkdtemp()
        for item in os.listdir(repo_path):
            try:
                shutil.move(os.path.join(repo_path, item), temp_dir)
            except:
                pass
        shutil.rmtree(repo_path)
        shutil.rmtree(temp_dir)
        return
    except:
        pass
    
    # Method 3: Last resort - individual file deletion
    try:
        for root, dirs, files in os.walk(repo_path, topdown=False):
            for file in files:
                try:
                    file_path = os.path.join(root, file)
                    os.chmod(file_path, stat.S_IWRITE | stat.S_IREAD)
                    os.remove(file_path)
                except:
                    pass
            for dir in dirs:
                try:
                    dir_path = os.path.join(root, dir)
                    os.rmdir(dir_path)
                except:
                    pass
        os.rmdir(repo_path)
    except:
        pass

def close_repository_connections(username: str, repo_name: str):
    """Close all active database connections for a specific repository"""
    # This function helps ensure all SQLite connections are closed before deletion
    try:
        # Force close all possible database connections
        repo_dir = os.path.join(database.DATA_ROOT, username, repo_name)
        if os.path.exists(repo_dir):
            for file in os.listdir(repo_dir):
                if file.endswith(".db"):
                    db_path = os.path.join(repo_dir, file)
                    try:
                        # Try to open and close the database to force connection release
                        import sqlite3
                        conn = sqlite3.connect(db_path)
                        conn.close()
                    except:
                        pass
                    
                    try:
                        # Also try with SQLAlchemy engine
                        temp_engine = database.create_engine(f"sqlite:///{db_path}")
                        temp_engine.dispose()
                    except:
                        pass
    except Exception as e:
        print(f"Warning: Could not close some database connections: {e}")
        # Continue with deletion even if connection closing fails


@app.get("/repos/{username}/{repo_name}/commits/{commit_hash}/blocks")
def get_commit_blocks(username: str, repo_name: str, commit_hash: str):
    """Get all blocks for a specific commit"""
    import os
    db_path = os.path.join(database.DATA_ROOT, username, repo_name, f"{commit_hash}.db")
    if not os.path.exists(db_path):
        raise HTTPException(status_code=404, detail="Commit not found")
    
    commit_db = database.get_commit_database_session(username, repo_name, commit_hash)
    blocks = commit_db.query(models.Commit).all()
    commit_db.close()
    
    return [{"x": b.x, "y": b.y, "z": b.z, "block_name": b.block_name, "block_state": b.block_state} for b in blocks]


@app.get("/repos/{username}/{repo_name}/head-blocks")
def get_head_blocks(username: str, repo_name: str):
    """Get blocks from the latest commit (HEAD) without cloning"""
    import os
    repo_path = os.path.join(database.DATA_ROOT, username, repo_name)
    if not os.path.exists(repo_path) or not os.path.exists(os.path.join(repo_path, "metadata.db")):
        raise HTTPException(status_code=404, detail="Repository not found")
    
    # Get the active commit (HEAD)
    meta_db = database.get_repo_metadata_session(username, repo_name)
    head_commit = meta_db.query(models.RepoMetadata).filter(models.RepoMetadata.is_active == True).first()
    meta_db.close()
    
    if not head_commit:
        raise HTTPException(status_code=404, detail="No commits found in repository")
    
    # Get blocks from the HEAD commit
    commit_db = database.get_commit_database_session(username, repo_name, head_commit.commit_hash)
    blocks = commit_db.query(models.Commit).all()
    commit_db.close()
    
    return {
        "commit_hash": head_commit.commit_hash,
        "commit_name": head_commit.commit_name,
        "message": head_commit.message,
        "blocks": [{"x": b.x, "y": b.y, "z": b.z, "block_name": b.block_name, "block_state": b.block_state} for b in blocks]
    }


# Remote Operations Endpoints

class PushRequest(BaseModel):
    remote_username: str
    remote_repo: str
    uuid: str

@app.post("/repos/{username}/{repo_name}/push")
def push_repo(username: str, repo_name: str, request: PushRequest, db: Session = Depends(get_database)):
    """Push commits to a remote repository (simulated by copying commits)"""
    user_check = db.query(exists().where(and_(models.User.uuid == request.uuid, models.User.username == username))).scalar()
    if not user_check:
        raise HTTPException(status_code=404, detail="User doesn't exist")
    
    # In a real implementation, this would push to actual remote
    # For now, we simulate by returning success
    return {"status": "pushed", "remote": f"{request.remote_username}/{request.remote_repo}"}


class PullRequest(BaseModel):
    remote_username: str
    remote_repo: str
    uuid: str

@app.post("/repos/{username}/{repo_name}/pull")
def pull_repo(username: str, repo_name: str, request: PullRequest, db: Session = Depends(get_database)):
    """Pull commits from a remote repository"""
    user_check = db.query(exists().where(and_(models.User.uuid == request.uuid, models.User.username == username))).scalar()
    if not user_check:
        raise HTTPException(status_code=404, detail="User doesn't exist")
    
    # Get remote commits
    remote_meta = database.get_repo_metadata_session(request.remote_username, request.remote_repo)
    remote_commits = remote_meta.query(models.RepoMetadata).order_by(models.RepoMetadata.time_stamp).all()
    remote_meta.close()
    
    # Copy commits to local
    local_meta = database.get_repo_metadata_session(username, repo_name)
    for commit in remote_commits:
        # Check if already exists
        existing = local_meta.query(models.RepoMetadata).filter_by(commit_hash=commit.commit_hash).first()
        if not existing:
            new_commit = models.RepoMetadata(
                commit_name=commit.commit_name,
                commit_hash=commit.commit_hash,
                message=commit.message,
                is_active=False
            )
            local_meta.add(new_commit)
            
            # Copy block data
            import shutil
            import os
            source_db = os.path.join(database.DATA_ROOT, request.remote_username, request.remote_repo, f"{commit.commit_hash}.db")
            dest_db = os.path.join(database.DATA_ROOT, username, repo_name, f"{commit.commit_hash}.db")
            if os.path.exists(source_db):
                shutil.copy2(source_db, dest_db)
    
    local_meta.commit()
    local_meta.close()
    
    return {"status": "pulled", "commits_added": len(remote_commits)}


class CloneRequest(BaseModel):
    source_username: str
    source_repo: str
    new_repo_name: str
    uuid: str

@app.post("/repos/{username}/clone")
def clone_repo(username: str, request: CloneRequest, db: Session = Depends(get_database)):
    """Clone a remote repository"""
    user_check = db.query(exists().where(and_(models.User.uuid == request.uuid, models.User.username == username))).scalar()
    if not user_check:
        raise HTTPException(status_code=404, detail="User doesn't exist")
    
    import os
    import shutil
    
    # Create new repo
    repo_path = os.path.join(database.DATA_ROOT, username, request.new_repo_name)
    os.makedirs(repo_path, exist_ok=True)
    
    # Copy metadata
    source_meta_path = os.path.join(database.DATA_ROOT, request.source_username, request.source_repo, "metadata.db")
    dest_meta_path = os.path.join(repo_path, "metadata.db")
    if os.path.exists(source_meta_path):
        shutil.copy2(source_meta_path, dest_meta_path)
    
    # Copy all commit databases
    source_path = os.path.join(database.DATA_ROOT, request.source_username, request.source_repo)
    if os.path.exists(source_path):
        for file in os.listdir(source_path):
            if file.endswith(".db") and file != "metadata.db":
                shutil.copy2(os.path.join(source_path, file), os.path.join(repo_path, file))
    
    return {"status": "cloned", "repo_name": request.new_repo_name, "source": f"{request.source_username}/{request.source_repo}"}


@app.post("/repos/{username}/{repo_name}/fetch")
def fetch_repo(username: str, repo_name: str, request: PullRequest, db: Session = Depends(get_database)):
    """Fetch commits from remote without merging (same as pull for now)"""
    return pull_repo(username, repo_name, request, db)
#↓USELESS MAYBE??????????
@app.get("/users/exists")
def check_user_exists(check_user: CheckRequest,
                      db: Session = Depends(get_database)):
    
    user_name_exists = db.query(exists().where(models.User.username == check_user.username)).scalar()

    if user_name_exists:
        return {"name": True}
    
    user_email_exists = db.query(exists().where(models.User.email == check_user.email)).scalar()
    if user_email_exists:
        return {"email": True}

    return {"exists": False}
#↑USELESS MAYBE??????????

@app.post("/login")
def login(request: LoginRequest, db: Session = Depends(get_database)):
    user = db.query(models.User).filter(models.User.username == request.username).first()

    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    # Frontend already sends hashed password, compare directly with stored hash
    if user.password != request.password:
        raise HTTPException(status_code=401, detail="Invalid password")
    
    return {
        "status": "successful",
        "username": user.username,
        "minecraft_username": user.minecraft_username,
        "uuid": user.uuid
    }

@app.post("/repos/{username}/{repo_name}/star")
def star_repo(username: str, repo_name: str, uuid: str, db: Session = Depends(get_database)):
    """Star a repository - user can only star once"""
    # Verify user exists
    user = db.query(models.User).filter(models.User.uuid == uuid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    # Find the repository owner
    repo_owner = db.query(models.User).filter(models.User.username == username).first()
    if not repo_owner:
        raise HTTPException(status_code=404, detail="Repository owner not found")

    # Find the repository
    repo = db.query(models.Repo).filter(
        and_(models.Repo.name == repo_name, models.Repo.creator_id == repo_owner.id)
    ).first()

    if not repo:
        raise HTTPException(status_code=404, detail="Repository not found")

    # Check if user already starred this repo
    existing_star = db.query(models.Star).filter(
        and_(models.Star.user_id == user.id, models.Star.repo_id == repo.id)
    ).first()

    if existing_star:
        raise HTTPException(status_code=400, detail="You have already starred this repository")

    # Create star record
    new_star = models.Star(user_id=user.id, repo_id=repo.id)
    db.add(new_star)

    # Increment stars count
    repo.stars = (repo.stars or 0) + 1
    db.commit()

    return {"status": "success", "stars": repo.stars, "starred": True}

@app.delete("/repos/{username}/{repo_name}/star")
def unstar_repo(username: str, repo_name: str, uuid: str, db: Session = Depends(get_database)):
    """Unstar a repository - only if user has starred it"""
    # Verify user exists
    user = db.query(models.User).filter(models.User.uuid == uuid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    # Find the repository owner
    repo_owner = db.query(models.User).filter(models.User.username == username).first()
    if not repo_owner:
        raise HTTPException(status_code=404, detail="Repository owner not found")

    # Find the repository
    repo = db.query(models.Repo).filter(
        and_(models.Repo.name == repo_name, models.Repo.creator_id == repo_owner.id)
    ).first()

    if not repo:
        raise HTTPException(status_code=404, detail="Repository not found")

    # Check if user has starred this repo
    existing_star = db.query(models.Star).filter(
        and_(models.Star.user_id == user.id, models.Star.repo_id == repo.id)
    ).first()

    if not existing_star:
        raise HTTPException(status_code=400, detail="You have not starred this repository")

    # Remove star record
    db.delete(existing_star)

    # Decrement stars count (minimum 0)
    repo.stars = max((repo.stars or 0) - 1, 0)
    db.commit()

    return {"status": "success", "stars": repo.stars, "starred": False}

@app.get("/repos/{username}/{repo_name}/starred")
def check_starred(username: str, repo_name: str, uuid: str, db: Session = Depends(get_database)):
    """Check if the current user has starred a repository"""
    # Verify user exists
    user = db.query(models.User).filter(models.User.uuid == uuid).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    # Find the repository owner
    repo_owner = db.query(models.User).filter(models.User.username == username).first()
    if not repo_owner:
        raise HTTPException(status_code=404, detail="Repository owner not found")

    # Find the repository
    repo = db.query(models.Repo).filter(
        and_(models.Repo.name == repo_name, models.Repo.creator_id == repo_owner.id)
    ).first()

    if not repo:
        raise HTTPException(status_code=404, detail="Repository not found")

    # Check if user has starred this repo
    existing_star = db.query(models.Star).filter(
        and_(models.Star.user_id == user.id, models.Star.repo_id == repo.id)
    ).first()

    return {"starred": existing_star is not None}

if __name__ == "__main__":

    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=True)


