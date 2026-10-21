const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),source=JSON.parse(fs.readFileSync(path.join(root,'backend/allowed-gmail-accounts.json'),'utf8'));
if(source.version!==1||!Array.isArray(source.allowedGmailAccounts)||!source.allowedGmailAccounts.every(x=>typeof x==='string'&&/^[a-z0-9._%+-]+@gmail\.com$/i.test(x.trim())))throw Error('Invalid approved Gmail list');
const content=JSON.stringify({version:1,allowedGmailAccounts:[...new Set(source.allowedGmailAccounts.map(x=>x.trim().toLowerCase()))]},null,2)+'\n';
for(const file of ['backend/allowed-gmail-accounts.json','app/src/main/assets/allowed-gmail-accounts.json','desktop/assets/allowed-gmail-accounts.json'])fs.writeFileSync(path.join(root,file),content);
console.log('Synchronized approved Gmail lists.');
