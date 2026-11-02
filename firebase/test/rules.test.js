// Firestore security rule tests (PRD 10.3). Run with: npm run test:rules
// Uses only the local emulator; never touches the real Firebase project.
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc, writeBatch,
} from 'firebase/firestore';

const WID = 'family1';
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-family-finance',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: Number(process.env.RULES_PORT ?? 8080) },
  });
});
after(async () => env.cleanup());
beforeEach(async () => env.clearFirestore());

const father = () => env.authenticatedContext('father', { email: 'father@example.com', email_verified: true }).firestore();
const mother = () => env.authenticatedContext('mother', { email: 'Mother@Example.com', email_verified: true }).firestore();
const stranger = () => env.authenticatedContext('stranger', { email: 'stranger@example.com', email_verified: true }).firestore();
const anonymous = () => env.unauthenticatedContext().firestore();

/** Puts an email on the founders allowlist, as the owner would in the Firebase console. */
async function allowFounder(email) {
  await env.withSecurityRulesDisabled(async (ctx) => setDoc(doc(ctx.firestore(), `founders/${email}`), {}));
}

/** Father founds the workspace: workspace + admin membership + pointer, in one batch. */
async function foundWorkspace(db = father()) {
  const b = writeBatch(db);
  b.set(doc(db, `workspaces/${WID}`), { name: 'Family', createdBy: 'father', createdAt: serverTimestamp(), schemaVersion: 1 });
  b.set(doc(db, `workspaces/${WID}/members/father`), {
    role: 'admin', status: 'active', email: 'father@example.com', displayName: 'Father', createdAt: serverTimestamp(),
  });
  b.set(doc(db, 'users/father'), { workspaceId: WID, updatedAt: serverTimestamp() });
  return b.commit();
}

/** Seeds an existing family with father (admin) and, optionally, mother (member). */
async function seedFamily({ withMother = false } = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `workspaces/${WID}`), { name: 'Family', createdBy: 'father', schemaVersion: 1 });
    await setDoc(doc(db, `workspaces/${WID}/members/father`), { role: 'admin', status: 'active', email: 'father@example.com' });
    if (withMother) {
      await setDoc(doc(db, `workspaces/${WID}/members/mother`), { role: 'member', status: 'active', email: 'mother@example.com' });
    }
  });
}

function txn(overrides = {}) {
  return {
    type: 'expense', amountPaise: 265000, date: '2026-11-01', title: 'உரம்',
    accountId: 'cash', categoryId: 'sys.fertilizers',
    createdBy: 'father', createdAt: serverTimestamp(), updatedBy: 'father', updatedAt: serverTimestamp(),
    version: 1, deleted: false,
    ...overrides,
  };
}

describe('founding a family', () => {
  test('an allowed person can create a new family and become its admin', async () => {
    await allowFounder('father@example.com');
    await assertSucceeds(foundWorkspace());
  });

  test('a signed-in person who is not on the founders list cannot create a family', async () => {
    await assertFails(foundWorkspace());
  });

  test('the founders list cannot be read or changed from the app', async () => {
    await allowFounder('father@example.com');
    await assertFails(getDoc(doc(father(), 'founders/father@example.com')));
    await assertFails(setDoc(doc(stranger(), 'founders/stranger@example.com'), {}));
  });

  test('cannot take over an existing family by "founding" it again', async () => {
    await seedFamily();
    // Even someone allowed to found families cannot re-found an existing one.
    await allowFounder('stranger@example.com');
    const db = stranger();
    const b = writeBatch(db);
    b.set(doc(db, `workspaces/${WID}`), { name: 'Mine', createdBy: 'stranger', schemaVersion: 1 });
    b.set(doc(db, `workspaces/${WID}/members/stranger`), { role: 'admin', status: 'active', email: 'stranger@example.com' });
    await assertFails(b.commit());
  });

  test('signed-out users can do nothing', async () => {
    await seedFamily();
    await assertFails(getDoc(doc(anonymous(), `workspaces/${WID}`)));
    await assertFails(setDoc(doc(anonymous(), `workspaces/${WID}/transactions/t1`), txn()));
  });
});

describe('outsiders', () => {
  test('cannot read the family or its records, even knowing the IDs', async () => {
    await seedFamily();
    await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), `workspaces/${WID}/transactions/t1`), { amountPaise: 1 }));
    await assertFails(getDoc(doc(stranger(), `workspaces/${WID}`)));
    await assertFails(getDoc(doc(stranger(), `workspaces/${WID}/transactions/t1`)));
    await assertFails(getDoc(doc(stranger(), `workspaces/${WID}/members/father`)));
  });

  test('cannot add themselves as a member without an invite', async () => {
    await seedFamily();
    const db = stranger();
    await assertFails(setDoc(doc(db, `workspaces/${WID}/members/stranger`), { role: 'member', status: 'active', email: 'stranger@example.com' }));
  });

  test('cannot point their profile at a family they are not in', async () => {
    await seedFamily();
    await assertFails(setDoc(doc(stranger(), 'users/stranger'), { workspaceId: WID, updatedAt: serverTimestamp() }));
  });
});

describe('invites and joining', () => {
  test('only the admin can invite', async () => {
    await seedFamily({ withMother: true });
    const invite = { workspaceId: WID, workspaceName: 'Family', invitedByName: 'Father', role: 'member', createdAt: serverTimestamp() };
    await assertSucceeds(setDoc(doc(father(), 'invites/aunt@example.com'), { ...invite, invitedBy: 'father' }));
    await assertFails(setDoc(doc(mother(), 'invites/uncle@example.com'), { ...invite, invitedBy: 'mother' }));
  });

  test('an invite cannot make someone an admin', async () => {
    await seedFamily();
    await assertFails(setDoc(doc(father(), 'invites/mother@example.com'), {
      workspaceId: WID, role: 'admin', invitedBy: 'father', createdAt: serverTimestamp(),
    }));
  });

  test('the invited person joins (email matched case-insensitively) and removes the invite', async () => {
    await seedFamily();
    await assertSucceeds(setDoc(doc(father(), 'invites/mother@example.com'), {
      workspaceId: WID, role: 'member', invitedBy: 'father', createdAt: serverTimestamp(),
    }));
    const db = mother();
    const b = writeBatch(db);
    b.set(doc(db, `workspaces/${WID}/members/mother`), {
      role: 'member', status: 'active', email: 'mother@example.com', displayName: 'Mother', createdAt: serverTimestamp(),
    });
    b.set(doc(db, 'users/mother'), { workspaceId: WID, updatedAt: serverTimestamp() });
    b.delete(doc(db, 'invites/mother@example.com'));
    await assertSucceeds(b.commit());
    await assertSucceeds(getDoc(doc(db, `workspaces/${WID}`)));
  });

  test('cannot join as admin even with an invite', async () => {
    await seedFamily();
    await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'invites/mother@example.com'), { workspaceId: WID, role: 'member', invitedBy: 'father' }));
    await assertFails(setDoc(doc(mother(), `workspaces/${WID}/members/mother`), { role: 'admin', status: 'active', email: 'mother@example.com' }));
  });

  test('an unverified email cannot use an invite', async () => {
    await seedFamily();
    await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'invites/mother@example.com'), { workspaceId: WID, role: 'member', invitedBy: 'father' }));
    const unverified = env.authenticatedContext('mother', { email: 'mother@example.com', email_verified: false }).firestore();
    await assertFails(setDoc(doc(unverified, `workspaces/${WID}/members/mother`), { role: 'member', status: 'active', email: 'mother@example.com' }));
  });
});

describe('membership changes', () => {
  test('a member cannot promote themselves to admin', async () => {
    await seedFamily({ withMother: true });
    await assertFails(updateDoc(doc(mother(), `workspaces/${WID}/members/mother`), { role: 'admin' }));
  });

  test('the admin cannot remove or demote themselves (no lock-out)', async () => {
    await seedFamily({ withMother: true });
    await assertFails(updateDoc(doc(father(), `workspaces/${WID}/members/father`), { role: 'member' }));
    await assertFails(deleteDoc(doc(father(), `workspaces/${WID}/members/father`)));
  });

  test('a removed member loses access', async () => {
    await seedFamily({ withMother: true });
    await assertSucceeds(updateDoc(doc(father(), `workspaces/${WID}/members/mother`), { status: 'removed' }));
    await assertFails(getDoc(doc(mother(), `workspaces/${WID}`)));
  });
});

describe('family records', () => {
  test('both members can add and read transactions', async () => {
    await seedFamily({ withMother: true });
    await assertSucceeds(setDoc(doc(father(), `workspaces/${WID}/transactions/t1`), txn()));
    await assertSucceeds(getDoc(doc(mother(), `workspaces/${WID}/transactions/t1`)));
    await assertSucceeds(setDoc(doc(mother(), `workspaces/${WID}/transactions/t2`), txn({ createdBy: 'mother', updatedBy: 'mother' })));
  });

  test('amounts must be whole positive paise', async () => {
    await seedFamily();
    const db = father();
    await assertFails(setDoc(doc(db, `workspaces/${WID}/transactions/t1`), txn({ amountPaise: 2650.5 })));
    await assertFails(setDoc(doc(db, `workspaces/${WID}/transactions/t1`), txn({ amountPaise: 0 })));
    await assertFails(setDoc(doc(db, `workspaces/${WID}/transactions/t1`), txn({ amountPaise: -100 })));
    await assertFails(setDoc(doc(db, `workspaces/${WID}/transactions/t1`), txn({ amountPaise: '2650' })));
  });

  test('dates must be plain YYYY-MM-DD text', async () => {
    await seedFamily();
    await assertFails(setDoc(doc(father(), `workspaces/${WID}/transactions/t1`), txn({ date: '01/11/2026' })));
  });

  test('a transfer must use two different accounts', async () => {
    await seedFamily();
    const transfer = { type: 'transfer', fromAccountId: 'cash', toAccountId: 'cash', accountId: null, categoryId: null };
    await assertFails(setDoc(doc(father(), `workspaces/${WID}/transactions/t1`), txn(transfer)));
    await assertSucceeds(setDoc(doc(father(), `workspaces/${WID}/transactions/t1`), txn({ ...transfer, toAccountId: 'bank' })));
  });

  test('nobody can pretend to be the other parent or rewrite who created a record', async () => {
    await seedFamily({ withMother: true });
    await assertFails(setDoc(doc(mother(), `workspaces/${WID}/transactions/t1`), txn({ createdBy: 'father', updatedBy: 'mother' })));
    await assertSucceeds(setDoc(doc(father(), `workspaces/${WID}/transactions/t1`), txn()));
    await assertFails(updateDoc(doc(mother(), `workspaces/${WID}/transactions/t1`), { createdBy: 'mother', updatedBy: 'mother', updatedAt: serverTimestamp(), version: 2 }));
    await assertSucceeds(updateDoc(doc(mother(), `workspaces/${WID}/transactions/t1`), { amountPaise: 300000, updatedBy: 'mother', updatedAt: serverTimestamp(), version: 2 }));
  });

  test('records can only be soft-deleted, never erased', async () => {
    await seedFamily();
    await assertSucceeds(setDoc(doc(father(), `workspaces/${WID}/transactions/t1`), txn()));
    await assertFails(deleteDoc(doc(father(), `workspaces/${WID}/transactions/t1`)));
    await assertSucceeds(updateDoc(doc(father(), `workspaces/${WID}/transactions/t1`), { deleted: true, updatedBy: 'father', updatedAt: serverTimestamp(), version: 2 }));
  });

  test('the edit log is append-only and signed by its author', async () => {
    await seedFamily({ withMother: true });
    const path = `workspaces/${WID}/edits/e1`;
    const entry = { transactionId: 't1', editedBy: 'father', editedAt: serverTimestamp(), baseVersion: 0, version: 1, deleted: false, state: {} };
    await assertFails(setDoc(doc(mother(), path), entry));
    await assertSucceeds(setDoc(doc(father(), path), entry));
    await assertFails(updateDoc(doc(father(), path), { version: 9 }));
    await assertFails(deleteDoc(doc(father(), path)));
  });

  test('accounts and notes are validated too', async () => {
    await seedFamily();
    const stamp = { createdBy: 'father', createdAt: serverTimestamp(), updatedBy: 'father', updatedAt: serverTimestamp(), version: 1, deleted: false };
    await assertSucceeds(setDoc(doc(father(), `workspaces/${WID}/accounts/cash`), { ...stamp, name: 'ரொக்கம்', type: 'CASH', openingBalancePaise: 2500000, openingBalanceDate: '2026-11-01', isActive: true }));
    await assertFails(setDoc(doc(father(), `workspaces/${WID}/accounts/bank`), { ...stamp, name: 'Bank', type: 'SAVINGS', openingBalancePaise: 1.5, openingBalanceDate: '2026-11-01', isActive: true }));
    await assertFails(setDoc(doc(father(), `workspaces/${WID}/notes/n1`), { ...stamp, title: '', description: '', date: '2026-11-01' }));
  });
});

describe('security audit hardening', () => {
  const MAX = 10000000000000; // Rs 10,000 crore in paise, as Money.MAX_AMOUNT_PAISE

  test('amounts above the app limit are refused, so totals can never overflow', async () => {
    await seedFamily();
    const db = father();
    await assertSucceeds(setDoc(doc(db, `workspaces/${WID}/transactions/t1`), txn({ amountPaise: MAX })));
    await assertFails(setDoc(doc(db, `workspaces/${WID}/transactions/t2`), txn({ amountPaise: MAX + 1 })));
    await assertFails(setDoc(doc(db, `workspaces/${WID}/transactions/t3`), txn({ amountPaise: 9007199254740991 })));
  });

  test('opening balances stay within the same limit, either way', async () => {
    await seedFamily();
    const stamp = { createdBy: 'father', createdAt: serverTimestamp(), updatedBy: 'father', updatedAt: serverTimestamp(), version: 1, deleted: false };
    const account = (paise) => ({ ...stamp, name: 'Bank', type: 'SAVINGS', openingBalancePaise: paise, openingBalanceDate: '2026-11-01', isActive: true });
    await assertSucceeds(setDoc(doc(father(), `workspaces/${WID}/accounts/a1`), account(-MAX)));
    await assertFails(setDoc(doc(father(), `workspaces/${WID}/accounts/a2`), account(MAX + 1)));
    await assertFails(setDoc(doc(father(), `workspaces/${WID}/accounts/a3`), account(-MAX - 1)));
  });

  test('the admin can remove a member but not change their role, email, or erase them', async () => {
    await seedFamily({ withMother: true });
    const db = father();
    await assertFails(updateDoc(doc(db, `workspaces/${WID}/members/mother`), { role: 'admin' }));
    await assertFails(updateDoc(doc(db, `workspaces/${WID}/members/mother`), { email: 'someone@example.com' }));
    await assertFails(updateDoc(doc(db, `workspaces/${WID}/members/mother`), { status: 'banana' }));
    await assertFails(deleteDoc(doc(db, `workspaces/${WID}/members/mother`)));
    await assertSucceeds(updateDoc(doc(db, `workspaces/${WID}/members/mother`), { status: 'removed' }));
    await assertSucceeds(updateDoc(doc(db, `workspaces/${WID}/members/mother`), { status: 'active' }));
  });

  test('joining must use up the invite in the same step', async () => {
    await seedFamily();
    await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'invites/mother@example.com'), { workspaceId: WID, role: 'member', invitedBy: 'father' }));
    await assertFails(setDoc(doc(mother(), `workspaces/${WID}/members/mother`), {
      role: 'member', status: 'active', email: 'mother@example.com', displayName: 'Mother', createdAt: serverTimestamp(),
    }));
  });

  test('a new membership cannot carry extra fields', async () => {
    await seedFamily();
    await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'invites/mother@example.com'), { workspaceId: WID, role: 'member', invitedBy: 'father' }));
    const db = mother();
    const b = writeBatch(db);
    b.set(doc(db, `workspaces/${WID}/members/mother`), {
      role: 'member', status: 'active', email: 'mother@example.com', displayName: 'Mother', createdAt: serverTimestamp(), isOwner: true,
    });
    b.delete(doc(db, 'invites/mother@example.com'));
    await assertFails(b.commit());
  });
});
