import uvicorn
import os
from fastapi import FastAPI, Depends, HTTPException, Body
from sqlalchemy import exists, and_
from sqlalchemy.orm import Session
from pydantic import BaseModel
from typing import List
from fastapi.middleware.cors import CORSMiddleware

import database
import models

class BlockSchema(BaseModel):
    x: int
    y: int
    z: int
    block_name: str
    block_state: str

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

@app.post("/users/{username}")
def create_user(username: str, 
                email: str, 
                password: str, 
                minecraft_username: str,
                uuid: str,
                db: Session = Depends(get_database)):

    user = models.User(username=username,
                    email=email,
                    password=password,
                    minecraft_username=minecraft_username,
                    uuid = uuid)
    db.add(user)
    db.commit()
    return {"message": f"User {username} created"}

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
                      minecraft_username: str,
                      db: Session = Depends(get_database)):
    user_name_exists = db.query(exists().where(models.User.username == username)).scalar()
    if user_name_exists:
        return {"exists name": user_name_exists}
    user_email_exists = db.query(exists().where(models.User.email == email)).scalar()
    if user_email_exists:
        return {"exists email": user_email_exists}
    user_minecraft_name_exists = db.query(exists().where(models.User.minecraft_username == minecraft_username)).scalar()
    if user_minecraft_name_exists:
        return {"exists name": user_minecraft_name_exists}

@app.get("/users/uuid")
def check_for_valid_uuid(uuid: str, db: Session = Depends(get_database)):
    uuid_exists = db.query(exists().where(models.User.uuid == uuid)).scalar()

    return {"exists": uuid_exists}

if __name__ == "__main__":

    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=True)


