const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {parseEnv}=require('node:util');
function configuration(){
 const filename=path.join(__dirname,'.env');let text=fs.existsSync(filename)?fs.readFileSync(filename,'utf8'):'';
 let settings=parseEnv(text);
 if(!settings.API_TOKEN&&!process.env.API_TOKEN){
  const token=crypto.randomBytes(32).toString('hex');text=text.replace(/^API_TOKEN=.*\r?\n?/m,'');text+='\nAPI_TOKEN='+token+'\n';
  fs.writeFileSync(filename,text,{mode:0o600});settings=parseEnv(text);
 }
 if(!settings.OTP_SECRET&&!process.env.OTP_SECRET){text+='\nOTP_SECRET='+crypto.randomBytes(32).toString('hex')+'\n';fs.writeFileSync(filename,text,{mode:0o600});settings=parseEnv(text);}
 for(const [key,value] of Object.entries(settings))if(process.env[key]===undefined)process.env[key]=value;
 return {token:process.env.API_TOKEN,host:process.env.API_HOST||'127.0.0.1',port:Number(process.env.PORT||8080)};
}
module.exports={configuration};
