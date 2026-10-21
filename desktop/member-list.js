import {enrollmentParts} from './enrollment.js';

export function seniorMemberRows(members,limit=50){
  return memberListRows(members.filter(member=>enrollmentParts(member.doe)), '', 'doe-oldest').slice(0,limit);
}

export function memberListRows(members, search='', sort='name') {
  const query=search.trim().toLowerCase(),digits=query.replace(/\D/g,'');
  const rows=members.filter(m=>{
    if(m.removed)return false;
    const parts=enrollmentParts(m.doe);
    const dates=[parts?.join('-')||'',parts?`${+parts[2]}/${+parts[1]}/${parts[0]}`:'',parts?`${parts[2]}/${parts[1]}/${parts[0]}`:''];
    return !query||m.name.toLowerCase().includes(query)||dates.some(date=>date.toLowerCase().includes(query))||
      (/^[+\d\s()-]+$/.test(query)&&digits.length>0&&String(m.cell||'').replace(/\D/g,'').includes(digits));
  });
  return rows.sort((a,b)=>{
    if(sort==='doe-oldest'||sort==='doe-newest'){
      const da=enrollmentParts(a.doe)?.join('-'),db=enrollmentParts(b.doe)?.join('-');
      if(!da&&db)return 1;
      if(da&&!db)return -1;
      if(da&&db&&da!==db)return (sort==='doe-newest'?-1:1)*da.localeCompare(db);
    }
    return a.name.localeCompare(b.name,undefined,{sensitivity:'base'})||String(a.id).localeCompare(String(b.id));
  });
}
