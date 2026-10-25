// Recipes: list, filter, add/edit (English + Khmer), publish, hide, approve or reject user recipes.
import { h, toast, openModal, confirmDialog, field, fmtAgo, slugify, REGIONS, STATUSES } from './ui.js';

const statusLabel = s => (STATUSES.find(([k]) => k === s) || [s, s])[1];

export async function renderRecipes(view, ctx, filters = {}) {
  const { store } = ctx;
  const [recipes, categories] = await Promise.all([store.listRecipes(), store.listCategories()]);
  const catName = id => categories.find(c => c.id === id)?.nameEn || id || '—';
  const reload = () => renderRecipes(view, ctx, state);
  const state = { text: '', status: '', category: '', ...filters };

  // Pending first, then newest change
  recipes.sort((a, b) => (a.status === 'pending' ? -1 : 0) - (b.status === 'pending' ? -1 : 0)
    || (b.updatedAt?.getTime() || 0) - (a.updatedAt?.getTime() || 0));

  const search = h('input', { class: 'grow', type: 'search', placeholder: 'Search by name (English or Khmer)', value: state.text });
  const statusSel = h('select', {}, h('option', { value: '', text: 'All statuses' }),
    STATUSES.map(([k, l]) => h('option', { value: k, selected: state.status === k }, l)));
  const catSel = h('select', {}, h('option', { value: '', text: 'All categories' }),
    categories.map(c => h('option', { value: c.id, selected: state.category === c.id }, c.nameEn)));
  const tbody = h('tbody');
  const count = h('span', { class: 'muted' });

  function draw() {
    const t = state.text.trim().toLowerCase();
    const list = recipes.filter(r =>
      (!state.status || r.status === state.status) &&
      (!state.category || r.categoryId === state.category) &&
      (!t || `${r.name?.en || ''} ${r.name?.km || ''}`.toLowerCase().includes(t)));
    count.textContent = `${list.length} of ${recipes.length}`;
    tbody.replaceChildren(...(list.length ? list.map(row) : [h('tr', {}, h('td', { colspan: 6, class: 'empty', text: 'No recipes match.' }))]));
  }
  search.addEventListener('input', () => { state.text = search.value; draw(); });
  statusSel.addEventListener('change', () => { state.status = statusSel.value; draw(); });
  catSel.addEventListener('change', () => { state.category = catSel.value; draw(); });

  async function setStatus(r, status, message) {
    // The app needs a number for every published recipe (user recipes arrive without one)
    const extra = {};
    if (status === 'published' && typeof r.numId !== 'number') {
      const numbers = recipes.map(x => x.numId).filter(n => typeof n === 'number');
      extra.numId = Math.min(-100, ...numbers) - 1;
    }
    await store.setRecipeStatus(r.id, status, extra);
    toast(message);
    reload();
  }

  async function remove(r) {
    if (await confirmDialog('Delete recipe?', `“${r.name?.en}” will be removed for good. To keep it but take it off the app, use Hide instead.`, 'Delete', true)) {
      await store.deleteRecipe(r.id);
      toast('Recipe deleted');
      reload();
    }
  }

  function row(r) {
    const actions = [];
    if (r.status === 'pending') {
      actions.push(h('button', { class: 'btn btn-small btn-primary', onClick: () => setStatus(r, 'published', 'Approved and published') }, 'Approve'));
      actions.push(h('button', { class: 'btn btn-small', onClick: () => setStatus(r, 'rejected', 'Rejected') }, 'Reject'));
    } else if (r.status === 'published') {
      actions.push(h('button', { class: 'btn btn-small', onClick: () => setStatus(r, 'hidden', 'Hidden from the app') }, 'Hide'));
    } else {
      actions.push(h('button', { class: 'btn btn-small', onClick: () => setStatus(r, 'published', 'Published') }, 'Publish'));
    }
    actions.push(h('button', { class: 'btn btn-small', onClick: () => editRecipe(r) }, 'Edit'));
    actions.push(h('button', { class: 'btn btn-small btn-danger', onClick: () => remove(r) }, 'Delete'));
    return h('tr', {},
      h('td', {}, r.photo?.url
        ? h('img', { class: 'thumb', src: r.photo.url, alt: '', loading: 'lazy', referrerpolicy: 'no-referrer',
          onError: e => e.currentTarget.replaceWith(h('div', { class: 'thumb', title: 'Photo didn’t load' })) })
        : h('div', { class: 'thumb' })),
      h('td', { class: 'two-line' }, h('strong', { text: r.name?.en || '(no name)' }), h('span', { class: 'km', text: r.name?.km || '' })),
      h('td', { text: catName(r.categoryId) }),
      h('td', {}, h('span', { class: `pill ${r.status}`, text: statusLabel(r.status) })),
      h('td', { class: 'two-line' }, h('strong', { text: fmtAgo(r.updatedAt) }), h('span', { text: r.authorName ? `by ${r.authorName}` : '' })),
      h('td', { class: 'actions' }, actions));
  }

  // ---------- Editor ----------
  function editRecipe(existing) {
    const r = existing ? structuredClone(existing) : {
      name: { en: '', km: '' }, description: { en: '', km: '' }, categoryId: categories[0]?.id || '',
      region: 'everywhere', serves: 4, cookMinutes: 30, ingredients: [], steps: { en: [], km: [] },
      photo: { url: '', author: '', license: '', page: '' }, status: 'published', authorName: 'Krouen Tes'
    };
    r.photo = r.photo || { url: '', author: '', license: '', page: '' };
    const inp = (value, attrs = {}) => h('input', { value: value ?? '', ...attrs });
    const nameEn = inp(r.name?.en, { maxlength: 80 });
    const nameKm = inp(r.name?.km, { maxlength: 80, class: 'km' });
    const descEn = h('textarea', { rows: 2 }, r.description?.en || '');
    const descKm = h('textarea', { rows: 2, class: 'km' }, r.description?.km || '');
    const cat = h('select', {}, categories.map(c => h('option', { value: c.id, selected: c.id === r.categoryId }, `${c.icon || ''} ${c.nameEn}`.trim())));
    const region = h('select', {}, REGIONS.map(([k, l]) => h('option', { value: k, selected: k === (r.region || 'everywhere') }, l)));
    const serves = inp(r.serves, { type: 'number', min: 1, max: 50 });
    const minutes = inp(r.cookMinutes, { type: 'number', min: 1, max: 1440 });
    const status = h('select', {}, STATUSES.map(([k, l]) => h('option', { value: k, selected: k === r.status }, l)));
    const photoUrl = inp(r.photo.url, { type: 'url', placeholder: 'https://upload.wikimedia.org/…' });
    const photoAuthor = inp(r.photo.author, { placeholder: 'Photographer' });
    const photoLicense = inp(r.photo.license, { placeholder: 'e.g. CC BY-SA 4.0' });
    const photoPage = inp(r.photo.page, { type: 'url', placeholder: 'Link to the photo’s page' });

    const ingBox = h('div');
    const addIngredient = (ing = { en: '', km: '', amount: { en: '', km: '' } }) => {
      const rowEl = h('div', { class: 'ing-row' },
        h('input', { value: ing.en || '', placeholder: 'Ingredient (English)', 'data-k': 'en' }),
        h('input', { value: ing.km || '', placeholder: 'គ្រឿងផ្សំ (ខ្មែរ)', class: 'km', 'data-k': 'km' }),
        h('input', { value: ing.amount?.en || '', placeholder: 'Amount', 'data-k': 'aen' }),
        h('input', { value: ing.amount?.km || '', placeholder: 'បរិមាណ', class: 'km', 'data-k': 'akm' }),
        h('button', { class: 'icon-btn', title: 'Remove', 'aria-label': 'Remove ingredient', onClick: () => rowEl.remove() }, '✕'));
      rowEl.dataset.image = ing.image || '';
      ingBox.append(rowEl);
    };
    (r.ingredients.length ? r.ingredients : [undefined]).forEach(i => addIngredient(i));
    const stepsEn = h('textarea', { rows: 6, placeholder: 'One step per line' }, (r.steps?.en || []).join('\n'));
    const stepsKm = h('textarea', { rows: 6, class: 'km', placeholder: 'មួយជំហានក្នុងមួយបន្ទាត់' }, (r.steps?.km || []).join('\n'));
    const error = h('div', { class: 'error-text' });

    const body = h('div', {},
      h('div', { class: 'row' }, field('Name (English)', nameEn), field('Name (Khmer)', nameKm)),
      h('div', { class: 'row' }, field('Short description (English)', descEn), field('Short description (Khmer)', descKm)),
      h('div', { class: 'row' }, field('Category', cat), field('Region', region), field('Serves', serves), field('Cook time (min)', minutes)),
      h('div', { class: 'section-title', text: 'Ingredients' }),
      ingBox,
      h('button', { class: 'btn btn-small', onClick: () => addIngredient() }, '+ Add ingredient'),
      h('div', { class: 'section-title', text: 'Steps' }),
      h('div', { class: 'row' }, field('English', stepsEn), field('Khmer', stepsKm)),
      h('div', { class: 'section-title', text: 'Photo' }),
      field('Photo link', photoUrl, 'Use free photos (e.g. Wikimedia Commons) and fill in the credit below.'),
      h('div', { class: 'row' }, field('Photographer', photoAuthor), field('Licence', photoLicense), field('Photo page', photoPage)),
      h('div', { class: 'section-title', text: 'Status' }),
      field('Status', status),
      error);

    openModal({
      title: existing ? `Edit ${r.name?.en || 'recipe'}` : 'New recipe',
      wide: true,
      body,
      actions: [
        { label: 'Cancel' },
        {
          label: 'Save', kind: 'primary', onClick: async () => {
            const ingredients = [...ingBox.querySelectorAll('.ing-row')].map(el => {
              const v = k => el.querySelector(`[data-k="${k}"]`).value.trim();
              const item = { en: v('en'), km: v('km'), amount: { en: v('aen'), km: v('akm') } };
              if (el.dataset.image) item.image = el.dataset.image;
              return item;
            }).filter(i => i.en || i.km);
            const lines = t => t.value.split('\n').map(s => s.trim()).filter(Boolean);
            const category = categories.find(c => c.id === cat.value);
            if (!nameEn.value.trim()) { error.textContent = 'Enter an English name.'; return false; }
            if (!category) { error.textContent = 'Choose a category (add one on the Categories page first).'; return false; }
            if (!ingredients.length) { error.textContent = 'Add at least one ingredient.'; return false; }
            if (!lines(stepsEn).length && !lines(stepsKm).length) { error.textContent = 'Add at least one step.'; return false; }
            if (photoUrl.value && !/^https:\/\//.test(photoUrl.value.trim())) { error.textContent = 'The photo link must start with https://'; return false; }
            const numbers = recipes.map(x => x.numId).filter(n => typeof n === 'number');
            const data = {
              ...r,
              numId: typeof r.numId === 'number' ? r.numId : Math.min(-100, ...numbers) - 1,
              slug: r.slug || slugify(nameEn.value),
              name: { en: nameEn.value.trim(), km: nameKm.value.trim() },
              description: { en: descEn.value.trim(), km: descKm.value.trim() },
              categoryId: category.id,
              category: { en: category.nameEn, km: category.nameKm || category.nameEn },
              region: region.value,
              serves: Math.max(1, parseInt(serves.value, 10) || 1),
              cookMinutes: Math.max(1, parseInt(minutes.value, 10) || 1),
              ingredients,
              steps: { en: lines(stepsEn), km: lines(stepsKm) },
              photo: photoUrl.value.trim()
                ? { url: photoUrl.value.trim(), author: photoAuthor.value.trim(), license: photoLicense.value.trim(), page: photoPage.value.trim() }
                : null,
              status: status.value
            };
            await store.saveRecipe(data);
            toast(existing ? 'Recipe saved' : 'Recipe added');
            reload();
          }
        }
      ]
    });
    nameEn.focus();
  }

  const pendingCount = recipes.filter(r => r.status === 'pending').length;
  view.replaceChildren(
    h('div', { class: 'view-head' },
      h('div', {}, h('h1', { text: 'Recipes' }),
        h('p', { class: 'muted', text: pendingCount ? `${pendingCount} waiting for review — shown first.` : 'Only Published recipes appear in the app.' })),
      h('button', { class: 'btn btn-primary', disabled: !categories.length, onClick: () => editRecipe(null) }, '+ New recipe')),
    h('div', { class: 'toolbar' }, search, statusSel, catSel, count),
    h('div', { class: 'table-card' },
      h('table', {},
        h('thead', {}, h('tr', {}, ['', 'Name', 'Category', 'Status', 'Updated', ''].map(t => h('th', { text: t })))),
        tbody)));
  draw();
}
