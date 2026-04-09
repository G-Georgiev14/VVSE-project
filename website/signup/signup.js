import { validateSignupForm, displayMessage, checkIfUserExists, validateSingleField } from "../checks.js";

// Check if user is already logged in
function checkAuthStatus() {
    const authToken = localStorage.getItem('authToken');
    const username = localStorage.getItem('username');
    
    if (authToken && username) {
        // User is already logged in, redirect to homepage
        window.location.href = '../homepage/homepage.html';
        return true;
    }
    return false;
}

const signupForm = document.getElementById("signupform");

// Handle signup form submission
async function handleSignup(event) {
	event.preventDefault();

	// Validate entire form
	const validation = validateSignupForm(signupForm);

	if (!validation.isValid) {
		return;
	}

	// Proceed with registration
	await checkIfUserExists(signupForm);
}

// Add event listeners when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
	// Check if user is already logged in
	if (checkAuthStatus()) {
		return; // Stop execution if redirecting
	}
	
	// Add submit event listener to form
	signupForm.addEventListener('submit', handleSignup);

	// Add real-time validation for each field
	const fields = [
		{ inputId: 'username', ruleKey: 'username' },
		{ inputId: 'email', ruleKey: 'email' },
		{ inputId: 'password', ruleKey: 'password' },
		{ inputId: 'mcUsername', ruleKey: 'mcUsername' }
	];

	fields.forEach(field => {
		const input = document.getElementById(field.inputId);
		if (input) {
			// Validate as user types
			input.addEventListener('input', () => {
				validateSingleField(field.inputId, field.ruleKey);
			});
			
			// Validate when field loses focus
			input.addEventListener('blur', () => {
				validateSingleField(field.inputId, field.ruleKey);
			});
		}
	});
});