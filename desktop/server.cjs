const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = __dirname;
const mime = {'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8','.mjs':'text/javascript; charset=utf-8','.map':'application/json','.json':'application/json','.png':'image/png','.jpg':'image/jpeg','.jpeg':'image/jpeg','.svg':'image/svg+xml'};
function startServer(port=4173){
  return new Promise((resolve,reject)=>{
    const server=http.createServer(async (req,res)=>{
      const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
      if(['/api/gmail-otp/start','/api/gmail-otp/resend','/api/gmail-otp/verify','/api/directory','/api/members'].includes(pathname)){
        res.setHeader('Content-Type','application/json; charset=utf-8');res.setHeader('Cache-Control','no-store');
        const finish=(status,data)=>{res.writeHead(status);res.end(JSON.stringify(data));};
        if(req.headers.origin&&req.headers.origin!==`http://${req.headers.host}`)return finish(403,{error:'Cross-origin requests are not allowed.'});
        const otp=pathname.startsWith('/api/gmail-otp/');
        if(req.method!==(otp?'POST':'GET'))return finish(405,{error:'Unsupported request method.'});
        try{
          const base=new URL((process.env.VERIFICATION_API_URL||'http://127.0.0.1:8080').replace(/\/$/,'')+'/');
          if(base.username||base.password||!(base.protocol==='https:'||(base.protocol==='http:'&&['localhost','127.0.0.1','10.0.2.2'].includes(base.hostname))))throw Error('Use HTTPS or a loopback address for the verification server.');
          let body='';if(otp){for await(const chunk of req){body+=chunk;if(Buffer.byteLength(body)>4096)throw Error('Request is too large.');}}
          const incoming=new URL(req.url,'http://localhost'),target=new URL('v1/'+pathname.slice(5),base);target.search=incoming.search;
          const headers=otp?{'Content-Type':'application/json'}:{Authorization:req.headers.authorization||''};
          const response=await fetch(target,{method:req.method,headers,...(otp?{body}:{}),signal:AbortSignal.timeout(25000),redirect:'error'});
          const chunks=[];let length=0;for await(const chunk of response.body){length+=chunk.length;if(length>2_000_000)throw Error('Backend response is too large.');chunks.push(chunk);}
          finish(response.status,JSON.parse(Buffer.concat(chunks).toString('utf8')));
        }catch(error){finish(503,{error:'Verification/directory server is unavailable. Start the configured backend and check its settings.'});}
        return;
      }
      if(req.method==='POST'&&pathname==='/api/recognize-area'){
        res.setHeader('Content-Type','application/json; charset=utf-8');
        const origin=req.headers.origin;
        if(origin&&origin!==`http://${req.headers.host}`){res.writeHead(403);return res.end(JSON.stringify({error:'Cross-origin area lookup is not allowed.'}));}
        const key=process.env.GOOGLE_MAPS_API_KEY;
        if(!key){res.writeHead(503);return res.end(JSON.stringify({error:'Google Geocoding is not configured. Set GOOGLE_MAPS_API_KEY and restart the app.'}));}
        try{
          let body='';for await(const chunk of req){body+=chunk;if(body.length>8192)throw new Error('Request is too large.');}
          const {address,placeId}=JSON.parse(body);if(placeId!==undefined&&(typeof placeId!=='string'||!placeId.trim()||placeId.length>256))throw new Error('Enter a valid Google Place ID.');if(placeId===undefined&&(typeof address!=='string'||!address.trim()||address.length>500))throw new Error('Enter a valid member address.');
          const url=new URL('https://maps.googleapis.com/maps/api/geocode/json');if(placeId)url.searchParams.set('place_id',placeId.trim());else{url.searchParams.set('address',address.trim());/* The attached DBA/KSR directory is Pakistani, so free-text address lookups are restricted to PK. */url.searchParams.set('components','country:PK');}url.searchParams.set('language','en');url.searchParams.set('key',key);
          const response=await fetch(url);if(!response.ok)throw new Error(`Google Geocoding returned HTTP ${response.status}.`);
          const data=await response.json();
          if(data.status==='ZERO_RESULTS'){res.writeHead(200);return res.end(JSON.stringify({area:null,source:null,placeId:null}));}
          if(data.status!=='OK')throw new Error(`Google Geocoding status: ${data.status}${data.error_message?` — ${data.error_message}`:''}`);
          const first=data.results[0];
          // Prefer the smallest useful locality; fall back to city, district, then province.
          const priorities=['neighborhood','sublocality_level_1','sublocality','locality','administrative_area_level_3','administrative_area_level_2','administrative_area_level_1'];
          let match=null;for(const result of data.results){for(const type of priorities){match=(result.address_components||[]).find(item=>item.types.includes(type));if(match)break;}if(match)break;}
          const country=first.address_components?.find(item=>item.types.includes('country'))?.short_name;
          if(country&&country!=='PK'){res.writeHead(200);return res.end(JSON.stringify({area:null,source:null,placeId:null}));}
          res.writeHead(200);return res.end(JSON.stringify({area:match?.long_name||null,source:match?.types?.[0]||null,placeId:first.place_id||null}));
        }catch(error){res.writeHead(400);return res.end(JSON.stringify({error:error.message||'Area lookup failed.'}));}
      }
      const file=pathname.startsWith('/vendor/')?path.join(root,'node_modules','pdfjs-dist',pathname.slice(8)):path.join(root,pathname==='/'?'index.html':pathname.slice(1));
      if(!file.startsWith(root)){res.writeHead(403);return res.end();}
      fs.readFile(file,(err,data)=>{if(err){res.writeHead(404);return res.end('Not found');}res.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store'});res.end(data);});
    });
    server.once('error',reject);server.listen(port,'127.0.0.1',()=>resolve({server,url:`http://127.0.0.1:${server.address().port}`}));
  });
}
if(require.main===module)startServer().then(({url})=>console.log(`Bar Member Manager is running at ${url}`)).catch(e=>{console.error(e);process.exit(1)});
module.exports={startServer};
