const normalizeCode=code=>String(code||'').replace(/[-\s]/g,'').toLowerCase();
export function newRecoveryCode(){return [...crypto.getRandomValues(new Uint8Array(20))].map(x=>x.toString(16).padStart(2,'0')).join('').toUpperCase().match(/.{4}/g).join('-');}
async function withRecovery(user,digest){const code=newRecoveryCode(),h=await digest(normalizeCode(code));return {user:{...user,recoverySalt:h.salt,recoveryHash:h.hash},recoveryCode:code};}
export async function prepareLocalAccount(signup,fields,users,digest){
  const email=fields.email.trim().toLowerCase(),password=fields.password;
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)||password.length<6)throw Error('Enter a valid email and password (at least 6 characters).');
  let account;
  if(signup){
    if(users[email])throw Error('This account already exists. Please sign in.');
    if(!fields.first.trim()||!fields.last.trim())throw Error('Enter your first and last name.');
    const h=await digest(password);account={email,first:fields.first.trim(),last:fields.last.trim(),...h,photo:null};
  }else{
    account=users[email];const h=account&&await digest(password,account.salt);
    if(!account||h.hash!==account.hash)throw Error('Email or password is incorrect.');
  }
  const result=!account.recoveryHash||fields.refreshRecovery==='on'?await withRecovery(account,digest):{user:{...account}};
  return {...result,purpose:signup?'signup':'signin'};
}
export async function preparePasswordReset(fields,users,digest){
  const email=String(fields.email||'').trim().toLowerCase(),account=users[email],code=normalizeCode(fields.code);
  if(!fields.password||fields.password.length<6)throw Error('Enter a password of at least 6 characters.');
  if(fields.password!==fields.confirmation)throw Error('Passwords do not match.');
  const proof=/^[a-f0-9]{40}$/.test(code)&&account?.recoveryHash&&await digest(code,account.recoverySalt);
  if(!proof||proof.hash!==account.recoveryHash)throw Error('Email or recovery code is incorrect, or recovery was never set up.');
  const password=await digest(fields.password);
  return withRecovery({...account,...password},digest);
}
const eye='<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></svg>';
function passwordBox(name,label,autocomplete){return `<div class="password-box"><input class="field" name="${name}" id="auth-${name}" type="password" aria-label="${label}" placeholder="${label}" minlength="6" required autocomplete="${autocomplete}"><button type="button" class="password-eye" data-password="auth-${name}" aria-controls="auth-${name}" aria-label="Show ${label.toLowerCase()}" aria-pressed="false">${eye}</button></div>`;}
function wireEyes(root){root.querySelectorAll('[data-password]').forEach(button=>{button.onclick=()=>{const field=root.querySelector('#'+button.dataset.password),visible=field.type==='password';field.type=visible?'text':'password';button.setAttribute('aria-pressed',String(visible));button.setAttribute('aria-label',(visible?'Hide ':'Show ')+field.getAttribute('aria-label').toLowerCase());button.classList.toggle('revealed',visible);};});}
export function mountLocalAuth(root,signup,{read,write,digest,onSwitch,onSignedIn}){
 const brand='<div class="auth-brand"><img class="auth-logo" src="/assets/DBAKSRLogo.jpeg" alt="District Bar Association, Kasur logo"><span class="brand-copy"><span>Bar Members List</span><span>District Bar Association, Kasur</span></span></div>';
 const $=s=>root.querySelector(s);
 async function enter(email){localStorage.setItem('bar.session',email);await onSignedIn();}
 function showRecovery(code,continueAction){
  root.innerHTML=`<div class="auth-box">${brand}<h1>Save your recovery code</h1><p>Store this code somewhere private. It resets your password on this app/device without email or phone verification. A new code replaces the previous one.</p><code id="recovery-value" class="recovery-value"></code><button type="button" class="primary" id="recovery-continue">I saved my code - continue</button><p class="small" id="auth-feedback" aria-live="polite"></p></div>`;
  $('#recovery-value').textContent=code;
  $('#recovery-continue').onclick=async()=>{const button=$('#recovery-continue');button.disabled=true;try{await continueAction();}catch(error){$('#auth-feedback').textContent=error.message;button.disabled=false;}};
 }
 function paint(reset=false){
  root.innerHTML=`<div class="auth-box">${brand}<h1>${reset?'Reset password':signup?'Create your account':'Welcome back'}</h1><form id="auth-form" class="form">${signup&&!reset?'<input class="field" name="first" placeholder="First name" required><input class="field" name="last" placeholder="Last name" required>':''}<input class="field" type="email" name="email" placeholder="Email" aria-label="Email" required autocomplete="email">${reset?'<p class="small">Enter the recovery code saved from this app. If you never saved a code, sign in with your existing password first to set one up.</p><input class="field" name="code" placeholder="Recovery code" aria-label="Recovery code" required autocomplete="off" spellcheck="false">':''}${passwordBox('password',reset?'New password':'Password',reset||signup?'new-password':'current-password')}${reset?passwordBox('confirmation','Confirm new password','new-password'):''}${!signup&&!reset?'<label class="recovery-option"><input name="refreshRecovery" type="checkbox"> Create a new recovery code</label>':''}<button class="primary" type="submit">${reset?'Reset password':signup?'Create account':'Sign in'}</button></form><p class="small" id="auth-feedback" aria-live="polite"></p>${!signup&&!reset?'<button class="link" id="forgot-password" type="button">Forgot password?</button>':''}<button class="link" id="switch-auth" type="button">${reset?'Back to sign in':signup?'Already have an account? Sign in':'New here? Create account'}</button></div>`;
  wireEyes(root);
  $('#switch-auth').onclick=()=>reset?paint():onSwitch(!signup);
  if(!signup&&!reset)$('#forgot-password').onclick=()=>paint(true);
  $('#auth-form').onsubmit=async event=>{
   event.preventDefault();const fields=Object.fromEntries(new FormData(event.currentTarget)),controls=[...root.querySelectorAll('button,input')];controls.forEach(c=>c.disabled=true);
   try{
    const users=await read('users',{});
    if(reset){
     const pending=await preparePasswordReset(fields,users,digest),current=await read('users',{}),email=pending.user.email;
     if(current[email]?.recoveryHash!==users[email].recoveryHash||current[email]?.hash!==users[email].hash)throw Error('Account changed. Try again using the latest recovery code.');
     current[email]={...current[email],hash:pending.user.hash,salt:pending.user.salt,recoveryHash:pending.user.recoveryHash,recoverySalt:pending.user.recoverySalt};await write('users',current);localStorage.removeItem('bar.session');
     showRecovery(pending.recoveryCode,async()=>{paint();$('#auth-feedback').textContent='Password reset. Sign in with your new password.';});
    }else{
     const pending=await prepareLocalAccount(signup,fields,users,digest),email=pending.user.email;
     if(signup||pending.recoveryCode){const current=await read('users',{});if(signup&&current[email])throw Error('An account already exists. Please sign in.');if(!signup&&current[email]?.hash!==users[email].hash)throw Error('Account changed. Please sign in again.');current[email]=signup?pending.user:{...current[email],recoveryHash:pending.user.recoveryHash,recoverySalt:pending.user.recoverySalt};await write('users',current);}
     if(pending.recoveryCode)showRecovery(pending.recoveryCode,()=>enter(email));else await enter(email);
    }
   }catch(error){$('#auth-feedback').textContent=error.message;controls.forEach(c=>c.disabled=false);}
  };
 }
 paint();
}
