const fs=require('fs'),assert=require('assert/strict'),crypto=require('crypto');
(async()=>{
 const auth=await import('data:text/javascript;base64,'+Buffer.from(fs.readFileSync('desktop/local-auth.js')).toString('base64'));
 const digest=async(password,salt=crypto.randomBytes(16).toString('hex'))=>({salt,hash:crypto.pbkdf2Sync(password,Buffer.from(salt,'hex'),120000,32,'sha256').toString('hex')});
 const signup=await auth.prepareLocalAccount(true,{email:'person@example.com',password:'original123',first:'First',last:'Last'},{},digest),account={...signup.user,photo:'saved-photo',custom:'keep'};
 const users={[account.email]:account},fields={email:account.email,code:signup.recoveryCode,password:'changed123',confirmation:'changed123'};
 assert.equal(signup.recoveryCode.replaceAll('-','').length,40);assert(!JSON.stringify(account).includes(signup.recoveryCode));
 for(const changed of [{code:''},{code:'A'.repeat(40)},{email:'missing@example.com'},{confirmation:'mismatch'}])await assert.rejects(auth.preparePasswordReset({...fields,...changed},users,digest));
 const reset=await auth.preparePasswordReset(fields,users,digest);assert.equal(reset.user.photo,'saved-photo');assert.equal(reset.user.custom,'keep');assert.notEqual(reset.user.hash,account.hash);assert.notEqual(reset.recoveryCode,signup.recoveryCode);
 const updated={[account.email]:reset.user};await assert.rejects(auth.preparePasswordReset(fields,updated,digest));
 await assert.rejects(auth.prepareLocalAccount(false,{email:account.email,password:'original123'},updated,digest));
 const signin=await auth.prepareLocalAccount(false,{email:account.email,password:'changed123'},updated,digest);assert(!signin.recoveryCode);
 const rotated=await auth.prepareLocalAccount(false,{email:account.email,password:'changed123',refreshRecovery:'on'},updated,digest);assert(rotated.recoveryCode);assert.equal(rotated.user.hash,reset.user.hash);
 const legacy={...account};delete legacy.recoveryHash;delete legacy.recoverySalt;await assert.rejects(auth.preparePasswordReset(fields,{[account.email]:legacy},digest));
 const enrolled=await auth.prepareLocalAccount(false,{email:account.email,password:'original123'},{[account.email]:legacy},digest);assert(enrolled.recoveryCode);assert.equal(enrolled.user.photo,'saved-photo');
 console.log('PASS: real PBKDF2 recovery, wrong/missing proof rejection, password replacement, old-code invalidation, code rotation, legacy enrollment, and profile preservation.');
})().catch(e=>{console.error(e);process.exitCode=1});
