const crypto=require('node:crypto'),fs=require('node:fs'),path=require('node:path');
const {gmailSender,gmail}=require('./gmail-sender.cjs');
class OtpError extends Error{constructor(status,message){super(message);this.status=status;}}
const hash=value=>crypto.createHash('sha256').update(value).digest('hex');
function allowedAccounts(){
  const data=JSON.parse(fs.readFileSync(path.join(__dirname,'allowed-gmail-accounts.json'),'utf8'));
  if(data.version!==1||!Array.isArray(data.allowedGmailAccounts)||!data.allowedGmailAccounts.every(gmail))throw new OtpError(503,'Approved Gmail accounts have not been configured correctly.');
  return new Set(data.allowedGmailAccounts.map(email=>email.trim().toLowerCase()));
}
function createOtp(sqlite,{provider=gmailSender(),now=Date.now,allowed=allowedAccounts,secret=process.env.OTP_SECRET}={}){
  sqlite.exec(`CREATE TABLE IF NOT EXISTS gmail_challenges (
    id_hash TEXT PRIMARY KEY,email TEXT NOT NULL,purpose TEXT NOT NULL,code_hash TEXT NOT NULL,
    expires_at INTEGER NOT NULL,attempts INTEGER NOT NULL DEFAULT 0,state TEXT NOT NULL,sent_at INTEGER NOT NULL
  ) STRICT;
  CREATE TABLE IF NOT EXISTS gmail_rate_limits(bucket TEXT PRIMARY KEY,count INTEGER NOT NULL,expires_at INTEGER NOT NULL) STRICT;`);
  const codeHash=(id,code)=>crypto.createHmac('sha256',secret).update(id+'.'+code).digest('hex');
  function approved(email){if(!gmail(email)||!allowed().has(email))throw new OtpError(403,'This Gmail account is not approved. Contact the app administrator.');}
  function configured(){if(!provider.configured||!secret||secret.length<32)throw new OtpError(503,'Gmail verification is not configured. Contact the app administrator.');}
  function limit(key,maximum,period){const time=now(),bucket=key+':'+Math.floor(time/period),row=sqlite.prepare('SELECT count FROM gmail_rate_limits WHERE bucket=?').get(bucket);if((row?.count||0)>=maximum)throw new OtpError(429,'Too many verification requests. Please wait.');sqlite.prepare('INSERT INTO gmail_rate_limits(bucket,count,expires_at) VALUES(?,1,?) ON CONFLICT(bucket) DO UPDATE SET count=count+1').run(bucket,time+period);}
  function sendLimits(email,ip){const time=now();sqlite.prepare('DELETE FROM gmail_challenges WHERE expires_at < ?').run(time-86400000);sqlite.prepare('DELETE FROM gmail_rate_limits WHERE expires_at < ?').run(time);const recent=sqlite.prepare('SELECT sent_at FROM gmail_challenges WHERE email=? ORDER BY sent_at DESC LIMIT 1').get(email);if(recent&&time-recent.sent_at<60000)throw new OtpError(429,'Wait 60 seconds before requesting another code.');limit('send-ip:'+hash(ip),10,15*60000);limit('send-email:'+hash(email),3,15*60000);limit('send-daily:'+hash(email),10,86400000);limit('send-global',100,86400000);}
  function challenge(id){if(typeof id!=='string'||!/^[a-f0-9]{64}$/.test(id))throw new OtpError(400,'Invalid verification request.');const row=sqlite.prepare('SELECT * FROM gmail_challenges WHERE id_hash=?').get(hash(id));if(!row||row.state!=='pending'||row.expires_at<=now()||row.attempts>=5)throw new OtpError(400,'This verification request expired or was already used. Request a new code.');approved(row.email);return row;}
  const result=(id,row)=>({challengeId:id,maskedEmail:row.email.slice(0,2)+'***@gmail.com',expiresAt:row.expires_at,resendAfter:Math.max(0,Math.ceil((row.sent_at+60000-now())/1000))});
  return {
    async start(body,ip){
      const email=typeof body.email==='string'?body.email.trim().toLowerCase():'';approved(email);configured();
      if(!['signup','signin'].includes(body.purpose))throw new OtpError(400,'Invalid verification purpose.');sendLimits(email,ip);
      const id=crypto.randomBytes(32).toString('hex'),code=crypto.randomInt(100000,1000000).toString(),time=now();
      const row={email,purpose:body.purpose,expires_at:time+10*60000,sent_at:time};
      sqlite.prepare("UPDATE gmail_challenges SET state='superseded' WHERE email=? AND state='pending'").run(email);
      sqlite.prepare('INSERT INTO gmail_challenges(id_hash,email,purpose,code_hash,expires_at,state,sent_at) VALUES(?,?,?,?,?,?,?)').run(hash(id),email,row.purpose,codeHash(hash(id),code),row.expires_at,'sending',time);
      try{await provider.send(email,code);sqlite.prepare("UPDATE gmail_challenges SET state='pending' WHERE id_hash=?").run(hash(id));return result(id,row);}catch{sqlite.prepare("UPDATE gmail_challenges SET state='failed' WHERE id_hash=?").run(hash(id));throw new OtpError(503,'Could not deliver the Gmail code. Check the server sender configuration.');}
    },
    async resend(body,ip){
      configured();const old=challenge(body.challengeId);sendLimits(old.email,ip);
      const id=crypto.randomBytes(32).toString('hex'),code=crypto.randomInt(100000,1000000).toString(),time=now();
      sqlite.prepare("UPDATE gmail_challenges SET state='sending',sent_at=? WHERE id_hash=?").run(time,old.id_hash);
      try{await provider.send(old.email,code);sqlite.prepare("UPDATE gmail_challenges SET id_hash=?,code_hash=?,state='pending' WHERE id_hash=?").run(hash(id),codeHash(hash(id),code),old.id_hash);return result(id,{...old,sent_at:time});}
      catch{sqlite.prepare("UPDATE gmail_challenges SET state='pending' WHERE id_hash=?").run(old.id_hash);throw new OtpError(503,'Could not resend the Gmail code. Try again later.');}
    },
    async verify(body,ip){
      configured();limit('check-ip:'+hash(ip),30,15*60000);
      if(typeof body.code!=='string'||!/^\d{6}$/.test(body.code))throw new OtpError(400,'Enter the six-digit code from your Gmail inbox.');
      const row=challenge(body.challengeId),candidate=codeHash(row.id_hash,body.code);
      if(!crypto.timingSafeEqual(Buffer.from(candidate,'hex'),Buffer.from(row.code_hash,'hex'))){sqlite.prepare('UPDATE gmail_challenges SET attempts=attempts+1,state=? WHERE id_hash=?').run(row.attempts+1>=5?'blocked':'pending',row.id_hash);throw new OtpError(400,'Invalid or expired code. Use the latest email code.');}
      sqlite.prepare("UPDATE gmail_challenges SET state='consumed' WHERE id_hash=?").run(row.id_hash);
      return {challengeId:body.challengeId,email:row.email,purpose:row.purpose,verifiedAt:now()};
    }
  };
}
async function readJson(req){if(!req.headers['content-type']?.startsWith('application/json'))throw new OtpError(415,'Use application/json.');const chunks=[];let size=0;for await(const chunk of req){size+=chunk.length;if(size>4096)throw new OtpError(413,'Request is too large.');chunks.push(chunk);}try{const value=JSON.parse(Buffer.concat(chunks).toString());if(!value||typeof value!=='object'||Array.isArray(value))throw Error();return value;}catch{throw new OtpError(400,'Invalid request.');}}
module.exports={createOtp,readJson,OtpError,allowedAccounts};
