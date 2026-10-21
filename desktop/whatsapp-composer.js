import {whatsappNumber,contactCards,BROADCAST_LIMIT} from './phone.js';
const escape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const draft={message:'',file:null,url:null,ids:new Set()};
export function clearWhatsAppDraft(){if(draft.url)URL.revokeObjectURL(draft.url);draft.message='';draft.file=null;draft.url=null;draft.ids.clear();}
export function downloadBlob(blob,name){const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
export function renderWhatsAppComposer(root,members,onBack){
  const eligible=members.filter(m=>!m.removed&&whatsappNumber(m.cell)),known=new Set(eligible.map(m=>m.id));
  draft.ids=new Set([...draft.ids].filter(id=>known.has(id)));
  root.innerHTML=`<button class="quiet" id="composer-back">Back to members</button><section class="insight-box composer-card"><h1>Compose WhatsApp message</h1><p class="sub">Prepare text and an image, then choose recipients and confirm sending in WhatsApp.</p><label class="composer-label" for="wa-message">Message / image caption</label><textarea id="wa-message" class="profile-input composer-message" rows="5" maxlength="4000">${escape(draft.message)}</textarea><label class="composer-label" for="wa-image">Attach image</label><input class="field" id="wa-image" type="file" accept="image/jpeg,image/png,image/webp"><img id="wa-preview" class="composer-preview ${draft.url?'':'hidden'}" alt="Message image preview"><div class="actions"><button class="secondary" id="wa-remove">Remove image</button><button class="primary" id="wa-share">Share text and image</button><button class="secondary" id="wa-copy">Copy message</button><button class="secondary" id="wa-save">Save image</button><a class="secondary composer-link" id="wa-open" href="whatsapp://send" target="_blank" rel="noopener">Open installed WhatsApp</a></div><p class="small" id="wa-feedback" aria-live="polite">Native sharing depends on your browser and installed apps. If unavailable, copy the message, save the image, then attach them inside WhatsApp.</p><details class="composer-contacts"><summary>Choose member contacts for export (<span id="wa-count">${draft.ids.size}</span> / ${BROADCAST_LIMIT})</summary><p class="small">Save the VCF, import it into your phone Contacts app, then create a broadcast list in WhatsApp. Selection here does not preselect WhatsApp recipients.</p><input class="search" id="wa-filter" placeholder="Search contacts by name or cell" aria-label="Search contacts"><div id="wa-contacts" class="contact-picker"></div><div class="actions"><button class="secondary" id="wa-export">Save selected contacts (.vcf)</button><button class="quiet" id="wa-clear">Clear selection</button></div></details><p class="small">Broadcast lists are managed in WhatsApp. Recipients must have your number saved. <a href="https://faq.whatsapp.com/861663048350950/" target="_blank" rel="noopener">Broadcast instructions</a>. Opening WhatsApp does not confirm sending or delivery.</p></section>`;
  const $=selector=>root.querySelector(selector),feedback=text=>{$('#wa-feedback').textContent=text;};
  $('#composer-back').onclick=onBack;
  function update(){
    $('#wa-open').href='whatsapp://send'+(draft.message?'?text='+encodeURIComponent(draft.message):'');
    $('#wa-share').disabled=!draft.message.trim()&&!draft.file;$('#wa-copy').disabled=!draft.message.trim();$('#wa-save').disabled=!draft.file;$('#wa-remove').disabled=!draft.file;
    const img=$('#wa-preview');img.classList.toggle('hidden',!draft.url);if(draft.url)img.src=draft.url;else img.removeAttribute('src');
    $('#wa-count').textContent=draft.ids.size;$('#wa-export').disabled=!draft.ids.size;
  }
  $('#wa-message').oninput=e=>{draft.message=e.target.value;update();};
  $('#wa-image').onchange=e=>{const file=e.target.files[0];if(!file)return;if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size>20*1024*1024){feedback('Choose a JPEG, PNG, or WebP image up to 20 MB.');return;}if(draft.url)URL.revokeObjectURL(draft.url);draft.file=file;draft.url=URL.createObjectURL(file);update();};
  $('#wa-remove').onclick=()=>{if(draft.url)URL.revokeObjectURL(draft.url);draft.file=null;draft.url=null;$('#wa-image').value='';update();};
  $('#wa-save').onclick=()=>{if(draft.file)downloadBlob(draft.file,draft.file.name);};
  $('#wa-copy').onclick=async()=>{try{await navigator.clipboard.writeText(draft.message);feedback('Message copied. Paste it into WhatsApp.');}catch{feedback('Select the message text and copy it manually.');}};
  $('#wa-share').onclick=async()=>{
    const data={text:draft.message,...(draft.file?{files:[draft.file]}:{})};
    if(!navigator.share||(draft.file&&(!navigator.canShare||!navigator.canShare(data)))){feedback('Sharing files is unavailable here. Use Copy message and Save image, then open WhatsApp and attach the image.');return;}
    try{await navigator.share(data);feedback('Returned from sharing. Confirm recipients and sending inside WhatsApp.');}catch(error){if(error.name!=='AbortError')feedback('Sharing could not start. Use Copy message and Save image instead.');}
  };
  function drawContacts(){
    const query=$('#wa-filter').value.trim().toLowerCase();
    const visible=eligible.filter(m=>m.name.toLowerCase().includes(query)||String(m.cell||'').includes(query));
    $('#wa-contacts').innerHTML=visible.map(m=>`<label class="contact-choice"><input type="checkbox" data-id="${escape(m.id)}" ${draft.ids.has(m.id)?'checked':''} ${draft.ids.size>=BROADCAST_LIMIT&&!draft.ids.has(m.id)?'disabled':''}><span>${escape(m.name)}<small>${escape(m.cell)}</small></span></label>`).join('');
    root.querySelectorAll('#wa-contacts input').forEach(input=>input.onchange=()=>{if(input.checked){if(draft.ids.size>=BROADCAST_LIMIT){input.checked=false;return;}draft.ids.add(input.dataset.id);}else draft.ids.delete(input.dataset.id);drawContacts();update();});
  }
  $('#wa-filter').oninput=drawContacts;$('#wa-clear').onclick=()=>{draft.ids.clear();drawContacts();update();};
  $('#wa-export').onclick=()=>{const rows=eligible.filter(m=>draft.ids.has(m.id));downloadBlob(new Blob([contactCards(rows)],{type:'text/vcard;charset=utf-8'}),'bar-members-whatsapp-contacts.vcf');feedback('Contacts saved. Import them with the phone Contacts app, then create your broadcast list inside WhatsApp.');};
  drawContacts();update();
}
