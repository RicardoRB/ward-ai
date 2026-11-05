import escapeHtml from 'escape-html';
export function f(el) {
  el.innerHTML = escapeHtml(location.search);
}
