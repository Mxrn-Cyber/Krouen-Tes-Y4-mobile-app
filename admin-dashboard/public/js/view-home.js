// Home page of the app: which recipes the Featured row shows (and their order),
// and which category chip is selected first. "Recent recipe" always shows the newest recipes.
import { h, toast, field } from './ui.js';

export async function renderHome(view, ctx) {
  const { store } = ctx;
  const [settings, recipes, categories] = await Promise.all([store.getHomeSettings(), store.listRecipes(), store.listCategories()]);
  const published = recipes.filter(r => r.status === 'published' && typeof r.numId === 'number')
    .sort((a, b) => b.numId - a.numId); // same order the app uses (first built-in first)
  const byNum = new Map(published.map(r => [r.numId, r]));
  let featured = (settings.featured || []).filter(n => byNum.has(n));
  let mode = featured.length ? 'pick' : 'all';

  // ----- Khmer dishes row -----
  const pickBox = h('div', {});
  const modeAll = h('input', { type: 'radio', name: 'khmer-mode', value: 'all', checked: mode === 'all' });
  const modePick = h('input', { type: 'radio', name: 'khmer-mode', value: 'pick', checked: mode === 'pick' });
  [modeAll, modePick].forEach(r => r.addEventListener('change', () => { mode = r.value; drawPick(); }));

  function drawPick() {
    if (mode === 'all') {
      pickBox.replaceChildren(h('p', { class: 'hint', text: `All ${published.length} published recipes show, oldest first.` }));
      return;
    }
    const rest = published.filter(r => !featured.includes(r.numId));
    const addSel = h('select', { 'aria-label': 'Recipe to add' },
      rest.length ? rest.map(r => h('option', { value: r.numId }, r.name?.en || '(no name)')) : [h('option', { text: 'All recipes are on Home' })]);
    const addBtn = h('button', { class: 'btn', disabled: !rest.length }, 'Add to Home');
    addBtn.addEventListener('click', () => { featured = [...featured, Number(addSel.value)]; drawPick(); });

    const move = (i, d) => {
      const j = i + d;
      if (j < 0 || j >= featured.length) return;
      const next = [...featured];
      [next[i], next[j]] = [next[j], next[i]];
      featured = next;
      drawPick();
    };
    pickBox.replaceChildren(
      featured.length
        ? h('ol', { class: 'pick-list' }, featured.map((n, i) => {
          const r = byNum.get(n);
          return h('li', {},
            r.photo?.url ? h('img', { class: 'thumb', src: r.photo.url, alt: '', loading: 'lazy', referrerpolicy: 'no-referrer' }) : h('div', { class: 'thumb' }),
            h('div', { class: 'two-line grow' }, h('strong', { text: r.name?.en || '(no name)' }), h('span', { class: 'km', text: r.name?.km || '' })),
            h('button', { class: 'icon-btn', title: 'Move up', 'aria-label': 'Move up', disabled: i === 0, onClick: () => move(i, -1) }, '↑'),
            h('button', { class: 'icon-btn', title: 'Move down', 'aria-label': 'Move down', disabled: i === featured.length - 1, onClick: () => move(i, 1) }, '↓'),
            h('button', { class: 'icon-btn', title: 'Take off Home', 'aria-label': 'Take off Home', onClick: () => { featured = featured.filter(x => x !== n); drawPick(); } }, '✕'));
        }))
        : h('p', { class: 'hint', text: 'No recipes picked yet. Until you add some, the app shows all of them.' }),
      h('div', { class: 'toolbar', style: 'margin-top:10px' }, addSel, addBtn));
  }
  drawPick();

  // ----- First category chip -----
  const visible = categories.filter(c => !c.hidden && !c.adultOnly);
  const firstCategory = h('select', {},
    h('option', { value: '' }, visible.length ? `The first one (${visible[0].nameEn})` : 'The first one'),
    visible.map(c => h('option', { value: c.id, selected: c.id === settings.firstCategory }, `${c.icon || ''} ${c.nameEn}`.trim())));

  const save = h('button', { class: 'btn btn-primary' }, 'Save Home page');
  save.addEventListener('click', async () => {
    save.disabled = true;
    try {
      await store.saveHomeSettings({
        featured: mode === 'pick' ? featured : [],
        firstCategory: firstCategory.value || null
      });
      toast('Home page saved. Phones update within seconds.');
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      save.disabled = false;
    }
  });

  view.replaceChildren(
    h('div', { class: 'view-head' },
      h('div', {}, h('h1', { text: 'Home page' }),
        h('p', { class: 'muted', text: 'What people see first when they open the app. Only published recipes show.' })),
      save),
    h('div', { class: 'card', style: 'margin-bottom:14px' },
      h('h2', { class: 'card-title', text: '1. Featured dishes row' }),
      published.length
        ? h('div', {},
          h('label', { class: 'check' }, modeAll, 'Show all published recipes'),
          h('label', { class: 'check' }, modePick, 'Choose which recipes show, and their order'),
          pickBox)
        : h('p', { class: 'hint', text: 'No published recipes yet. Add or publish some on Recipes.' })),
    h('div', { class: 'card' },
      h('h2', { class: 'card-title', text: '2. Category row' }),
      field('Category chip selected first', firstCategory,
        'Its recipes fill the row under the chips. People can tap other chips. 18+ categories don’t show on Home.'),
      h('p', { class: 'hint', text: '“Recent recipe” always shows the newest published recipes.' }))
  );
}
