from sqlalchemy import Column, Integer, String, DateTime, Boolean
from sqlalchemy.orm import declarative_base
import datetime

Base = declarative_base()
RepoBase = declarative_base()
CommitBase = declarative_base()


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True,  autoincrement=True)
    username = Column(String, unique=True, index=True)
    email = Column(String, unique=True)
    password = Column(String)
    minecraft_username = Column(String, unique=True, index=True)
    uuid = Column(String, unique=True, index=True)

class Repo(Base):
    __tablename__ = "repos"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    name = Column(String)
    user_id = Column(Integer)

class RepoMetadata(RepoBase):
    __tablename__ = "commit_history"

    id = Column(Integer, primary_key=True, autoincrement=True)
    commit_name = Column(String)
    commit_hash = Column(String, unique=True)
    message = Column(String)
    time_stamp = Column(DateTime, default=datetime.datetime.utcnow)
    is_active = Column(Boolean, default=False)

class Commit(CommitBase):
    __tablename__ = "blocks"

    id = Column(Integer, primary_key=True, autoincrement=True)
    x = Column(Integer)
    y = Column(Integer)
    z = Column(Integer)
    block_name = Column(String)
    block_state = Column(String)

