const fs=require('fs'),assert=require('assert/strict');
(async()=>{
 const auth=await import('data:text/javascript;base64,'+Buffer.from(fs.readFileSync('desktop/local-auth.js')).toString('base64'));
 const oldForm=global.FormData,oldLocal=global.localStorage,oldFetch=global.fetch;
 let users={},session,signedIn=0,recovery;
 global.fetch=()=>{throw Error('Local sign-in must not call a backend');};
 global.FormData=class{constructor(form){this.form=form;}*[Symbol.iterator](){yield* Object.entries(this.form.fields);}};
 global.localStorage={removeItem:()=>{},setItem:(key,value)=>{assert.equal(key,'bar.session');session=value;}};
 const digest=async password=>({salt:'salt',hash:'hash:'+password}),read=async()=>structuredClone(users),write=async(key,value)=>{assert.equal(key,'users');users=structuredClone(value);};
 const mount=signup=>{const nodes={'#auth-form':{},'#switch-auth':{},'#auth-feedback':{},'#forgot-password':{},'#recovery-value':{},'#recovery-continue':{}},controls=[{},{}],root={innerHTML:'',querySelector:s=>nodes[s],querySelectorAll:()=>controls};auth.mountLocalAuth(root,signup,{read,write,digest,onSwitch:()=>{},onSignedIn:async()=>{signedIn++;}});assert(!root.innerHTML.includes('verification'));return nodes;};
 try{
  let nodes=mount(true);await nodes['#auth-form'].onsubmit({preventDefault(){},currentTarget:{fields:{first:'Existing',last:'User',email:'person@example.com',password:'secret123'}}});assert.equal(signedIn,0);assert(nodes['#recovery-value'].textContent);recovery=nodes['#recovery-value'].textContent;await nodes['#recovery-continue'].onclick();assert.equal(signedIn,1);assert.equal(session,'person@example.com');assert.equal(users[session].hash,'hash:secret123');
  nodes=mount(false);await nodes['#auth-form'].onsubmit({preventDefault(){},currentTarget:{fields:{email:session,password:'wrong123'}}});assert.equal(signedIn,1);assert.equal(nodes['#auth-feedback'].textContent,'Email or password is incorrect.');
  nodes=mount(false);await nodes['#auth-form'].onsubmit({preventDefault(){},currentTarget:{fields:{email:session,password:'secret123'}}});assert.equal(signedIn,2);
  nodes=mount(false);nodes['#forgot-password'].onclick();await nodes['#auth-form'].onsubmit({preventDefault(){},currentTarget:{fields:{email:session,code:recovery,password:'updated123',confirmation:'updated123'}}});
  assert.equal(signedIn,2);assert.notEqual(nodes['#recovery-value'].textContent,recovery);assert.equal(users[session].hash,'hash:updated123');await nodes['#recovery-continue'].onclick();assert.equal(nodes['#auth-feedback'].textContent,'Password reset. Sign in with your new password.');
  nodes=mount(false);await nodes['#auth-form'].onsubmit({preventDefault(){},currentTarget:{fields:{email:session,password:'updated123'}}});assert.equal(signedIn,3);
  console.log('PASS: offline signup, session creation, wrong-password rejection, password reset/code replacement, and sign-in with no OTP or backend.');
 }finally{global.FormData=oldForm;global.localStorage=oldLocal;global.fetch=oldFetch;}
})().catch(e=>{console.error(e);process.exitCode=1});
