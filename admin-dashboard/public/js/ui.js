// Small DOM helpers shared by every view (no framework).

/** Creates an element. attrs: class, text, html (trusted only), on<Event>, any attribute. */
export function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value === undefined || value === null || value === false) continue;
    if (key === 'class') el.className = value;
    else if (key === 'text') el.textContent = value;
    else if (key === 'html') el.innerHTML = value; // only for our own static markup
    else if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2).toLowerCase(), value);
    else if (key === 'value') el.value = value;
    else if (key === 'checked') el.checked = !!value;
    else if (value === true) el.setAttribute(key, '');
    else el.setAttribute(key, value);
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

let toastTimer;
export function toast(message, kind = 'ok') {
  const el = document.getElementById('toast');
  el.textContent = message;
  el.className = `toast show ${kind === 'error' ? 'error' : ''}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (el.className = 'toast'), 3200);
}

/** Opens the shared <dialog>. actions: [{label, kind, onClick(close) → may return false to keep it open}] */
export function openModal({ title, body, actions = [], wide = false }) {
  const modal = document.getElementById('modal');
  modal.className = `modal ${wide ? 'wide' : ''}`;
  modal.replaceChildren(
    h('div', { class: 'modal-head' },
      h('h2', { text: title }),
      h('button', { class: 'icon-btn', 'aria-label': 'Close', onClick: () => closeModal() }, '✕')),
    h('div', { class: 'modal-body' }, body),
    h('div', { class: 'modal-foot' },
      actions.map(a => h('button', {
        class: `btn ${a.kind === 'primary' ? 'btn-primary' : a.kind === 'danger' ? 'btn-danger' : ''}`,
        onClick: async (e) => {
          const btn = e.currentTarget;
          if (!a.onClick) return closeModal();
          btn.disabled = true;
          try {
            const keep = await a.onClick();
            if (keep !== false) closeModal();
          } catch (err) {
            console.error(err);
            toast(err.message || 'Something went wrong', 'error');
          } finally {
            btn.disabled = false;
          }
        }
      }, a.label)))
  );
  if (!modal.open) modal.showModal();
  return modal;
}

export function closeModal() {
  const modal = document.getElementById('modal');
  if (modal.open) modal.close();
}

export function confirmDialog(title, message, okLabel = 'OK', danger = false) {
  return new Promise(resolve => {
    let answered = false;
    const modal = openModal({
      title,
      body: h('p', { text: message }),
      actions: [
        { label: 'Cancel', onClick: () => { answered = true; resolve(false); } },
        { label: okLabel, kind: danger ? 'danger' : 'primary', onClick: () => { answered = true; resolve(true); } }
      ]
    });
    modal.addEventListener('close', () => { if (!answered) resolve(false); }, { once: true });
  });
}

export function fmtDate(d) {
  if (!d) return '—';
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function fmtAgo(d) {
  if (!d) return '—';
  const days = Math.floor((Date.now() - d.getTime()) / 86_400_000);
  if (days <= 0) return 'Today';
  if (days === 1) return 'Yesterday';
  if (days < 30) return `${days} days ago`;
  return fmtDate(d);
}

/** "+85512345678" → "+855 ••• •• 678": phone numbers are personal data, shown in full only on request. */
export function maskPhone(phone) {
  if (!phone) return '—';
  const cc = phone.startsWith('+855') ? '+855' : phone.slice(0, 3);
  return `${cc} ••• •• ${phone.slice(-3)}`;
}

export function slugify(text) {
  return String(text || '').toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40);
}

export function field(labelText, input, hint) {
  return h('div', { class: 'field' }, h('label', { text: labelText }), input, hint ? h('div', { class: 'hint', text: hint }) : null);
}

export const REGIONS = [
  ['everywhere', 'Everywhere'],
  ['coast', 'Coast (Kampot, Kep)'],
  ['north', 'North (Thai border)'],
  ['northwest', 'Northwest'],
  ['uplands', 'Uplands (Khmer Loeu)']
];

export const STATUSES = [
  ['published', 'Published'],
  ['pending', 'Waiting for review'],
  ['hidden', 'Hidden'],
  ['rejected', 'Rejected']
];
