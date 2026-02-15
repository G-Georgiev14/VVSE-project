export const rules = {
  email: /^[a-zA-Z0-9._]+@[a-zA-Z-]+.[a-zA-Z]{2,}$/,
  username: /^[a-zA-Z0-9]{3,16}$/,
  password: /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).{8,30}$/,
  minecraftUsername: /^[a-zA-Z0-9]{3,16}$/
}

export function validateInput(id, regex, errorId, errorMsg){
  const input = document.getElementById(id);
  const errorDisplay = document.getElementById(errorId);

  console.log(id, input, errorDisplay);

  input.oninput = function() {
    // Only show green border for valid inputs during typing
    if (regex.test(input.value)) {
      input.style.border = "2px solid #2ecc71";
      errorDisplay.innerText = "";
    } else {
      // Reset to default border during typing
      input.style.border = "3px solid #8B7355";
      errorDisplay.innerText = "";
    }
  };

  // Function to validate on button click
  input.validateOnClick = function() {
    if (input.value === ""){
      input.style.border = "2px solid #cd1a06";
      input.style.transition = "transform 0.3s ease";
      input.classList.add('input-error');
      setTimeout(() => input.classList.remove('input-error'), 500);
      errorDisplay.innerText = "cannot be empty";
      return false;
    }
    else if (regex.test(input.value)) {
      input.style.border = "2px solid #2ecc71";
      errorDisplay.innerText = "";
      return true;
    }
    else {
      input.style.border = "2px solid #cd1a06";
      input.style.transition = "transform 0.3s ease";
      input.classList.add('input-error');
      setTimeout(() => input.classList.remove('input-error'), 500);
      errorDisplay.innerText = errorMsg;
      return false;
    }
  };
}

export function check_user_exists(email, username, minecraft_username){
    
}