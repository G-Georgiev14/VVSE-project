import { validateInput, rules, checkUserExists, createUser } from './checks.js';

const signup_btn = document.getElementById('signup');
signup_btn.addEventListener('click', start_signup);

validateInput('email', rules.email, 'emailError', 'Invalid email format.');
validateInput('username', rules.username, 'usernameError', 'Invalid username format.');
validateInput('password', rules.password, 'passwordError', 'Invalid password format.');
validateInput('mcUsername', rules.minecraftUsername, 
              'mcUsernameError', 'Invalid minecarft username format.');

async function start_signup(){
  const emailInput = document.getElementById('email');
  const usernameInput = document.getElementById('username');
  const passwordInput = document.getElementById('password');
  const mcUsernameInput = document.getElementById('mcUsername');

  const emailValidate = emailInput.validateOnClick();
  const usernameValidate = usernameInput.validateOnClick();
  const passwordValidate = passwordInput.validateOnClick();
  const mcUsernameValidate = mcUsernameInput.validateOnClick()

  if(emailValidate && usernameValidate && passwordValidate && mcUsernameValidate){

      const userExistsData = await checkUserExists(emailInput.value, usernameInput.value);

      if (userExistsData && (userExistsData.name || userExistsData.email)) {
      showErrorMessage('User with this username or email already exists!');
      return;
    }

    const result = await createUser(
      usernameInput.value,
      emailInput.value,
      passwordInput.value,
      mcUsernameInput.value
    );

    if(result){
      console.log('User signed up successfully:', username);
      showSuccessMessage('Account created successfully! You can now login.');
    
      // Clear form
      emailInput.value = '';
      usernameInput.value = '';
      passwordInput.value = '';
      mcUsernameInput.value = '';
    }
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
