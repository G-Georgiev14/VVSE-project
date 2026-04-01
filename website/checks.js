const DATABASE_API_URL = "http://127.0.0.1:8000";

const Rules = {
	username: { min: 3, max: 16 },
	password: { min: 8, max: 20 },
	mcUsername: { min: 3, max: 16 },
	email: { regex: /^[^\s@]+@[^\s@]+\.[^\s@]+$/ }
};


export function checkIfFieldsAreFilledRight(form){

	const inputs = form.querySelectorAll('input');
	let isAllValid = true;

	inputs.forEach(input => {
		const rule = Rules[input.id];
		const val = input.value.trim(); 
		const errorSpan = document.getElementById(`${input.id}Error`);
		let error = "";

		if(!val) error = "Required!";
		else if(input.id === 'email' && !rule.regex.test(val)) error = "Invalid Email!";
		else if(rule){
			if(rule.min && val.length < rule.min) error = `Too short (Min ${rule.min})`;
			if(rule.max && val.length > rule.max) error = `Too long (Max ${rule.max})`
		} 

		const isInvalid = error !== "";

		if(isInvalid){
			input.classList.remove('invalid');
			void input.offsetWidth;
			isAllValid = false;
		}

		input.classList.toggle('invalid', isInvalid);
		input.classList.toggle('valid', !isInvalid);

		if(errorSpan) errorSpan.textContent = error;
	});

	return isAllValid;
}

async function canConnectToServer(){
	const URL = `${DATABASE_API_URL}/db-check`;
	try{
		const response = await fetch(URL, { method: 'GET', node: 'cors'});
		if(response.ok) return true;
		else return false;
	}

	catch(error){
		return false;
	}
}

export function displayMessage(type, message){
	const successDisplay = document.getElementById("successMessage");
	const errorDisplay = document.getElementById("errorMessage");

	successDisplay.textContent = "";
	errorDisplay.textContent = "";
	successDisplay.style.display = "none";
	errorDisplay.style.display = "none";

	if (type === "success"){
		successDisplay.textContent = message;
		successDisplay.style.display = "block";
	}

	else if (type === "error"){
		errorDisplay.textContent = message;
		errorDisplay.style.display = "block";
	}

	else alert("criticalError");
}


export async function checkIfUserExists(form){
	console.log(canConnectToServer());
	if(canConnectToServer()){
		const user = form.querySelectorAll("input");

	}

	else{
		alert("Cannot connect to server");
	}
}