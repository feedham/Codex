const tls=require('node:tls'),readline=require('node:readline'),crypto=require('node:crypto');
const gmail=value=>typeof value==='string'&&/^[a-z0-9._%+-]+@gmail\.com$/i.test(value);
function gmailSender(env=process.env){
  const sender=(env.GMAIL_SENDER||'').trim().toLowerCase(),password=(env.GMAIL_APP_PASSWORD||'').replace(/\s/g,'');
  return {configured:gmail(sender)&&password.length>=16,async send(recipient,code){
    if(!gmail(recipient)||!/^\d{6}$/.test(code))throw Error('Invalid verification recipient or code.');
    const socket=tls.connect({host:'smtp.gmail.com',port:465,servername:'smtp.gmail.com',rejectUnauthorized:true});
    const lines=readline.createInterface({input:socket,crlfDelay:Infinity}),replies=[],waiters=[];let response='',closedError;
    function fail(error){closedError=error;while(waiters.length)waiters.shift().reject(error);}
    socket.setTimeout(20000,()=>socket.destroy(Error('Gmail connection timed out.')));
    socket.on('error',()=>fail(Error('Gmail delivery is unavailable. Check the sender settings.')));
    socket.on('close',()=>fail(Error('Gmail closed the connection.')));
    lines.on('line',line=>{
      response+=line+'\n';if(response.length>16384){socket.destroy();return;}
      if(/^\d{3} /.test(line)){const reply={code:Number(line.slice(0,3)),text:response};response='';if(waiters.length)waiters.shift().resolve(reply);else replies.push(reply);}
    });
    const next=()=>replies.length?Promise.resolve(replies.shift()):closedError?Promise.reject(closedError):new Promise((resolve,reject)=>waiters.push({resolve,reject}));
    async function expect(command,codes){if(command!==null)socket.write(command+'\r\n');const reply=await next();if(!codes.includes(reply.code))throw Error('Gmail did not accept the verification email. Check the sender settings.');}
    try{
      await expect(null,[220]);await expect('EHLO bar-members.local',[250]);
      await expect('AUTH PLAIN '+Buffer.from('\0'+sender+'\0'+password).toString('base64'),[235]);
      await expect('MAIL FROM:<'+sender+'>',[250]);await expect('RCPT TO:<'+recipient+'>',[250]);await expect('DATA',[354]);
      const message=['From: Bar Members List <'+sender+'>','To: <'+recipient+'>','Subject: Your Bar Members List verification code','Date: '+new Date().toUTCString(),'Message-ID: <'+crypto.randomUUID()+'@gmail.com>','MIME-Version: 1.0','Content-Type: text/plain; charset=UTF-8','Content-Transfer-Encoding: 7bit','','Your verification code is: '+code,'','This code expires in 10 minutes. Do not share it.','If you did not request this code, you can ignore this email.',''].join('\r\n');
      socket.write(message+'\r\n.\r\n');await expect(null,[250]);
      // The DATA acknowledgement confirms Gmail accepted the message; QUIT is best effort.
      socket.write('QUIT\r\n');socket.end();
    }finally{lines.close();socket.destroy();}
  }};
}
module.exports={gmailSender,gmail};
