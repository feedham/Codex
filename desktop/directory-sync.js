export function mergeDirectoryRows(existing,incoming){
  const key=m=>m.stableKey||(m.memberNo?'member:'+m.memberNo:m.id),old=new Map(existing.map(m=>[key(m),m])),replacements=new Map(),added=[];
  for(const fresh of incoming){
    const prior=old.get(key(fresh));
    if(!prior){added.push(fresh);continue;}
    replacements.set(prior.id,{...fresh,id:prior.id,stableKey:prior.stableKey||fresh.id,area:prior.area??fresh.area,gender:prior.gender??fresh.gender,cnic:prior.cnic??fresh.cnic,referenceName:prior.referenceName||null,groupAffiliation:prior.groupAffiliation||'Independent Voter',called:!!prior.called,status:prior.status||'More Working Required',photo:prior.photo||fresh.photo,removed:!!prior.removed});
  }
  return existing.map(m=>replacements.get(m.id)||m).concat(added);
}
export function openDirectoryDownload(modal,onRows){
  modal.classList.remove('hidden');modal.innerHTML='<div class="dialog"><h2>Download SQLite directory</h2><p class="small">Download the source member list. Local campaign details and members absent from the server are kept.</p><form id="directory-form" class="form"><label for="directory-token">Directory access token</label><input id="directory-token" class="field" type="password" minlength="32" required autocomplete="off"><button class="primary">Download directory</button></form><p id="directory-feedback" class="small" aria-live="polite"></p><button id="directory-close" class="quiet">Cancel</button></div>';
  const $=s=>modal.querySelector(s);$('#directory-close').onclick=()=>{modal.classList.add('hidden');modal.innerHTML='';};
  $('#directory-form').onsubmit=async event=>{
    event.preventDefault();const token=$('#directory-token').value.trim(),controls=[...modal.querySelectorAll('button,input')];controls.forEach(c=>c.disabled=true);
    try{
      async function get(path){const response=await fetch('/api/'+path,{headers:{Authorization:'Bearer '+token}});const data=await response.json();if(!response.ok)throw Error(data.error||'Directory download failed.');return data;}
      const meta=await get('directory');if(!/^[a-f0-9]{64}$/.test(meta.revision)||!Number.isInteger(meta.memberCount)||meta.memberCount<1||meta.memberCount>20000)throw Error('Invalid directory metadata.');
      const rows=[],ids=new Set();let after='';
      do{
        const data=await get('members?revision='+meta.revision+'&limit=100&after='+encodeURIComponent(after));
        if(data.revision!==meta.revision||!Array.isArray(data.members)||data.members.length<1||data.members.length>100)throw Error('Directory changed or returned an invalid page. Retry the download.');
        for(const m of data.members){
          if(typeof m.id!=='string'||ids.has(m.id)||!m.firstName)throw Error('Invalid or duplicate member record.');ids.add(m.id);
          rows.push({...m,name:[m.firstName,m.lastName].filter(Boolean).join(' '),doe:m.doe||m.enrollmentSource,called:false,status:'More Working Required',groupAffiliation:'Independent Voter',removed:false});
        }
        if(rows.length>meta.memberCount)throw Error('Directory count mismatch.');
        if(data.next&&(data.next===after||data.next!==rows.at(-1).id))throw Error('Invalid directory cursor.');
        after=data.next;$('#directory-feedback').textContent='Downloaded '+rows.length+' / '+meta.memberCount;
      }while(after);
      if(rows.length!==meta.memberCount)throw Error('The download was incomplete. Local data was not changed.');
      await onRows(rows);modal.classList.add('hidden');modal.innerHTML='';
    }catch(error){$('#directory-feedback').textContent=error.message;controls.forEach(c=>c.disabled=false);}
  };
}
