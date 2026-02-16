const API_BASE_URL = "http://127.0.0.1:8000";

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

export async function checkUserExists(email, username){
    const url = `${API_BASE_URL}/users/exists?username=${encodeURIComponent(username)}&email=${encodeURIComponent(email)}`;

    const response = await fetch(url);
    if (!response.ok){
      console.error("Server error during existence check");
      return null;
    }
    return await response.json();
}

async function checkUuidExists(uuid) {
  const url = `${API_BASE_URL}/users/uuid?uuid=${uuid}`
  const response = await fetch(url);
  return await response.json();

}

export async function createUser(username, email, hashedPassword, mcUsername){
  const url = `${API_BASE_URL}/users`;

  const userData = {
    username: username,
    email: email,
    password: hashedPassword,
    minecraft_username: mcUsername
  };

  const response = await fetch(url,{
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(userData)
  });

  if(!response.ok)return null;
  return await response.json();
}

export async function loginUser(username, hashedPassword) {
  const url = `${API_BASE_URL}/login`;

  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: hashedPassword})
  });

  if (!response.ok) return { error: response.detail || "Login failed"};
  return await response.json();
  
}

export async function generateHash(author, password, algorithm = 'SHA-256') {

  const combineInput = author.toLowerCase() + password;
  const msgBuffer = new TextEncoder().encode(combineInput);
  const hashBuffer = await crypto.subtle.digest(algorithm, msgBuffer);

  const hashArray = Array.from(new Uint8Array(hashBuffer));
  const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');

  return hashHex;
}
