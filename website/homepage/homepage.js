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
         window.location.href = '../login/login.html';
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
    try {
        console.log('Fetching all public repositories from:', `${API_BASE_URL}/public-repos`);
        // Call backend API to list all public repositories
        const response = await fetch(`${API_BASE_URL}/public-repos`);
        
        console.log('Response status:', response.status);
        
        if (response.ok) {
            const result = await response.json();
            console.log('API response:', result);
            const repoNames = result.repos || [];
            console.log('Number of public repos found:', repoNames.length);
            
            // Transform repo names into repository objects with additional metadata
            repositories = repoNames.map(repo => ({
                name: repo.name,
                username: repo.username,
                description: `Minecraft build repository`,
                visibility: repo.visibility || 'public',
                language: 'Minecraft',
                updated: 'Recently',
                commits: 0
            }));
            
            console.log('Transformed repositories:', repositories);
        } else {
            const errorText = await response.text();
            console.error('Failed to load repositories - Status:', response.status);
            console.error('Error response text:', errorText);
            try {
                const error = JSON.parse(errorText);
                console.error('Parsed error:', error);
            } catch {
                console.error('Could not parse error as JSON');
            }
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
    // Sign up button
    const signupBtn = document.querySelector('#signupBtn');
    if (signupBtn) {
        signupBtn.addEventListener('click', handleSignup);
    }

    // Login button
    const loginBtn = document.querySelector('#loginBtn');
    if (loginBtn) {
        loginBtn.addEventListener('click', handleLogin);
    }

    // New repository button
    const newRepoBtn = document.querySelector('.new-repo-btn');
    if (newRepoBtn) {
        newRepoBtn.addEventListener('click', () => {
            window.location.href = 'userpage.html';
        });
    }

    // Search button
    const searchBtn = document.querySelector('.search-btn');
    if (searchBtn) {
        searchBtn.addEventListener('click', showSearchModal);
    }

    // Create repository button in empty state - redirect to userpage
    const createRepoBtn = document.querySelector('.create-repo-btn');
    if (createRepoBtn) {
        createRepoBtn.addEventListener('click', () => {
            window.location.href = 'userpage.html';
        });
    }

    // Navigation items - allow default link behavior, no custom handling needed
    // const navItems = document.querySelectorAll('.nav-item');
    // navItems.forEach(item => {
    //     item.addEventListener('click', (e) => {
    //         e.preventDefault();
    //         const section = item.dataset.section || item.textContent.trim();
    //         handleNavigation(section);
    //     });
    // });

    // Repository cards
    document.addEventListener('click', (e) => {
        if (e.target.closest('.repo-name a')) {
            e.preventDefault();
            const repoName = e.target.closest('.repo-name a').textContent.trim();
            openRepository(repoName);
        }
        
                
        // View button clicks
        if (e.target.closest('.view-btn')) {
            const btn = e.target.closest('.view-btn');
            console.log('View button clicked:', btn);
            console.log('Button disabled:', btn.disabled);
            console.log('Button dataset:', btn.dataset);
            const repoName = btn.dataset.repo;
            console.log('Repository name:', repoName);
            if (!btn.disabled) {
                openRepository(repoName);
            } else {
                console.log('View button is disabled, cannot open repository');
            }
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

function handleSignup() {
    // Redirect to signup page
    window.location.href = '../signup/signup.html';
}

function handleLogin() {
    // Redirect to login page
    window.location.href = '../login/login.html';
}

function updateUI() {
    // Update username in profile
    const usernameElement = document.querySelector('.username');
    if (usernameElement && currentUser) {
        usernameElement.textContent = currentUser.username;
    }

    // Show/hide profile menu and auth buttons based on login status
    const profileMenu = document.querySelector('#profileMenu');
    const signupBtn = document.querySelector('#signupBtn');
    const loginBtn = document.querySelector('#loginBtn');
    const searchBtn = document.querySelector('.search-btn');
    const newRepoBtn = document.querySelector('.new-repo-btn');
    
    if (currentUser) {
        // User is logged in - show profile menu and all buttons, hide auth buttons
        if (profileMenu) {
            profileMenu.style.display = 'flex';
        }
        if (signupBtn) {
            signupBtn.style.display = 'none';
        }
        if (loginBtn) {
            loginBtn.style.display = 'none';
        }
        if (searchBtn) {
            searchBtn.style.display = 'flex';
        }
        if (newRepoBtn) {
            newRepoBtn.style.display = 'flex';
        }
    } else {
        // User is not logged in - hide profile menu, search, and new repo buttons, show auth buttons
        if (profileMenu) {
            profileMenu.style.display = 'none';
        }
        if (signupBtn) {
            signupBtn.style.display = 'flex';
        }
        if (loginBtn) {
            loginBtn.style.display = 'flex';
        }
        if (searchBtn) {
            searchBtn.style.display = 'none';
        }
        if (newRepoBtn) {
            newRepoBtn.style.display = 'none';
        }
    }

    // Update repositories display
    renderRepositories();
}

function renderRepositories() {
    const repositoriesGrid = document.querySelector('.repositories-grid');
    const emptyState = document.querySelector('.empty-state');
    const username = currentUser ? currentUser.username : null;

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
                        <a href="#" data-repo="${repo.name}" data-username="${repo.username}">
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
                <div class="repo-author">
                    <i class="fas fa-user"></i>
                    <span class="author-name">${repo.username}</span>
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
                    ${!currentUser ? 
                    `<button class="view-btn login-redirect-btn">
                        <i class="fas fa-lock btn-icon"></i>
                        <span>Login to view</span>
                    </button>` :
                    `<button class="view-btn" data-repo="${repo.name}" data-username="${repo.username}">
                        <i class="fas fa-${repo.visibility === 'private' ? 'lock' : 'eye'} btn-icon"></i>
                        <span>View</span>
                    </button>`}
                    <button class="star-btn" data-repo="${repo.name}">
                        <i class="fas fa-star btn-icon"></i>
                        <span>Star</span>
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
                    ${repo.created ? `
                    <span class="repo-created">
                        <i class="fas fa-calendar-plus"></i>
                        Created ${repo.created}
                    </span>` : ''}
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

    // Add login redirect button listeners
    const loginRedirectBtns = document.querySelectorAll('.login-redirect-btn');
    loginRedirectBtns.forEach(button => {
        button.addEventListener('click', () => {
            window.location.href = '../login/login.html';
        });
    });

    // Add view button listeners for logged-in users
    const viewButtons = document.querySelectorAll('.view-btn:not(.login-redirect-btn)');
    viewButtons.forEach(button => {
        button.addEventListener('click', () => {
            const repoName = button.dataset.repo;
            const username = button.dataset.username;
            openRepository(repoName, username);
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

async function openRepository(repoName, username) {
    console.log('openRepository called with:', repoName, 'username:', username);
    console.log('Available repositories:', repositories);
    
    // Find the repository data
    const repo = repositories.find(r => r.name === repoName && r.username === username);
    console.log('Found repository:', repo);
    
    if (repo) {
        // Show repository content with actual data
        console.log('Showing repository content modal');
        await showRepositoryContentModal(repo, username);
    } else {
        console.log('Repository not found');
        showNotification('Repository not found', 'info');
    }
}

async function showRepositoryContentModal(repo, username) {
    const modal = createRepositoryContentModal(repo, username);
    document.body.appendChild(modal);
    
    // Setup modal event listeners
    setupRepositoryContentModal(modal);
    
    // Load repository content
    await loadRepositoryContent(repo.name, username, modal);
}

function createRepositoryContentModal(repo, username) {
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
                                        <strong>Owner:</strong> ${username || 'Unknown'}
                                    </div>
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
                                    <button class="btn btn-primary" onclick="cloneRepository('${repo.name}', '${username}')">
                                        <i class="fas fa-code-branch"></i> Clone
                                    </button>
                                    <button class="btn btn-secondary" onclick="downloadRepository('${repo.name}')">
                                        <i class="fas fa-download"></i> Download
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

async function loadRepositoryContent(repoName, username, modal) {
    // Find the repository data to check visibility
    const repo = repositories.find(r => r.name === repoName && r.username === username);
    
    // Check if repository is private and user doesn't have access
    // For private repos, only the owner can view the content
    if (repo && repo.visibility === 'private' && (!currentUser || currentUser.username !== username)) {
        showRestrictedAccessMessage(modal, repo);
        return;
    }
    
    if (!currentUser) {
        showNotification('Please log in to view repository content', 'error');
        return;
    }
    
    try {
        // Load commit history using the repository owner's username
        const commitsResponse = await fetch(`${API_BASE_URL}/users/${username}/${repoName}/log`);
        if (commitsResponse.ok) {
            const commits = await commitsResponse.json();
            displayCommits(commits, modal);
            modal.querySelector('#commitCount').textContent = commits.length;
        } else {
            console.error('Failed to load commits');
            modal.querySelector('#commitCount').textContent = '0';
            modal.querySelector('#commitsList').innerHTML = '<p class="error-message">Failed to load commit history</p>';
        }
        
        // Load current blocks using the repository owner's username
        const blocksResponse = await fetch(`${API_BASE_URL}/repos/${username}/${repoName}/head-blocks`);
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
    
    // Update commits tab with restricted message
    modal.querySelector('#commitsList').innerHTML = `
        <div class="restricted-access-notice">
            <div class="access-icon">
                <i class="fas fa-lock"></i>
            </div>
            <h3>Commits Restricted</h3>
            <p>Commit history is not available for private repositories.</p>
        </div>
    `;
    
    // Update blocks tab with restricted message
    modal.querySelector('#blocksViewer').innerHTML = `
        <div class="restricted-access-notice">
            <div class="access-icon">
                <i class="fas fa-lock"></i>
            </div>
            <h3>Blocks Data Restricted</h3>
            <p>Blocks data is not available for private repositories.</p>
        </div>
    `;
    
    // Update counts
    modal.querySelector('#commitCount').textContent = 'Restricted';
    modal.querySelector('#blockCount').textContent = 'Restricted';
}

function displayBlocks(blocks, modal, headData) {
    const blocksViewer = modal.querySelector('#blocksViewer');
    
    if (blocks.length === 0) {
        blocksViewer.innerHTML = '<p class="empty-message">No blocks found in this repository</p>';
        return;
    }
    
    // Group blocks by type for better organization
    const blocksByType = {};
    blocks.forEach(block => {
        if (!blocksByType[block.block_name]) {
            blocksByType[block.block_name] = [];
        }
        blocksByType[block.block_name].push(block);
    });
    
    // Create blocks display
    let blocksHTML = `
        <div class="blocks-summary">
            <h3>Current Commit: ${headData.commit_name}</h3>
            <p class="commit-message">${headData.message || 'No message'}</p>
            <div class="blocks-stats">
                <span class="stat-item">Total Blocks: ${blocks.length}</span>
                <span class="stat-item">Block Types: ${Object.keys(blocksByType).length}</span>
            </div>
        </div>
        
        <div class="blocks-list">
            <h4>Block Types</h4>
    `;
    
    Object.entries(blocksByType).forEach(([blockType, blockList]) => {
        blocksHTML += `
            <div class="block-type-group">
                <h5>
                    <i class="fas fa-cube"></i>
                    ${blockType} (${blockList.length})
                </h5>
                <div class="block-coordinates">
                    ${blockList.slice(0, 5).map(block => 
                        `<span class="coord">(${block.x}, ${block.y}, ${block.z})</span>`
                    ).join('')}
                    ${blockList.length > 5 ? `<span class="more-text">... and ${blockList.length - 5} more</span>` : ''}
                </div>
            </div>
        `;
    });
    
    blocksHTML += '</div>';
    blocksViewer.innerHTML = blocksHTML;
}

function cloneRepository(repoName, username) {
    if (currentUser) {
        showCloneModal(repoName, username);
    } else {
        showNotification('Please log in to clone repositories', 'error');
    }
}

function downloadRepository(repoName) {
    if (currentUser) {
        showNotification(`Downloading ${repoName}...`, 'info');
        // Simulate download - in real implementation this would trigger actual download
        setTimeout(() => {
            showNotification(`${repoName} downloaded successfully!`, 'success');
        }, 2000);
    } else {
        showNotification('Please log in to download repositories', 'error');
    }
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
        <div class="modal clone-modal">
            <div class="modal-header">
                <h2><i class="fas fa-code-branch"></i> Clone Repository</h2>
                <div class="repo-source">
                    <span class="repo-path">${username}/${repoName}</span>
                    <span class="clone-badge">Ready to clone</span>
                </div>
            </div>
            <div class="modal-body">
                <div class="clone-tabs">
                    <button class="tab-btn active" data-tab="quick">Quick Clone</button>
                    <button class="tab-btn" data-tab="commands">Commands</button>
                </div>
                
                <div class="tab-content">
                    <div class="tab-pane active" id="quick">
                        <div class="quick-clone-section">
                            <div class="clone-options">
                                <div class="option-group">
                                    <label class="checkbox-label">
                                        <input type="checkbox" id="includeCommits" checked>
                                        <span class="checkmark"></span>
                                        Include all commits
                                    </label>
                                    <label class="checkbox-label">
                                        <input type="checkbox" id="includeBlocks" checked>
                                        <span class="checkmark"></span>
                                        Include all blocks
                                    </label>
                                </div>
                            </div>

                            <div class="clone-preview">
                                <h4>Preview</h4>
                                <div class="preview-item">
                                    <i class="fas fa-folder"></i>
                                    <span id="previewName">${repoName}-clone</span>
                                </div>
                                <div class="preview-details">
                                    <span id="previewSize">Calculating...</span>
                                    <span id="previewCommits">Unknown commits</span>
                                </div>
                            </div>
                        </div>
                    </div>
                    
                    <div class="tab-pane" id="commands">
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
    const confirmBtn = modal.querySelector('#confirmClone');
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
    
    // Checkbox interactions
    const includeCommits = modal.querySelector('#includeCommits');
    const includeBlocks = modal.querySelector('#includeBlocks');
    
    [includeCommits, includeBlocks].forEach(checkbox => {
        checkbox.addEventListener('change', () => {
            updateClonePreview(modal);
        });
    });
    
    cancelBtn.addEventListener('click', closeModal);
    
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            closeModal();
        }
    });
    
    confirmBtn.addEventListener('click', () => {
        performClone(modal);
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
            modal.querySelector('#sourceSize').textContent = `~${estimatedSize} KB`;
        }
        
        // Update preview
        updateClonePreview(modal);
        
    } catch (error) {
        console.error('Failed to load repository stats:', error);
        modal.querySelector('#sourceCommits').textContent = 'Unknown';
        modal.querySelector('#sourceBlocks').textContent = 'Unknown';
        modal.querySelector('#sourceSize').textContent = 'Unknown';
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
            await loadRepositories();
            renderRepositories();
            
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
    
    // Update UI to show auth buttons and hide profile menu
    updateUI();
    
    // Show notification
    showNotification('You have been signed out successfully', 'success');
    
    // Close the profile dropdown
    closeProfileMenu();
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
