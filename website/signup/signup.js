import { checkIfFieldsAreFilledRight } from "../checks.js";

const signupBtn = document.getElementById("signup");
const signupForm = document.getElementById("signupform")
signupBtn.addEventListener('click', signup);

async function signup(event) {
	event.preventDefault();
	checkIfFieldsAreFilledRight(signupForm);
}