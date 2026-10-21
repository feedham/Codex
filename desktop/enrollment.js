// Directory D.O.E values use month/day/year. ISO dates are also accepted in backups.
export const unknownEnrollment = 'Missing / invalid D.O.E';
export const enrollmentMonths = ['January','February','March','April','May','June','July','August','September','October','November','December'];
export function enrollmentParts(value) {
  const text = String(value ?? '').trim();
  const us = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(text);
  const iso = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text);
  if (!us && !iso) return null;
  const [year, month, day] = us ? [+us[3], +us[1], +us[2]] : [+iso[1], +iso[2], +iso[3]];
  const date = new Date(0);
  date.setUTCFullYear(year, month - 1, day);
  if (year < 1 || date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return null;
  return [String(year).padStart(4,'0'), String(month).padStart(2,'0'), String(day).padStart(2,'0')];
}
export function enrollmentRows(members, path = []) {
  return members.filter(m => {
    if (m.removed) return false;
    const parts = enrollmentParts(m.doe);
    return path[0] === unknownEnrollment ? !parts : path.every((part, i) => parts?.[i] === part);
  });
}
export function enrollmentGroups(members, path = []) {
  const counts = new Map();
  for (const member of enrollmentRows(members, path)) {
    const key = enrollmentParts(member.doe)?.[path.length] ?? unknownEnrollment;
    counts.set(key, (counts.get(key) || 0) + 1);
  }
  return [...counts].sort(([a],[b]) => a === unknownEnrollment ? 1 : b === unknownEnrollment ? -1 : b.localeCompare(a));
}
