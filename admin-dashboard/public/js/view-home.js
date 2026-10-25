// Home page of the app: which Khmer dishes the first row shows (and their order),
// and which TheMealDB groups fill the "Trending" and "Recent recipe" rows.
import { h, toast, field } from './ui.js';
import { mealCategories } from './online.js';

const DEFAULT_TRENDING = 'Beef';
const DEFAULT_RECENT = 'Chicken';

export async function renderHome(view, ctx) {
  const { store } = ctx;
  const [settings, recipes] = await Promise.all([store.getHomeSettings(), store.listRecipes()]);
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
      pickBox.replaceChildren(h('p', { class: 'hint', text: `All ${published.length} published Khmer recipes show, in the usual order.` }));
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

  // ----- Trending / Recent rows -----
  let categories = [];
  let listError = '';
  try {
    categories = await mealCategories();
  } catch (e) {
    listError = e.message;
  }
  const pickCategory = (current, fallback) => {
    const value = current || fallback;
    if (!categories.length) return h('input', { value, maxlength: 40 });
    const options = categories.includes(value) ? categories : [value, ...categories];
    return h('select', {}, options.map(c => h('option', { value: c, selected: c === value }, c)));
  };
  const trending = pickCategory(settings.trendingCategory, DEFAULT_TRENDING);
  const recent = pickCategory(settings.recentCategory, DEFAULT_RECENT);

  const save = h('button', { class: 'btn btn-primary' }, 'Save Home page');
  save.addEventListener('click', async () => {
    const trendingValue = trending.value.trim();
    const recentValue = recent.value.trim();
    if (!trendingValue || !recentValue) return toast('Choose a group for both rows', 'error');
    save.disabled = true;
    try {
      await store.saveHomeSettings({
        featured: mode === 'pick' ? featured : [],
        trendingCategory: trendingValue,
        recentCategory: recentValue
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
        h('p', { class: 'muted', text: 'What people see first when they open the app. Hidden online dishes never show here.' })),
      save),
    h('div', { class: 'card', style: 'margin-bottom:14px' },
      h('h2', { class: 'card-title', text: '1. Khmer dishes row' }),
      published.length
        ? h('div', {},
          h('label', { class: 'check' }, modeAll, 'Show all published Khmer recipes'),
          h('label', { class: 'check' }, modePick, 'Choose which recipes show, and their order'),
          pickBox)
        : h('p', { class: 'hint', text: 'No published Khmer recipes yet. Add or publish some on Recipes.' })),
    h('div', { class: 'card' },
      h('h2', { class: 'card-title', text: '2. World dishes rows' }),
      listError ? h('div', { class: 'callout warn', text: `${listError} You can type a TheMealDB group name instead.` }) : null,
      h('div', { class: 'row' },
        field('“Trending” row (and the chip selected first)', trending, 'People can still tap other chips.'),
        field('“Recent recipe” row', recent)))
  );
}
