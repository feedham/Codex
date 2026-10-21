const assert=require('node:assert/strict'),{DatabaseSync}=require('node:sqlite'),{createOtp,allowedAccounts}=require('./gmail-otp.cjs');
(async()=>{
 const db=new DatabaseSync(':memory:');let time=1700000000000,sent=[];
 const otp=createOtp(db,{secret:'s'.repeat(64),now:()=>time,allowed:()=>new Set(['approved@gmail.com']),provider:{configured:true,send:async(email,code)=>sent.push({email,code})}});
 await assert.rejects(otp.start({email:'other@gmail.com',purpose:'signup'},'ip'),e=>e.status===403);assert.equal(sent.length,0);
 let c=await otp.start({email:'APPROVED@gmail.com',purpose:'signup'},'ip');assert(!JSON.stringify(c).includes(sent[0].code));
 await assert.rejects(otp.verify({challengeId:c.challengeId,code:'000000'},'ip'));
 const proof=await otp.verify({challengeId:c.challengeId,code:sent[0].code},'ip');assert.equal(proof.email,'approved@gmail.com');assert.equal(proof.purpose,'signup');
 await assert.rejects(otp.verify({challengeId:c.challengeId,code:sent[0].code},'ip'));
 time+=61000;c=await otp.start({email:'approved@gmail.com',purpose:'signin'},'ip');
 await assert.rejects(otp.resend({challengeId:c.challengeId},'ip'),e=>e.status===429);
 time+=61000;const next=await otp.resend({challengeId:c.challengeId},'ip');
 await assert.rejects(otp.verify({challengeId:c.challengeId,code:sent.at(-1).code},'ip'));
 const row=db.prepare('SELECT code_hash FROM gmail_challenges WHERE state=?').get('pending');assert(!row.code_hash.includes(sent.at(-1).code));
 time+=600001;await assert.rejects(otp.verify({challengeId:next.challengeId,code:sent.at(-1).code},'ip'));
 assert(allowedAccounts().size>0);db.close();console.log('PASS: Gmail approval, OTP consumption, expiry, resend rotation/cooldown, and hashed storage (fake sender; no email sent).');
})().catch(e=>{console.error(e);process.exitCode=1});
