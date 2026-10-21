const {test}=require('node:test'),assert=require('node:assert/strict');
const {normalize,enrollmentDate,prepare,literal}=require('./prepare-data.cjs');
const {createServer}=require('./server.cjs');
const {database}=require('./db.cjs'),{importDirectory}=require('./import.cjs');

test('real SQLite import, SQL export, HTTP download, and failed-import rollback',async()=>{
  const fs=require('node:fs'),path=require('node:path');
  const data=prepare(),db=database(':memory:');
  importDirectory(db,data);
  assert.equal(db.sqlite.prepare('SELECT count(*) AS n FROM directory_members').get().n,2594);
  assert.equal(importDirectory(db,data,true),false);
  const before=db.sqlite.prepare('SELECT revision FROM directory_metadata').get().revision;
  assert.throws(()=>importDirectory(db,{...data,rows:[data.rows[0],data.rows[0]]}));
  assert.equal(db.sqlite.prepare('SELECT count(*) AS n FROM directory_members').get().n,2594);
  assert.equal(db.sqlite.prepare('SELECT revision FROM directory_metadata').get().revision,before);
  const sqlDb=database(':memory:');
  sqlDb.sqlite.exec(fs.readFileSync(path.join(__dirname,'schema.sql'),'utf8'));
  sqlDb.sqlite.exec(fs.readFileSync(path.join(__dirname,'generated/members.sql'),'utf8'));
  assert.equal(sqlDb.sqlite.prepare('SELECT count(*) AS n FROM directory_members').get().n,2594);
  sqlDb.end();
  const server=createServer(db,'r'.repeat(40));await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base='http://127.0.0.1:'+server.address().port;
  const get=async p=>{const response=await fetch(base+p,{headers:{Authorization:'Bearer '+'r'.repeat(40)}});assert.equal(response.status,200);return response.json();};
  try{
    const meta=await get('/v1/directory');let after='',ids=[];
    do{const page=await get('/v1/members?revision='+meta.revision+'&after='+encodeURIComponent(after));assert(page.members.length<=100);ids.push(...page.members.map(m=>m.id));after=page.next;}while(after);
    assert.equal(ids.length,meta.memberCount);assert.equal(new Set(ids).size,2594);
    const [plan]=await db.execute('EXPLAIN QUERY PLAN SELECT * FROM directory_members WHERE id > ? ORDER BY id LIMIT 101',['member:1']);
    assert(plan.some(row=>/INDEX/.test(row.detail)));
  }finally{await new Promise(resolve=>server.close(resolve));db.end();}
});
test('PDF date conversion preserves missing and invalid source dates for review',()=>{
  assert.equal(enrollmentDate('2/29/2024'),'2024-02-29');assert.equal(enrollmentDate('2/29/2023'),null);
  assert.equal(enrollmentDate('11/12/2022'),'2022-11-12');assert.equal(enrollmentDate('13/1/2020'),null);
  const row=normalize({id:'a',firstName:'A',doe:'2/30/2024'});assert.equal(row[5],null);assert.equal(row[6],'2/30/2024');assert.equal(row[16],true);
  assert.equal(literal("O'Brien"),"'O''Brien'");
});
test('all extracted PDF members convert without dropped or duplicate records',()=>{
  const {rows,metadata}=prepare();assert.equal(rows.length,2594);assert.equal(new Set(rows.map(r=>r[0])).size,2594);assert.equal(metadata.memberCount,2594);
  assert(rows.every(r=>r.length===17&&r[0].length<=160&&r[1].length<=160&&r[2].length<=240));
  assert.match(metadata.revision,/^[a-f0-9]{64}$/);assert.match(metadata.sourceSha256,/^[a-f0-9]{64}$/);
});
test('API requires authentication, bounds pages, uses cursor parameters, and detects changed snapshots',async()=>{
  const revision='a'.repeat(64),calls=[];
  const pool={async execute(sql,args){calls.push({sql,args});if(sql.includes('member_count AS'))return [[{revision,memberCount:2}]];return [[{revision,id:'a',firstName:'A',needsReview:0},{revision,id:'b',firstName:'B',needsReview:1}]];}};
  const token='t'.repeat(40),server=createServer(pool,token);await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base='http://127.0.0.1:'+server.address().port;
  const get=(path,auth=true)=>fetch(base+path,{headers:auth?{Authorization:'Bearer '+token}:{}});
  try{
    assert.equal((await get('/v1/directory',false)).status,401);
    assert.equal((await get('/v1/directory')).status,200);
    assert.equal((await get('/v1/members?limit=101')).status,400);
    assert.equal((await get('/v1/members?limit=0')).status,400);
    assert.equal((await get('/v1/members?limit=1&revision=wrong')).status,409);
    const response=await get('/v1/members?limit=1&revision='+revision+'&after='+encodeURIComponent("' OR 1=1 --"));
    assert.equal(response.status,200);const body=await response.json();assert.equal(body.members.length,1);assert.equal(body.next,'a');assert.equal(body.members[0].needsReview,false);
    assert.deepEqual(calls.at(-1).args,["' OR 1=1 --"]);assert(!calls.at(-1).sql.includes("OR 1=1"));
    assert.equal((await fetch(base+'/v1/directory',{method:'POST',headers:{Authorization:'Bearer '+token}})).status,405);
  }finally{await new Promise(resolve=>server.close(resolve));}
});
