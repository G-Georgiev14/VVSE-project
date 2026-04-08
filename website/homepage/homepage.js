const API_BASE_URL = "http://127.0.0.1:8000";

// Global state
let currentUser = null;
let repositories = [];

// Initialize homepage when DOM is loaded
document.addEventListener('DOMContentLoaded', init);

async function init() {
    // Test backend connection first
    const backendRunning = await testBackendConnection();
    if (!backendRunning) {
        console.warn('Backend server is not running. Some features may not work.');
    }
    
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
    if (!currentUser) {
        // Use mock repositories for testing when no user is logged in
        repositories = getMockRepositories();
        return;
    }

    try {
        // Call backend API to list repositories
        const response = await fetch(`${API_BASE_URL}/repos/${currentUser.username}?uuid=${currentUser.uuid}`);
        
        if (response.ok) {
            const result = await response.json();
            const repoNames = result.repos || [];
            
            // Transform repo names into repository objects with additional metadata
            repositories = repoNames.map(repoName => ({
                name: repoName,
                description: `Minecraft build repository`,
                visibility: 'public',
                language: 'Minecraft',
                updated: 'Recently',
                commits: 0
            }));
        } else {
            const error = await response.json();
            console.error('Failed to load repositories:', error);
            // Don't fall back to mock data - keep empty to show backend is unavailable
            repositories = [];
        }
    } catch (error) {
        console.error('Failed to load repositories:', error);
        // Don't fall back to mock data - keep empty to show backend is unavailable
        repositories = [];
    }
}

function getMockRepositories() {
    return [];
}

function setupEventListeners() {
    // New repository button
    const newRepoBtn = document.querySelector('.new-repo-btn');
    if (newRepoBtn) {
        newRepoBtn.addEventListener('click', showCreateRepositoryModal);
    }

    // Search button
    const searchBtn = document.querySelector('.search-btn');
    if (searchBtn) {
        searchBtn.addEventListener('click', showSearchModal);
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
            const section = item.dataset.section || item.textContent.trim();
            handleNavigation(section);
        });
    });

    // Repository cards
    document.addEventListener('click', (e) => {
        if (e.target.closest('.repo-name a')) {
            e.preventDefault();
            const repoName = e.target.closest('.repo-name a').textContent.trim();
            openRepository(repoName);
        }
        
        // Clone button clicks
        if (e.target.closest('.clone-btn')) {
            const btn = e.target.closest('.clone-btn');
            const repoName = btn.dataset.repo;
            const username = btn.dataset.user;
            showCloneModal(repoName, username);
        }
        
        // View button clicks
        if (e.target.closest('.view-btn')) {
            const btn = e.target.closest('.view-btn');
            const repoName = btn.dataset.repo;
            openRepository(repoName);
        }
        
        // Star button clicks
        if (e.target.closest('.star-btn')) {
            const btn = e.target.closest('.star-btn');
            const repoName = btn.dataset.repo;
            toggleStar(btn, repoName);
        }
        
        // Filter button clicks
        if (e.target.closest('.filter-btn')) {
            const btn = e.target.closest('.filter-btn');
            const filter = btn.dataset.filter;
            filterRepositories(filter);
        }
    });

    // Edit profile button
    const editProfileBtn = document.querySelector('.edit-profile-btn');
    if (editProfileBtn) {
        editProfileBtn.addEventListener('click', showEditProfileModal);
    }

    // Quick action buttons
    const quickActionBtns = document.querySelectorAll('.quick-action-btn');
    quickActionBtns.forEach(btn => {
        btn.addEventListener('click', (e) => {
            const action = e.currentTarget.querySelector('span').textContent.toLowerCase();
            handleQuickAction(action);
        });
    });

    // Achievement badges
    const achievementBadges = document.querySelectorAll('.achievement-badge:not(.locked)');
    achievementBadges.forEach(badge => {
        badge.addEventListener('click', () => showAchievementDetails(badge));
        badge.addEventListener('mouseenter', () => {
            badge.style.transform = 'scale(1.1) rotate(5deg)';
        });
        badge.addEventListener('mouseleave', () => {
            badge.style.transform = 'scale(1) rotate(0deg)';
        });
    });

    // Profile menu dropdown
    const profileMenu = document.querySelector('.profile-menu');
    if (profileMenu) {
        profileMenu.addEventListener('click', toggleProfileMenu);
    }

    // Close dropdown when clicking outside
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.profile-menu')) {
            closeProfileMenu();
        }
    });

    // Add hover effects to repository cards
    const repoCards = document.querySelectorAll('.repository-card');
    repoCards.forEach(card => {
        card.addEventListener('mouseenter', () => {
            card.style.transform = 'translateY(-0.25rem)';
        });
        card.addEventListener('mouseleave', () => {
            card.style.transform = 'translateY(0)';
        });
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

    repositoriesGrid.style.display = 'flex';
    emptyState.style.display = 'none';

    repositoriesGrid.innerHTML = repositories.map((repo, index) => {
        const isFeatured = index === 0;
        const tags = generateTags(repo.name, repo.description);
        const icon = getRepoIcon(repo.name, repo.language);
        
        return `
            <div class="repository-card ${isFeatured ? 'featured' : ''}" data-language="${repo.language.toLowerCase()}" style="animation-delay: ${index * 0.1 + 0.1}s">
                <div class="repo-header">
                    <h3 class="repo-name">
                        <a href="#" data-repo="${repo.name}">
                            <i class="fas fa-${icon}"></i>
                            ${repo.name}
                        </a>
                    </h3>
                    <div class="repo-badges">
                        <span class="repo-visibility ${repo.visibility}">
                            <i class="fas fa-${repo.visibility === 'public' ? 'globe' : 'lock'}"></i>
                            ${repo.visibility}
                        </span>
                        ${isFeatured ? '<span class="repo-badge featured-badge"><i class="fas fa-star"></i>Featured</span>' : ''}
                    </div>
                </div>
                <div class="repo-description">
                    ${repo.description}
                </div>
                <div class="repo-tags">
                    ${tags.map(tag => `<span class="tag">${tag}</span>`).join('')}
                </div>
                <div class="repo-stats">
                    <div class="stat-item">
                        <i class="fas fa-cube stat-icon"></i>
                        <span class="stat-value">${repo.blocks || 0}</span>
                        <span class="stat-label">Blocks</span>
                    </div>
                    <div class="stat-item">
                        <i class="fas fa-code-branch stat-icon"></i>
                        <span class="stat-value">${repo.commits || 0}</span>
                        <span class="stat-label">Commits</span>
                    </div>
                    <div class="stat-item">
                        <i class="fas fa-star stat-icon"></i>
                        <span class="stat-value">${repo.stars || Math.floor(Math.random() * 50)}</span>
                        <span class="stat-label">Stars</span>
                    </div>
                </div>
                <div class="repo-actions">
                    <button class="clone-btn" data-repo="${repo.name}" data-user="${currentUser ? currentUser.username : 'G-Georgiev14'}">
                        <i class="fas fa-code-branch btn-icon"></i>
                        <span>Clone</span>
                    </button>
                    <button class="view-btn" data-repo="${repo.name}">
                        <i class="fas fa-eye btn-icon"></i>
                        <span>View</span>
                    </button>
                    <button class="star-btn" data-repo="${repo.name}">
                        <i class="fas fa-star btn-icon"></i>
                        <span>Star</span>
                    </button>
                    <button class="delete-btn" data-repo="${repo.name}">
                        <i class="fas fa-trash btn-icon"></i>
                        <span>Delete</span>
                    </button>
                </div>
                <div class="repo-meta">
                    <span class="repo-language">
                        <span class="language-dot ${repo.language.toLowerCase()}"></span>
                        ${repo.language}
                    </span>
                    <span class="repo-updated">
                        <i class="fas fa-clock"></i>
                        Updated ${repo.updated}
                    </span>
                </div>
            </div>
        `;
    }).join('');

    // Reset scroll position to left
    repositoriesGrid.scrollLeft = 0;

    // Re-attach event listeners for new elements
    attachRepositoryListeners();
}

function generateTags(name, description) {
    const tags = [];
    const nameLower = name.toLowerCase();
    const descLower = description.toLowerCase();
    
    if (nameLower.includes('castle') || descLower.includes('medieval')) tags.push('medieval');
    if (nameLower.includes('house') || descLower.includes('modern')) tags.push('modern');
    if (nameLower.includes('redstone') || descLower.includes('circuit')) tags.push('redstone');
    if (nameLower.includes('castle') || nameLower.includes('build')) tags.push('castle');
    if (descLower.includes('survival')) tags.push('survival');
    if (descLower.includes('decorative')) tags.push('decorative');
    if (descLower.includes('technical') || descLower.includes('automation')) tags.push('technical');
    
    return tags.slice(0, 3); // Limit to 3 tags
}

function getRepoIcon(name, language) {
    const nameLower = name.toLowerCase();
    if (nameLower.includes('castle')) return 'cube';
    if (nameLower.includes('house')) return 'home';
    if (nameLower.includes('redstone')) return 'bolt';
    if (language === 'Redstone') return 'bolt';
    return 'cube';
}

function attachRepositoryListeners() {
    const repoCards = document.querySelectorAll('.repository-card');
    repoCards.forEach(card => {
        card.addEventListener('mouseenter', () => {
            card.style.transform = 'translateY(-0.25rem)';
        });
        card.addEventListener('mouseleave', () => {
            card.style.transform = 'translateY(0)';
        });
    });

    // Add delete button listeners
    const deleteButtons = document.querySelectorAll('.delete-btn');
    deleteButtons.forEach(button => {
        button.addEventListener('click', handleDeleteRepository);
    });
}

function handleDeleteRepository(event) {
    const button = event.currentTarget;
    const repoName = button.dataset.repo;
    
    if (!repoName) return;
    
    // Show confirmation dialog
    const confirmed = confirm(`Are you sure you want to delete the repository "${repoName}"? This action cannot be undone.`);
    
    if (confirmed) {
        deleteRepository(repoName);
    }
}

async function deleteRepository(repoName) {
    try {
        if (currentUser) {
            // Try to delete from backend
            const response = await fetch(`${API_BASE_URL}/repos/${currentUser.username}/${repoName}?uuid=${currentUser.uuid}`, {
                method: 'DELETE'
            });
            
            if (response.ok) {
                const result = await response.json();
                console.log('Delete response:', result);
                showNotification(`Repository "${repoName}" deleted successfully!`, 'success');
                
                // Remove from local array and re-render only after successful backend delete
                repositories = repositories.filter(repo => repo.name !== repoName);
                renderRepositories();
            } else {
                const error = await response.json();
                console.error('Delete failed:', error);
                showNotification(`Failed to delete repository: ${error.detail}`, 'error');
            }
        } else {
            // No user logged in - just remove from mock data
            repositories = repositories.filter(repo => repo.name !== repoName);
            renderRepositories();
            showNotification(`Repository "${repoName}" deleted successfully!`, 'success');
        }
    } catch (error) {
        console.error('Delete failed:', error);
        showNotification(`Failed to delete repository: ${error.message}`, 'error');
    }
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
    // Find the repository data
    const repo = repositories.find(r => r.name === repoName);
    
    if (repo) {
        // Show repository details in a modal
        showRepositoryDetailsModal(repo);
    } else {
        showNotification('Repository not found', 'info');
    }
}

function showRepositoryDetailsModal(repo) {
    const modal = createRepositoryDetailsModal(repo);
    document.body.appendChild(modal);
    
    // Setup modal event listeners
    setupRepositoryDetailsModal(modal);
}

function createRepositoryDetailsModal(repo) {
    const modal = document.createElement('div');
    modal.className = 'modal-overlay';
    modal.innerHTML = `
        <div class="modal">
            <div class="modal-header">
                <h2><i class="fas fa-cube"></i> ${repo.name}</h2>
            </div>
            <div class="modal-body">
                <div class="repo-details">
                    <div class="detail-section">
                        <h3>Description</h3>
                        <p>${repo.description || 'No description provided'}</p>
                    </div>
                    
                    <div class="detail-section">
                        <h3>Information</h3>
                        <div class="info-grid">
                            <div class="info-item">
                                <strong>Visibility:</strong> 
                                <span class="visibility-badge ${repo.visibility}">
                                    <i class="fas fa-${repo.visibility === 'public' ? 'globe' : 'lock'}"></i>
                                    ${repo.visibility}
                                </span>
                            </div>
                            <div class="info-item">
                                <strong>Language:</strong> 
                                <span class="language-badge">
                                    <span class="language-dot ${repo.language ? repo.language.toLowerCase() : 'minecraft'}"></span>
                                    ${repo.language || 'Minecraft'}
                                </span>
                            </div>
                            <div class="info-item">
                                <strong>Commits:</strong> ${repo.commits || 0}
                            </div>
                            <div class="info-item">
                                <strong>Updated:</strong> ${repo.updated || 'Unknown'}
                            </div>
                        </div>
                    </div>
                    
                    <div class="detail-section">
                        <h3>Actions</h3>
                        <div class="action-buttons">
                            <button class="btn btn-primary" onclick="cloneRepository('${repo.name}')">
                                <i class="fas fa-code-branch"></i> Clone
                            </button>
                            <button class="btn btn-secondary" onclick="downloadRepository('${repo.name}')">
                                <i class="fas fa-download"></i> Download
                            </button>
                        </div>
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" id="closeDetails">Close</button>
            </div>
        </div>
    `;
    return modal;
}

function setupRepositoryDetailsModal(modal) {
    const closeBtn = modal.querySelector('#closeDetails');
    
    const closeModal = () => {
        document.body.removeChild(modal);
    };
    
    closeBtn.addEventListener('click', closeModal);
    
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            closeModal();
        }
    });
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
                    <label>Repository Visibility</label>
                    <div class="radio-group">
                        <div class="radio-option selected" data-value="public">
                            <div class="radio-custom"></div>
                            <div class="radio-label">
                                <i class="fas fa-globe radio-icon"></i>
                                Public
                                <div class="radio-description">Anyone can see this repository</div>
                            </div>
                        </div>
                        <div class="radio-option" data-value="private">
                            <div class="radio-custom"></div>
                            <div class="radio-label">
                                <i class="fas fa-lock radio-icon"></i>
                                Private
                                <div class="radio-description">You choose who can see and commit</div>
                            </div>
                        </div>
                    </div>
                    <input type="hidden" name="visibility" id="visibilityInput" value="public">
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
    const cancelBtn = modal.querySelector('#cancelRepo');
    const createBtn = modal.querySelector('#createRepoBtn');
    const radioOptions = modal.querySelectorAll('.radio-option');
    const visibilityInput = modal.querySelector('#visibilityInput');

    const closeModal = () => {
        document.body.removeChild(modal);
    };

    // Radio button functionality
    radioOptions.forEach(option => {
        option.addEventListener('click', () => {
            // Remove selected class from all options
            radioOptions.forEach(opt => opt.classList.remove('selected'));
            // Add selected class to clicked option
            option.classList.add('selected');
            // Update hidden input value
            visibilityInput.value = option.dataset.value;
        });
    });

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
    const visibilityInput = modal.querySelector('#visibilityInput');

    const repoData = {
        name: nameInput.value.trim(),
        description: descriptionInput.value.trim(),
        visibility: visibilityInput.value
    };

    if (!repoData.name) {
        alert('Repository name is required');
        return;
    }

    if (!currentUser) {
        alert('You must be logged in to create repositories or/and start the server');
        return;
    }

    try {
        console.log('Creating repository with data:', repoData);
        console.log('Current user:', currentUser);
        console.log('API URL:', `${API_BASE_URL}/repos/${currentUser.username}`);
        
        // Call backend API to create repository
        const response = await fetch(`${API_BASE_URL}/repos/${currentUser.username}?repo_name=${encodeURIComponent(repoData.name)}&uuid=${currentUser.uuid}`, {
            method: 'POST'
        });

        console.log('Response status:', response.status);
        console.log('Response ok:', response.ok);

        if (response.ok) {
            const result = await response.json();
            console.log('Repository created:', result);
            
            // Add to local repositories for display
            repositories.unshift({
                name: repoData.name,
                description: repoData.description,
                visibility: repoData.visibility,
                language: 'Minecraft',
                updated: 'just now',
                commits: 0
            });

            renderRepositories();
            document.body.removeChild(modal);
            showNotification('Repository created successfully!', 'success');
        } else {
            const errorText = await response.text();
            console.error('Error response:', errorText);
            let errorMessage = 'Failed to create repository';
            
            try {
                const error = JSON.parse(errorText);
                errorMessage = error.detail || error.message || errorMessage;
            } catch {
                errorMessage = errorText || errorMessage;
            }
            
            throw new Error(errorMessage);
        }
    } catch (error) {
        console.error('Failed to create repository:', error);
        
        // Check if it's a network error (backend not running)
        if (error.message.includes('Failed to fetch') || error.message.includes('NetworkError') || error.name === 'TypeError') {
            alert('Backend server is not running. Please start the backend server on http://127.0.0.1:8000');
        } else {
            alert(`Failed to create repository: ${error.message || error.toString()}`);
        }
    }
}

// Test function to check backend connectivity
async function testBackendConnection() {
    try {
        console.log('Testing backend connection...');
        const response = await fetch(`${API_BASE_URL}/db-check`);
        if (response.ok) {
            const result = await response.json();
            console.log('Backend is running:', result);
            return true;
        } else {
            console.error('Backend returned error:', response.status);
            return false;
        }
    } catch (error) {
        console.error('Backend connection failed:', error);
        return false;
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

function showCloneModal(repoName, username) {
    const modal = createCloneModal(repoName, username);
    document.body.appendChild(modal);
    setupCloneModal(modal);
}

function createCloneModal(repoName, username) {
    const modal = document.createElement('div');
    modal.className = 'modal-overlay';
    modal.innerHTML = `
        <div class="modal">
            <div class="modal-header">
                <h2>🔗 Clone Repository</h2>
            </div>
            <div class="modal-body">
                <div class="clone-info">
                    <p><strong>Repository:</strong> ${username}/${repoName}</p>
                    <p><strong>Clone URL:</strong> <code>http://127.0.0.1:8000/repos/${username}/${repoName}</code></p>
                </div>
                <div class="form-group">
                    <label for="cloneRepoName">New repository name</label>
                    <input type="text" id="cloneRepoName" value="${repoName}-clone" placeholder="Enter new repository name">
                </div>
                <div class="clone-commands">
                    <h3>Minecraft Commands:</h3>
                    <div class="command-block">
                        <code>/git clone ${repoName}-clone http://127.0.0.1:8000/repos/${username}/${repoName}</code>
                        <button class="copy-btn" data-command="/git clone ${repoName}-clone http://127.0.0.1:8000/repos/${username}/${repoName}">📋 Copy</button>
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" id="cancelClone">Cancel</button>
                <button class="btn btn-primary" id="confirmClone">Clone Repository</button>
            </div>
        </div>
    `;
    return modal;
}

function setupCloneModal(modal) {
    const cancelBtn = modal.querySelector('#cancelClone');
    const confirmBtn = modal.querySelector('#confirmClone');
    
    const closeModal = () => {
        document.body.removeChild(modal);
    };
    
    cancelBtn.addEventListener('click', closeModal);
    
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            closeModal();
        }
    });
    
    confirmBtn.addEventListener('click', () => {
        performClone(modal);
    });
    
    // Copy button functionality
    modal.addEventListener('click', (e) => {
        if (e.target.classList.contains('copy-btn')) {
            const command = e.target.dataset.command;
            navigator.clipboard.writeText(command).then(() => {
                e.target.textContent = '✅ Copied!';
                setTimeout(() => {
                    e.target.textContent = '📋 Copy';
                }, 2000);
            });
        }
    });
}

async function performClone(modal) {
    const repoNameInput = modal.querySelector('#cloneRepoName');
    const newRepoName = repoNameInput.value.trim();
    
    if (!newRepoName) {
        alert('Repository name is required');
        return;
    }
    
    if (!currentUser) {
        alert('You must be logged in to clone repositories');
        return;
    }
    
    try {
        // Get the source repo info from the button that triggered this modal
        const sourceRepo = modal.querySelector('.clone-info strong').textContent;
        const [sourceUsername, sourceRepoName] = sourceRepo.replace('Repository: ', '').split('/');
        
        const response = await fetch(`${API_BASE_URL}/repos/${currentUser.username}/clone`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                source_username: sourceUsername,
                source_repo: sourceRepoName,
                new_repo_name: newRepoName,
                uuid: currentUser.uuid
            })
        });
        
        if (response.ok) {
            const result = await response.json();
            alert(`Repository cloned successfully! New repo: ${newRepoName}`);
            
            // Refresh repositories list
            await loadRepositories();
            renderRepositories();
            
            document.body.removeChild(modal);
        } else {
            const error = await response.json();
            alert(`Failed to clone repository: ${error.detail || 'Unknown error'}`);
        }
    } catch (error) {
        console.error('Clone failed:', error);
        alert('Failed to clone repository. Please try again.');
    }
}

function filterRepositories(filter) {
    const filterBtns = document.querySelectorAll('.filter-btn');
    const repoCards = document.querySelectorAll('.repository-card');
    
    // Update active filter button with animation
    filterBtns.forEach(btn => {
        btn.classList.remove('active');
        if (btn.dataset.filter === filter) {
            setTimeout(() => btn.classList.add('active'), 50);
        }
    });
    
    // Filter repository cards with animation
    repoCards.forEach((card, index) => {
        setTimeout(() => {
            if (filter === 'all') {
                card.style.display = 'block';
                card.style.opacity = '0';
                card.style.transform = 'translateY(20px)';
                setTimeout(() => {
                    card.style.opacity = '1';
                    card.style.transform = 'translateY(0)';
                }, 50);
            } else {
                const language = card.dataset.language;
                if (language === filter) {
                    card.style.display = 'block';
                    card.style.opacity = '0';
                    card.style.transform = 'translateY(20px)';
                    setTimeout(() => {
                        card.style.opacity = '1';
                        card.style.transform = 'translateY(0)';
                    }, 50);
                } else {
                    card.style.opacity = '0';
                    card.style.transform = 'translateY(-20px)';
                    setTimeout(() => {
                        card.style.display = 'none';
                    }, 300);
                }
            }
        }, index * 100);
    });
}

function toggleStar(button, repoName) {
    const isStarred = button.classList.contains('starred');
    
    if (isStarred) {
        button.classList.remove('starred');
        button.querySelector('i').className = 'fas fa-star btn-icon';
        showNotification(`Unstarred ${repoName}`, 'info');
    } else {
        button.classList.add('starred');
        button.querySelector('i').className = 'fas fa-star btn-icon';
        showNotification(`Starred ${repoName}!`, 'success');
        
        // Add star animation
        button.style.transform = 'scale(1.2) rotate(72deg)';
        setTimeout(() => {
            button.style.transform = 'scale(1) rotate(0deg)';
        }, 300);
    }
}

function showSearchModal() {
    const modal = createSearchModal();
    document.body.appendChild(modal);
    setupSearchModal(modal);
}

function createSearchModal() {
    const modal = document.createElement('div');
    modal.className = 'modal-overlay';
    modal.innerHTML = `
        <div class="modal">
            <div class="modal-header">
                <h2><i class="fas fa-search"></i> Search Repositories</h2>
            </div>
            <div class="modal-body">
                <div class="form-group">
                    <input type="text" id="searchInput" placeholder="Search repositories by name or description..." autofocus>
                </div>
                <div class="search-options">
                    <label>
                        <input type="checkbox" id="searchMyRepos" checked>
                        My repositories
                    </label>
                    <label>
                        <input type="checkbox" id="searchAllRepos">
                        All repositories
                    </label>
                </div>
                <div id="searchResults" class="search-results"></div>
            </div>
        </div>
    `;
    return modal;
}

function setupSearchModal(modal) {
    const searchInput = modal.querySelector('#searchInput');
    const searchResults = modal.querySelector('#searchResults');
    
    const closeModal = () => {
        document.body.removeChild(modal);
    };
    
    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });
    
    searchInput.addEventListener('input', (e) => {
        const query = e.target.value.toLowerCase();
        if (query.length > 0) {
            performSearch(query, searchResults);
        } else {
            searchResults.innerHTML = '';
        }
    });
}

function performSearch(query, resultsContainer) {
    const filtered = repositories.filter(repo => 
        repo.name.toLowerCase().includes(query) || 
        repo.description.toLowerCase().includes(query)
    );
    
    if (filtered.length === 0) {
        resultsContainer.innerHTML = '<p class="no-results">No repositories found</p>';
        return;
    }
    
    resultsContainer.innerHTML = filtered.map(repo => `
        <div class="search-result-item" onclick="openRepository('${repo.name}')">
            <div class="search-result-name">${repo.name}</div>
            <div class="search-result-desc">${repo.description}</div>
        </div>
    `).join('');
}

function handleQuickAction(action) {
    switch(action) {
        case 'saved':
            showNotification('Saved items coming soon!', 'info');
            break;
        case 'settings':
            showNotification('Settings panel coming soon!', 'info');
            break;
    }
}

function toggleProfileMenu(e) {
    e.stopPropagation();
    const existingMenu = document.querySelector('.profile-dropdown');
    if (existingMenu) {
        existingMenu.remove();
        return;
    }
    
    const dropdown = document.createElement('div');
    dropdown.className = 'profile-dropdown';
    dropdown.innerHTML = `
        <a href="#" id="profileLink"><i class="fas fa-user"></i> Profile</a>
        <a href="#" id="settingsLink"><i class="fas fa-cog"></i> Settings</a>
        <a href="#" id="signOutLink"><i class="fas fa-sign-out-alt"></i> Sign out</a>
    `;
    
    document.querySelector('.profile-menu').appendChild(dropdown);
    
    // Add event listeners to dropdown items
    document.getElementById('profileLink').addEventListener('click', (e) => {
        e.preventDefault();
        showNotification('Profile page coming soon!', 'info');
        closeProfileMenu();
    });
    
    document.getElementById('settingsLink').addEventListener('click', (e) => {
        e.preventDefault();
        showNotification('Settings panel coming soon!', 'info');
        closeProfileMenu();
    });
    
    document.getElementById('signOutLink').addEventListener('click', (e) => {
        e.preventDefault();
        signOut();
    });
}

function closeProfileMenu() {
    const dropdown = document.querySelector('.profile-dropdown');
    if (dropdown) dropdown.remove();
}

function signOut() {
    // Clear authentication data from localStorage
    localStorage.removeItem('authToken');
    localStorage.removeItem('username');
    
    // Clear current user state
    currentUser = null;
    
    // Show notification
    showNotification('You have been signed out successfully', 'success');
    
    // Redirect to login page after a short delay
    setTimeout(() => {
        window.location.href = '../login/login.html';
    }, 1500);
}

function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `notification ${type}`;
    notification.innerHTML = `
        <i class="fas fa-${type === 'success' ? 'check-circle' : 'info-circle'}"></i>
        <span>${message}</span>
    `;
    
    document.body.appendChild(notification);
    
    setTimeout(() => {
        notification.style.opacity = '1';
        notification.style.transform = 'translateY(0)';
    }, 100);
    
    setTimeout(() => {
        notification.style.opacity = '0';
        notification.style.transform = 'translateY(-20px)';
        setTimeout(() => {
            document.body.removeChild(notification);
        }, 300);
    }, 3000);
}
