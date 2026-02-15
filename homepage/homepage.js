// Homepage JavaScript for Minecraft Git System
class HomepageManager {
    constructor() {
        this.currentUser = null;
        this.repositories = [];
        this.init();
    }

    async init() {
        // Check if user is logged in
        await this.checkAuthStatus();
        
        // Load user repositories
        await this.loadRepositories();
        
        // Setup event listeners
        this.setupEventListeners();
        
        // Update UI with user data
        this.updateUI();
    }

    async checkAuthStatus() {
        const token = localStorage.getItem('authToken');
        const username = localStorage.getItem('username');
        
        if (!token || !username) {
            // Redirect to login if not authenticated
            window.location.href = '../signup/login.html';
            return;
        }

        try {
            // Verify token with backend
            const response = await fetch(`http://127.0.0.1:8000/users/uuid?uuid=${token}`);
            const data = await response.json();
            
            if (!data.exists) {
                // Token invalid, redirect to login
                localStorage.removeItem('authToken');
                localStorage.removeItem('username');
                window.location.href = '../signup/login.html';
                return;
            }

            this.currentUser = {
                username: username,
                uuid: token
            };
        } catch (error) {
            console.error('Auth check failed:', error);
            window.location.href = '../signup/login.html';
        }
    }

    async loadRepositories() {
        if (!this.currentUser) return;

        try {
            // For now, we'll use mock data since we need to implement the repository listing endpoint
            this.repositories = await this.getMockRepositories();
            
            // TODO: Replace with actual API call when endpoint is ready
            // const response = await fetch(`http://127.0.0.1:8000/users/${this.currentUser.username}/repositories`);
            // this.repositories = await response.json();
        } catch (error) {
            console.error('Failed to load repositories:', error);
            this.repositories = [];
        }
    }

    getMockRepositories() {
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

    setupEventListeners() {
        // New repository button
        const newRepoBtn = document.querySelector('.new-repo-btn');
        if (newRepoBtn) {
            newRepoBtn.addEventListener('click', () => this.showCreateRepositoryModal());
        }

        // Create repository button in empty state
        const createRepoBtn = document.querySelector('.create-repo-btn');
        if (createRepoBtn) {
            createRepoBtn.addEventListener('click', () => this.showCreateRepositoryModal());
        }

        // Navigation items
        const navItems = document.querySelectorAll('.nav-item');
        navItems.forEach(item => {
            item.addEventListener('click', (e) => {
                e.preventDefault();
                this.handleNavigation(item.textContent);
            });
        });

        // Repository cards
        document.addEventListener('click', (e) => {
            if (e.target.closest('.repo-name a')) {
                e.preventDefault();
                const repoName = e.target.closest('.repo-name a').textContent;
                this.openRepository(repoName);
            }
        });

        // Edit profile button
        const editProfileBtn = document.querySelector('.edit-profile-btn');
        if (editProfileBtn) {
            editProfileBtn.addEventListener('click', () => this.showEditProfileModal());
        }

        // Achievement badges
        const achievementBadges = document.querySelectorAll('.achievement-badge:not(.locked)');
        achievementBadges.forEach(badge => {
            badge.addEventListener('click', () => this.showAchievementDetails(badge));
        });
    }

    updateUI() {
        // Update username in profile
        const usernameElement = document.querySelector('.username');
        if (usernameElement && this.currentUser) {
            usernameElement.textContent = this.currentUser.username;
        }

        // Update repositories display
        this.renderRepositories();
    }

    renderRepositories() {
        const repositoriesGrid = document.getElementById('repositoriesGrid');
        const emptyState = document.getElementById('emptyState');

        if (this.repositories.length === 0) {
            repositoriesGrid.style.display = 'none';
            emptyState.style.display = 'block';
            return;
        }

        repositoriesGrid.style.display = 'grid';
        emptyState.style.display = 'none';

        repositoriesGrid.innerHTML = this.repositories.map(repo => `
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

    handleNavigation(section) {
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
                this.showOverview();
                break;
            case 'Repositories':
                this.showAllRepositories();
                break;
            case 'Projects':
                this.showProjects();
                break;
            case 'Packages':
                this.showPackages();
                break;
            case 'Stars':
                this.showStars();
                break;
        }
    }

    showOverview() {
        // Show overview content (current view)
        this.renderRepositories();
    }

    showAllRepositories() {
        // Show all repositories with filtering options
        console.log('Showing all repositories');
    }

    showProjects() {
        // Show projects section
        console.log('Showing projects');
    }

    showPackages() {
        // Show packages section
        console.log('Showing packages');
    }

    showStars() {
        // Show starred repositories
        console.log('Showing starred repositories');
    }

    openRepository(repoName) {
        // Navigate to repository page
        console.log(`Opening repository: ${repoName}`);
        // TODO: Navigate to repository detail page
        // window.location.href = `repository.html?user=${this.currentUser.username}&repo=${repoName}`;
    }

    showCreateRepositoryModal() {
        // Show modal for creating new repository
        const modal = this.createRepositoryModal();
        document.body.appendChild(modal);
        
        // Setup modal event listeners
        this.setupRepositoryModal(modal);
    }

    createRepositoryModal() {
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

    setupRepositoryModal(modal) {
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
            this.createRepository(modal);
        });
    }

    async createRepository(modal) {
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
            this.repositories.unshift({
                ...repoData,
                language: 'Minecraft',
                updated: 'just now',
                commits: 0
            });

            this.renderRepositories();
            document.body.removeChild(modal);
        } catch (error) {
            console.error('Failed to create repository:', error);
            alert('Failed to create repository. Please try again.');
        }
    }

    showEditProfileModal() {
        console.log('Show edit profile modal');
        // TODO: Implement edit profile functionality
    }

    showAchievementDetails(badge) {
        console.log('Show achievement details for:', badge);
        // TODO: Implement achievement details modal
    }
}

// Add modal styles
const modalStyles = `
    .modal-overlay {
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background-color: rgba(0, 0, 0, 0.8);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 1000;
    }

    .modal {
        background-color: #161b22;
        border: 1px solid #21262d;
        border-radius: 6px;
        width: 90%;
        max-width: 500px;
        max-height: 90vh;
        overflow-y: auto;
    }

    .modal-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 16px 20px;
        border-bottom: 1px solid #21262d;
    }

    .modal-header h2 {
        font-size: 20px;
        font-weight: 600;
        color: #f0f6fc;
        margin: 0;
    }

    .modal-close {
        background: none;
        border: none;
        color: #8b949e;
        font-size: 24px;
        cursor: pointer;
        padding: 0;
        width: 32px;
        height: 32px;
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: 4px;
    }

    .modal-close:hover {
        background-color: #21262d;
        color: #c9d1d9;
    }

    .modal-body {
        padding: 20px;
    }

    .form-group {
        margin-bottom: 16px;
    }

    .form-group label {
        display: block;
        margin-bottom: 8px;
        font-weight: 500;
        color: #f0f6fc;
    }

    .form-group input,
    .form-group textarea {
        width: 100%;
        padding: 8px 12px;
        background-color: #0d1117;
        border: 1px solid #21262d;
        border-radius: 6px;
        color: #c9d1d9;
        font-size: 14px;
    }

    .form-group input:focus,
    .form-group textarea:focus {
        outline: none;
        border-color: #58a6ff;
    }

    .form-group textarea {
        resize: vertical;
        min-height: 80px;
    }

    .modal-footer {
        display: flex;
        justify-content: flex-end;
        gap: 8px;
        padding: 16px 20px;
        border-top: 1px solid #21262d;
    }

    .btn {
        padding: 8px 16px;
        border-radius: 6px;
        font-size: 14px;
        font-weight: 500;
        cursor: pointer;
        border: 1px solid;
    }

    .btn-secondary {
        background-color: #21262d;
        color: #c9d1d9;
        border-color: #30363d;
    }

    .btn-secondary:hover {
        background-color: #30363d;
    }

    .btn-primary {
        background-color: #238636;
        color: white;
        border-color: rgba(240, 246, 252, 0.1);
    }

    .btn-primary:hover {
        background-color: #2ea043;
    }
`;

// Add styles to page
const styleSheet = document.createElement('style');
styleSheet.textContent = modalStyles;
document.head.appendChild(styleSheet);

// Initialize homepage when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    new HomepageManager();
});
