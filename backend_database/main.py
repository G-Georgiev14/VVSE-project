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

app.add_middleware(
    CORSMiddleware, 
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"]
)

database.init_global_database()


def generate_hash(username: str, password: str) -> str:
    """Generate SHA-256 hash of username + password (same as frontend)"""
    data = (username + password).encode('utf-8')
    hash_object = hashlib.sha256(data)
    return hash_object.hexdigest()


@app.get("/db-check")
def db_check():
    return {"server": True}


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
    
    commit_database = database.get_commit_database_session(username, repo_name, commit_name)

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

    return history


# Repository Management Endpoints

@app.post("/repos/{username}")
def create_repo(username: str, repo_name: str, uuid: str, db: Session = Depends(get_database)):
    """Initialize a new repository for a user"""
    user_check = db.query(exists().where(and_(models.User.uuid == uuid, models.User.username == username))).scalar()
    if not user_check:
        raise HTTPException(status_code=404, detail="User doesn't exist")
    
    # Create repo directory structure
    import os
    repo_path = os.path.join(database.DATA_ROOT, username, repo_name)
    os.makedirs(repo_path, exist_ok=True)
    
    # Initialize metadata database
    meta_db = database.get_repo_metadata_session(username, repo_name)
    meta_db.close()
    
    return {"status": "success", "repo_name": repo_name, "message": "Repository initialized"}


@app.get("/repos/{username}")
def list_repos(username: str, uuid: str, db: Session = Depends(get_database)):
    """List all repositories for a user"""
    user_check = db.query(exists().where(and_(models.User.uuid == uuid, models.User.username == username))).scalar()
    if not user_check:
        raise HTTPException(status_code=404, detail="User doesn't exist")
    
    user_path = os.path.join(database.DATA_ROOT, username)
    if not os.path.exists(user_path):
        return {"repos": []}
    
    repos = []
    for item in os.listdir(user_path):
        item_path = os.path.join(user_path, item)
        if os.path.isdir(item_path):
            # Check if it has a metadata.db (is a valid repo)
            if os.path.exists(os.path.join(item_path, "metadata.db")):
                repos.append(item)
    
    return {"repos": repos}


@app.get("/repos/{username}/{repo_name}/exists")
def repo_exists(username: str, repo_name: str):
    """Check if a repository exists"""
    import os
    repo_path = os.path.join(database.DATA_ROOT, username, repo_name)
    exists_flag = os.path.exists(repo_path) and os.path.exists(os.path.join(repo_path, "metadata.db"))
    return {"exists": exists_flag}


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

if __name__ == "__main__":

    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=True)


