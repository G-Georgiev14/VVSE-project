const API_BASE_URL = "http://127.0.0.1:8000";

// Global state
let currentUser = null;
let userRepositories = [];

// Initialize userpage when DOM is loaded
document.addEventListener('DOMContentLoaded', init);

async function init() {
    // Check if user is logged in
    await checkAuthStatus();
    
    if (!currentUser) {
        // Redirect to login if not authenticated
        window.location.href = '../login/login.html';
        return;
    }
    
    // Load user repositories
    await loadUserRepositories();
    
    // Setup event listeners
    setupEventListeners();
    
    // Update UI with user data
    updateUI();
}

async function checkAuthStatus() {
    const token = localStorage.getItem('authToken');
    const username = localStorage.getItem('username');
    
    if (!token || !username) {
        console.log('No token or username found. Redirecting to login.');
        return false;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/users/uuid?uuid=${token}`);
        const data = await response.json();
        
        if (!data) {
            localStorage.removeItem('authToken');
            localStorage.removeItem('username');
            return false;
        }

        currentUser = {
            username: username,
            uuid: token
        };
        return true;
    } catch (error) {
        console.error('Auth check failed:', error);
        return false;
    }
}

async function loadUserRepositories() {
    if (!currentUser) return;

    try {
        const response = await fetch(`${API_BASE_URL}/repos/${currentUser.username}?uuid=${currentUser.uuid}`);
        
        if (response.ok) {
            const result = await response.json();
            const repoNames = result.repos || [];
            
            userRepositories = await Promise.all(repoNames.map(async (repo) => {
                // Fetch commit count for each repository
                let commitCount = 0;
                try {
                    const commitsResponse = await fetch(`${API_BASE_URL}/users/${currentUser.username}/${repo.name}/log`);
                    if (commitsResponse.ok) {
                        const commits = await commitsResponse.json();
                        commitCount = commits.length;
                    }
                } catch (error) {
                    console.error(`Failed to load commits for ${repo.name}:`, error);
                }

                // Fetch blocks count for each repository
                let blockCount = 0;
                try {
                    const blocksResponse = await fetch(`${API_BASE_URL}/repos/${currentUser.username}/${repo.name}/head-blocks`);
                    if (blocksResponse.ok) {
                        const data = await blocksResponse.json();
                        blockCount = data.blocks ? data.blocks.length : 0;
                    }
                } catch (error) {
                    console.error(`Failed to load blocks for ${repo.name}:`, error);
                }

                return {
                    name: repo.name,
                    description: `Minecraft build repository`,
                    visibility: repo.visibility || 'public',
                    language: 'Minecraft',
                    updated: 'Recently',
                    commits: commitCount,
                    stars: repo.stars || 0,
                    blocks: blockCount
                };
            }));
        } else {
            const error = await response.json();
            console.error('Failed to load user repositories:', error);
            userRepositories = [];
        }
    } catch (error) {
        console.error('Failed to load user repositories:', error);
        userRepositories = [];
    }
}

function setupEventListeners() {
    // Create repository buttons (multiple buttons for better UX)
    const createRepoBtns = ['createRepoBtn2', 'createRepoBtn3'];
    createRepoBtns.forEach(btnId => {
        const btn = document.getElementById(btnId);
        if (btn) {
            console.log(`Found create repository button: ${btnId}`);
            btn.addEventListener('click', (e) => {
                console.log(`Create repository button ${btnId} clicked`);
                e.preventDefault();
                showCreateRepositoryModal();
            });
        } else {
            console.log(`Create repository button not found: ${btnId}`);
        }
    });

    // Search button
    const searchBtn = document.getElementById('searchBtn');
    if (searchBtn) {
        searchBtn.addEventListener('click', showSearchModal);
    }

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

    // Modal event listeners
    const closeBtn = document.getElementById('closeCreateModal');
    const cancelBtn = document.getElementById('cancelCreateRepo');
    const submitBtn = document.getElementById('submitCreateRepo');
    const form = document.getElementById('createRepoForm');

    if (closeBtn) closeBtn.addEventListener('click', hideCreateRepositoryModal);
    if (cancelBtn) cancelBtn.addEventListener('click', hideCreateRepositoryModal);
    if (submitBtn) submitBtn.addEventListener('click', handleCreateRepository);
    if (form) form.addEventListener('submit', (e) => {
        e.preventDefault();
        handleCreateRepository();
    });

    // Radio button selection
    const radioOptions = document.querySelectorAll('.radio-option input[type="radio"]');
    radioOptions.forEach(option => {
        option.addEventListener('change', handleRadioChange);
    });

    // Form validation
    const repoNameInput = document.getElementById('repoName');
    const repoDescInput = document.getElementById('repoDescription');
    
    if (repoNameInput) {
        repoNameInput.addEventListener('input', validateRepoName);
        repoNameInput.addEventListener('blur', validateRepoName);
    }
    
    if (repoDescInput) {
        repoDescInput.addEventListener('input', validateRepoDescription);
        repoDescInput.addEventListener('blur', validateRepoDescription);
    }
}

function handleRadioChange(e) {
    const selectedOption = e.target.closest('.radio-option');
    const allOptions = document.querySelectorAll('.radio-option');
    
    allOptions.forEach(option => option.classList.remove('selected'));
    selectedOption.classList.add('selected');
}

function validateRepoName() {
    const repoNameInput = document.getElementById('repoName');
    const errorSpan = document.getElementById('repoNameError');
    const value = repoNameInput.value.trim();
    
    if (!value) {
        errorSpan.textContent = 'Repository name is required';
        return false;
    }
    
    if (value.length < 3) {
        errorSpan.textContent = 'Repository name must be at least 3 characters';
        return false;
    }
    
    if (!/^[a-zA-Z0-9_-]+$/.test(value)) {
        errorSpan.textContent = 'Repository name can only contain letters, numbers, hyphens, and underscores';
        return false;
    }
    
    errorSpan.textContent = '';
    return true;
}

function validateRepoDescription() {
    const repoDescInput = document.getElementById('repoDescription');
    const errorSpan = document.getElementById('repoDescriptionError');
    const value = repoDescInput.value.trim();
    
    if (value && value.length > 500) {
        errorSpan.textContent = 'Description must be less than 500 characters';
        return false;
    }
    
    errorSpan.textContent = '';
    return true;
}

function showCreateRepositoryModal() {
    // Show modal for creating new repository
    const modal = createRepositoryModal();
    document.body.appendChild(modal);
    
    // Setup modal event listeners
    setupRepositoryModal(modal);
}

function hideCreateRepositoryModal() {
    const modal = document.getElementById('createRepoModal');
    if (modal) {
        modal.style.display = 'none';
        // Clear form
        document.getElementById('repoName').value = '';
        document.getElementById('repoDescription').value = '';
        // Reset radio buttons
        document.querySelectorAll('.radio-option').forEach(option => {
            option.classList.remove('selected');
        });
        document.querySelector('input[value="public"]').checked = true;
        document.querySelector('input[value="public"]').closest('.radio-option').classList.add('selected');
        // Clear errors
        document.getElementById('repoNameError').textContent = '';
        document.getElementById('repoDescriptionError').textContent = '';
    }
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
        const response = await fetch(`${API_BASE_URL}/repos/${currentUser.username}?repo_name=${encodeURIComponent(repoData.name)}&uuid=${currentUser.uuid}&visibility=${encodeURIComponent(repoData.visibility)}`, {
            method: 'POST'
        });

        console.log('Response status:', response.status);
        console.log('Response ok:', response.ok);

        if (response.ok) {
            const result = await response.json();
            console.log('Repository created:', result);
            
            // Add to local repositories for display
            const now = new Date();
            userRepositories.unshift({
                name: repoData.name,
                description: repoData.description,
                visibility: repoData.visibility,
                language: 'Minecraft',
                updated: formatDateTime(now),
                created: formatDateTime(now),
                commits: 0
            });

            renderUserRepositories();
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

async function handleCreateRepository() {
    console.log('handleCreateRepository called');
    
    if (!validateRepoName()) {
        console.log('Repository name validation failed');
        return;
    }
    
    if (!validateRepoDescription()) {
        console.log('Repository description validation failed');
        return;
    }

    const repoName = document.getElementById('repoName').value.trim();
    const repoDescription = document.getElementById('repoDescription').value.trim();
    const visibility = document.querySelector('input[name="visibility"]:checked').value;
    
    console.log('Repository data:', { repoName, repoDescription, visibility });
    console.log('Current user:', currentUser);

    try {
        const response = await fetch(`${API_BASE_URL}/repos/${currentUser.username}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                repo_name: repoName,
                uuid: currentUser.uuid,
                visibility: visibility,
                description: repoDescription
            })
        });

        if (response.ok) {
            showNotification('Repository created successfully!', 'success');
            
            // Hide modal and clear form
            hideCreateRepositoryModal();
            
            // Reload repositories
            await loadUserRepositories();
            updateUI();
        } else {
            const error = await response.json();
            console.error('Backend error response:', error);
            console.error('Error type:', typeof error);
            console.error('Is array?', Array.isArray(error));
            
            if (Array.isArray(error)) {
                console.error('Validation errors:', error);
                error.forEach((err, index) => {
                    console.error(`Error ${index + 1}:`, err);
                });
            } else {
                console.error('Single error:', error);
            }
            
            showNotification(`Failed to create repository: ${error.detail || JSON.stringify(error)}`, 'error');
        }
    } catch (error) {
        console.error('Create repository error:', error);
        showNotification('Failed to create repository. Please try again later.', 'error');
    }
}

function updateUI() {
    // Update repository count
    const repoCount = document.getElementById('repoCount');
    if (repoCount) {
        repoCount.textContent = userRepositories.length;
    }

    // Render user repositories
    renderUserRepositories();
}

function renderUserRepositories() {
    const repositoriesGrid = document.getElementById('userRepositoriesGrid');
    const emptyState = document.getElementById('emptyState');

    if (userRepositories.length === 0) {
        repositoriesGrid.style.display = 'none';
        emptyState.style.display = 'block';
        return;
    }

    repositoriesGrid.style.display = 'flex';
    emptyState.style.display = 'none';

    repositoriesGrid.innerHTML = userRepositories.map((repo, index) => {
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
                        <span class="stat-value" id="stars-${repo.name}">${repo.stars || 0}</span>
                        <span class="stat-label">Stars</span>
                    </div>
                </div>
                <div class="repo-actions">
                    <button class="view-btn" data-repo="${repo.name}">
                        <i class="fas fa-${repo.visibility === 'private' ? 'lock' : 'eye'} btn-icon"></i>
                        <span>View</span>
                    </button>
                    <!-- Star button hidden for own repos -->
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

    // Add event listeners for repository actions
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
    
    return tags.slice(0, 3);
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
    // Add delete button listeners
    const deleteButtons = document.querySelectorAll('.delete-btn');
    deleteButtons.forEach(button => {
        button.addEventListener('click', handleDeleteRepository);
    });

    // Add view button listeners
    const viewButtons = document.querySelectorAll('.view-btn');
    viewButtons.forEach(button => {
        button.addEventListener('click', () => {
            const repoName = button.dataset.repo;
            // Open repository viewer (same as homepage)
            openRepository(repoName);
        });
    });

    // Add star button listeners
    const starButtons = document.querySelectorAll('.star-btn');
    starButtons.forEach(button => {
        button.addEventListener('click', () => {
            const repoName = button.dataset.repo;
            toggleStar(button, repoName);
        });
    });
}

async function handleDeleteRepository(event) {
    const button = event.currentTarget;
    const repoName = button.dataset.repo;
    
    if (!repoName) return;
    
    const confirmed = confirm(`Are you sure you want to delete the repository "${repoName}"? This action cannot be undone.`);
    
    if (confirmed) {
        try {
            const response = await fetch(`${API_BASE_URL}/repos/${currentUser.username}/${repoName}?uuid=${currentUser.uuid}`, {
                method: 'DELETE'
            });
            
            if (response.ok) {
                showNotification(`Repository "${repoName}" deleted successfully!`, 'success');
                await loadUserRepositories();
                updateUI();
            } else {
                const error = await response.json();
                showNotification(`Failed to delete repository: ${error.detail || 'Unknown error'}`, 'error');
            }
        } catch (error) {
            console.error('Delete failed:', error);
            showNotification('Failed to delete repository. Please try again later.', 'error');
        }
    }
}

function showSearchModal() {
    showNotification('Search functionality coming soon!', 'info');
}

async function toggleStar(button, repoName, repoOwner) {
    const isStarred = button.classList.contains('starred');

    if (!currentUser) {
        showNotification('Please log in to star repositories', 'error');
        return;
    }

    // Prevent creator from starring their own repo
    if (currentUser.username === repoOwner) {
        showNotification('You cannot star your own repository', 'error');
        return;
    }

    try {
        const method = isStarred ? 'DELETE' : 'POST';
        const response = await fetch(`${API_BASE_URL}/repos/${repoOwner}/${repoName}/star?uuid=${currentUser.uuid}`, {
            method: method
        });

        if (response.ok) {
            const result = await response.json();
            const newStars = result.stars;

            // Update button state
            if (isStarred) {
                button.classList.remove('starred');
                showNotification(`Unstarred ${repoName}`, 'info');
            } else {
                button.classList.add('starred');
                showNotification(`Starred ${repoName}!`, 'success');
            }

            // Update star count display
            const starsDisplay = document.getElementById(`stars-${repoName}`);
            if (starsDisplay) {
                starsDisplay.textContent = newStars;
            }

            // Update local repository data
            const repo = userRepositories.find(r => r.name === repoName);
            if (repo) {
                repo.stars = newStars;
            }
        } else {
            const error = await response.json();
            // If already starred, unstar it (toggle behavior)
            if (error.detail && error.detail.includes('already starred')) {
                // Auto-unstar since already starred
                const unstarResponse = await fetch(`${API_BASE_URL}/repos/${repoOwner}/${repoName}/star?uuid=${currentUser.uuid}`, {
                    method: 'DELETE'
                });
                if (unstarResponse.ok) {
                    const result = await unstarResponse.json();
                    button.classList.remove('starred');
                    showNotification(`Unstarred ${repoName}`, 'info');
                    const starsDisplay = document.getElementById(`stars-${repoName}`);
                    if (starsDisplay) starsDisplay.textContent = result.stars;
                    const repo = userRepositories.find(r => r.name === repoName);
                    if (repo) repo.stars = result.stars;
                }
            } else if (error.detail && error.detail.includes('not starred')) {
                // Auto-star since not starred
                const starResponse = await fetch(`${API_BASE_URL}/repos/${repoOwner}/${repoName}/star?uuid=${currentUser.uuid}`, {
                    method: 'POST'
                });
                if (starResponse.ok) {
                    const result = await starResponse.json();
                    button.classList.add('starred');
                    showNotification(`Starred ${repoName}!`, 'success');
                    const starsDisplay = document.getElementById(`stars-${repoName}`);
                    if (starsDisplay) starsDisplay.textContent = result.stars;
                    const repo = userRepositories.find(r => r.name === repoName);
                    if (repo) repo.stars = result.stars;
                }
            } else {
                showNotification(`Failed to toggle star: ${error.detail || 'Unknown error'}`, 'error');
            }
        }
    } catch (error) {
        console.error('Star toggle failed:', error);
        showNotification('Failed to toggle star. Please try again.', 'error');
    }
}

function cloneRepository(repoName) {
    if (currentUser) {
        showCloneModal(repoName, currentUser.username);
    } else {
        showNotification('Please log in to clone repositories', 'error');
    }
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
        <div class="modal clone-modal">
            <div class="modal-header">
                <h2><i class="fas fa-code-branch"></i> Clone Repository</h2>
                <div class="repo-source">
                    <span class="repo-path">${username}/${repoName}</span>
                    <span class="clone-badge">Ready to clone</span>
                </div>
            </div>
            <div class="modal-body">
                <div class="tab-content">
                    <div class="tab-pane active" id="commands">
                        <div class="command-section">
                            <h4>Minecraft Clone Commands</h4>
                            <div class="command-blocks">
                                <div class="command-block">
                                    <div class="command-header">
                                        <span class="command-type">Basic Clone</span>
                                        <button class="copy-btn" data-command="/git clone ${repoName}-clone http://127.0.0.1:8000/repos/${username}/${repoName}">
                                            <i class="fas fa-copy"></i>
                                        </button>
                                    </div>
                                    <code class="command-text">/git clone ${repoName}-clone http://127.0.0.1:8000/repos/${username}/${repoName}</code>
                                </div>
                                
                                <div class="command-block">
                                    <div class="command-header">
                                        <span class="command-type">With Custom Name</span>
                                        <button class="copy-btn" data-command="/git clone my-${repoName} http://127.0.0.1:8000/repos/${username}/${repoName}">
                                            <i class="fas fa-copy"></i>
                                        </button>
                                    </div>
                                    <code class="command-text">/git clone my-${repoName} http://127.0.0.1:8000/repos/${username}/${repoName}</code>
                                </div>
                            </div>
                            
                            <div class="clone-url-section">
                                <h4>Repository URL</h4>
                                <div class="url-block">
                                    <code class="url-text">http://127.0.0.1:8000/repos/${username}/${repoName}</code>
                                    <button class="copy-btn" data-command="http://127.0.0.1:8000/repos/${username}/${repoName}">
                                        <i class="fas fa-copy"></i>
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" id="cancelClone">
                    <i class="fas fa-times"></i> Cancel
                </button>
            </div>
        </div>
    `;
    return modal;
}

function setupCloneModal(modal) {
    const cancelBtn = modal.querySelector('#cancelClone');
    const tabBtns = modal.querySelectorAll('.tab-btn');
    const tabPanes = modal.querySelectorAll('.tab-pane');
    const previewName = modal.querySelector('#previewName');
    
    const closeModal = () => {
        document.body.removeChild(modal);
    };
    
    // Tab switching functionality
    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const targetTab = btn.dataset.tab;
            
            // Update active states
            tabBtns.forEach(b => b.classList.remove('active'));
            tabPanes.forEach(p => p.classList.remove('active'));
            
            btn.classList.add('active');
            modal.querySelector(`#${targetTab}`).classList.add('active');
        });
    });
    
    // Checkbox interactions (only if elements exist)
    const includeCommits = modal.querySelector('#includeCommits');
    const includeBlocks = modal.querySelector('#includeBlocks');
    
    if (includeCommits && includeBlocks) {
        [includeCommits, includeBlocks].forEach(checkbox => {
            checkbox.addEventListener('change', () => {
                updateClonePreview(modal);
            });
        });
    }
    
    cancelBtn.addEventListener('click', closeModal);
    
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            closeModal();
        }
    });
    
    
    // Enhanced copy button functionality
    modal.addEventListener('click', (e) => {
        if (e.target.closest('.copy-btn')) {
            const btn = e.target.closest('.copy-btn');
            const command = btn.dataset.command;
            
            navigator.clipboard.writeText(command).then(() => {
                const originalHTML = btn.innerHTML;
                btn.innerHTML = '<i class="fas fa-check"></i>';
                btn.classList.add('copied');
                
                setTimeout(() => {
                    btn.innerHTML = originalHTML;
                    btn.classList.remove('copied');
                }, 2000);
            });
        }
    });
    
    // Initialize preview and load repository stats
    updateClonePreview(modal);
    loadRepositoryStats(modal);
}

async function loadRepositoryStats(modal) {
    const repoPath = modal.querySelector('.repo-path').textContent;
    const [username, repoName] = repoPath.split('/');
    
    try {
        // Get commit history to count commits
        const commitsResponse = await fetch(`${API_BASE_URL}/users/${username}/${repoName}/log`);
        if (commitsResponse.ok) {
            const commits = await commitsResponse.json();
            modal.querySelector('#sourceCommits').textContent = commits.length;
        }
        
        // Get blocks data to count blocks
        const blocksResponse = await fetch(`${API_BASE_URL}/repos/${username}/${repoName}/head-blocks`);
        if (blocksResponse.ok) {
            const data = await blocksResponse.json();
            const blockCount = data.blocks ? data.blocks.length : 0;
            modal.querySelector('#sourceBlocks').textContent = blockCount;
            
            // Estimate size (rough calculation)
            const estimatedSize = Math.max(1, Math.round(blockCount * 0.1)); // Rough estimate
            modal.querySelector('#previewSize').textContent = `~${estimatedSize} KB`;
        }
        
        // Update preview
        updateClonePreview(modal);
        
    } catch (error) {
        console.error('Failed to load repository stats:', error);
        modal.querySelector('#sourceCommits').textContent = 'Unknown';
        modal.querySelector('#sourceBlocks').textContent = 'Unknown';
        modal.querySelector('#previewSize').textContent = 'Unknown';
    }
}

function updateClonePreview(modal) {
    const includeCommits = modal.querySelector('#includeCommits').checked;
    const includeBlocks = modal.querySelector('#includeBlocks').checked;
    const previewSize = modal.querySelector('#previewSize');
    const previewCommits = modal.querySelector('#previewCommits');
    
    const sourceCommits = modal.querySelector('#sourceCommits').textContent;
    const sourceBlocks = modal.querySelector('#sourceBlocks').textContent;
    
    const commitsCount = includeCommits && sourceCommits !== 'Loading...' && sourceCommits !== 'Unknown' ? sourceCommits : '0';
    const blocksCount = includeBlocks && sourceBlocks !== 'Loading...' && sourceBlocks !== 'Unknown' ? sourceBlocks : '0';
    
    previewCommits.textContent = `${commitsCount} commits`;
    
    // Estimate size based on what's included
    let sizeKB = 0;
    if (includeCommits) {
        sizeKB += parseInt(commitsCount) * 2; // Rough estimate per commit
    }
    if (includeBlocks) {
        sizeKB += parseInt(blocksCount) * 0.1; // Rough estimate per block
    }
    
    previewSize.textContent = sizeKB > 0 ? `~${Math.max(1, Math.round(sizeKB))} KB` : 'Minimal';
}

async function performClone(modal) {
    const includeCommits = modal.querySelector('#includeCommits').checked;
    const includeBlocks = modal.querySelector('#includeBlocks').checked;
    
    // Get source repository info and generate default clone name
    const repoPath = modal.querySelector('.repo-path').textContent;
    const [sourceUsername, sourceRepoName] = repoPath.split('/');
    const newRepoName = `${sourceRepoName}-clone`;
    
    if (!currentUser) {
        showNotification('You must be logged in to clone repositories', 'error');
        return;
    }
    
    try {
        // Show loading state
        const confirmBtn = modal.querySelector('#confirmClone');
        const originalText = confirmBtn.innerHTML;
        confirmBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Cloning...';
        confirmBtn.disabled = true;
        
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
            showNotification(`Repository "${newRepoName}" cloned successfully!`, 'success');
            
            // Refresh repositories list
            await loadUserRepositories();
            renderUserRepositories();
            
            document.body.removeChild(modal);
        } else {
            const error = await response.json();
            showNotification(`Failed to clone repository: ${error.detail || 'Unknown error'}`, 'error');
        }
        
        // Reset button state
        confirmBtn.innerHTML = originalText;
        confirmBtn.disabled = false;
        
    } catch (error) {
        console.error('Clone failed:', error);
        showNotification('Failed to clone repository. Please try again.', 'error');
        
        // Reset button state
        const confirmBtn = modal.querySelector('#confirmClone');
        confirmBtn.innerHTML = '<i class="fas fa-download"></i> Clone Repository';
        confirmBtn.disabled = false;
    }
}

function downloadRepository(repoName) {
    if (currentUser) {
        showNotification(`Downloading ${repoName}...`, 'info');
        // Simulate download - in real implementation this would trigger actual download
    } else {
        showNotification('Please log in to download repositories', 'error');
    }
}

function displayBlocks(blocks, modal, headData) {
    const blocksViewer = modal.querySelector('#blocksViewer');
    
    if (blocks.length === 0) {
        blocksViewer.innerHTML = '<p class="empty-message">No blocks found in this repository</p>';
        return;
    }
    
    const blocksHTML = blocks.map(block => `
        <div class="block-item">
            <div class="block-header">
                <span class="block-type">${block.type || 'Unknown'}</span>
                <span class="block-coords">(${block.x || 0}, ${block.y || 0}, ${block.z || 0})</span>
            </div>
            <div class="block-data">
                ${block.data ? JSON.stringify(block.data, null, 2) : 'No additional data'}
            </div>
        </div>
    `).join('');
    
    blocksViewer.innerHTML = blocksHTML;
}

async function openRepository(repoName) {
    console.log('openRepository called with:', repoName);
    console.log('Available repositories:', userRepositories);
    
    // Find the repository data
    const repo = userRepositories.find(r => r.name === repoName);
    console.log('Found repository:', repo);
    
    if (repo) {
        // Show repository content with actual data
        console.log('Showing repository content modal');
        await showRepositoryContentModal(repo);
    } else {
        console.log('Repository not found');
        showNotification('Repository not found', 'info');
    }
}

async function showRepositoryContentModal(repo) {
    const modal = createRepositoryContentModal(repo);
    document.body.appendChild(modal);
    
    // Setup modal event listeners
    setupRepositoryContentModal(modal);
    
    // Load repository content
    await loadRepositoryContent(repo.name, modal);
}

function createRepositoryContentModal(repo) {
    const modal = document.createElement('div');
    modal.className = 'modal-overlay';
    modal.innerHTML = `
        <div class="modal repository-viewer">
            <div class="modal-header">
                <h2><i class="fas fa-cube"></i> ${repo.name}</h2>
                <div class="repo-meta-info">
                    <span class="visibility-badge ${repo.visibility}">
                        <i class="fas fa-${repo.visibility === 'public' ? 'globe' : 'lock'}"></i>
                        ${repo.visibility}
                    </span>
                    <span class="language-badge">
                        <span class="language-dot ${repo.language ? repo.language.toLowerCase() : 'minecraft'}"></span>
                        ${repo.language || 'Minecraft'}
                    </span>
                </div>
            </div>
            <div class="modal-body">
                <div class="repo-content">
                    <div class="content-tabs">
                        <button class="tab-btn active" data-tab="overview">Overview</button>
                        <button class="tab-btn" data-tab="commits">Commits</button>
                        <button class="tab-btn" data-tab="blocks">Blocks</button>
                    </div>
                    
                    <div class="tab-content">
                        <div class="tab-pane active" id="overview">
                            <div class="overview-section">
                                <h3>Description</h3>
                                <p>${repo.description || 'No description provided'}</p>
                            </div>
                            
                            <div class="overview-section">
                                <h3>Repository Information</h3>
                                <div class="info-grid">
                                    <div class="info-item">
                                        <strong>Created:</strong> ${repo.created || 'Unknown'}
                                    </div>
                                    <div class="info-item">
                                        <strong>Updated:</strong> ${repo.updated || 'Unknown'}
                                    </div>
                                    <div class="info-item">
                                        <strong>Commits:</strong> <span id="commitCount">Loading...</span>
                                    </div>
                                    <div class="info-item">
                                        <strong>Total Blocks:</strong> <span id="blockCount">Loading...</span>
                                    </div>
                                </div>
                            </div>
                            
                            <div class="overview-section">
                                <h3>Actions</h3>
                                <div class="action-buttons">
                                    <button class="btn btn-primary" onclick="cloneRepository('${repo.name}')">
                                        <i class="fas fa-code-branch"></i> Clone
                                    </button>
                                </div>
                            </div>
                        </div>
                        
                        <div class="tab-pane" id="commits">
                            <div class="commits-list" id="commitsList">
                                <div class="loading-spinner">
                                    <i class="fas fa-spinner fa-spin"></i>
                                    <span>Loading commit history...</span>
                                </div>
                            </div>
                        </div>
                        
                        <div class="tab-pane" id="blocks">
                            <div class="blocks-viewer" id="blocksViewer">
                                <div class="loading-spinner">
                                    <i class="fas fa-spinner fa-spin"></i>
                                    <span>Loading blocks data...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" id="closeContent">Close</button>
            </div>
        </div>
    `;
    return modal;
}

function setupRepositoryContentModal(modal) {
    const closeBtn = modal.querySelector('#closeContent');
    const tabBtns = modal.querySelectorAll('.tab-btn');
    const tabPanes = modal.querySelectorAll('.tab-pane');
    
    const closeModal = () => {
        document.body.removeChild(modal);
    };
    
    closeBtn.addEventListener('click', closeModal);
    
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            closeModal();
        }
    });
    
    // Tab switching
    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const targetTab = btn.dataset.tab;
            
            // Update active states
            tabBtns.forEach(b => b.classList.remove('active'));
            tabPanes.forEach(p => p.classList.remove('active'));
            
            btn.classList.add('active');
            modal.querySelector(`#${targetTab}`).classList.add('active');
        });
    });
}

async function loadRepositoryContent(repoName, modal) {
    // Find repository data to check visibility
    const repo = userRepositories.find(r => r.name === repoName);
    
    // Check if repository is private and user doesn't have access
    // For private repos, only the owner can view the content
    if (repo && repo.visibility === 'private' && !currentUser) {
        showRestrictedAccessMessage(modal, repo);
        return;
    }
    
    if (!currentUser) {
        showNotification('Please log in to view repository content', 'error');
        return;
    }
    
    try {
        // Load commit history
        const commitsResponse = await fetch(`${API_BASE_URL}/users/${currentUser.username}/${repoName}/log`);
        if (commitsResponse.ok) {
            const commits = await commitsResponse.json();
            displayCommits(commits, modal);
            modal.querySelector('#commitCount').textContent = commits.length;
        } else {
            console.error('Failed to load commits');
            modal.querySelector('#commitCount').textContent = '0';
            modal.querySelector('#commitsList').innerHTML = '<p class="error-message">Failed to load commit history</p>';
        }
        
        // Load current blocks
        const blocksResponse = await fetch(`${API_BASE_URL}/repos/${currentUser.username}/${repoName}/head-blocks`);
        if (blocksResponse.ok) {
            const headData = await blocksResponse.json();
            displayBlocks(headData.blocks, modal, headData);
            modal.querySelector('#blockCount').textContent = headData.blocks.length;
        } else {
            console.error('Failed to load blocks');
            modal.querySelector('#blockCount').textContent = '0';
            modal.querySelector('#blocksViewer').innerHTML = '<p class="error-message">No blocks found in this repository</p>';
        }
        
    } catch (error) {
        console.error('Failed to load repository content:', error);
        modal.querySelector('#commitCount').textContent = 'Error';
        modal.querySelector('#blockCount').textContent = 'Error';
        modal.querySelector('#commitsList').innerHTML = '<p class="error-message">Failed to load repository content</p>';
        modal.querySelector('#blocksViewer').innerHTML = '<p class="error-message">Failed to load repository content</p>';
    }
}

function displayCommits(commits, modal) {
    const commitsList = modal.querySelector('#commitsList');
    
    if (commits.length === 0) {
        commitsList.innerHTML = '<p class="empty-message">No commits found in this repository</p>';
        return;
    }
    
    commitsList.innerHTML = commits.map(commit => `
        <div class="commit-item">
            <div class="commit-header">
                <div class="commit-info">
                    <h4 class="commit-name">${commit.commit_name}</h4>
                    <span class="commit-hash">${commit.commit_hash.substring(0, 8)}</span>
                </div>
                <div class="commit-time">
                    ${formatDateTime(new Date(commit.time_stamp))}
                </div>
            </div>
            <div class="commit-message">${commit.message || 'No message'}</div>
        </div>
    `).join('');
}

function showRestrictedAccessMessage(modal, repo) {
    // Update overview tab with restricted access message
    const overviewTab = modal.querySelector('#overview');
    overviewTab.innerHTML = `
        <div class="overview-section">
            <div class="restricted-access-notice">
                <div class="access-icon">
                    <i class="fas fa-lock"></i>
                </div>
                <h3>Private Repository</h3>
                <p>This repository is private and its content is restricted.</p>
                <p class="access-description">Only the repository owner can view commits, blocks, and detailed information.</p>
            </div>
        </div>
        
        <div class="overview-section">
            <h3>Available Actions</h3>
            <div class="action-buttons">
                <button class="btn btn-primary" onclick="cloneRepository('${repo.name}')">
                    <i class="fas fa-code-branch"></i> Clone
                </button>
            </div>
            <p class="clone-note">You can clone this repository if you have access permissions.</p>
        </div>
        
        <div class="overview-section">
            <h3>Repository Information</h3>
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
                    <strong>Created:</strong> ${repo.created || 'Unknown'}
                </div>
                <div class="info-item">
                    <strong>Updated:</strong> ${repo.updated || 'Unknown'}
                </div>
            </div>
        </div>
    `;
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
        <div class="profile-username"><i class="fas fa-user"></i> ${currentUser ? currentUser.username : 'User'}</div>
        <a href="#" id="signOutLink"><i class="fas fa-sign-out-alt"></i> Sign out</a>
    `;
    
    document.querySelector('.profile-menu').appendChild(dropdown);
    
    // Add event listeners
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
    localStorage.removeItem('authToken');
    localStorage.removeItem('username');
    currentUser = null;
    
    showNotification('You have been signed out successfully', 'success');
    closeProfileMenu();
    
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
        notification.remove();
    }, 3000);
}

function formatDateTime(date) {
    const options = {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    };
    return date.toLocaleDateString('en-US', options);
}
