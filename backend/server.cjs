const http=require('node:http'),crypto=require('node:crypto');
const {createOtp,readJson,OtpError}=require('./gmail-otp.cjs');
function createServer(pool,token,options={}){
  if(!token||token.length<32)throw Error('API_TOKEN must contain at least 32 characters.');
  const otp=pool.sqlite?createOtp(pool.sqlite,options.otp):null;
  return http.createServer(async(req,res)=>{
    res.setHeader('Content-Type','application/json; charset=utf-8');res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
    const send=(status,data)=>{res.writeHead(status);res.end(JSON.stringify(data));};
    try{
      const url=new URL(req.url,'http://localhost');
      if(['/v1/gmail-otp/start','/v1/gmail-otp/resend','/v1/gmail-otp/verify'].includes(url.pathname)){
        if(req.method!=='POST')return send(405,{error:'Use POST.'});
        if(!otp)return send(503,{error:'Gmail verification is unavailable.'});
        const body=await readJson(req),ip=req.socket.remoteAddress||'unknown';
        const operation=url.pathname.split('/').at(-1);
        return send(200,await otp[operation](body,ip));
      }
      const supplied=crypto.createHash('sha256').update(req.headers.authorization||'').digest();
      const authorized=crypto.createHash('sha256').update('Bearer '+token).digest();
      if(!crypto.timingSafeEqual(supplied,authorized))return send(401,{error:'Authentication required.'});
      if(req.method!=='GET')return send(405,{error:'This directory API is read-only.'});
      if(!['/v1/directory','/v1/members'].includes(url.pathname))return send(404,{error:'Not found.'});
      // One statement reads both revision and rows, so a concurrent import cannot mix snapshots.
      if(url.pathname==='/v1/directory'){
        const [meta]=await pool.execute('SELECT revision,member_count AS memberCount,source_filename AS sourceFilename FROM directory_metadata WHERE singleton=1');
        if(!meta.length)return send(503,{error:'Import the PDF directory first.'});
        return send(200,meta[0]);
      }
      const after=url.searchParams.get('after')||'',limitText=url.searchParams.get('limit')||'100';
      if(after.length>160||!/^\d+$/.test(limitText)||Number(limitText)<1||Number(limitText)>100)return send(400,{error:'Use a limit of 1 to 100 and a valid cursor.'});
      const limit=Number(limitText);
      const [rows]=await pool.execute(`SELECT d.revision,m.id,m.first_name AS firstName,m.last_name AS lastName,m.member_number AS memberNo,m.cell_number AS cell,m.enrollment_date AS doe,m.enrollment_source AS enrollmentSource,m.area,m.chamber,m.biometric,m.gender,m.parent_name AS parent,m.residence,m.photo_asset AS photo,m.cnic,m.needs_review AS needsReview FROM directory_metadata d LEFT JOIN (SELECT * FROM directory_members WHERE id > ? ORDER BY id LIMIT ${limit+1}) m ON TRUE WHERE d.singleton=1 ORDER BY m.id`,[after]);
      if(!rows.length)return send(503,{error:'Import the PDF directory first.'});
      const revision=rows[0].revision;
      if(url.searchParams.get('revision')!==revision)return send(409,{error:'Directory changed. Restart the download.'});
      const members=rows.filter(r=>r.id!==null).slice(0,limit).map(({revision,...member})=>({...member,needsReview:!!member.needsReview}));
      send(200,{revision,members,next:rows.filter(r=>r.id!==null).length>limit?members.at(-1).id:null});
    }catch(error){if(error instanceof OtpError)return send(error.status,{error:error.message});console.error('Backend request failed:',error.code||error.name);send(503,{error:'Service unavailable. Try again later.'});}
  });
}
module.exports={createServer};
