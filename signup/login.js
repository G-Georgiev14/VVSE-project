import { validateInput, rules } from './checks.js';

const login_btn = document.getElementById('login');
login_btn.addEventListener('click', start_login);

// Set up validation for login inputs
validateInput('loginUsername', rules.username, 'loginUsernameError', 'Invalid username format.');
validateInput('loginPassword', rules.password, 'loginPasswordError', 'Password must be 8-30 characters with uppercase, lowercase, and numbers.');

function start_login(){
  const usernameInput = document.getElementById('loginUsername');
  const passwordInput = document.getElementById('loginPassword');

  // Validate all inputs on button click
  const isUsernameValid = usernameInput.validateOnClick();
  const isPasswordValid = passwordInput.validateOnClick();

  // Get input values
  const username = usernameInput.value;
  const password = passwordInput.value;

  // Only proceed if all inputs are valid
  if (isUsernameValid && isPasswordValid) {
    
    // Get stored users from localStorage
    const users = JSON.parse(localStorage.getItem('minecraftUsers') || '[]');
    
    // Find user with matching username and password
    const user = users.find(u => u.username === username && u.password === password);
    
    if (user) {
      console.log('Login successful for user:', username);
      showLoginSuccessMessage(`Login successful! Welcome back, ${user.minecraft_username}!`);
      
      // Store current session
      localStorage.setItem('currentUser', JSON.stringify(user));
      
      // Here you would typically redirect to dashboard/home
      setTimeout(() => {
        // window.location.href = 'dashboard.html';
        console.log('Would redirect to dashboard...');
      }, 2000);
    } else {
      console.log('Invalid username or password');
      showLoginErrorMessage('Invalid username or password.');
    }
  } else {
    console.log('Form has validation errors');
    showLoginErrorMessage('Please fix all validation errors before logging in.');
  }
}

function showLoginSuccessMessage(message) {
  const successDiv = document.getElementById('loginSuccessMessage');
  successDiv.textContent = message;
  successDiv.style.display = 'block';
  setTimeout(() => {
    successDiv.style.display = 'none';
  }, 5000);
}

function showLoginErrorMessage(message) {
  const errorDiv = document.getElementById('loginErrorMessage');
  errorDiv.textContent = message;
  errorDiv.style.display = 'block';
  setTimeout(() => {
    errorDiv.style.display = 'none';
  }, 5000);
}
