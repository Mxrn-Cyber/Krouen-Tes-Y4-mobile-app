// Categories: add, rename (English + Khmer), reorder, hide, choose an icon and where dishes come from.
import { h, toast, openModal, confirmDialog, field, slugify } from './ui.js';
import { SOURCE_TYPES } from './defaults.js';

export async function renderCategories(view, ctx) {
  const { store } = ctx;
  const [categories, recipes] = await Promise.all([store.listCategories(), store.listRecipes()]);
  const countFor = id => recipes.filter(r => r.categoryId === id).length;
  const sourceLabel = c => (SOURCE_TYPES.find(([k]) => k === c.source?.type) || SOURCE_TYPES[0])[1] +
    (c.source?.value ? `: ${c.source.value}` : '');

  const reload = () => renderCategories(view, ctx);

  async function move(index, delta) {
    const ids = categories.map(c => c.id);
    const j = index + delta;
    if (j < 0 || j >= ids.length) return;
    [ids[index], ids[j]] = [ids[j], ids[index]];
    await store.reorderCategories(ids);
    reload();
  }

  async function toggleHidden(c) {
    await store.saveCategory({ ...c, hidden: !c.hidden });
    toast(c.hidden ? `${c.nameEn} is visible in the app` : `${c.nameEn} is hidden from the app`);
    reload();
  }

  async function remove(c) {
    const n = countFor(c.id);
    if (n > 0) {
      toast(`Move or delete its ${n} recipe${n > 1 ? 's' : ''} first`, 'error');
      return;
    }
    if (await confirmDialog('Delete category?', `“${c.nameEn}” will disappear from the app.`, 'Delete', true)) {
      await store.deleteCategory(c.id);
      toast('Category deleted');
      reload();
    }
  }

  const rows = categories.map((c, i) => h('tr', {},
    h('td', { class: 'emoji', text: c.icon || '·' }),
    h('td', { class: 'two-line' }, h('strong', { text: c.nameEn }), h('span', { class: 'km', text: c.nameKm || '—' })),
    h('td', { text: countFor(c.id) }),
    h('td', { class: 'muted', text: sourceLabel(c) + (c.adultOnly ? ' · 18+' : '') }),
    h('td', {}, h('span', { class: `pill ${c.hidden ? 'hidden' : 'active'}`, text: c.hidden ? 'Hidden' : 'Visible' })),
    h('td', { class: 'actions' },
      h('button', { class: 'icon-btn', title: 'Move up', 'aria-label': `Move ${c.nameEn} up`, disabled: i === 0, onClick: () => move(i, -1) }, '↑'),
      h('button', { class: 'icon-btn', title: 'Move down', 'aria-label': `Move ${c.nameEn} down`, disabled: i === categories.length - 1, onClick: () => move(i, 1) }, '↓'),
      h('button', { class: 'btn btn-small', onClick: () => toggleHidden(c) }, c.hidden ? 'Show' : 'Hide'),
      h('button', { class: 'btn btn-small', onClick: () => editCategory(c) }, 'Edit'),
      h('button', { class: 'btn btn-small btn-danger', onClick: () => remove(c) }, 'Delete'))
  ));

  function editCategory(existing) {
    const isNew = !existing;
    const c = existing ? { ...existing, source: { ...(existing.source || { type: 'none', value: '' }) } }
      : { id: '', nameEn: '', nameKm: '', icon: '', hidden: false, adultOnly: false, order: categories.length + 1, source: { type: 'none', value: '' } };
    const nameEn = h('input', { value: c.nameEn, maxlength: 40, required: true });
    const nameKm = h('input', { value: c.nameKm, maxlength: 40, class: 'km' });
    const icon = h('input', { value: c.icon, maxlength: 4, placeholder: '🍲' });
    const idInput = h('input', { value: c.id, maxlength: 40, disabled: !isNew });
    if (isNew) nameEn.addEventListener('input', () => { if (!idInput.dataset.touched) idInput.value = slugify(nameEn.value); });
    idInput.addEventListener('input', () => (idInput.dataset.touched = '1'));
    const sourceType = h('select', {}, SOURCE_TYPES.map(([k, label]) => h('option', { value: k, selected: k === c.source.type }, label)));
    const sourceValue = h('input', { value: c.source.value, placeholder: 'e.g. Seafood' });
    const sourceHint = h('div', { class: 'hint' });
    const adult = h('input', { type: 'checkbox', checked: c.adultOnly });
    const hidden = h('input', { type: 'checkbox', checked: c.hidden });
    const error = h('div', { class: 'error-text' });
    const syncSource = () => {
      const t = sourceType.value;
      sourceValue.parentElement.hidden = !(t === 'mealCategory' || t === 'mealSearch');
      sourceHint.textContent = t === 'mealSearch' ? 'Words to search for, separated by commas (e.g. cookie, biscuit).'
        : t === 'mealCategory' ? 'A TheMealDB category name, e.g. Seafood, Dessert, Vegetarian.'
          : t === 'cocktails' ? 'Cocktails always ask “Are you 18 or older?” first.' : '';
      if (t === 'cocktails') adult.checked = true;
    };
    sourceType.addEventListener('change', syncSource);

    const body = h('div', {},
      h('div', { class: 'row' }, field('Name (English)', nameEn), field('Name (Khmer)', nameKm)),
      h('div', { class: 'row' }, field('Icon (emoji)', icon), field('ID', idInput, isNew ? 'Used in links; can’t change later.' : 'Can’t be changed.')),
      h('div', { class: 'section-title', text: 'Dishes' }),
      field('Show', sourceType),
      h('div', { class: 'field' }, h('label', { text: 'Online source' }), sourceValue),
      sourceHint,
      h('div', { class: 'section-title', text: 'Visibility' }),
      h('label', { class: 'check' }, adult, 'Adults only (asks “18 or older?”)'),
      h('label', { class: 'check' }, hidden, 'Hide from the app'),
      error);
    openModal({
      title: isNew ? 'New category' : `Edit ${c.nameEn}`,
      body,
      actions: [
        { label: 'Cancel' },
        {
          label: 'Save', kind: 'primary', onClick: async () => {
            const id = isNew ? slugify(idInput.value || nameEn.value) : c.id;
            if (!nameEn.value.trim()) { error.textContent = 'Enter an English name.'; return false; }
            if (!id) { error.textContent = 'Enter an ID (letters and numbers).'; return false; }
            if (id === 'all') { error.textContent = '“all” is used by the app for “All”. Choose another ID.'; return false; }
            if (isNew && categories.some(x => x.id === id)) { error.textContent = 'That ID is already used.'; return false; }
            const t = sourceType.value;
            if ((t === 'mealCategory' || t === 'mealSearch') && !sourceValue.value.trim()) { error.textContent = 'Enter what to look for online.'; return false; }
            await store.saveCategory({
              ...c, id, nameEn: nameEn.value.trim(), nameKm: nameKm.value.trim(), icon: icon.value.trim(),
              adultOnly: adult.checked || t === 'cocktails', hidden: hidden.checked,
              source: { type: t, value: (t === 'mealCategory' || t === 'mealSearch') ? sourceValue.value.trim() : '' }
            });
            toast(isNew ? 'Category added' : 'Category saved');
            reload();
          }
        }
      ]
    });
    syncSource();
    nameEn.focus();
  }

  view.replaceChildren(
    h('div', { class: 'view-head' },
      h('div', {}, h('h1', { text: 'Categories' }),
        h('p', { class: 'muted', text: 'The chips on the app’s Categories tab, in this order. Changes reach phones within seconds.' })),
      h('button', { class: 'btn btn-primary', onClick: () => editCategory(null) }, '+ New category')),
    h('div', { class: 'table-card' },
      categories.length
        ? h('table', {},
          h('thead', {}, h('tr', {}, ['', 'Name', 'Recipes', 'Online source', 'In app', ''].map(t => h('th', { text: t })))),
          h('tbody', {}, rows))
        : h('div', { class: 'empty', text: 'No categories yet. Import the built-in content on Overview, or add one.' }))
  );
}
