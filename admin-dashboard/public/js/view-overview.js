// Overview: key numbers, audience growth (new users per week) and things waiting for an admin.
import { h, toast, fmtAgo } from './ui.js';
import { fromBuiltIn } from './defaults.js';

const WEEKS = 12;
const DAY = 86_400_000;

function startOfWeek(d) {
  const x = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const day = (x.getDay() + 6) % 7; // Monday = 0
  return new Date(x.getTime() - day * DAY);
}

/** New users per week for the last WEEKS weeks, oldest first. */
function weeklySignups(users) {
  const thisWeek = startOfWeek(new Date());
  const weeks = Array.from({ length: WEEKS }, (_, i) => ({ start: new Date(thisWeek.getTime() - (WEEKS - 1 - i) * 7 * DAY), count: 0 }));
  for (const u of users) {
    if (!u.createdAt) continue;
    const idx = Math.floor((startOfWeek(u.createdAt).getTime() - weeks[0].start.getTime()) / (7 * DAY));
    if (idx >= 0 && idx < WEEKS) weeks[idx].count++;
  }
  return weeks;
}

function growthChart(weeks) {
  const W = 640, H = 240, left = 36, right = 12, top = 24, bottom = 34;
  const max = Math.max(4, ...weeks.map(w => w.count));
  const step = (W - left - right) / weeks.length;
  const barW = Math.min(30, step * 0.6);
  const y = v => top + (H - top - bottom) * (1 - v / max);
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  svg.setAttribute('viewBox', `0 0 ${W} ${H}`);
  svg.setAttribute('role', 'img');
  svg.setAttribute('aria-label', 'New users per week, last 12 weeks');
  const add = (tag, attrs, text) => {
    const el = document.createElementNS(ns, tag);
    for (const [k, v] of Object.entries(attrs)) el.setAttribute(k, v);
    if (text !== undefined) el.textContent = text;
    svg.append(el);
    return el;
  };
  // grid: 0, half, max
  [0, Math.round(max / 2), max].forEach(v => {
    add('line', { x1: left, x2: W - right, y1: y(v), y2: y(v), stroke: '#E6E8E7' });
    add('text', { x: left - 8, y: y(v) + 4, 'text-anchor': 'end', 'font-size': 11, fill: '#6B7570' }, v);
  });
  weeks.forEach((w, i) => {
    const cx = left + step * i + step / 2;
    const isLast = i === weeks.length - 1;
    add('rect', {
      x: cx - barW / 2, y: y(w.count), width: barW, height: Math.max(0, y(0) - y(w.count)), rx: 4,
      fill: isLast ? '#00BF63' : '#9BE7B8'
    }).append(Object.assign(document.createElementNS(ns, 'title'), { textContent: `Week of ${w.start.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })}: ${w.count} new` }));
    if (w.count > 0) add('text', { x: cx, y: y(w.count) - 6, 'text-anchor': 'middle', 'font-size': 11, fill: '#1F2A24' }, w.count);
    if (i % 2 === (weeks.length - 1) % 2) {
      add('text', { x: cx, y: H - 12, 'text-anchor': 'middle', 'font-size': 11, fill: '#6B7570' },
        w.start.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' }));
    }
  });
  return svg;
}

export async function renderOverview(view, { store, go }) {
  const [users, recipes, categories] = await Promise.all([store.listUsers(), store.listRecipes(), store.listCategories()]);
  const weekAgo = Date.now() - 7 * DAY;
  const published = recipes.filter(r => r.status === 'published').length;
  const pending = recipes.filter(r => r.status === 'pending');
  const newThisWeek = users.filter(u => u.createdAt && u.createdAt.getTime() >= weekAgo).length;
  const activeThisWeek = users.filter(u => u.lastSeenAt && u.lastSeenAt.getTime() >= weekAgo).length;
  const blocked = users.filter(u => u.blocked).length;

  const weeks = weeklySignups(users);
  const inChart = weeks.reduce((a, w) => a + w.count, 0);
  const last = weeks[weeks.length - 1].count;
  const prev = weeks[weeks.length - 2].count;
  const trend = last === prev ? 'the same as the week before' : last > prev ? `up from ${prev} the week before` : `down from ${prev} the week before`;

  const stat = (label, value, sub, attention) =>
    h('div', { class: `stat ${attention ? 'attention' : ''}` },
      h('div', { class: 'label', text: label }), h('div', { class: 'value', text: value }), sub ? h('div', { class: 'sub', text: sub }) : null);

  const children = [
    h('div', { class: 'view-head' },
      h('div', {}, h('h1', { text: 'Overview' }), h('p', { class: 'muted', text: 'Your app at a glance.' })))
  ];

  if (!recipes.length) {
    const importBtn = h('button', { class: 'btn btn-primary' }, 'Import built-in content');
    importBtn.addEventListener('click', async () => {
      importBtn.disabled = true;
      try {
        const json = await (await fetch('data/khmer_recipes.json')).json();
        const result = await store.importBuiltIns((json.recipes || []).map(fromBuiltIn));
        toast(`Imported ${result.categories} categories and ${result.recipes} recipes`);
        go('overview');
      } catch (e) {
        importBtn.disabled = false;
        toast(e.message, 'error');
      }
    });
    children.push(h('div', { class: 'callout', style: 'margin-bottom:18px; display:flex; gap:16px; align-items:center; justify-content:space-between; flex-wrap:wrap' },
      h('div', {}, h('strong', { text: 'No recipes yet. ' }),
        'Import the 13 Khmer recipes and 16 categories the app ships with, then edit them here.'),
      importBtn));
  }

  children.push(h('div', { class: 'stats' },
    stat('Users', users.length, `${activeThisWeek} active this week`),
    stat('New this week', newThisWeek),
    stat('Published recipes', published),
    stat('Waiting for review', pending.length, pending.length ? 'Open Recipes to approve' : 'Nothing to review', pending.length > 0),
    stat('Categories', categories.length, `${categories.filter(c => c.hidden).length} hidden`),
    stat('Blocked users', blocked)
  ));

  const recent = [...users].filter(u => u.createdAt).sort((a, b) => b.createdAt - a.createdAt).slice(0, 6);
  children.push(h('div', { class: 'grid-2' },
    h('div', { class: 'card chart-wrap' },
      h('h2', { text: `${inChart} new users in the last ${WEEKS} weeks` }),
      h('p', { class: 'muted', style: 'margin:4px 0 12px', text: `This week: ${last}, ${trend}. Total: ${users.length}.` }),
      growthChart(weeks)),
    h('div', { class: 'card' },
      h('h2', { text: 'Newest users' }),
      recent.length
        ? h('ul', { class: 'list-plain' }, recent.map(u => h('li', {}, h('span', { text: u.name || 'No name' }), h('span', { class: 'muted', text: fmtAgo(u.createdAt) }))))
        : h('p', { class: 'muted', text: 'No sign-ups yet.' }),
      pending.length ? h('div', { style: 'margin-top:16px' },
        h('button', { class: 'btn', onClick: () => go('recipes') }, `Review ${pending.length} recipe${pending.length > 1 ? 's' : ''}`)) : null)
  ));

  view.replaceChildren(...children);
}
