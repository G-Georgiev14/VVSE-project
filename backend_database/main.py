import uvicorn
import os
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

app = FastAPI()

app.add_middleware(CORSMiddleware, 
                   allow_origins=["*"],
                   allow_methods=["*"],
                   allow_headers=["*"])

database.init_global_database()

@app.get("/")
def home():
    return {"message": "Server is running"}

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

    user = models.User(
        username=user_data.username,
        email=user_data.email,
        password=user_data.password, 
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
        raise HTTPException(status_code=404, detail="User doesnt exist")
    
    commit_database = database.get_commit_database_session(username, repo_name, commit_name)

    new_block = [models.Commit(**b.model_dump()) for b in blocks]
    commit_database.add_all(new_block)
    commit_database.commit()
    commit_database.close()

    meta_datebase = database.get_repo_metadata_session(username, repo_name)

    meta_datebase.query(models.RepoMetadata).update({models.RepoMetadata.is_active:False})

    new_history = models.RepoMetadata(
        commit_name = commit_name,
        commit_hash = commit_hash,
        message = message,
        is_active = True
    )

    meta_datebase.add(new_history)
    meta_datebase.commit()

    meta_datebase.close()

    return {"status": "commited", "commit_name": commit_name, "hash": commit_hash}

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

@app.get("/users/exists")
def check_user_exists(username: str,
                      email: str,
                      db: Session = Depends(get_database)):
    
    user_name_exists = db.query(exists().where(models.User.username == username)).scalar()

    if user_name_exists:
        return {"name": True}
    
    user_email_exists = db.query(exists().where(models.User.email == email)).scalar()
    if user_email_exists:
        return {"email": True}

    return{"exists": False}

@app.post("/login")
def login(request: LoginRequest, db: Session = Depends(get_database)):
    user = db.query(models.User).filter(models.User.username == request.username).first()

    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    if user.password != request.password:
        raise HTTPException(status_code=401, detail="Invalid password")
    
    return{
        "status": "successful",
        "username": user.username,
        "minecraft_username": user.minecraft_username,
        "uuid": user.uuid
    }

@app.get("/users/uuid")
def check_for_valid_uuid(uuid: str, db: Session = Depends(get_database)):
    exists = db.query(exists().where(models.User.uuid == uuid)).scalar()
    return exists
    

if __name__ == "__main__":

    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=True)


