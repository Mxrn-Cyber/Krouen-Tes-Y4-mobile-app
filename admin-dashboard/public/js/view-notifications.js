// Notifications: news for app users. Published ones show in the app's Notifications (bell) tab,
// newest first, with a dot on the bell until the person opens it.
import { h, toast, openModal, confirmDialog, field, fmtAgo } from './ui.js';

export async function renderNotifications(view, ctx) {
  const { store } = ctx;
  const [notes, recipes] = await Promise.all([store.listAnnouncements(), store.listRecipes()]);
  const published = recipes.filter(r => r.status === 'published' && typeof r.numId === 'number');
  const recipeName = n => published.find(r => r.numId === n)?.name?.en || '';
  let head = null;
  // Only redraw if the admin is still on this page
  const reload = () => { if (head?.isConnected) renderNotifications(view, ctx); };

  async function setStatus(n, status) {
    await store.saveAnnouncement({ ...n, status });
    toast(status === 'published' ? 'Sent to the app' : 'Taken off the app');
    reload();
  }

  async function remove(n) {
    if (await confirmDialog('Delete notification?', `“${n.titleEn}” will be removed from the app for good.`, 'Delete', true)) {
      await store.deleteAnnouncement(n.id);
      toast('Notification deleted');
      reload();
    }
  }

  function edit(existing) {
    const n = existing || { titleEn: '', titleKm: '', bodyEn: '', bodyKm: '', imageUrl: '', recipeNumId: null, status: 'published' };
    const titleEn = h('input', { value: n.titleEn, maxlength: 80 });
    const titleKm = h('input', { value: n.titleKm || '', maxlength: 80, lang: 'km' });
    const bodyEn = h('textarea', { rows: 3, maxlength: 500 }, n.bodyEn || '');
    const bodyKm = h('textarea', { rows: 3, maxlength: 500, lang: 'km' }, n.bodyKm || '');
    const image = h('input', { value: n.imageUrl || '', placeholder: 'https://…', type: 'url' });
    const recipe = h('select', {},
      h('option', { value: '' }, 'No link'),
      published.map(r => h('option', { value: r.numId, selected: r.numId === n.recipeNumId }, r.name?.en || '(no name)')));
    const status = h('select', {},
      h('option', { value: 'published', selected: n.status === 'published' }, existing?.publishedAt ? 'Show in the app' : 'Send now'),
      h('option', { value: 'draft', selected: n.status !== 'published' }, 'Save as draft (not in the app)'));
    const error = h('div', { class: 'error-text' });

    openModal({
      title: existing ? 'Edit notification' : 'New notification',
      body: h('div', {},
        h('div', { class: 'row' }, field('Title (English)', titleEn), field('Title (Khmer)', titleKm)),
        h('div', { class: 'row' }, field('Message (English)', bodyEn), field('Message (Khmer)', bodyKm)),
        field('Picture (optional)', image, 'A link to a photo you have the right to use.'),
        field('Opens a recipe when tapped (optional)', recipe),
        field('Status', status, existing?.publishedAt ? 'Editing doesn’t show it as new again.' : 'People see a dot on the bell until they open it.'),
        error),
      actions: [
        { label: 'Cancel' },
        {
          label: 'Save', kind: 'primary', onClick: async () => {
            error.textContent = '';
            if (!titleEn.value.trim() && !titleKm.value.trim()) { error.textContent = 'Enter a title.'; return false; }
            if (!bodyEn.value.trim() && !bodyKm.value.trim()) { error.textContent = 'Enter a message.'; return false; }
            const url = image.value.trim();
            if (url && !/^https:\/\//i.test(url)) { error.textContent = 'The picture link must start with https://'; return false; }
            await store.saveAnnouncement({
              ...n,
              titleEn: titleEn.value.trim(), titleKm: titleKm.value.trim(),
              bodyEn: bodyEn.value.trim(), bodyKm: bodyKm.value.trim(),
              imageUrl: url, recipeNumId: recipe.value ? Number(recipe.value) : null,
              status: status.value
            });
            toast(status.value === 'published' ? 'Saved. It’s in the app now.' : 'Draft saved');
            reload();
          }
        }
      ]
    });
    titleEn.focus();
  }

  const rows = notes.map(n => h('tr', {},
    h('td', { class: 'two-line' }, h('strong', { text: n.titleEn || n.titleKm }), h('span', { class: 'km', text: n.titleKm || '' })),
    h('td', { class: 'muted', text: recipeName(n.recipeNumId) || '—' }),
    h('td', {}, h('span', { class: `pill ${n.status === 'published' ? 'published' : 'hidden'}`, text: n.status === 'published' ? 'In the app' : 'Draft' })),
    h('td', { class: 'muted', text: n.publishedAt ? fmtAgo(n.publishedAt) : '—' }),
    h('td', { class: 'actions' },
      n.status === 'published'
        ? h('button', { class: 'btn btn-small', onClick: () => setStatus(n, 'draft') }, 'Take off')
        : h('button', { class: 'btn btn-small btn-primary', onClick: () => setStatus(n, 'published') }, 'Send'),
      h('button', { class: 'btn btn-small', onClick: () => edit(n) }, 'Edit'),
      h('button', { class: 'btn btn-small btn-danger', onClick: () => remove(n) }, 'Delete'))));

  head = h('div', { class: 'view-head' },
      h('div', {}, h('h1', { text: 'Notifications' }),
        h('p', { class: 'muted', text: 'News in the app’s Notifications tab. Phones don’t get a pop-up alert yet: people see a dot on the bell next time they open the app.' })),
      h('button', { class: 'btn btn-primary', onClick: () => edit(null) }, '+ New notification'));
  view.replaceChildren(
    head,
    h('div', { class: 'table-card' },
      notes.length
        ? h('table', {},
          h('thead', {}, h('tr', {}, ['Title', 'Opens recipe', 'Status', 'Sent', ''].map(t => h('th', { text: t })))),
          h('tbody', {}, rows))
        : h('div', { class: 'empty', text: 'No notifications yet.' }))
  );
}
