// Demo data store: same API as the Firebase store, in memory, so the dashboard can be tried
// without Firebase (open index.html?demo). Users are made-up code names with fake numbers.
import { DEFAULT_CATEGORIES } from './defaults.js';

function seeded(seed) {
  let s = seed;
  return () => (s = (s * 16807) % 2147483647) / 2147483647;
}

function demoUsers() {
  const rand = seeded(42);
  const users = [];
  const now = Date.now();
  for (let i = 1; i <= 64; i++) {
    const daysAgo = Math.floor(Math.pow(rand(), 0.7) * 84);
    const createdAt = new Date(now - daysAgo * 86_400_000);
    users.push({
      uid: `demo-${i}`,
      name: `Cook ${String(i).padStart(2, '0')}`,
      phone: `+85500000${String(100 + i).padStart(4, '0')}`,
      blocked: i % 23 === 0,
      createdAt,
      lastSeenAt: new Date(createdAt.getTime() + rand() * (now - createdAt.getTime()))
    });
  }
  return users;
}

export function createDemoStore() {
  let categories = [];
  let recipes = [];
  let users = demoUsers();
  let admins = [{ uid: 'demo-admin', email: 'admin@example.com', name: 'Demo admin', addedAt: new Date() }];
  let requests = [{ uid: 'demo-req', email: 'new.helper@example.com', name: 'New helper', requestedAt: new Date() }];
  let nextId = 1;
  const me = { uid: 'demo-admin', email: 'admin@example.com', name: 'Demo admin' };

  return {
    demo: true,
    onAuth(cb) { setTimeout(() => cb(me), 0); return () => {}; },
    signIn() { return Promise.resolve(); },
    signOut() { location.href = location.pathname; return Promise.resolve(); },
    async isAdmin() { return true; },
    async requestAdmin() {},

    async listCategories() { return [...categories].sort((a, b) => a.order - b.order); },
    async saveCategory(c) {
      const i = categories.findIndex(x => x.id === c.id);
      if (i >= 0) categories[i] = { ...categories[i], ...c }; else categories.push({ ...c });
    },
    async deleteCategory(id) { categories = categories.filter(c => c.id !== id); },
    async reorderCategories(ids) { ids.forEach((id, i) => { const c = categories.find(x => x.id === id); if (c) c.order = i + 1; }); },

    async listRecipes() { return recipes.map(r => ({ ...r })); },
    async saveRecipe(r) {
      const now = new Date();
      if (r.id) {
        const i = recipes.findIndex(x => x.id === r.id);
        recipes[i] = { ...recipes[i], ...r, updatedAt: now };
        return r.id;
      }
      const id = `demo-recipe-${nextId++}`;
      recipes.push({ ...r, id, createdAt: now, updatedAt: now });
      return id;
    },
    async setRecipeStatus(id, status, extra = {}) { const r = recipes.find(x => x.id === id); if (r) { Object.assign(r, extra); r.status = status; r.updatedAt = new Date(); } },
    async deleteRecipe(id) { recipes = recipes.filter(r => r.id !== id); },

    async listUsers() { return users.map(u => ({ ...u })); },
    async setBlocked(uid, blocked) { const u = users.find(x => x.uid === uid); if (u) u.blocked = blocked; },

    async listAdmins() { return admins.map(a => ({ ...a })); },
    async listAdminRequests() { return requests.map(r => ({ ...r })); },
    async approveAdmin(req) { admins.push({ uid: req.uid, email: req.email, name: req.name, addedAt: new Date() }); requests = requests.filter(r => r.uid !== req.uid); },
    async rejectAdminRequest(uid) { requests = requests.filter(r => r.uid !== uid); },
    async removeAdmin(uid) { admins = admins.filter(a => a.uid !== uid); },

    async importBuiltIns(list) {
      const now = new Date();
      recipes = list.map((r, i) => ({ ...r, id: r.slug || `built-in-${i}`, createdAt: now, updatedAt: now }));
      // One made-up recipe waiting for review, to show the approve flow
      recipes.push({
        id: 'demo-pending', numId: -200, name: { en: 'Grilled corn with scallion oil', km: 'ពោតអាំងខ្ទឹមស្លឹក' },
        category: { en: 'Salad', km: 'ញាំ និងសាឡាដ' }, categoryId: 'salad', region: 'everywhere',
        description: { en: 'Demo recipe sent by a user.', km: '' }, serves: 2, cookMinutes: 20,
        ingredients: [{ en: 'Corn', km: 'ពោត', amount: { en: '2', km: '2' } }],
        steps: { en: ['Grill the corn.'], km: ['អាំងពោត។'] }, photo: null,
        status: 'pending', authorName: 'Cook 07', createdAt: now, updatedAt: now
      });
      const have = new Set(categories.map(c => c.id));
      const added = DEFAULT_CATEGORIES.filter(c => !have.has(c.id)).map(c => ({ ...c, source: { ...c.source } }));
      categories = [...categories, ...added];
      return { categories: added.length, recipes: list.length };
    }
  };
}
