import { validateInput, rules } from './checks.js';

const signup_btn = document.getElementById('signup');
signup_btn.addEventListener('click', start_signup);

validateInput('email', rules.email, 'emailError', 'Invalid email format.');
validateInput('username', rules.username, 'usernameError', 'Invalid username format.');
validateInput('password', rules.password, 'passwordError', 'Invalid password format.');
validateInput('minecraftUsername', rules.minecraftUsername, 
              'minecraftUsernameError', 'Invalid minecarft username format.');

function start_signup(){
  const emailInput = document.getElementById('email');
  const usernameInput = document.getElementById('username');
  const passwordInput = document.getElementById('password');
  const minecraftUsernameInput = document.getElementById('minecraftUsername');

  // Validate all inputs on button click
  const isEmailValid = emailInput.validateOnClick();
  const isUsernameValid = usernameInput.validateOnClick();
  const isPasswordValid = passwordInput.validateOnClick();
  const isMinecraftUsernameValid = minecraftUsernameInput.validateOnClick();

  // Only proceed if all inputs are valid
  if (isEmailValid && isUsernameValid && isPasswordValid && isMinecraftUsernameValid) {
    const email = emailInput.value;
    const username = usernameInput.value;
    const password = passwordInput.value;
    const minecraft_username = minecraftUsernameInput.value;

    // Store user in localStorage
    const users = JSON.parse(localStorage.getItem('minecraftUsers') || '[]');
    
    // Check if user already exists
    const userExists = users.some(user => user.username === username || user.email === email);
    
    if (userExists) {
      showErrorMessage('User with this username or email already exists!');
      return;
    }
    
    // Add new user
    const newUser = {
      email,
      username,
      password,
      minecraft_username,
      createdAt: new Date().toISOString()
    };
    
    users.push(newUser);
    localStorage.setItem('minecraftUsers', JSON.stringify(users));
    
    console.log('User signed up successfully:', username);
    showSuccessMessage('Account created successfully! You can now login.');
    
    // Clear form
    emailInput.value = '';
    usernameInput.value = '';
    passwordInput.value = '';
    minecraftUsernameInput.value = '';
    
  } else {
    console.log('Form has validation errors');
  }
}

function showSuccessMessage(message) {
  const successDiv = document.getElementById('successMessage');
  successDiv.textContent = message;
  successDiv.style.display = 'block';
  setTimeout(() => {
    successDiv.style.display = 'none';
  }, 5000);
}

function showErrorMessage(message) {
  const errorDiv = document.getElementById('errorMessage');
  errorDiv.textContent = message;
  errorDiv.style.display = 'block';
  setTimeout(() => {
    errorDiv.style.display = 'none';
  }, 5000);
}
