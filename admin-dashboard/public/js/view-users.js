// Users: app users (block / unblock) and dashboard admins (approve requests, remove).
// Phone numbers are personal data: masked by default, shown in full only when an admin asks.
import { h, toast, confirmDialog, fmtDate, fmtAgo, maskPhone } from './ui.js';

export async function renderUsers(view, ctx, tab = 'app') {
  const { store, user: me } = ctx;
  const reload = t => renderUsers(view, ctx, t || tab);

  const tabs = h('div', { class: 'tabs' },
    h('button', { class: `tab ${tab === 'app' ? 'active' : ''}`, onClick: () => reload('app') }, 'App users'),
    h('button', { class: `tab ${tab === 'admins' ? 'active' : ''}`, onClick: () => reload('admins') }, 'Dashboard admins'));

  const head = h('div', { class: 'view-head' },
    h('div', {}, h('h1', { text: 'Users' }),
      h('p', { class: 'muted', text: tab === 'app'
        ? 'People who signed in on the app. Blocked users are signed out and can’t use the app.'
        : 'Google accounts that can open this dashboard.' })));

  if (tab === 'admins') {
    const [admins, requests] = await Promise.all([store.listAdmins(), store.listAdminRequests()]);
    const requestRows = requests.map(r => h('tr', {},
      h('td', { class: 'two-line' }, h('strong', { text: r.name || r.email }), h('span', { text: r.email })),
      h('td', { text: fmtAgo(r.requestedAt) }),
      h('td', { class: 'actions' },
        h('button', { class: 'btn btn-small btn-primary', onClick: async () => {
          if (await confirmDialog('Make admin?', `${r.email} will be able to change all content and block users.`, 'Make admin')) {
            await store.approveAdmin(r, me);
            toast(`${r.email} is now an admin`);
            reload('admins');
          }
        } }, 'Approve'),
        h('button', { class: 'btn btn-small', onClick: async () => { await store.rejectAdminRequest(r.uid); toast('Request removed'); reload('admins'); } }, 'Reject'))));
    const adminRows = admins.map(a => h('tr', {},
      h('td', { class: 'two-line' }, h('strong', { text: a.name || a.email || a.uid }), h('span', { text: a.email || '' })),
      h('td', { text: fmtDate(a.addedAt) }),
      h('td', { class: 'actions' }, a.uid === me.uid
        ? h('span', { class: 'muted', text: 'You' })
        : h('button', { class: 'btn btn-small btn-danger', onClick: async () => {
          if (await confirmDialog('Remove admin?', `${a.email || a.uid} will no longer open the dashboard.`, 'Remove', true)) {
            await store.removeAdmin(a.uid);
            toast('Admin removed');
            reload('admins');
          }
        } }, 'Remove'))));
    view.replaceChildren(head, tabs,
      requests.length ? h('div', { style: 'margin-bottom:18px' },
        h('h2', { style: 'margin-bottom:10px', text: `Requests (${requests.length})` }),
        h('div', { class: 'table-card' }, h('table', {},
          h('thead', {}, h('tr', {}, ['Account', 'Asked', ''].map(t => h('th', { text: t })))),
          h('tbody', {}, requestRows)))) : null,
      h('h2', { style: 'margin-bottom:10px', text: `Admins (${admins.length})` }),
      h('div', { class: 'table-card' }, h('table', {},
        h('thead', {}, h('tr', {}, ['Account', 'Added', ''].map(t => h('th', { text: t })))),
        h('tbody', {}, adminRows))));
    return;
  }

  const users = (await store.listUsers()).sort((a, b) => (b.createdAt?.getTime() || 0) - (a.createdAt?.getTime() || 0));
  const search = h('input', { class: 'grow', type: 'search', placeholder: 'Search by name or last digits of the phone number' });
  const statusSel = h('select', {},
    h('option', { value: '', text: 'Everyone' }), h('option', { value: 'active', text: 'Active' }), h('option', { value: 'blocked', text: 'Blocked' }));
  const count = h('span', { class: 'muted' });
  const tbody = h('tbody');

  async function toggleBlock(u) {
    const blocking = !u.blocked;
    const ok = await confirmDialog(
      blocking ? 'Block this user?' : 'Unblock this user?',
      blocking ? `${u.name || 'This user'} will be signed out and can’t use the app until you unblock them.`
        : `${u.name || 'This user'} can use the app again.`,
      blocking ? 'Block' : 'Unblock', blocking);
    if (!ok) return;
    await store.setBlocked(u.uid, blocking);
    u.blocked = blocking;
    toast(blocking ? 'User blocked' : 'User unblocked');
    draw();
  }

  function row(u) {
    const phone = h('span', { class: 'phone', text: maskPhone(u.phone) });
    const reveal = u.phone ? h('button', { class: 'reveal', onClick: () => { phone.textContent = u.phone; reveal.remove(); } }, 'Show') : null;
    return h('tr', {},
      h('td', { class: 'two-line' }, h('strong', { text: u.name || 'No name' }), h('span', {}, phone, reveal)),
      h('td', { text: fmtDate(u.createdAt) }),
      h('td', { text: fmtAgo(u.lastSeenAt) }),
      h('td', {}, h('span', { class: `pill ${u.blocked ? 'blocked' : 'active'}`, text: u.blocked ? 'Blocked' : 'Active' })),
      h('td', { class: 'actions' },
        h('button', { class: `btn btn-small ${u.blocked ? '' : 'btn-danger'}`, onClick: () => toggleBlock(u) }, u.blocked ? 'Unblock' : 'Block')));
  }

  function draw() {
    const t = search.value.trim().toLowerCase();
    const digits = t.replace(/\D/g, '');
    const list = users.filter(u =>
      (!statusSel.value || (statusSel.value === 'blocked') === u.blocked) &&
      (!t || (u.name || '').toLowerCase().includes(t) || (digits.length >= 3 && (u.phone || '').endsWith(digits))));
    count.textContent = `${list.length} of ${users.length}`;
    tbody.replaceChildren(...(list.length ? list.map(row) : [h('tr', {}, h('td', { colspan: 5, class: 'empty', text: users.length ? 'No users match.' : 'No one has signed in on the app yet.' }))]));
  }
  search.addEventListener('input', draw);
  statusSel.addEventListener('change', draw);

  view.replaceChildren(head, tabs,
    h('div', { class: 'toolbar' }, search, statusSel, count),
    h('div', { class: 'table-card' }, h('table', {},
      h('thead', {}, h('tr', {}, ['User', 'Joined', 'Last seen', 'Status', ''].map(t => h('th', { text: t })))),
      tbody)));
  draw();
}
