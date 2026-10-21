export function createGenderPredictor(model){
 if(model.version!==1||!model.weights||!model.dictionary)throw Error('Invalid name classifier');
 const skip=new Set(model.skip),ambiguous=new Set(model.ambiguous),surnames=new Set(model.surnames);
 return name=>{
  const words=String(name||'').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z\s.'-]/g,' ').split(/[\s.'-]+/).filter(x=>x.length>1&&!skip.has(x));
  const token=words[0];if(!token||token.length>30||ambiguous.has(token)||surnames.has(token))return null;
  if(model.dictionary[token])return model.dictionary[token];
  if(token.length<4)return null;
  const s='^'+token+'$',features=new Set();for(let n=2;n<=4;n++)for(let i=0;i+n<=s.length;i++)features.add(s.slice(i,i+n));
  let known=0,score=0;for(const f of features)if(Object.hasOwn(model.weights,f)){known++;score+=model.weights[f];}
  if(known/features.size<model.minCoverage||Math.abs(score)<model.minMargin)return null;
  return score>0?'Male':'Female';
 };
}
let predict=()=>null,loaded;
export function loadGenderModel(){return loaded??=(fetch('/assets/name-gender-model.json',{cache:'no-store'}).then(r=>{if(!r.ok)throw Error('Name classifier unavailable');return r.json();}).then(model=>{predict=createGenderPredictor(model);}).catch(()=>{predict=()=>null;}));}
export function suggestGender(name){return predict(name);}
