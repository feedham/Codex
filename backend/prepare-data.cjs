const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const columns=['id','first_name','last_name','member_number','cell_number','enrollment_date','enrollment_source','area','chamber','biometric','gender','parent_name','residence','photo_asset','cnic','source_page','needs_review'];
function enrollmentDate(value){
  const match=/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(value||'');
  if(!match)return null;
  const [,month,day,year]=match,iso=`${year}-${month.padStart(2,'0')}-${day.padStart(2,'0')}`;
  const date=new Date(iso+'T00:00:00Z');
  return !Number.isNaN(date.valueOf())&&date.toISOString().slice(0,10)===iso?iso:null;
}
function normalize(member){
  const date=enrollmentDate(member.doe);
  return [member.id,member.firstName,member.lastName||'',member.memberNo||null,member.cell||null,date,member.doe||null,member.area||null,member.chamber||null,member.biometric||null,member.gender||null,member.parent||null,member.residence||null,member.photo||null,member.cnic||null,member.page||null,!!member.needsReview||!date];
}
// Standard SQL quoting preserves names, Unicode, quotes, and backslashes in SQLite.
function literal(value){return value===null?'NULL':typeof value==='boolean'?(value?'1':'0'):typeof value==='number'?String(value):"'"+value.replaceAll("'","''")+"'";}
function prepare(){
  const root=path.resolve(__dirname,'..'),members=JSON.parse(fs.readFileSync(path.join(root,'desktop/default-members.json'),'utf8'));
  const rows=members.filter(m=>!m.removed).map(normalize);
  if(new Set(rows.map(r=>r[0])).size!==rows.length)throw Error('Duplicate member IDs in PDF extraction.');
  const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
  const metadata={revision:sha(JSON.stringify(rows)),sourceFilename:'AdvocatesListofDBAKSR2025.pdf',sourceSha256:sha(fs.readFileSync(path.join(root,'AdvocatesListofDBAKSR2025.pdf'))),memberCount:rows.length};
  const out=path.join(__dirname,'generated');fs.mkdirSync(out,{recursive:true});
  fs.writeFileSync(path.join(out,'directory.json'),JSON.stringify({metadata,columns,rows}));
  const update=columns.slice(1).map(c=>`${c}=excluded.${c}`).join(',');
  const statements=['-- Generated from the existing PDF extraction. Schema: ../schema.sql','BEGIN IMMEDIATE;','DELETE FROM directory_members;'];
  for(let i=0;i<rows.length;i+=100)statements.push(`INSERT INTO directory_members (${columns.join(',')}) VALUES\n${rows.slice(i,i+100).map(row=>'('+row.map(literal).join(',')+')').join(',\n')}\nON CONFLICT(id) DO UPDATE SET ${update};`);
  statements.push(`INSERT INTO directory_metadata (singleton,revision,source_filename,source_sha256,member_count) VALUES (1,${[metadata.revision,metadata.sourceFilename,metadata.sourceSha256,rows.length].map(literal).join(',')}) ON CONFLICT(singleton) DO UPDATE SET revision=excluded.revision,source_filename=excluded.source_filename,source_sha256=excluded.source_sha256,member_count=excluded.member_count,imported_at=CURRENT_TIMESTAMP;`,'COMMIT;');
  fs.writeFileSync(path.join(out,'members.sql'),statements.join('\n'));
  console.log(`Prepared ${rows.length} members; ${rows.filter(r=>!r[5]).length} dates flagged for review. No campaign edits are imported.`);
  return {metadata,columns,rows};
}
if(require.main===module)prepare();
module.exports={prepare,normalize,enrollmentDate,literal};
