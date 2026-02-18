import { validateInput, rules, loginUser, generateHash } from './checks.js';

const login_btn = document.getElementById('login');
login_btn.addEventListener('click', start_login);

// Set up validation for login inputs
validateInput('loginUsername', rules.username, 'loginUsernameError', 'Invalid username format.');
validateInput('loginPassword', rules.password, 'loginPasswordError', 'Password must be 8-30 characters with uppercase, lowercase, and numbers.');

async function start_login(){
  const usernameInput = document.getElementById('loginUsername');
  const passwordInput = document.getElementById('loginPassword');

  // Validate all inputs on button click
  const isUsernameValid = usernameInput.validateOnClick();
  const isPasswordValid = passwordInput.validateOnClick();

  // Only proceed if all inputs are valid
  if (isUsernameValid && isPasswordValid) {
    
    const hashedPassword = await generateHash(usernameInput.value, passwordInput.value);

    const result = await loginUser(usernameInput.value, hashedPassword);

    if (result && !result.error){
      console.log('Login successful for user:', result.username);
      console.log('Login result:', result);
      showLoginSuccessMessage(`Login successful! Welcome back, ${result.minecraft_username}!`);
      
      // Store authentication data
      localStorage.setItem('authToken', result.uuid);
      localStorage.setItem('username', result.username);
      localStorage.setItem('minecraftUsername', result.minecraft_username);
      
      // Redirect to homepage after a short delay
      setTimeout(() => {
        console.log('Redirecting to homepage...');
        window.location.href = '/homepage/homepage.html'
      }, 1500);
    }
    else{
      showLoginErrorMessage('Invalid username or password.');
    }
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
