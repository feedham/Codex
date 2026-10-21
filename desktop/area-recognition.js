const openDb = new Promise((resolve, reject) => {
  const request = indexedDB.open('bar-member-manager', 1);
  request.onsuccess = () => resolve(request.result);
  request.onerror = () => reject(request.error);
});

async function read(key, fallback) {
  const db = await openDb;
  return new Promise(resolve => {
    const request = db.transaction('state').objectStore('state').get(key);
    request.onsuccess = () => resolve(request.result ?? fallback);
    request.onerror = () => resolve(fallback);
  });
}

async function write(key, value) {
  const db = await openDb;
  return new Promise((resolve, reject) => {
    const tx = db.transaction('state', 'readwrite');
    tx.objectStore('state').put(value, key);
    tx.oncomplete = resolve;
    tx.onerror = () => reject(tx.error);
  });
}

const normalizeAddress = value => String(value || '').trim().toLocaleLowerCase().replace(/\s+/g, ' ');
const runtimeAreas = new Map();
window.memberAreaFor = member => member.area || runtimeAreas.get(normalizeAddress(member.residence))?.area || null;
window.clearMemberAreas = () => runtimeAreas.clear();

async function recognizeAreas(button) {
  const originalLabel = button.textContent;
  button.disabled = true;
  try {
    const members = await read('members', []);
    const groups = new Map();
    for (const member of members) {
      if (member.removed || !member.residence || member.area) continue;
      const key = normalizeAddress(member.residence);
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(member);
    }
    const pending = [...groups].filter(([address]) => !runtimeAreas.has(address));
    const eligibleCount = pending.reduce((sum, [, rows]) => sum + rows.length, 0);
    if (!pending.length) {
      alert('Every member residence address has already been checked in this session, or no addresses are available.');
      return;
    }

    const addressLookups = pending.filter(([, rows]) => !rows.some(member => member.placeId)).length;
    const placeIdLookups = pending.length - addressLookups;
    if (!confirm(`Recognize areas for ${eligibleCount.toLocaleString()} members using ${pending.length.toLocaleString()} unique lookups? ${addressLookups.toLocaleString()} residence addresses and ${placeIdLookups.toLocaleString()} saved Google Place IDs will be sent to Google Maps Geocoding. Google may bill your Cloud project for requests. Continue only if Geocoding API and billing are enabled for your key.`)) return;

    let completed = 0;
    let failure = null;
    button.textContent = `Recognizing 0 / ${pending.length}…`;
    let next = 0;
    const worker = async () => {
      while (next < pending.length && !failure) {
        const [address, rows] = pending[next++];
        try {
          const placeId = rows.find(member => member.placeId)?.placeId;
          const response = await fetch('/api/recognize-area', {
            method: 'POST',
            headers: {'Content-Type':'application/json'},
            body: JSON.stringify(placeId ? {placeId} : {address:rows[0].residence})
          });
          const data = await response.json();
          if (!response.ok) throw new Error(data.error || `Area lookup failed (${response.status}).`);
          runtimeAreas.set(address, {area:data.area, source:data.source});
          if (data.placeId) rows.forEach(member => { member.placeId = data.placeId; });
          completed++;
          button.textContent = `Recognizing ${completed} / ${pending.length}…`;
        } catch (error) {
          failure ||= error;
        }
      }
    };
    await Promise.all(Array.from({length:Math.min(3, pending.length)}, worker));

    // Google permits indefinite storage of Place IDs; area labels stay in memory for this session only.
    await write('members', members);
    window.dispatchEvent(new CustomEvent('members-area-updated'));
    if (failure) throw new Error(`Stopped after ${completed} successful lookups; their Place IDs were saved. ${failure.message}`);
    const matched = pending.reduce((count, [address, rows]) => count + (runtimeAreas.get(address)?.area ? rows.length : 0), 0);
    alert(`Area recognition complete: ${matched.toLocaleString()} members matched to an area. ${eligibleCount - matched} addresses could not be assigned a specific area.`);
  } catch (error) {
    alert(`Could not recognize member areas: ${error.message}`);
  } finally {
    button.disabled = false;
    button.textContent = originalLabel;
  }
}

function attachAreaButton() {
  const importButton = document.querySelector('#import');
  const actions = importButton?.parentElement;
  if (!actions || document.querySelector('#recognize-areas')) return;
  const button = document.createElement('button');
  button.id = 'recognize-areas';
  button.className = 'secondary';
  button.textContent = '⌖ Recognize areas';
  button.title = 'Resolve Area from residence using Google Maps Geocoding';
  button.addEventListener('click', () => recognizeAreas(button));
  actions.append(button);
}

new MutationObserver(attachAreaButton).observe(document.querySelector('#content'), {childList:true, subtree:true});
