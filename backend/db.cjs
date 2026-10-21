const fs=require('node:fs'),path=require('node:path');
const {DatabaseSync}=require('node:sqlite');
function database(filename=process.env.SQLITE_FILE||path.join(__dirname,'data/bar-members.sqlite')){
 if(filename!==':memory:')fs.mkdirSync(path.dirname(path.resolve(filename)),{recursive:true});
 const sqlite=new DatabaseSync(filename,{timeout:5000});
 sqlite.exec('PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;');
 return {sqlite,async execute(sql,args=[]){return [sqlite.prepare(sql).all(...args)];},end(){sqlite.close();}};
}
module.exports={database};
