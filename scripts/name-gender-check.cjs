const fs=require('fs'),assert=require('assert/strict');
(async()=>{
 const model=JSON.parse(fs.readFileSync('desktop/assets/name-gender-model.json','utf8'));
 assert.equal(fs.readFileSync('app/src/main/assets/name-gender-model.json','utf8'),JSON.stringify(model));
 const {createGenderPredictor}=await import('data:text/javascript;base64,'+Buffer.from(fs.readFileSync('desktop/name-gender.js')).toString('base64'));
 const predict=createGenderPredictor(model);
 const cases=[['Muhammad Asif','Male'],['Dr. Ayesha Khan','Female'],['M. Waseem','Male'],['Syed Muhammad Ali','Male'],['Fatima Abdullah','Female'],['Noor Ahmed',null],['Kiran Ali',null],['Naseem Ahmed',null],['Khan',null],['',null],['???',null],['Zxqv Jutt',null],['?????',null],['Zainaba','Female']];
 for(const [name,expected] of cases)assert.equal(predict(name),expected,name);
 assert(!model.dictionary.zainaba,'Out-of-vocabulary case exercises learned character patterns.');
 const {mergeDirectoryRows}=await import('data:text/javascript;base64,'+Buffer.from(fs.readFileSync('desktop/directory-sync.js')).toString('base64'));
 const saved={id:'a',name:'Ayesha Khan',gender:'Other',referenceName:'reference',called:true,status:'My Voter'};
 const refreshed=mergeDirectoryRows([saved],[{id:'a',firstName:'Ayesha',name:'Ayesha Khan',gender:'Female'}]);assert.equal(refreshed[0].gender,'Other');assert.equal(refreshed[0].referenceName,'reference');
 const real=JSON.parse(fs.readFileSync('desktop/default-members.json'));let suggestions=0;for(const m of real){const result=predict(m.name);assert(result===null||result==='Male'||result==='Female');if(result)suggestions++;}
 console.log('PASS: name normalization, ambiguous/unknown abstention, learned-pattern fallback, cross-app model parity, preserved corrections, and '+real.length+' directory records without errors. Suggestions available for '+suggestions+' records; this is coverage, not accuracy.');
})().catch(e=>{console.error(e);process.exitCode=1});
