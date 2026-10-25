// Data access with Firebase (Auth: Google sign-in for admins; Firestore: content and users).
import { initializeApp } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js';
import {
  getAuth, GoogleAuthProvider, onAuthStateChanged, signInWithPopup, signOut
} from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js';
import {
  initializeFirestore, collection, doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, addDoc,
  writeBatch, serverTimestamp, query, orderBy
} from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-firestore.js';
import { DEFAULT_CATEGORIES } from './defaults.js';

const toDate = t => (t && typeof t.toDate === 'function') ? t.toDate() : null;

// Category: Firestore fields ↔ dashboard shape
function categoryFromDoc(d) {
  const x = d.data();
  return {
    id: d.id, nameEn: x.nameEn || d.id, nameKm: x.nameKm || '', icon: x.icon || '',
    order: x.order ?? 999, hidden: !!x.hidden, adultOnly: !!x.adultOnly || x.drink === 'cocktail'
  };
}

function categoryToDoc(c) {
  return {
    nameEn: c.nameEn.trim(), nameKm: (c.nameKm || '').trim(), icon: (c.icon || '').trim(),
    order: Number(c.order) || 0, hidden: !!c.hidden, adultOnly: !!c.adultOnly,
    // Old online-source fields (TheMealDB / TheCocktailDB) are cleared
    mealDbCategory: null, mealDbSearch: [], drink: null,
    updatedAt: serverTimestamp()
  };
}

function recipeFromDoc(d) {
  const x = d.data();
  return { ...x, id: d.id, createdAt: toDate(x.createdAt), updatedAt: toDate(x.updatedAt) };
}

export async function createFirebaseStore(config) {
  const app = initializeApp(config);
  const auth = getAuth(app);
  // Missing optional fields are skipped instead of failing the whole save
  const db = initializeFirestore(app, { ignoreUndefinedProperties: true });

  return {
    demo: false,

    // ----- Auth -----
    onAuth(callback) {
      return onAuthStateChanged(auth, u => callback(u ? { uid: u.uid, email: u.email, name: u.displayName } : null));
    },
    signIn() {
      return signInWithPopup(auth, new GoogleAuthProvider());
    },
    signOut() {
      return signOut(auth);
    },
    async isAdmin(uid) {
      try {
        return (await getDoc(doc(db, 'admins', uid))).exists();
      } catch (e) {
        return false;
      }
    },
    async requestAdmin(user) {
      await setDoc(doc(db, 'adminRequests', user.uid), {
        email: user.email || '', name: user.name || '', requestedAt: serverTimestamp()
      });
    },

    // ----- Categories -----
    async listCategories() {
      const snap = await getDocs(query(collection(db, 'categories'), orderBy('order')));
      return snap.docs.map(categoryFromDoc);
    },
    async saveCategory(c) {
      await setDoc(doc(db, 'categories', c.id), categoryToDoc(c), { merge: true });
    },
    async deleteCategory(id) {
      await deleteDoc(doc(db, 'categories', id));
    },
    async reorderCategories(ids) {
      const batch = writeBatch(db);
      ids.forEach((id, i) => batch.update(doc(db, 'categories', id), { order: i + 1 }));
      await batch.commit();
    },

    // ----- Recipes -----
    async listRecipes() {
      const snap = await getDocs(collection(db, 'recipes'));
      return snap.docs.map(recipeFromDoc);
    },
    async saveRecipe(r) {
      const { id, createdAt, updatedAt, ...data } = r;
      data.updatedAt = serverTimestamp();
      if (id) {
        await setDoc(doc(db, 'recipes', id), data, { merge: true });
        return id;
      }
      data.createdAt = serverTimestamp();
      const ref = await addDoc(collection(db, 'recipes'), data);
      return ref.id;
    },
    async setRecipeStatus(id, status, extra = {}) {
      await updateDoc(doc(db, 'recipes', id), { ...extra, status, updatedAt: serverTimestamp() });
    },
    async deleteRecipe(id) {
      await deleteDoc(doc(db, 'recipes', id));
    },

    // ----- Users -----
    async listUsers() {
      const snap = await getDocs(collection(db, 'users'));
      return snap.docs.map(d => {
        const x = d.data();
        return {
          uid: d.id, name: x.name || '', phone: x.phone || '', blocked: !!x.blocked,
          createdAt: toDate(x.createdAt), lastSeenAt: toDate(x.lastSeenAt)
        };
      });
    },
    async setBlocked(uid, blocked) {
      await updateDoc(doc(db, 'users', uid), { blocked });
    },

    // ----- Admins -----
    async listAdmins() {
      const snap = await getDocs(collection(db, 'admins'));
      return snap.docs.map(d => ({ uid: d.id, ...d.data(), addedAt: toDate(d.data().addedAt) }));
    },
    async listAdminRequests() {
      const snap = await getDocs(collection(db, 'adminRequests'));
      return snap.docs.map(d => ({ uid: d.id, ...d.data(), requestedAt: toDate(d.data().requestedAt) }));
    },
    async approveAdmin(req, by) {
      const batch = writeBatch(db);
      batch.set(doc(db, 'admins', req.uid), { email: req.email || '', name: req.name || '', addedBy: by.email || '', addedAt: serverTimestamp() });
      batch.delete(doc(db, 'adminRequests', req.uid));
      await batch.commit();
    },
    async rejectAdminRequest(uid) {
      await deleteDoc(doc(db, 'adminRequests', uid));
    },
    async removeAdmin(uid) {
      await deleteDoc(doc(db, 'admins', uid));
    },

    // ----- First-time import -----
    // ----- Home page -----
    async getHomeSettings() {
      const snap = await getDoc(doc(db, 'settings', 'home'));
      return snap.exists() ? snap.data() : {};
    },
    async saveHomeSettings(s) {
      await setDoc(doc(db, 'settings', 'home'), {
        featured: s.featured, firstCategory: s.firstCategory || null,
        updatedAt: serverTimestamp()
      });
    },

    // ----- Notifications (the app's bell tab) -----
    async listAnnouncements() {
      const snap = await getDocs(collection(db, 'announcements'));
      return snap.docs.map(d => {
        const x = d.data();
        return { ...x, id: d.id, createdAt: toDate(x.createdAt), updatedAt: toDate(x.updatedAt), publishedAt: toDate(x.publishedAt) };
      }).sort((a, b) => (b.createdAt?.getTime() ?? Infinity) - (a.createdAt?.getTime() ?? Infinity));
    },
    async saveAnnouncement(a) {
      const { id, createdAt, updatedAt, publishedAt, ...data } = a;
      data.updatedAt = serverTimestamp();
      // The first time it goes out it gets a date; editing it later doesn't make it "new" again
      data.publishedAt = publishedAt || (a.status === 'published' ? serverTimestamp() : null);
      if (id) {
        await setDoc(doc(db, 'announcements', id), data, { merge: true });
        return id;
      }
      data.createdAt = serverTimestamp();
      const ref = await addDoc(collection(db, 'announcements'), data);
      return ref.id;
    },
    async deleteAnnouncement(id) {
      await deleteDoc(doc(db, 'announcements', id));
    },

    async importBuiltIns(recipes) {
      // Categories admins already have are left as they are
      const existing = new Set((await getDocs(collection(db, 'categories'))).docs.map(d => d.id));
      const added = DEFAULT_CATEGORIES.filter(c => !existing.has(c.id));
      const batch = writeBatch(db);
      added.forEach(c => batch.set(doc(db, 'categories', c.id), categoryToDoc(c)));
      recipes.forEach(r => batch.set(doc(db, 'recipes', r.slug || `built-in${r.numId}`), {
        ...r, createdAt: serverTimestamp(), updatedAt: serverTimestamp()
      }));
      await batch.commit();
      return { categories: added.length, recipes: recipes.length };
    }
  };
}
