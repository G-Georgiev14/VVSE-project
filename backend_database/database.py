from pathlib import Path
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from models import Base, RepoBase, CommitBase

BASE_DIR = Path(__file__).resolve().parent
DATA_ROOT = BASE_DIR / "database"

GLOBAL_DB_URL = f"sqlite:///{DATA_ROOT}/users.db"
global_engine = create_engine(GLOBAL_DB_URL, connect_args={"check_same_thread": False})
GlobalSessionLocal = sessionmaker(bind=global_engine)

def init_global_database():
    DATA_ROOT.mkdir(parents=True, exist_ok=True)
    Base.metadata.create_all(bind=global_engine)

def get_repo_metadata_session(username: str, repo_name: str):
    path = DATA_ROOT / username / repo_name
    path.mkdir(parents=True, exist_ok=True)

    database_file = path / "metadata.db"
    engine = create_engine(f"sqlite:///{database_file}")
    RepoBase.metadata.create_all(bind=engine)
    return sessionmaker(bind=engine)()

def get_commit_database_session(username: str, repo_name: str, commit_hash: str):
    path = DATA_ROOT / username / repo_name
    path.mkdir(parents=True, exist_ok=True)

    database_file = path / f"{commit_hash}.db"
    engine = create_engine(f"sqlite:///{database_file}")
    CommitBase.metadata.create_all(bind=engine)
    return sessionmaker(bind=engine)()
