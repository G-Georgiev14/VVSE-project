const DATABASE_API_URL = "http://127.0.0.1:8000";

// Validation rules for different input types
const validationRules = {
	username: {
		min: 3,
		max: 16,
		pattern: /^[a-zA-Z0-9_]+$/,
		message: "Username must be 3-16 characters (letters, numbers, underscore only)"
	},
	password: {
		min: 8,
		max: 50,
		pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]/,
		message: "Password must be 8-50 characters with uppercase, lowercase, number, and special character (@$!%*?&)"
	},
	mcUsername: {
		min: 3,
		max: 16,
		pattern: /^[a-zA-Z0-9_]+$/,
		message: "Minecraft username must be 3-16 characters (letters, numbers, underscore only)"
	},
	email: {
		pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
		message: "Please enter a valid email address"
	}
};

// Validate a single field and apply visual feedback
function validateField(input, rule, errorElement, isSubmit = false) {
	const value = input.value.trim();
	let isValid = true;
	let errorMessage = '';

	// Check if empty
	if (!value) {
		errorMessage = "This field is required";
		isValid = false;
	} else {
		// Check minimum length
		if (rule.min && value.length < rule.min) {
			errorMessage = `Too short (minimum ${rule.min} characters)`;
			isValid = false;
		}

		// Check maximum length
		if (rule.max && value.length > rule.max) {
			errorMessage = `Too long (maximum ${rule.max} characters)`;
			isValid = false;
		}

		// Check pattern
		if (rule.pattern && !rule.pattern.test(value)) {
			errorMessage = rule.message;
			isValid = false;
		}
	}

	// Update error message
	errorElement.textContent = errorMessage;

	// Apply visual styling
	if (isValid) {
		input.classList.remove('invalid');
		input.classList.add('valid');
		input.style.setProperty('border', '3px solid #4CAF50', 'important');
		input.style.background = '';
		input.style.color = '';
	} else {
		input.classList.remove('valid');
		input.classList.add('invalid');
		input.style.setProperty('border', '3px solid red', 'important');
		input.style.background = '';
		input.style.color = '';
	}

	return isValid;
}

// Validate entire signup form
export function validateSignupForm(form) {
	const fields = [
		{ id: 'username', input: form.querySelector('#username'), rule: validationRules.username },
		{ id: 'email', input: form.querySelector('#email'), rule: validationRules.email },
		{ id: 'password', input: form.querySelector('#password'), rule: validationRules.password },
		{ id: 'mcUsername', input: form.querySelector('#mcUsername'), rule: validationRules.mcUsername }
	];

	let isFormValid = true;
	let firstInvalidField = null;

	// Validate each field
	fields.forEach(field => {
		const errorElement = document.getElementById(`${field.id}Error`);
		
		if (field.input && errorElement && field.rule) {
			const isValid = validateField(field.input, field.rule, errorElement, true);
			
			if (!isValid) {
				isFormValid = false;
				if (!firstInvalidField) {
					firstInvalidField = field.input;
				}
			}
		}
	});

	// Focus on first invalid field
	if (!isFormValid && firstInvalidField) {
		firstInvalidField.focus();
	}

	return { isValid: isFormValid };
}

// Validate individual field for real-time validation
export function validateSingleField(inputId, ruleKey) {
	const input = document.getElementById(inputId);
	const errorElement = document.getElementById(`${inputId}Error`);
	const rule = validationRules[ruleKey];

	if (!input || !errorElement || !rule) return true;

	return validateField(input, rule, errorElement);
}

// Login validation rules
const loginValidationRules = {
	loginUsername: {
		min: 3,
		max: 16,
		pattern: /^[a-zA-Z0-9_]+$/,
		message: "Username must be 3-16 characters (letters, numbers, underscore only)"
	},
	loginPassword: {
		min: 1,
		message: "Password is required"
	}
};

// Validate login field
function validateLoginField(inputId, errorId, isSubmit = false) {
	const input = document.getElementById(inputId);
	const errorElement = document.getElementById(errorId);
	const ruleKey = inputId.replace('login', '').toLowerCase();
	const rule = loginValidationRules[inputId];

	if (!input || !errorElement || !rule) return true;

	const value = input.value.trim();
	let isValid = true;
	let errorMessage = '';

	// Check if empty
	if (!value) {
		errorMessage = "This field is required";
		isValid = false;
	} else {
		// Check minimum length
		if (rule.min && value.length < rule.min) {
			errorMessage = `Too short (minimum ${rule.min} characters)`;
			isValid = false;
		}

		// Check maximum length
		if (rule.max && value.length > rule.max) {
			errorMessage = `Too long (maximum ${rule.max} characters)`;
			isValid = false;
		}

		// Check pattern
		if (rule.pattern && !rule.pattern.test(value)) {
			errorMessage = rule.message;
			isValid = false;
		}
	}

	// Update error message
	errorElement.textContent = errorMessage;

	// Apply visual styling
	if (isValid) {
		input.classList.remove('invalid');
		input.classList.add('valid');
		input.style.setProperty('border', '3px solid #4CAF50', 'important');
		input.style.background = '';
		input.style.color = '';
	} else {
		input.classList.remove('valid');
		input.classList.add('invalid');
		input.style.setProperty('border', '3px solid red', 'important');
		input.style.background = '';
		input.style.color = '';
	}

	return isValid;
}

// Validate entire login form
export function validateLoginForm(form) {
	const fields = [
		{ id: 'loginUsername', input: form.querySelector('#loginUsername'), rule: loginValidationRules.loginUsername },
		{ id: 'loginPassword', input: form.querySelector('#loginPassword'), rule: loginValidationRules.loginPassword }
	];

	let isFormValid = true;
	let firstInvalidField = null;

	// Validate each field
	fields.forEach(field => {
		const errorElement = document.getElementById(`${field.id}Error`);
		
		if (field.input && errorElement && field.rule) {
			const isValid = validateLoginField(field.id, `${field.id}Error`, true);
			
			if (!isValid) {
				isFormValid = false;
				if (!firstInvalidField) {
					firstInvalidField = field.input;
				}
			}
		}
	});

	// Focus on first invalid field
	if (!isFormValid && firstInvalidField) {
		firstInvalidField.focus();
	}

	return { isValid: isFormValid };
}

// Validate individual login field for real-time validation
export function validateSingleLoginField(inputId) {
	return validateLoginField(inputId, `${inputId}Error`);
}

// Server connection check
async function canConnectToServer() {
	try {
		const response = await fetch(`${DATABASE_API_URL}/db-check`, { 
			method: 'GET', 
			mode: 'cors',
			headers: {
				'Content-Type': 'application/json'
			}
		});
		return response.ok;
	} catch (error) {
		console.error('Server connection error:', error);
		return false;
	}
}

// Display messages
export function displayMessage(type, message) {
	// Clear all messages first
	const containers = [
		document.getElementById("successMessage"),
		document.getElementById("errorMessage"),
		document.getElementById("loginSuccessMessage"),
		document.getElementById("loginErrorMessage")
	];

	containers.forEach(container => {
		if (container) {
			container.textContent = "";
			container.style.display = "none";
			container.classList.remove('error', 'success');
		}
	});

	// Show appropriate message
	let targetContainer = null;
	if (type === "success") {
		targetContainer = document.getElementById("successMessage") || 
						 document.getElementById("loginSuccessMessage");
	} else if (type === "error") {
		targetContainer = document.getElementById("errorMessage") || 
						 document.getElementById("loginErrorMessage");
	}

	if (targetContainer) {
		targetContainer.textContent = message;
		targetContainer.style.display = "block";
		targetContainer.classList.add(type);
		
		// Auto-hide after 5 seconds
		setTimeout(() => {
			targetContainer.style.display = "none";
			targetContainer.classList.remove(type);
		}, 5000);
	}
}

// Check if user exists and register
export async function checkIfUserExists(form) {
	const serverConnected = await canConnectToServer();
	
	if (!serverConnected) {
		displayMessage("error", "Cannot connect to server. Please check your connection.");
		return false;
	}

	const userData = {
		username: form.querySelector('#username')?.value,
		email: form.querySelector('#email')?.value,
		password: form.querySelector('#password')?.value,
		mcUsername: form.querySelector('#mcUsername')?.value
	};

	try {
		const requestData = {
			username: userData.username,
			email: userData.email,
			password: userData.password,
			minecraft_username: userData.mcUsername
		};
		
		console.log('Sending data to server:', requestData);
		
		let response;
		let retries = 3;
		
		// Retry mechanism for server connection issues
		for (let i = 0; i < retries; i++) {
			try {
				response = await fetch(`${DATABASE_API_URL}/users`, {
					method: 'POST',
					headers: {
						'Content-Type': 'application/json',
					},
					body: JSON.stringify(requestData)
				});
				
				// If successful, break out of retry loop
				if (response.ok) {
					break;
				}
				
				// If not successful and not last retry, wait and try again
				if (i < retries - 1) {
					console.log(`Retry ${i + 1}/${retries - 1} - waiting 1 second...`);
					await new Promise(resolve => setTimeout(resolve, 1000));
				}
			} catch (fetchError) {
				console.error(`Fetch attempt ${i + 1} failed:`, fetchError);
				if (i < retries - 1) {
					console.log(`Retry ${i + 1}/${retries - 1} - waiting 1 second...`);
					await new Promise(resolve => setTimeout(resolve, 1000));
				} else {
					throw fetchError;
				}
			}
		}

		console.log('Server response status:', response.status);
		console.log('Server response headers:', response.headers);

		const result = await response.json();
		console.log('Server response body:', result);

		if (response.ok && result.status === 'success') {
			console.log('=== REGISTRATION SUCCESSFUL ===');
			console.log('Server response:', result);
			return true;
		} else {
			console.log('Registration failed with details:', result);
			displayMessage("error", result.detail || "Registration failed");
			return false;
		}
	} catch (error) {
		console.error('Registration error:', error);
		displayMessage("error", "Registration failed. Please try again.");
		return false;
	}
}

// Login user function
export async function loginUser(username, hashedPassword) {
	const serverConnected = await canConnectToServer();
	
	if (!serverConnected) {
		displayMessage("error", "Cannot connect to server. Please check your connection.");
		return null;
	}

	try {
		const response = await fetch(`${DATABASE_API_URL}/login`, {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json',
			},
			body: JSON.stringify({
				username: username,
				password: hashedPassword
			})
		});

		const result = await response.json();

		if (response.ok && result.status === 'successful') {
			return {
				username: result.username,
				minecraft_username: result.minecraft_username,
				uuid: result.uuid
			};
		} else {
			return { error: result.detail || "Login failed" };
		}
	} catch (error) {
		console.error('Login error:', error);
		return { error: "Server error. Please try again later." };
	}
}

// Generate password hash
export async function generateHash(username, password) {
	const encoder = new TextEncoder();
	const data = encoder.encode(username + password);
	const hashBuffer = await crypto.subtle.digest('SHA-256', data);
	const hashArray = Array.from(new Uint8Array(hashBuffer));
	const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
	return hashHex;
}

// Repository Management API Functions

// Create a new repository
export async function createRepository(username, repoName, uuid) {
	const serverConnected = await canConnectToServer();
	
	if (!serverConnected) {
		displayMessage("error", "Cannot connect to server. Please check your connection.");
		return false;
	}

	try {
		const response = await fetch(`${DATABASE_API_URL}/repos/${username}`, {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json',
			},
			body: JSON.stringify({
				repo_name: repoName,
				uuid: uuid
			})
		});

		const result = await response.json();

		if (response.ok) {
			displayMessage("success", `Repository "${repoName}" created successfully!`);
			return true;
		} else {
			displayMessage("error", result.detail || "Failed to create repository");
			return false;
		}
	} catch (error) {
		console.error('Create repository error:', error);
		displayMessage("error", "Failed to create repository. Please try again.");
		return false;
	}
}

// List all repositories for a user
export async function listRepositories(username, uuid) {
	const serverConnected = await canConnectToServer();
	
	if (!serverConnected) {
		console.error("Cannot connect to server");
		return [];
	}

	try {
		const response = await fetch(`${DATABASE_API_URL}/repos/${username}?uuid=${uuid}`, {
			method: 'GET',
			headers: {
				'Content-Type': 'application/json',
			}
		});

		const result = await response.json();

		if (response.ok) {
			return result.repos || [];
		} else {
			console.error('Failed to list repositories:', result.detail);
			return [];
		}
	} catch (error) {
		console.error('List repositories error:', error);
		return [];
	}
}

// Check if a repository exists
export async function repositoryExists(username, repoName) {
	const serverConnected = await canConnectToServer();
	
	if (!serverConnected) {
		return false;
	}

	try {
		const response = await fetch(`${DATABASE_API_URL}/repos/${username}/${repoName}/exists`, {
			method: 'GET',
			headers: {
				'Content-Type': 'application/json',
			}
		});

		const result = await response.json();

		if (response.ok) {
			return result.exists;
		} else {
			console.error('Failed to check repository existence:', result.detail);
			return false;
		}
	} catch (error) {
		console.error('Repository exists check error:', error);
		return false;
	}
}

// Clone a repository
export async function cloneRepository(username, sourceUsername, sourceRepo, newRepoName, uuid) {
	const serverConnected = await canConnectToServer();
	
	if (!serverConnected) {
		displayMessage("error", "Cannot connect to server. Please check your connection.");
		return false;
	}

	try {
		const response = await fetch(`${DATABASE_API_URL}/repos/${username}/clone`, {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json',
			},
			body: JSON.stringify({
				source_username: sourceUsername,
				source_repo: sourceRepo,
				new_repo_name: newRepoName,
				uuid: uuid
			})
		});

		const result = await response.json();

		if (response.ok) {
			displayMessage("success", `Repository cloned successfully as "${newRepoName}"!`);
			return true;
		} else {
			displayMessage("error", result.detail || "Failed to clone repository");
			return false;
		}
	} catch (error) {
		console.error('Clone repository error:', error);
		displayMessage("error", "Failed to clone repository. Please try again.");
		return false;
	}
}

// Get commit history for a repository
export async function getCommitHistory(username, repoName) {
	const serverConnected = await canConnectToServer();
	
	if (!serverConnected) {
		console.error("Cannot connect to server");
		return [];
	}

	try {
		const response = await fetch(`${DATABASE_API_URL}/users/${username}/${repoName}/log`, {
			method: 'GET',
			headers: {
				'Content-Type': 'application/json',
			}
		});

		const result = await response.json();

		if (response.ok) {
			return result;
		} else {
			console.error('Failed to get commit history:', result.detail);
			return [];
		}
	} catch (error) {
		console.error('Get commit history error:', error);
		return [];
	}
}

// Get blocks from a specific commit
export async function getCommitBlocks(username, repoName, commitHash) {
	const serverConnected = await canConnectToServer();
	
	if (!serverConnected) {
		console.error("Cannot connect to server");
		return [];
	}

	try {
		const response = await fetch(`${DATABASE_API_URL}/repos/${username}/${repoName}/commits/${commitHash}/blocks`, {
			method: 'GET',
			headers: {
				'Content-Type': 'application/json',
			}
		});

		const result = await response.json();

		if (response.ok) {
			return result;
		} else {
			console.error('Failed to get commit blocks:', result.detail);
			return [];
		}
	} catch (error) {
		console.error('Get commit blocks error:', error);
		return [];
	}
}

// Get head blocks (latest commit) for clone preview
export async function getHeadBlocks(username, repoName) {
	const serverConnected = await canConnectToServer();
	
	if (!serverConnected) {
		console.error("Cannot connect to server");
		return null;
	}

	try {
		const response = await fetch(`${DATABASE_API_URL}/repos/${username}/${repoName}/head-blocks`, {
			method: 'GET',
			headers: {
				'Content-Type': 'application/json',
			}
		});

		const result = await response.json();

		if (response.ok) {
			return result;
		} else {
			console.error('Failed to get head blocks:', result.detail);
			return null;
		}
	} catch (error) {
		console.error('Get head blocks error:', error);
		return null;
	}
}

// Push commits to remote repository
export async function pushRepository(username, repoName, remoteUsername, remoteRepo, uuid) {
	const serverConnected = await canConnectToServer();
	
	if (!serverConnected) {
		displayMessage("error", "Cannot connect to server. Please check your connection.");
		return false;
	}

	try {
		const response = await fetch(`${DATABASE_API_URL}/repos/${username}/${repoName}/push`, {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json',
			},
			body: JSON.stringify({
				remote_username: remoteUsername,
				remote_repo: remoteRepo,
				uuid: uuid
			})
		});

		const result = await response.json();

		if (response.ok) {
			displayMessage("success", `Repository pushed to "${remoteUsername}/${remoteRepo}"!`);
			return true;
		} else {
			displayMessage("error", result.detail || "Failed to push repository");
			return false;
		}
	} catch (error) {
		console.error('Push repository error:', error);
		displayMessage("error", "Failed to push repository. Please try again.");
		return false;
	}
}

// Pull commits from remote repository
export async function pullRepository(username, repoName, remoteUsername, remoteRepo, uuid) {
	const serverConnected = await canConnectToServer();
	
	if (!serverConnected) {
		displayMessage("error", "Cannot connect to server. Please check your connection.");
		return false;
	}

	try {
		const response = await fetch(`${DATABASE_API_URL}/repos/${username}/${repoName}/pull`, {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json',
			},
			body: JSON.stringify({
				remote_username: remoteUsername,
				remote_repo: remoteRepo,
				uuid: uuid
			})
		});

		const result = await response.json();

		if (response.ok) {
			displayMessage("success", `Repository pulled from "${remoteUsername}/${remoteRepo}"!`);
			return true;
		} else {
			displayMessage("error", result.detail || "Failed to pull repository");
			return false;
		}
	} catch (error) {
		console.error('Pull repository error:', error);
		displayMessage("error", "Failed to pull repository. Please try again.");
		return false;
	}
}

// Export validation rules
export { validationRules };