# GitBuild

> **Git for Minecraft Builds** — Version control meets block placement. Save, track, and collaborate on your Minecraft creations with the power of Git-like workflows.

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.1-green.svg)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.1.0.19--beta-orange.svg)](https://neoforged.net)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0beta-yellow.svg)]()

![GitBuild Demo](demo/Demo.mp4)

---

## ✨ What is GitBuild?

GitBuild brings the power of **version control** to Minecraft. Just like Git tracks code changes, GitBuild tracks every block you place, break, or modify — giving you the freedom to experiment, collaborate, and never lose your progress.

### Key Features

| Feature | Description |
|---------|-------------|
| 🔄 **Auto-Tracking** | Automatically stage every block you place or break — no manual work needed |
| 👻 **Ghost Blocks** | Visual guides show exactly which blocks are missing when restoring builds |
| 📍 **Build Instances** | Create the same build in multiple locations, dimensions, or worlds |
| 🔍 **Clone Preview** | Preview builds with rotation before placing them — see it before you build it |
| ⭐ **Star Repositories** | Discover and star public builds from the community |
| 🌐 **Web Interface** | Browse, manage, and showcase your builds through the website |

---

## 🚀 Quick Start

### 1. Start the Backend
```bash
cd backend_database
pip install -r requirements.txt
python main.py
```
The API server starts at `http://localhost:8000`

### 2. Launch the Website
Open `website/homepage/homepage.html` in your browser or serve it with any static file server.

### 3. Install the Mod
1. Build the mod: `cd gitbuild && ./gradlew build`
2. Copy the `.jar` from `gitbuild/build/libs/` to your Minecraft `mods` folder
3. Launch Minecraft with NeoForge 26.1.0.19-beta

### 4. In-Game Setup
```
/git auth <username> <password>   # Log in
/git init public MyFirstBuild     # Create a repository
/git activate MyFirstBuild        # Start tracking
```

---

## 🎮 How It Works

### Automatic Tracking (Set It and Forget It)
Once you activate a repository, GitBuild automatically tracks your changes:
- **Place a block** → Auto-staged ✓
- **Break a block** → Auto-staged ✓
- **Run `/git status`** → See what's changed
- **Run `/git commit -m "My message"`** → Save your progress

### Build Instances
Build the same creation in multiple places:
- Build a castle in the Overworld
- Build the same castle in the Nether
- Build it again 1000 blocks away
- All tracked under one repository!

### Clone with Preview
Want to copy someone's build? Preview it first:
```
/git clone                          # Start preview
/git clone rotate 90               # Rotate the preview
/git clone anchor                  # Set placement point
/git clone confirm                 # Build it! (OP only)
```

---

## 📚 Common Commands

### Authentication
| Command | Description |
|---------|-------------|
| `/git auth <user> [pass]` | Log in to GitBuild |

### Repository Management
| Command | Description |
|---------|-------------|
| `/git init public <name>` | Create public repository |
| `/git init private <name>` | Create private repository |
| `/git activate <name>` | Switch to repository |
| `/git repoList` | List your repositories |
| `/git deactivate` | Pause tracking |

### Staging
| Command | Description |
|---------|-------------|
| `/git add <from> [to]` | Stage blocks (supports hollow/outline) |
| `/git rm <from> [to]` | Remove and stage |
| `/git unstage <from> [to]` | Unstage blocks |
| `/git autoadd on/off` | Toggle auto-staging |
| `/git autorm on/off` | Toggle auto-removal |

### Commit & History
| Command | Description |
|---------|-------------|
| `/git commit <message>` | Save staged changes |
| `/git status` | See what's staged |
| `/git diff` | Compare with last commit |
| `/git log` | View commit history |
| `/git revert [hash]` | Create undo commit |
| `/git reset [hash]` | Hard reset to commit |

### Remote Operations
| Command | Description |
|---------|-------------|
| `/git push` | Push to remote |
| `/git pull <user/repo>` | Pull from another repo |
| `/git clone` | Preview & clone builds |

### Instances
| Command | Description |
|---------|-------------|
| `/git instance list` | See all instances |
| `/git instance new [name]` | Create new instance |
| `/git instance highlight` | Show beacon beam |
| `/git instance select <id>` | Switch instance |

**Full command list:** Run `/git help` in-game

---

## 🏗️ Project Structure

```
VVSE-project/
├── website/              # Frontend (HTML/CSS/JS)
│   ├── homepage/         # Main interface, user profiles
│   ├── login/            # Authentication pages
│   └── signup/           # Registration
│
├── backend_database/     # REST API (Python/FastAPI)
│   ├── main.py           # API endpoints
│   ├── models.py         # Database schemas
│   └── database.py       # SQLite management
│
└── gitbuild/             # Minecraft Mod (Java/NeoForge)
    ├── src/main/java/    # Mod source code
    └── build.gradle      # Build configuration
```

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|------------|
| **Frontend** | Vanilla HTML5, CSS3, JavaScript |
| **Backend** | Python 3, FastAPI, SQLAlchemy, SQLite |
| **Mod** | Java 17+, NeoForge 26.1.0.19-beta, Minecraft 26.1 |
| **API** | RESTful JSON API with CORS support |

---

## 🔧 Development

### Backend Development
```bash
cd backend_database
pip install -r requirements.txt
python main.py              # Dev server with reload
```

### Mod Development
```bash
cd gitbuild
./gradlew build             # Build the mod
./gradlew runClient         # Test in dev environment
```

### Frontend Development
The website is static HTML/CSS/JS. Any static file server works:
```bash
cd website
python -m http.server 8080
```

---

## 🤝 Contributing

Contributions welcome! Areas we'd love help with:
- 🎨 UI/UX improvements for the website
- 🐛 Bug fixes and performance optimizations
- 📝 Documentation and tutorials
- 🌍 Translation support
- ✨ New mod features

---

## 📜 License

MIT License — See [LICENSE](LICENSE) for details.

---

## 🙋 FAQ

**Q: Does this work on servers?**  
A: Yes! Install the mod on the server and clients. All players with the mod can use GitBuild commands.

**Q: Can I use this in single-player?**  
A: Absolutely. It gives you full version control for your single-player worlds.

**Q: Does it support modded blocks?**  
A: Yes! Any block registered in Minecraft's block registry can be tracked.


**Q: Can I export builds to schematics?**  
A: Not directly yet, but you can clone any commit to your world and use other tools to export.

---

<div align="center">

**[Website](website/homepage/homepage.html) • [Issues](../../issues) • [Demo](demo/Demo.mp4)**

Built with ❤️ for the Minecraft community

</div>