const API_BASE_URL = "http://127.0.0.1:8000";

// Global state
let currentUser = null;
let repositories = [];

// Initialize homepage when DOM is loaded
document.addEventListener('DOMContentLoaded', init);

async function init() {
    // Check if user is logged in
    await checkAuthStatus();
    
    // Load user repositories
    await loadRepositories();
    
    // Setup event listeners
    setupEventListeners();
    
    // Update UI with user data
    updateUI();
}

async function checkAuthStatus() {
    const token = localStorage.getItem('authToken');
    const username = localStorage.getItem('username');
    
    if (!token || !username) {
        console.log('No token or username found in localStorage. Redirecting to login.');
        // window.location.href = '../signup/login.html';
        return;
    }

    try {
        console.log('Attempting to verify token with backend:', token);
        const response = await fetch(`${API_BASE_URL}/users/uuid?uuid=${token}`);
        const data = await response.json();
        console.log('Backend response for token verification:', data);
        
        if (!data) {
            console.log('Token invalid according to backend. Redirecting to login.');
            // Token invalid, redirect to login
            localStorage.removeItem('authToken');
            localStorage.removeItem('username');
            // window.location.href = '../signup/login.html';
            return;
        }

        console.log('Token is valid. User authenticated.');
        currentUser = {
            username: username,
            uuid: token
        };
    } catch (error) {
        console.error('Auth check failed:', error);
        // window.location.href = '../signup/login.html';
    }
}

async function loadRepositories() {
    if (!currentUser) return;

    try {
        // For now, we'll use mock data since we need to implement the repository listing endpoint
        repositories = getMockRepositories();
        
        // TODO: Replace with actual API call when endpoint is ready
        // const response = await fetch(`http://127.0.0.1:8000/users/${currentUser.username}/repositories`);
        // repositories = await response.json();
    } catch (error) {
        console.error('Failed to load repositories:', error);
        repositories = [];
    }
}

function getMockRepositories() {
    return [
        {
            name: 'castle-build',
            description: 'Medieval castle with towers and moat',
            language: 'Minecraft',
            visibility: 'Public',
            updated: '2 hours ago',
            commits: 12
        },
        {
            name: 'modern-house',
            description: 'Modern architectural house design',
            language: 'Minecraft',
            visibility: 'Public',
            updated: '1 day ago',
            commits: 8
        },
        {
            name: 'redstone-circuit',
            description: 'Complex redstone logic gates and circuits',
            language: 'Redstone',
            visibility: 'Public',
            updated: '3 days ago',
            commits: 15
        }
    ];
}

function setupEventListeners() {
    // New repository button
    const newRepoBtn = document.querySelector('.new-repo-btn');
    if (newRepoBtn) {
        newRepoBtn.addEventListener('click', showCreateRepositoryModal);
    }

    // Create repository button in empty state
    const createRepoBtn = document.querySelector('.create-repo-btn');
    if (createRepoBtn) {
        createRepoBtn.addEventListener('click', showCreateRepositoryModal);
    }

    // Navigation items
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            handleNavigation(item.textContent);
        });
    });

    // Repository cards
    document.addEventListener('click', (e) => {
        if (e.target.closest('.repo-name a')) {
            e.preventDefault();
            const repoName = e.target.closest('.repo-name a').textContent;
            openRepository(repoName);
        }
    });

    // Edit profile button
    const editProfileBtn = document.querySelector('.edit-profile-btn');
    if (editProfileBtn) {
        editProfileBtn.addEventListener('click', showEditProfileModal);
    }

    // Achievement badges
    const achievementBadges = document.querySelectorAll('.achievement-badge:not(.locked)');
    achievementBadges.forEach(badge => {
        badge.addEventListener('click', () => showAchievementDetails(badge));
    });
}

function updateUI() {
    // Update username in profile
    const usernameElement = document.querySelector('.username');
    if (usernameElement && currentUser) {
        usernameElement.textContent = currentUser.username;
    }

    // Update repositories display
    renderRepositories();
}

function renderRepositories() {
    const repositoriesGrid = document.getElementById('repositoriesGrid');
    const emptyState = document.getElementById('emptyState');

    if (repositories.length === 0) {
        repositoriesGrid.style.display = 'none';
        emptyState.style.display = 'block';
        return;
    }

    repositoriesGrid.style.display = 'grid';
    emptyState.style.display = 'none';

    repositoriesGrid.innerHTML = repositories.map(repo => `
        <div class="repository-card">
            <div class="repo-header">
                <h3 class="repo-name">
                    <a href="#" data-repo="${repo.name}">${repo.name}</a>
                </h3>
                <span class="repo-visibility">${repo.visibility}</span>
            </div>
            <div class="repo-description">
                ${repo.description}
            </div>
            <div class="repo-meta">
                <span class="repo-language">
                    <span class="language-dot ${repo.language.toLowerCase()}"></span>
                    ${repo.language}
                </span>
                <span class="repo-commits">${repo.commits} commits</span>
                <span class="repo-updated">Updated ${repo.updated}</span>
            </div>
        </div>
    `).join('');
}

function handleNavigation(section) {
    // Update active nav item
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        item.classList.remove('active');
        if (item.textContent === section) {
            item.classList.add('active');
        }
    });

    // Handle different sections
    switch (section) {
        case 'Overview':
            showOverview();
            break;
        case 'Repositories':
            showAllRepositories();
            break;
        case 'Projects':
            showProjects();
            break;
        case 'Packages':
            showPackages();
            break;
        case 'Stars':
            showStars();
            break;
    }
}

function showOverview() {
    // Show overview content (current view)
    renderRepositories();
}

function showAllRepositories() {
    // Show all repositories with filtering options
    console.log('Showing all repositories');
}

function showProjects() {
    // Show projects section
    console.log('Showing projects');
}

function showPackages() {
    // Show packages section
    console.log('Showing packages');
}

function showStars() {
    // Show starred repositories
    console.log('Showing starred repositories');
}

function openRepository(repoName) {
    // Navigate to repository page
    console.log(`Opening repository: ${repoName}`);
    // TODO: Navigate to repository detail page
    // window.location.href = `repository.html?user=${currentUser.username}&repo=${repoName}`;
}

function showCreateRepositoryModal() {
    // Show modal for creating new repository
    const modal = createRepositoryModal();
    document.body.appendChild(modal);
    
    // Setup modal event listeners
    setupRepositoryModal(modal);
}

function createRepositoryModal() {
    const modal = document.createElement('div');
    modal.className = 'modal-overlay';
    modal.innerHTML = `
        <div class="modal">
            <div class="modal-header">
                <h2>Create new repository</h2>
                <button class="modal-close">&times;</button>
            </div>
            <div class="modal-body">
                <div class="form-group">
                    <label for="repoName">Repository name</label>
                    <input type="text" id="repoName" placeholder="my-awesome-build" required>
                </div>
                <div class="form-group">
                    <label for="repoDescription">Description (optional)</label>
                    <textarea id="repoDescription" placeholder="Describe your Minecraft build..."></textarea>
                </div>
                <div class="form-group">
                    <label>
                        <input type="radio" name="visibility" value="public" checked>
                        Public
                    </label>
                    <label>
                        <input type="radio" name="visibility" value="private">
                        Private
                    </label>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" id="cancelRepo">Cancel</button>
                <button class="btn btn-primary" id="createRepoBtn">Create repository</button>
            </div>
        </div>
    `;
    return modal;
}

function setupRepositoryModal(modal) {
    const closeBtn = modal.querySelector('.modal-close');
    const cancelBtn = modal.querySelector('#cancelRepo');
    const createBtn = modal.querySelector('#createRepoBtn');

    const closeModal = () => {
        document.body.removeChild(modal);
    };

    closeBtn.addEventListener('click', closeModal);
    cancelBtn.addEventListener('click', closeModal);

    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            closeModal();
        }
    });

    createBtn.addEventListener('click', () => {
        createRepository(modal);
    });
}

async function createRepository(modal) {
    const nameInput = modal.querySelector('#repoName');
    const descriptionInput = modal.querySelector('#repoDescription');
    const visibilityInput = modal.querySelector('input[name="visibility"]:checked');

    const repoData = {
        name: nameInput.value.trim(),
        description: descriptionInput.value.trim(),
        visibility: visibilityInput.value
    };

    if (!repoData.name) {
        alert('Repository name is required');
        return;
    }

    try {
        // TODO: Implement repository creation API call
        console.log('Creating repository:', repoData);
        
        // Add to local repositories for demo
        repositories.unshift({
            ...repoData,
            language: 'Minecraft',
            updated: 'just now',
            commits: 0
        });

        renderRepositories();
        document.body.removeChild(modal);
    } catch (error) {
        console.error('Failed to create repository:', error);
        alert('Failed to create repository. Please try again.');
    }
}

function showEditProfileModal() {
    console.log('Show edit profile modal');
    // TODO: Implement edit profile functionality
}

function showAchievementDetails(badge) {
    console.log('Show achievement details for:', badge);
    // TODO: Implement achievement details modal
}
