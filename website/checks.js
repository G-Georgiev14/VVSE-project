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