const {app,BrowserWindow,shell}=require('electron');
const {startServer}=require('./server.cjs');
let local;
app.whenReady().then(async()=>{local=await startServer(0);const win=new BrowserWindow({width:1180,height:820,minWidth:720,minHeight:560,backgroundColor:'#080d16',webPreferences:{contextIsolation:true,nodeIntegration:false}});
  function external(target){try{const url=new URL(target);if(['https:','http:','tel:','whatsapp:'].includes(url.protocol))shell.openExternal(target).catch(()=>{});}catch{}}
  win.webContents.setWindowOpenHandler(({url})=>{external(url);return {action:'deny'};});
  win.webContents.on('will-navigate',(event,target)=>{try{if(new URL(target).origin!==new URL(local.url).origin){event.preventDefault();external(target);}}catch{event.preventDefault();}});
  await win.loadURL(local.url);
});
app.on('window-all-closed',()=>{if(local)local.server.close();if(process.platform!=='darwin')app.quit()});
