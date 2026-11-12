export function show(element, html) {
  element.innerHTML = html;
}

export function write(markup) {
  document.write(markup);
}

export function insert(element, markup) {
  element.insertAdjacentHTML('beforeend', markup);
}
