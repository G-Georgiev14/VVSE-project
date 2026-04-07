import { validateLoginForm, loginUser, generateHash, displayMessage, validateSingleLoginField } from '../checks.js';

const loginForm = document.getElementById('loginform');

// Handle login form submission
async function handleLogin(event) {
	event.preventDefault();

	// Validate entire form
	const validation = validateLoginForm(loginForm);

	if (!validation.isValid) {
		return;
	}

	// Get form values
	const usernameInput = document.getElementById('loginUsername');
	const passwordInput = document.getElementById('loginPassword');

	const username = usernameInput.value.trim();
	const password = passwordInput.value;

	try {
		// Generate password hash
		const hashedPassword = await generateHash(username, password);

		// Attempt login
		const result = await loginUser(username, hashedPassword);

		if (result && !result.error) {
			// Show success message
			displayMessage("success", `Login successful! Welcome back, ${result.minecraft_username}!`);
			
			// Store authentication data
			localStorage.setItem('authToken', result.uuid);
			localStorage.setItem('username', result.username);
			localStorage.setItem('minecraftUsername', result.minecraft_username);
			
			// Redirect to homepage after a short delay
			setTimeout(() => {
				window.location.href = '../homepage/homepage.html';
			}, 1500);
		} else {
			// Show error message
			const errorMessage = result?.error || 'Invalid username or password.';
			displayMessage("error", errorMessage);
		}
	} catch (error) {
		console.error('Login error:', error);
		displayMessage("error", "Login failed. Please try again later.");
	}
}

// Add event listeners when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
	// Add submit event listener to form
	loginForm.addEventListener('submit', handleLogin);

	// Add real-time validation for each field
	const fields = ['loginUsername', 'loginPassword'];

	fields.forEach(fieldId => {
		const input = document.getElementById(fieldId);
		if (input) {
			// Validate as user types
			input.addEventListener('input', () => {
				validateSingleLoginField(fieldId);
			});
			
			// Validate when field loses focus
			input.addEventListener('blur', () => {
				validateSingleLoginField(fieldId);
			});
		}
	});
});


