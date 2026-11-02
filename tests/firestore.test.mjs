import { before, after, beforeEach, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, collection, setDoc, updateDoc, deleteDoc, arrayUnion, writeBatch } from 'firebase/firestore';
let env;
const code = 'A1B2C3D4E5F60708';
const amal = { id: 'fajr', name: 'Fajr', category: 'Prayer', frequency: 'daily', weekday: 5, hijriMonth: 9, hijriDay: 1, active: true };
const circle = { id: 'family', name: 'Family', owner: 'admin', inviteCode: code, members: ['admin'] };
const db = uid => uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore();
before(async () => { env = await initializeTestEnvironment({ projectId: 'demo-amaal-tracker', firestore: { rules: readFileSync('firestore.rules', 'utf8') } }); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    const f = ctx.firestore();
    await setDoc(doc(f, 'circles/family'), circle);
    await setDoc(doc(f, `invites/${code}`), { circleId: 'family', owner: 'admin' });
    await setDoc(doc(f, 'circles/family/amals/fajr'), amal);
  });
});
test('anonymous and nonmembers cannot read a circle or its logs', async () => {
  await assertFails(getDoc(doc(db(null), 'circles/family')));
  await assertFails(getDoc(doc(db('outsider'), 'circles/family')));
  await assertFails(getDocs(collection(db('outsider'), 'circles/family/logs')));
  await assertSucceeds(getDoc(doc(db('admin'), 'circles/family')));
});
test('invites require authentication and cannot be enumerated', async () => {
  await assertFails(getDoc(doc(db(null), `invites/${code}`)));
  await assertFails(getDocs(collection(db('outsider'), 'invites')));
  await assertSucceeds(getDoc(doc(db('outsider'), `invites/${code}`)));
});
test('join requires the code and adds only the caller', async () => {
  const f = db('guest');
  await assertFails(updateDoc(doc(f, 'circles/family'), { members: arrayUnion('guest') }));
  await assertFails(updateDoc(doc(f, 'circles/family'), { members: arrayUnion('guest'), joinProof: 'wrong-code' }));
  await assertFails(updateDoc(doc(f, 'circles/family'), { members: arrayUnion('guest', 'third'), joinProof: code }));
  const batch = writeBatch(f);
  batch.update(doc(f, 'circles/family'), { members: arrayUnion('guest'), joinProof: code });
  batch.set(doc(f, 'circles/family/profiles/guest'), { name: 'Guest' });
  batch.set(doc(f, 'users/guest'), { circleId: 'family', name: 'Guest' });
  await assertSucceeds(batch.commit());
  await assertSucceeds(getDoc(doc(f, 'circles/family')));
});
test('members cannot remove others or change ownership', async () => {
  const f = db('guest');
  await assertSucceeds(updateDoc(doc(f, 'circles/family'), { members: arrayUnion('guest'), joinProof: code }));
  await assertFails(updateDoc(doc(f, 'circles/family'), { members: ['guest'] }));
  await assertFails(updateDoc(doc(f, 'circles/family'), { owner: 'guest' }));
  await assertSucceeds(setDoc(doc(f, 'circles/family/amals/fajr'), { ...amal, name: 'Changed' }));
});
test('admin can create circle, invite, profile and amals atomically', async () => {
  const f = db('newadmin'); const batch = writeBatch(f); const inviteCode = '1234567890ABCDEF';
  batch.set(doc(f, 'circles/new'), { ...circle, id: 'new', owner: 'newadmin', members: ['newadmin'], inviteCode });
  batch.set(doc(f, `invites/${inviteCode}`), { circleId: 'new', owner: 'newadmin' });
  batch.set(doc(f, 'circles/new/profiles/newadmin'), { name: 'Admin' });
  for (let i = 0; i < 10; i++) batch.set(doc(f, `circles/new/amals/a${i}`), { ...amal, id: `a${i}` });
  batch.set(doc(f, 'users/newadmin'), { circleId: 'new' });
  await assertSucceeds(batch.commit());
});
test('logs must belong to the caller and use valid weighted scores', async () => {
  const f = db('admin'); const id = 'admin_2026-10-31_fajr';
  const ref = doc(f, `circles/family/logs/${id}`);
  const entry = { id, uid: 'admin', amalId: 'fajr', date: '2026-10-31', status: 'Prayed in Jamat', score: 8 };
  await assertSucceeds(setDoc(ref, entry));
  await assertFails(setDoc(ref, { ...entry, score: 100 }));
  await assertFails(setDoc(ref, { ...entry, uid: 'other' }));
  await assertFails(deleteDoc(doc(db('outsider'), `circles/family/logs/${id}`)));
  await assertSucceeds(deleteDoc(ref));
});
test('users cannot read another user private document', async () => {
  await assertSucceeds(setDoc(doc(db('admin'), 'users/admin'), { name: 'Admin' }));
  await assertFails(getDoc(doc(db('outsider'), 'users/admin')));
});
test('joined members may read but cannot alter another member logs', async () => {
  const f = db('admin'); const id = 'admin_2026-10-31_fajr';
  const entry = { id, uid: 'admin', amalId: 'fajr', date: '2026-10-31', status: 'Qada', score: 2 };
  await assertSucceeds(setDoc(doc(f, `circles/family/logs/${id}`), entry));
  const guest = db('guest');
  await assertSucceeds(updateDoc(doc(guest, 'circles/family'), { members: arrayUnion('guest'), joinProof: code }));
  const ref = doc(guest, `circles/family/logs/${id}`);
  await assertSucceeds(getDoc(ref));
  await assertFails(setDoc(ref, { ...entry, status: 'Takbir-e-Ula', score: 10 }));
  await assertFails(deleteDoc(ref));
  await assertFails(setDoc(doc(guest, 'invites/1234567890ABCDEF'), { circleId: 'family', owner: 'guest' }));
});
test('only the family owner can rename it without changing ownership or membership', async () => {
  const admin = db('admin'); const guest = db('guest');
  await assertSucceeds(updateDoc(doc(admin, 'circles/family'), { name: 'Our family' }));
  await assertSucceeds(updateDoc(doc(guest, 'circles/family'), { members: arrayUnion('guest'), joinProof: code }));
  await assertFails(updateDoc(doc(guest, 'circles/family'), { name: 'Changed by member' }));
  await assertFails(updateDoc(doc(admin, 'circles/family'), { name: '' }));
  await assertFails(updateDoc(doc(admin, 'circles/family'), { name: 'x'.repeat(81) }));
  await assertFails(updateDoc(doc(admin, 'circles/family'), { name: 'Hijack', owner: 'guest' }));
});
test('admin deletes a custom amal atomically while retaining history and its definition', async () => {
  const f = db('admin');
  const custom = { ...amal, id: 'reading', name: 'Reading', category: 'Tilawat' };
  await assertSucceeds(setDoc(doc(f, 'circles/family/amals/reading'), custom));
  const log = { id: 'admin_2026-10-31_reading', uid: 'admin', amalId: 'reading', date: '2026-10-31', status: 'Completed', score: 10 };
  await assertSucceeds(setDoc(doc(f, `circles/family/logs/${log.id}`), log));
  await assertFails(deleteDoc(doc(f, 'circles/family/amals/reading')));
  const batch = writeBatch(f);
  batch.set(doc(f, 'circles/family/retiredAmals/reading'), custom);
  batch.delete(doc(f, 'circles/family/amals/reading'));
  await assertSucceeds(batch.commit());
  const removed = await assertSucceeds(getDoc(doc(f, 'circles/family/amals/reading')));
  if (removed.exists()) throw new Error('Deleted definition still exists in active amals');
  await assertSucceeds(getDoc(doc(f, `circles/family/logs/${log.id}`)));
  await assertSucceeds(getDoc(doc(f, 'circles/family/retiredAmals/reading')));
  await assertFails(updateDoc(doc(f, 'circles/family/retiredAmals/reading'), { category: 'Common' }));
  await assertFails(setDoc(doc(f, 'circles/family/amals/reading'), custom));
  await assertFails(getDoc(doc(db('outsider'), 'circles/family/retiredAmals/reading')));
});
test('protected salah and daily Quran IDs cannot be deleted even by admin', async () => {
  const f = db('admin');
  await env.withSecurityRulesDisabled(async ctx => {
    for (const id of ['fajr', 'zuhr', 'asr', 'maghrib', 'isha', 'tilawat']) {
      await setDoc(doc(ctx.firestore(), `circles/family/amals/${id}`), { ...amal, id });
    }
  });
  for (const id of ['fajr', 'zuhr', 'asr', 'maghrib', 'isha', 'tilawat']) {
    const batch = writeBatch(f);
    batch.set(doc(f, `circles/family/retiredAmals/${id}`), { ...amal, id });
    batch.delete(doc(f, `circles/family/amals/${id}`));
    await assertFails(batch.commit());
  }
  await assertFails(updateDoc(doc(f, 'circles/family/amals/fajr'), { category: 'Common' }));
  await assertFails(updateDoc(doc(f, 'circles/family/amals/fajr'), { frequency: 'weekly' }));
});
test('ordinary members cannot delete custom amals or forge retained definitions', async () => {
  const admin = db('admin'); const guest = db('guest');
  const custom = { ...amal, id: 'custom', name: 'Custom', category: 'Common' };
  await assertSucceeds(setDoc(doc(admin, 'circles/family/amals/custom'), custom));
  await assertSucceeds(updateDoc(doc(guest, 'circles/family'), { members: arrayUnion('guest'), joinProof: code }));
  const batch = writeBatch(guest);
  batch.set(doc(guest, 'circles/family/retiredAmals/custom'), custom);
  batch.delete(doc(guest, 'circles/family/amals/custom'));
  await assertFails(batch.commit());
  const forged = writeBatch(admin);
  forged.set(doc(admin, 'circles/family/retiredAmals/custom'), { ...custom, category: 'Tilawat' });
  forged.delete(doc(admin, 'circles/family/amals/custom'));
  await assertFails(forged.commit());
});
test('members can review all members in bounded monthly queries, outsiders cannot', async () => {
  const f = db('admin');
  await assertSucceeds(updateDoc(doc(db('guest'), 'circles/family'), { members: arrayUnion('guest'), joinProof: code }));
  await env.withSecurityRulesDisabled(async ctx => {
    for (const uid of ['admin', 'guest']) {
      const id = `${uid}_2026-08-01_fajr`;
      await setDoc(doc(ctx.firestore(), `circles/family/logs/${id}`), { id, uid, amalId: 'fajr', date: '2026-08-01', status: 'Qada', score: 2 });
    }
  });
  const { query, where } = await import('firebase/firestore');
  const monthly = f => query(collection(f, 'circles/family/logs'), where('date', '>=', '2026-08-01'), where('date', '<=', '2026-08-31'));
  const result = await assertSucceeds(getDocs(monthly(db('guest'))));
  if (result.size !== 2) throw new Error('Expected both family members in monthly query');
  await assertFails(getDocs(monthly(db('outsider'))));
});
test('membership discovery lists every joined family without exposing other families', async () => {
  await env.withSecurityRulesDisabled(async ctx => {
    const f = ctx.firestore();
    await setDoc(doc(f, 'circles/second'), { ...circle, id: 'second', name: 'Second family', owner: 'other', members: ['other', 'admin'] });
    await setDoc(doc(f, 'circles/private'), { ...circle, id: 'private', name: 'Private', owner: 'outsider', members: ['outsider'] });
    await setDoc(doc(f, 'users/admin'), { circleId: 'family' });
  });
  const { query, where } = await import('firebase/firestore');
  const own = query(collection(db('admin'), 'circles'), where('members', 'array-contains', 'admin'));
  const result = await assertSucceeds(getDocs(own));
  const ids = result.docs.map(d => d.id).sort();
  if (JSON.stringify(ids) !== JSON.stringify(['family', 'second'])) throw new Error('Joined families missing or private family exposed');
  await assertFails(getDocs(collection(db('admin'), 'circles')));
  await assertFails(getDocs(query(collection(db('outsider'), 'circles'), where('members', 'array-contains', 'admin'))));
  await assertFails(getDocs(query(collection(db(null), 'circles'), where('members', 'array-contains', 'admin'))));
});
test('switching the selected family never grants membership or admin powers', async () => {
  const f = db('admin');
  await env.withSecurityRulesDisabled(async ctx => {
    await setDoc(doc(ctx.firestore(), 'circles/second'), { ...circle, id: 'second', owner: 'other', members: ['other', 'admin'] });
    await setDoc(doc(ctx.firestore(), 'circles/private'), { ...circle, id: 'private', owner: 'outsider', members: ['outsider'] });
    await setDoc(doc(ctx.firestore(), 'circles/second/amals/fajr'), amal);
  });
  await assertSucceeds(updateDoc(doc(f, 'circles/family'), { name: 'Owned family' }));
  await assertSucceeds(setDoc(doc(f, 'users/admin'), { circleId: 'second' }));
  await assertSucceeds(getDoc(doc(f, 'circles/second')));
  await assertFails(updateDoc(doc(f, 'circles/second'), { name: 'Not my family to rename' }));
  await assertSucceeds(setDoc(doc(f, 'circles/second/amals/fajr'), { ...amal, name: 'Changed by member' }));
  const entry = { id: 'admin_2026-10-31_fajr', uid: 'admin', amalId: 'fajr', date: '2026-10-31', status: 'Qada', score: 2 };
  await assertSucceeds(setDoc(doc(f, `circles/family/logs/${entry.id}`), entry));
  await assertSucceeds(setDoc(doc(f, `circles/second/logs/${entry.id}`), { ...entry, status: 'Takbir-e-Ula', score: 10 }));
  const first = await assertSucceeds(getDoc(doc(f, `circles/family/logs/${entry.id}`)));
  const second = await assertSucceeds(getDoc(doc(f, `circles/second/logs/${entry.id}`)));
  if (first.data().score !== 2 || second.data().score !== 10) throw new Error('Family records were mixed');
  await assertSucceeds(setDoc(doc(f, 'users/admin'), { circleId: 'private' }));
  await assertFails(getDoc(doc(f, 'circles/private')));
  await assertFails(getDocs(collection(db('outsider'), 'circles/family/logs')));
});

test('members can add, edit and archive amals but outsiders cannot', async () => {
  const f = db('guest');
  await assertSucceeds(updateDoc(doc(f, 'circles/family'), { members: arrayUnion('guest'), joinProof: code }));
  await assertSucceeds(updateDoc(doc(f, 'circles/family/amals/fajr'), { name: 'Fajr prayer', active: false }));
  await assertFails(updateDoc(doc(f, 'circles/family/amals/fajr'), { category: 'Common' }));
  await assertSucceeds(setDoc(doc(f, 'circles/family/amals/new'), { ...amal, id: 'new', category: 'Common' }));
  await assertFails(updateDoc(doc(db('outsider'), 'circles/family/amals/new'), { name: 'No' }));
});
test('owner locks a family, resumes cleanup, and deletes its invite and parent', async () => {
  const f = db('admin'); const guest = db('guest'); const ref = doc(f, 'circles/family');
  await assertSucceeds(updateDoc(doc(guest, 'circles/family'), { members: arrayUnion('guest'), joinProof: code }));
  await assertFails(updateDoc(doc(guest, 'circles/family'), { deleting: true }));
  await assertFails(deleteDoc(ref));
  await assertSucceeds(updateDoc(ref, { deleting: true }));
  await assertSucceeds(updateDoc(ref, { deleting: true }));
  await assertFails(updateDoc(ref, { deleting: false }));
  await assertFails(updateDoc(ref, { name: 'Rename locked family' }));
  await assertFails(updateDoc(doc(db('newmember'), 'circles/family'), { members: arrayUnion('newmember'), joinProof: code }));
  await assertFails(setDoc(doc(f, 'circles/family/amals/new'), { ...amal, id: 'new' }));
  await assertFails(updateDoc(doc(guest, 'circles/family/amals/fajr'), { active: false }));
  await assertFails(setDoc(doc(guest, 'circles/family/profiles/guest'), { name: 'Guest' }));
  await assertFails(setDoc(doc(f, 'circles/family/logs/admin_2026-10-31_fajr'), {
    id: 'admin_2026-10-31_fajr', uid: 'admin', amalId: 'fajr', date: '2026-10-31', status: 'Qada', score: 2
  }));
  await env.withSecurityRulesDisabled(async ctx => {
    for (const collection of ['profiles', 'logs', 'retiredAmals']) await setDoc(doc(ctx.firestore(), `circles/family/${collection}/test`), { test: true });
  });
  for (const collectionName of ['amals', 'profiles', 'logs', 'retiredAmals']) {
    const page = await assertSucceeds(getDocs(collection(f, `circles/family/${collectionName}`)));
    for (const item of page.docs) {
      await assertFails(deleteDoc(doc(guest, item.ref.path)));
      await assertSucceeds(deleteDoc(item.ref));
    }
  }
  await assertFails(deleteDoc(doc(guest, `invites/${code}`)));
  await assertFails(deleteDoc(doc(guest, 'circles/family')));
  await assertFails(deleteDoc(ref)); // Invite must be removed, too.
  const batch = writeBatch(f);
  batch.delete(doc(f, `invites/${code}`)); batch.delete(ref);
  await assertSucceeds(batch.commit());
  const invite = await assertSucceeds(getDoc(doc(f, `invites/${code}`)));
  if (invite.exists()) throw new Error('Invite remained after deletion');
});

test('owners can delete former occasion built-ins and azkar, while daily Quran stays daily', async () => {
  const f = db('admin');
  for (const id of ['kahf', 'ashura', 'azkar', 'kindness']) {
    const definition = { ...amal, id, category: 'Common' };
    await assertSucceeds(setDoc(doc(f, `circles/family/amals/${id}`), definition));
    const batch = writeBatch(f);
    batch.set(doc(f, `circles/family/retiredAmals/${id}`), definition);
    batch.delete(doc(f, `circles/family/amals/${id}`));
    await assertSucceeds(batch.commit());
  }
  const quran = doc(f, 'circles/family/amals/tilawat');
  await assertSucceeds(setDoc(quran, { ...amal, id: 'tilawat', category: 'Tilawat' }));
  await assertFails(updateDoc(quran, { category: 'Common' }));
  await assertFails(updateDoc(quran, { frequency: 'weekly' }));
});
