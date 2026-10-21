const fs=require('node:fs'),path=require('node:path');
const {database}=require('./db.cjs'),{prepare}=require('./prepare-data.cjs');
function importDirectory(db,data=prepare(),skipIfCurrent=false){
 const {metadata,columns,rows}=data,sqlite=db.sqlite;
 sqlite.exec(fs.readFileSync(path.join(__dirname,'schema.sql'),'utf8'));
 const existing=sqlite.prepare('SELECT revision FROM directory_metadata WHERE singleton=1').get();
 if(skipIfCurrent&&existing?.revision===metadata.revision)return false;
 sqlite.exec('BEGIN IMMEDIATE');
 try{
  sqlite.exec('DELETE FROM directory_members');
  const insert=sqlite.prepare('INSERT INTO directory_members ('+columns.join(',')+') VALUES ('+columns.map(()=>'?').join(',')+')');
  for(const row of rows)insert.run(...row.map(value=>typeof value==='boolean'?Number(value):value));
  sqlite.prepare('INSERT INTO directory_metadata (singleton,revision,source_filename,source_sha256,member_count) VALUES (1,?,?,?,?) ON CONFLICT(singleton) DO UPDATE SET revision=excluded.revision,source_filename=excluded.source_filename,source_sha256=excluded.source_sha256,member_count=excluded.member_count,imported_at=CURRENT_TIMESTAMP').run(metadata.revision,metadata.sourceFilename,metadata.sourceSha256,metadata.memberCount);
  sqlite.exec('COMMIT');console.log('Imported '+rows.length+' members into SQLite.');return true;
 }catch(error){sqlite.exec('ROLLBACK');throw error;}
}
if(require.main===module){let db;try{db=database();importDirectory(db);}catch(error){console.error(error.message);process.exitCode=1;}finally{db?.end();}}
module.exports={importDirectory};
