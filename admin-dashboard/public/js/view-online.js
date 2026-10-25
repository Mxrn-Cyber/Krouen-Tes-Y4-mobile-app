// Online dishes: browse the world dishes and drinks the app shows (TheMealDB / TheCocktailDB)
// and hide any of them from the app. They can't be edited here: they belong to those services.
import { h, toast, fmtAgo } from './ui.js';
import { ONLINE_SOURCES, mealCategories, mealAreas, listDishes } from './online.js';

// Remembered while the dashboard is open, so coming back shows the same list
const last = { tab: 'browse', source: 'category', value: 'Beef', search: '' };

export async function renderOnline(view, ctx) {
  const { store } = ctx;
  let hidden = await store.listHiddenDishes();
  const hiddenIds = () => new Set(hidden.map(d => d.id));

  const head = h('div', { class: 'view-head' },
    h('div', {}, h('h1', { text: 'Online dishes' }),
      h('p', { class: 'muted', text: 'World food and drinks come live from TheMealDB and TheCocktailDB. You can’t edit them, but you can hide any dish: it disappears from Home, Categories, Search and lists in the app.' })));

  const tabs = () => h('div', { class: 'tabs' },
    h('button', { class: `tab ${last.tab === 'browse' ? 'active' : ''}`, onClick: () => { last.tab = 'browse'; draw(); } }, 'Browse'),
    h('button', { class: `tab ${last.tab === 'hidden' ? 'active' : ''}`, onClick: () => { last.tab = 'hidden'; draw(); } }, `Hidden (${hidden.length})`));

  async function toggle(dish, button) {
    button.disabled = true;
    try {
      if (hiddenIds().has(dish.id)) {
        await store.showDish(dish.id);
        hidden = hidden.filter(d => d.id !== dish.id);
        toast(`“${dish.name}” is back in the app`);
      } else {
        await store.hideDish(dish);
        hidden = [...hidden, { ...dish, hiddenAt: new Date() }];
        toast(`“${dish.name}” is hidden from the app`);
      }
    } catch (e) {
      toast(e.message, 'error');
    }
    if (head.isConnected) draw(false); // still on this page
  }

  // ----- Browse -----
  const results = h('div', {});
  let dishes = null;
  let loadToken = 0;

  function drawResults() {
    if (dishes === null) return results.replaceChildren(h('p', { class: 'muted', text: 'Loading dishes…' }));
    if (!dishes.length) return results.replaceChildren(h('div', { class: 'table-card' }, h('div', { class: 'empty', text: 'No dishes found.' })));
    const ids = hiddenIds();
    const hiddenHere = dishes.filter(d => ids.has(d.id)).length;
    results.replaceChildren(
      h('p', { class: 'muted', text: `${dishes.length} dishes${hiddenHere ? ` · ${hiddenHere} hidden` : ''}` }),
      h('div', { class: 'dish-grid' }, dishes.map(d => {
        const isHidden = ids.has(d.id);
        const btn = h('button', { class: `btn btn-small ${isHidden ? 'btn-primary' : ''}` }, isHidden ? 'Show in app' : 'Hide');
        btn.addEventListener('click', () => toggle(d, btn));
        return h('div', { class: `dish ${isHidden ? 'is-hidden' : ''}` },
          h('img', { src: d.image ? `${d.image}/preview` : null, alt: '', loading: 'lazy', referrerpolicy: 'no-referrer',
            onError: e => { if (!e.currentTarget.dataset.retry && d.image) { e.currentTarget.dataset.retry = '1'; e.currentTarget.src = d.image; } } }),
          h('div', { class: 'dish-body' },
            h('strong', { text: d.name }),
            isHidden ? h('span', { class: 'pill hidden', text: 'Hidden' }) : null,
            btn));
      })));
  }

  async function load() {
    const token = ++loadToken;
    dishes = null;
    drawResults();
    try {
      const list = await listDishes(last.source, last.source === 'search' ? last.search : last.value);
      if (token !== loadToken) return; // a newer list was asked for
      dishes = list;
    } catch (e) {
      if (token !== loadToken) return;
      dishes = [];
      results.replaceChildren(h('div', { class: 'callout warn', text: e.message }));
      return;
    }
    drawResults();
  }

  function browseToolbar() {
    const sourceSel = h('select', { 'aria-label': 'What to list' },
      ONLINE_SOURCES.map(([k, label]) => h('option', { value: k, selected: k === last.source }, label)));
    const valueSel = h('select', { 'aria-label': 'Which one' });
    const searchInput = h('input', { type: 'search', placeholder: 'Dish name, e.g. curry', value: last.search, class: 'grow' });
    const go = h('button', { class: 'btn btn-primary' }, 'Show');

    async function fillValues() {
      const needsList = last.source === 'category' || last.source === 'area';
      valueSel.hidden = !needsList;
      searchInput.hidden = last.source !== 'search';
      if (!needsList) return;
      valueSel.replaceChildren(h('option', { text: 'Loading…' }));
      try {
        const values = last.source === 'category' ? await mealCategories() : await mealAreas();
        if (!values.includes(last.value)) last.value = values[0] || '';
        valueSel.replaceChildren(...values.map(v => h('option', { value: v, selected: v === last.value }, v)));
      } catch (e) {
        valueSel.replaceChildren(h('option', { text: 'Couldn’t load the list' }));
        toast(e.message, 'error');
      }
    }

    sourceSel.addEventListener('change', async () => {
      last.source = sourceSel.value;
      await fillValues();
      if (last.source !== 'search') load();
      else searchInput.focus();
    });
    valueSel.addEventListener('change', () => { last.value = valueSel.value; load(); });
    searchInput.addEventListener('keydown', e => { if (e.key === 'Enter') { last.search = searchInput.value; load(); } });
    go.addEventListener('click', () => { last.search = searchInput.value; load(); });
    fillValues();
    return h('div', { class: 'toolbar' }, sourceSel, valueSel, searchInput, go);
  }

  // ----- Hidden list -----
  function hiddenTable() {
    if (!hidden.length) return h('div', { class: 'table-card' }, h('div', { class: 'empty', text: 'Nothing is hidden. Use Browse to find a dish and hide it.' }));
    const list = [...hidden].sort((a, b) => (b.hiddenAt?.getTime() || 0) - (a.hiddenAt?.getTime() || 0));
    return h('div', { class: 'table-card' }, h('table', {},
      h('thead', {}, h('tr', {}, ['', 'Name', 'Type', 'Hidden', ''].map(t => h('th', { text: t })))),
      h('tbody', {}, list.map(d => {
        const btn = h('button', { class: 'btn btn-small' }, 'Show in app');
        btn.addEventListener('click', () => toggle(d, btn));
        return h('tr', {},
          h('td', {}, d.image ? h('img', { class: 'thumb', src: d.image, alt: '', loading: 'lazy', referrerpolicy: 'no-referrer' }) : h('div', { class: 'thumb' })),
          h('td', { text: d.name || d.id }),
          h('td', { class: 'muted', text: d.kind === 'drink' ? 'Drink' : 'Food' }),
          h('td', { class: 'muted', text: fmtAgo(d.hiddenAt) }),
          h('td', { class: 'actions' }, btn));
      }))));
  }

  let toolbar = null;
  function draw(full = true) {
    if (last.tab === 'hidden') {
      view.replaceChildren(head, tabs(), hiddenTable());
      return;
    }
    if (full || !toolbar) toolbar = browseToolbar();
    view.replaceChildren(head, tabs(), toolbar, results);
    if (full || dishes === null) load(); else drawResults();
  }

  draw();
}
