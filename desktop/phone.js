export const BROADCAST_LIMIT=256;
export function whatsappNumber(value){
  const text=String(value||'').trim();if(!/^\+?[0-9 ()-]+$/.test(text))return null;
  const digits=text.replace(/\D/g,'');
  const number=text.startsWith('+')?digits:digits.startsWith('00')?digits.slice(2):digits.startsWith('0')&&digits.length===11?'92'+digits.slice(1):digits.startsWith('3')&&digits.length===10?'92'+digits:digits;
  return /^[1-9][0-9]{7,14}$/.test(number)?number:null;
}
export function contactCards(members){
  if(members.length>BROADCAST_LIMIT)throw Error('Choose at most 256 members.');
  const escape=value=>String(value||'').replaceAll('\\','\\\\').replace(/\r\n?|\n/g,'\\n').replaceAll(';','\\;').replaceAll(',','\\,');
  const seen=new Set();
  return members.map(m=>{const number=whatsappNumber(m.cell);if(!number||seen.has(number))return '';seen.add(number);return `BEGIN:VCARD\r\nVERSION:3.0\r\nN:${escape(m.lastName)};${escape(m.firstName||m.name)};;;\r\nFN:${escape(m.name||[m.firstName,m.lastName].filter(Boolean).join(' '))}\r\nTEL;TYPE=CELL:+${number}\r\nEND:VCARD\r\n`;}).join('');
}
