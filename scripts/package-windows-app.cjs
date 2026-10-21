const fs=require('node:fs'),path=require('node:path'),asar=require('../desktop/node_modules/@electron/asar');
(async()=>{
  const root=path.resolve(__dirname,'..'),desktop=path.join(root,'desktop');
  const stage=path.join(root,'.tools','windows-app-source');fs.mkdirSync(stage,{recursive:true});
  for(const name of ['gmail-auth.js','phone-auth.js','text-analysis.cjs','text-analysis-ui.js','text-analysis-config.json']){
    const stale=path.join(stage,name);if(fs.existsSync(stale))fs.unlinkSync(stale);
  }
  const files=['index.html','app.css','extra.css','app.js','phone.js','local-auth.js','name-gender.js','whatsapp-composer.js','directory-sync.js','enrollment.js','member-list.js','insight-layout.js','area-recognition.js','default-members.json','server.cjs','electron.cjs'];
  for(const name of files)fs.copyFileSync(path.join(desktop,name),path.join(stage,name));
  for(const name of ['assets','member-photos','node_modules/pdfjs-dist'])fs.cpSync(path.join(desktop,name),path.join(stage,name),{recursive:true});
  const original=JSON.parse(fs.readFileSync(path.join(desktop,'package.json'),'utf8'));
  fs.writeFileSync(path.join(stage,'package.json'),JSON.stringify({name:original.name,version:original.version,description:original.description,main:'electron.cjs'}));
  const temporary=path.join(root,'.tools','updated-windows-app.asar');
  await asar.createPackage(stage,temporary);
  for(const name of files)if(!asar.extractFile(temporary,name).equals(fs.readFileSync(path.join(desktop,name))))throw Error('Archive differs from source: '+name);
  const destination=path.join(desktop,'dist/win-unpacked/resources/app.asar');
  if(!destination.startsWith(root+path.sep))throw Error('Archive destination is outside the workspace');
  const backup=path.join(root,'.tools','windows-app-before-text-analysis.asar');
  if(!fs.existsSync(backup))fs.copyFileSync(destination,backup);
  fs.copyFileSync(temporary,destination);
  console.log('Updated and verified Windows app archive: '+destination);
})().catch(error=>{console.error(error);process.exitCode=1});
