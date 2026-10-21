const {configuration}=require('./local-config.cjs'),{database}=require('./db.cjs');
const {importDirectory}=require('./import.cjs'),{createServer}=require('./server.cjs');
function main(){let db;
 try{
  const config=configuration();db=database();importDirectory(db,undefined,true);
  const server=createServer(db,config.token);
  server.on('error',error=>{console.error('Could not start backend:',error.message);db.end();process.exitCode=1;});
  server.listen(config.port,config.host,()=>{console.log('SQLite directory backend: http://'+config.host+':'+server.address().port);console.log('Keep this window open. Your private API_TOKEN is in backend/.env.');});
  function stop(){server.close(()=>db.end());server.closeIdleConnections();}
  process.once('SIGINT',stop);process.once('SIGTERM',stop);return server;
 }catch(error){console.error('Could not start backend:',error.message);db?.end();process.exitCode=1;}
}
if(require.main===module)main();module.exports={main};
