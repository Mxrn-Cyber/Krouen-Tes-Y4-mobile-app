// Krouen Tes admin dashboard — start-up, sign-in and navigation.
import { h, toast } from './ui.js';
import { renderOverview } from './view-overview.js';
import { renderCategories } from './view-categories.js';
import { renderRecipes } from './view-recipes.js';
import { renderUsers } from './view-users.js';
import { renderHome } from './view-home.js';
import { renderOnline } from './view-online.js';
import { renderNotifications } from './view-notifications.js';

const VIEWS = {
  overview: renderOverview, home: renderHome, categories: renderCategories, recipes: renderRecipes,
  online: renderOnline, notifications: renderNotifications, users: renderUsers
};
const isDemo = new URLSearchParams(location.search).has('demo');

/** Firebase config: from Firebase Hosting (/__/firebase/init.json), else js/config.js for local testing. */
async function loadConfig() {
  try {
    const res = await fetch('/__/firebase/init.json');
    if (res.ok) return await res.json();
  } catch (e) { /* not on Firebase Hosting */ }
  try {
    const mod = await import('./config.js');
    if (mod.firebaseConfig && !String(mod.firebaseConfig.apiKey).startsWith('PASTE')) return mod.firebaseConfig;
  } catch (e) { /* no local config */ }
  return null;
}

async function start() {
  let store;
  if (isDemo) {
    const { createDemoStore } = await import('./store-demo.js');
    store = createDemoStore();
    document.getElementById('demo-banner').hidden = false;
  } else {
    const config = await loadConfig();
    if (!config) return showSetupNeeded();
    const { createFirebaseStore } = await import('./store-firebase.js');
    store = await createFirebaseStore(config);
  }

  const ctx = { store, user: null, go };
  document.getElementById('btn-signin').addEventListener('click', async () => {
    try {
      await store.signIn();
    } catch (e) {
      if (e.code !== 'auth/popup-closed-by-user') toast(e.message, 'error');
    }
  });
  document.getElementById('btn-signout').addEventListener('click', () => store.signOut());
  document.getElementById('nav').addEventListener('click', e => {
    const btn = e.target.closest('[data-view]');
    if (btn) go(btn.dataset.view);
  });
  window.addEventListener('hashchange', () => show(location.hash.slice(1)));

  store.onAuth(async user => {
    ctx.user = user;
    if (!user) return showSignIn();
    if (!(await store.isAdmin(user.uid))) return showNotAdmin(user, store);
    document.getElementById('signin').hidden = true;
    document.getElementById('shell').hidden = false;
    document.getElementById('me-email').textContent = user.email || '';
    show(location.hash.slice(1) || 'overview');
  });

  function go(name) {
    if (location.hash.slice(1) === name) show(name);
    else location.hash = name;
  }

  async function show(name) {
    if (!ctx.user || document.getElementById('shell').hidden) return;
    const render = VIEWS[name] || VIEWS.overview;
    document.querySelectorAll('.nav-item').forEach(b => b.classList.toggle('active', b.dataset.view === (VIEWS[name] ? name : 'overview')));
    const view = document.getElementById('view');
    view.replaceChildren(h('p', { class: 'muted', text: 'Loading…' }));
    try {
      await render(view, ctx);
    } catch (e) {
      console.error(e);
      view.replaceChildren(h('div', { class: 'callout warn' },
        h('strong', { text: 'Couldn’t load this page. ' }),
        e.code === 'permission-denied'
          ? 'The database refused: check that the Firestore rules from admin-dashboard/firestore.rules are deployed.'
          : (e.message || 'Unknown error')));
    }
    view.focus();
  }
}

function showSignIn() {
  document.getElementById('shell').hidden = true;
  document.getElementById('signin').hidden = false;
  document.getElementById('signin-text').textContent = 'Sign in with the Google account an admin added to the dashboard.';
  document.getElementById('signin-extra').replaceChildren();
  document.getElementById('btn-signin').hidden = false;
}

/** Signed in, but not on the admin list: offer to send a request (first admin is added by hand). */
function showNotAdmin(user, store) {
  document.getElementById('shell').hidden = true;
  document.getElementById('signin').hidden = false;
  document.getElementById('btn-signin').hidden = true;
  document.getElementById('signin-text').textContent = `${user.email} isn’t an admin yet.`;
  const extra = document.getElementById('signin-extra');
  const requestBtn = h('button', { class: 'btn btn-primary btn-wide' }, 'Ask to become an admin');
  requestBtn.addEventListener('click', async () => {
    try {
      await store.requestAdmin(user);
      requestBtn.disabled = true;
      requestBtn.textContent = 'Request sent';
    } catch (e) { toast(e.message, 'error'); }
  });
  extra.replaceChildren(
    requestBtn,
    h('div', { class: 'callout' },
      h('strong', { text: 'First admin? ' }),
      'In the Firebase console open Firestore → start collection ', h('code', { text: 'admins' }),
      ' → document ID ', h('code', { text: user.uid }), ' → add a field ', h('code', { text: 'email' }),
      ' = ', h('code', { text: user.email || '' }), '. Then reload this page.'),
    h('button', { class: 'btn btn-ghost btn-wide', onClick: () => store.signOut() }, 'Use another account')
  );
}

function showSetupNeeded() {
  document.getElementById('signin').hidden = false;
  document.getElementById('btn-signin').hidden = true;
  document.getElementById('signin-text').textContent = 'Firebase isn’t connected yet.';
  document.getElementById('signin-extra').replaceChildren(h('div', { class: 'callout warn' },
    'Deploy this folder to Firebase Hosting (see README.md), or for local testing copy ',
    h('code', { text: 'js/config.example.js' }), ' to ', h('code', { text: 'js/config.js' }),
    ' and paste your web app config. To look around first, open ', h('a', { href: '?demo', text: 'demo mode' }), '.'));
}

start();
